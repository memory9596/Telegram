package org.telegram.messenger;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.ProxyFileDescriptorCallback;
import android.os.storage.StorageManager;
import android.provider.OpenableColumns;
import android.system.ErrnoException;
import android.system.OsConstants;

import androidx.annotation.RequiresApi;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DataSource;

import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Read-only seekable external playback, using the same FileLoader as PhotoViewer. */
public class VideoStreamingProvider extends ContentProvider {
    public static final int REQUEST_CODE = 5012;
    private static volatile Session launchedSession;
    static final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private HandlerThread thread;
    private Handler handler;

    static final class Session {
        final MessageObject message;
        final int account;
        final TLRPC.Document document;
        final Uri source;
        final String token;
        final FileDownloadTasks.ExternalOwner owner = new FileDownloadTasks.ExternalOwner(this::finish);
        volatile long lastRead = android.os.SystemClock.elapsedRealtime();
        private int descriptors;
        private Runnable idleRelease;
        private boolean activityReturned;
        private boolean transferActive;
        private boolean finished;
        private final Runnable expiry = new Runnable() {
            @Override public void run() {
                synchronized (Session.this) {
                    if (finished) return;
                    if (expired() || !available()) finish();
                    else AndroidUtilities.runOnUIThread(this, 60_000);
                }
            }
        };
        Session(MessageObject message, TLRPC.Document document, Uri source, String token) {
            this.message = message;
            account = message.currentAccount;
            this.document = document;
            this.source = source;
            this.token = token;
        }
        void touch() { lastRead = android.os.SystemClock.elapsedRealtime(); }
        synchronized boolean available() {
            return !finished && !owner.cancelled && !FileDownloadTasks.isCancelled(account, document)
                    && (!transferActive || FileDownloadTasks.isExternalActive(account, document, owner));
        }
        synchronized void start() {
            FileDownloadTasks.getInstance(account).beginExternal(message, document, owner);
            transferActive = true;
            AndroidUtilities.runOnUIThread(expiry, 60_000);
        }
        synchronized boolean acquire() {
            if (!available()) return false;
            if (idleRelease != null) AndroidUtilities.cancelRunOnUIThread(idleRelease);
            idleRelease = null;
            descriptors++;
            touch();
            if (!transferActive) {
                FileDownloadTasks.getInstance(account).beginExternal(message, document, owner);
                transferActive = true;
            }
            return true;
        }
        synchronized void release() {
            if (descriptors > 0) descriptors--;
            touch();
            if (!finished && activityReturned && descriptors == 0) releaseWhenIdle();
        }
        synchronized void onActivityResult() {
            activityReturned = true;
            if (!finished && descriptors == 0) releaseWhenIdle();
        }
        private synchronized void releaseWhenIdle() {
            if (idleRelease != null) AndroidUtilities.cancelRunOnUIThread(idleRelease);
            idleRelease = new Runnable() {
                @Override public void run() {
                    synchronized (Session.this) {
                        if (idleRelease != this) return;
                        idleRelease = null;
                        if (!finished && activityReturned && descriptors == 0) finish();
                    }
                }
            };
            AndroidUtilities.runOnUIThread(idleRelease, 1_500);
        }
        private void stopTransfer() {
            if (!transferActive) return;
            transferActive = false;
            FileDownloadTasks.getInstance(account).releaseExternal(document, owner);
        }
        synchronized void finish() {
            if (finished) return;
            finished = true;
            if (idleRelease != null) AndroidUtilities.cancelRunOnUIThread(idleRelease);
            idleRelease = null;
            AndroidUtilities.cancelRunOnUIThread(expiry);
            sessions.remove(token, this);
            if (launchedSession == this) launchedSession = null;
            stopTransfer();
        }
        synchronized boolean expired() {
            return android.os.SystemClock.elapsedRealtime() - lastRead >= 30 * 60_000L;
        }
    }

    /** A completed external Activity ends the handoff after its last reader closes. */
    public static void onActivityResult() {
        Session session = launchedSession;
        if (session != null) session.onActivityResult();
    }

    private static void finishExternalPlayback() {
        Session session = launchedSession;
        launchedSession = null;
        if (session != null) session.finish();
    }

    @Override public boolean onCreate() { return true; }

    static Session session(Uri uri) {
        String token = uri == null ? null : uri.getLastPathSegment();
        Session session = token == null ? null : sessions.get(token);
        if (session != null && !session.available()) {
            session.finish();
            return null;
        }
        if (session != null) session.touch();
        return session;
    }

    public static boolean open(Activity activity, MessageObject message, TLRPC.Document document, Runnable beforeLaunch) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()
                || message == null || message.messageOwner == null || document == null) return false;
        finishExternalPlayback();
        Session session = null;
        String token = UUID.randomUUID().toString();
        try {
            File file = FileDownloadTasks.localFile(message.currentAccount, document, message);
            Uri source = file == null ? FileStreamLoadOperation.prepareUri(message.currentAccount, document, message) : Uri.fromFile(file);
            if (source == null || !"file".equals(source.getScheme()) && !"tg".equals(source.getScheme())) return false;
            session = new Session(message, document, source, token);
            sessions.put(token, session);
            session.start();
            Uri uri = Build.VERSION.SDK_INT >= 26
                    ? new Uri.Builder().scheme("content").authority(ApplicationLoader.getApplicationId() + ".video_streaming").appendPath(token).build()
                    : VideoStreamingServer.getInstance().uri(token);
            Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, document.mime_type == null ? "video/mp4" : document.mime_type);
            intent.setClipData(ClipData.newRawUri("video", uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (beforeLaunch != null) beforeLaunch.run();
            launchedSession = session;
            activity.startActivityForResult(intent, REQUEST_CODE);
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            sessions.remove(token);
            launchedSession = null;
            if (session != null) session.finish();
            return false;
        }
    }

    @Override public String getType(Uri uri) {
        Session session = session(uri);
        return session == null ? null : session.document.mime_type == null ? "video/mp4" : session.document.mime_type;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Session session = session(uri);
        if (session == null) return null;
        String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = FileLoader.getDocumentFileName(session.document);
            else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = session.document.size;
        }
        cursor.addRow(row);
        return cursor;
    }

    @RequiresApi(26)
    @Override public synchronized ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Session session = session(uri);
        if (!"r".equals(mode) || session == null || Build.VERSION.SDK_INT < 26) throw new FileNotFoundException("Stream unavailable");
        if (thread == null) {
            thread = new HandlerThread("VideoStreamingProvider");
            thread.start();
            handler = new Handler(thread.getLooper());
        }
        if (!session.acquire()) throw new FileNotFoundException("Stream unavailable");
        StreamCallback callback = new StreamCallback(session);
        try {
            StorageManager manager = getContext() == null ? null : (StorageManager) getContext().getSystemService(android.content.Context.STORAGE_SERVICE);
            if (manager == null) throw new IOException("Storage unavailable");
            return manager.openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_ONLY, callback, handler);
        } catch (IOException | RuntimeException e) {
            callback.onRelease();
            throw new FileNotFoundException(e.toString());
        }
    }

    @RequiresApi(26)
    private static final class StreamCallback extends ProxyFileDescriptorCallback {
        final Session session;
        final DataSource source = new FileStreamLoadOperation();
        long position = -1;
        boolean opened;
        boolean released;
        java.io.RandomAccessFile localFile;
        StreamCallback(Session session) { this.session = session; }
        @Override public long onGetSize() { return session.document.size; }
        @Override public int onRead(long offset, int size, byte[] data) throws ErrnoException {
            session.touch();
            if (released) throw new ErrnoException("onRead", OsConstants.EIO);
            if (offset >= session.document.size) return 0;
            if (!session.available() || !FileDownloadTasks.isExternalActive(session.account, session.document, session.owner)
                    || FileDownloadTasks.isBlocked(session.account, session.document)) {
                onRelease();
                throw new ErrnoException("onRead", OsConstants.EIO);
            }
            try {
                if (localFile == null) {
                    File file = FileDownloadTasks.localFile(session.account, session.document, session.message);
                    if (file != null) {
                        source.close();
                        opened = false;
                        localFile = new java.io.RandomAccessFile(file, "r");
                    }
                }
                if (localFile != null) {
                    localFile.seek(offset);
                    int count = localFile.read(data, 0, (int) Math.min(size, session.document.size - offset));
                    if (count < 0) throw new IOException("Incomplete local stream file");
                    return count;
                }
                if (!opened || offset != position) {
                    source.close();
                    source.open(new DataSpec.Builder().setUri(session.source).setPosition(offset).build());
                    opened = true;
                }
                int count = source.read(data, 0, (int) Math.min(size, session.document.size - offset));
                if (count < 0) throw new IOException("Stream interrupted");
                position = offset + count;
                return count;
            } catch (IOException e) {
                try { source.close(); } catch (IOException ignored) {}
                opened = false;
                throw new ErrnoException("onRead", OsConstants.EIO, e);
            }
        }
        @Override public void onRelease() {
            if (released) return;
            released = true;
            try { source.close(); } catch (IOException e) { FileLog.e(e); }
            try { if (localFile != null) localFile.close(); } catch (IOException e) { FileLog.e(e); }
            session.release();
        }
    }

    @Override public void shutdown() {
        for (Session session : sessions.values()) session.finish();
        if (thread != null) thread.quitSafely();
        thread = null;
        handler = null;
        super.shutdown();
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
