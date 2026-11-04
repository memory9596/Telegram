package org.telegram.messenger

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Copies complete files to public storage and removes incomplete output on cancellation. */
object FileExport {
    private val executor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "FileExport").apply { isDaemon = true }
    }

    enum class Target(private val label: Int) {
        GALLERY(R.string.SaveToGallery), DOWNLOADS(R.string.SaveToDownloads), MUSIC(R.string.SaveToMusic);

        fun label(): String = LocaleController.getString(label)
    }

    fun interface Callback {
        fun done(success: Boolean)
    }

    fun interface Writer {
        @Throws(IOException::class)
        fun write(stream: OutputStream, cancelled: AtomicBoolean)
    }

    fun interface Composer {
        @Throws(IOException::class)
        fun write(source: File, companion: File, stream: OutputStream, cancelled: AtomicBoolean)
    }

    /** The action receives its owner without keeping a closed screen alive during a download. */
    @JvmStatic
    fun <T : Any> onSaved(owner: T, action: Utilities.Callback2<T, Int>): MessagesStorage.IntCallback {
        val reference = WeakReference(owner)
        return MessagesStorage.IntCallback { count -> reference.get()?.let { action.run(it, count) } }
    }

    private data class Request(val account: Int, val source: FileDownloadSource, val target: Target, val companion: FileDownloadSource?)

    private fun request(message: MessageObject?, target: Target?): Request? {
        val owner = message?.messageOwner ?: return null
        val media = MessageObject.getMedia(owner) ?: return null
        val photo = message.getPhoto()
        val document = message.qualityToSave
            ?: message.cachedQuality?.takeIf { it.isCached() }?.document
            ?: message.getDocumentFast()
        val source: FileDownloadSource
        var companion: FileDownloadSource? = null
        if (message.isLivePhoto() || document == null && photo != null) {
            val size = photo?.sizes?.let {
                FileLoader.getClosestPhotoSizeWithSize(it, AndroidUtilities.getPhotoSize(true), false, null, true)
            } ?: return null
            if (FileLoader.getAttachFileName(size).isEmpty()) return null
            source = FileDownloadSource.photo(message, photo, size)
            if (message.isLivePhoto()) {
                companion = FileDownloadSource.document(message, media.document ?: return null)
            }
        } else {
            source = FileDownloadSource.document(message, document ?: return null)
        }
        return Request(message.currentAccount, source, target ?: if (message.isMusic()) Target.MUSIC else Target.DOWNLOADS, companion)
    }

    @JvmStatic
    fun saveMessages(messages: List<MessageObject?>?, target: Target?, onSaved: MessagesStorage.IntCallback?) {
        val requests = messages.orEmpty().mapNotNull { request(it, target) }
            .distinctBy { "${it.account}:${it.source.fileName}:${it.target}" }
        AndroidUtilities.runOnUIThread {
            if (requests.isEmpty()) {
                onSaved?.run(0)
                return@runOnUIThread
            }
            var remaining = requests.size
            var saved = 0
            for (request in requests) {
                val composer = if (request.companion == null) null else Composer { photo, video, stream, cancelled ->
                    MediaController.writeMotionPhotoCancellable(photo, video, stream) { cancelled.get() }
                }
                FileDownloadTasks.getInstance(request.account).export(request.source, request.target, request.companion, composer) { success ->
                    if (success) saved++
                    if (--remaining == 0) onSaved?.run(saved)
                }
            }
        }
    }

    @JvmStatic
    fun save(source: File?, filename: String?, mime: String?, target: Target, cancelled: AtomicBoolean, callback: Callback) {
        save(source, filename, mime, target, cancelled, { stream, token ->
            requireNotNull(source).inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (!token.get()) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    stream.write(buffer, 0, count)
                }
            }
        }, callback)
    }

    @JvmStatic
    fun save(source: File?, filename: String?, mime: String?, target: Target, cancelled: AtomicBoolean, writer: Writer, callback: Callback) {
        executor.execute {
            val resolver = ApplicationLoader.applicationContext.contentResolver
            var output: Uri? = null
            var legacyOutput: File? = null
            var success = false
            try {
                if (cancelled.get()) return@execute
                if (source == null || !source.isFile || AndroidUtilities.isInternalUri(Uri.fromFile(source))) {
                    throw IOException("Invalid export source")
                }
                val video = mime?.startsWith("video/") == true
                val directory = when (target) {
                    Target.DOWNLOADS -> Environment.DIRECTORY_DOWNLOADS
                    Target.MUSIC -> Environment.DIRECTORY_MUSIC
                    Target.GALLERY -> if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                }
                val name = if (target == Target.GALLERY) {
                    AndroidUtilities.generateFileName(if (video) 1 else 0, FileLoader.getFileExtension(source))
                } else {
                    filename?.takeIf { it.isNotEmpty() }?.let { File(it).name } ?: source.name
                }
                val stream = if (Build.VERSION.SDK_INT >= 29) {
                    val collection = when (target) {
                        Target.DOWNLOADS -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        Target.MUSIC -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        Target.GALLERY -> if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, mime ?: "application/octet-stream")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/Telegram")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(collection, values) ?: throw IOException("Cannot create export output")
                    output = uri
                    resolver.openOutputStream(uri)
                } else {
                    val destination = File(Environment.getExternalStoragePublicDirectory(directory), "Telegram")
                    if (!destination.isDirectory && !destination.mkdirs()) throw IOException("Cannot create export directory")
                    uniqueFile(destination, name).also { legacyOutput = it }.outputStream()
                } ?: throw IOException("Cannot open export output")
                stream.use { writer.write(it, cancelled) }
                if (!cancelled.get()) {
                    output?.let { uri ->
                        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                    } ?: legacyOutput?.let { AndroidUtilities.addMediaToGallery(it) }
                    success = true
                }
            } catch (e: Exception) {
                FileLog.e(e)
            } finally {
                if (!success) {
                    try {
                        output?.let { resolver.delete(it, null, null) }
                        legacyOutput?.delete()
                    } catch (e: Exception) {
                        FileLog.e(e)
                    }
                }
                val result = success
                AndroidUtilities.runOnUIThread { callback.done(result) }
            }
        }
    }

    private fun uniqueFile(directory: File, name: String): File {
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val suffix = if (dot > 0) name.substring(dot) else ""
        repeat(10000) { index ->
            val file = File(directory, if (index == 0) name else "$base($index)$suffix")
            if (file.createNewFile()) return file
        }
        throw IOException("Cannot allocate export name")
    }
}
