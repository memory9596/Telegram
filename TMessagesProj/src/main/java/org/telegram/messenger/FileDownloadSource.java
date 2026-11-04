package org.telegram.messenger;

import java.io.File;

import org.telegram.tgnet.TLRPC;

/** The concrete Telegram file requested by a consumer, independent of its UI. */
public final class FileDownloadSource {
    public final MessageObject message;
    public final TLRPC.Document document;
    public final TLRPC.PhotoSize photoSize;
    public final ImageLocation imageLocation;
    public final String fileName;
    public final String displayName;
    public final String mime;

    private FileDownloadSource(MessageObject message, TLRPC.Document document, TLRPC.Photo photo, TLRPC.PhotoSize size) {
        this.message = message;
        this.document = document;
        photoSize = size;
        imageLocation = size == null ? null : ImageLocation.getForPhoto(size, photo);
        fileName = document != null ? FileLoader.getAttachFileName(document) : FileLoader.getAttachFileName(size);
        displayName = document != null ? FileLoader.getDocumentFileName(document) : fileName;
        mime = document != null ? document.mime_type : "image/jpeg";
    }

    public static FileDownloadSource document(MessageObject message, TLRPC.Document document) {
        if (message == null || message.messageOwner == null || document == null) throw new IllegalArgumentException("Missing document source");
        return new FileDownloadSource(message, document, null, null);
    }

    public static FileDownloadSource photo(MessageObject message, TLRPC.Photo photo, TLRPC.PhotoSize size) {
        if (message == null || message.messageOwner == null || photo == null || size == null) throw new IllegalArgumentException("Missing photo source");
        return new FileDownloadSource(message, null, photo, size);
    }

    public File localFile(int account) {
        if (document != null) return FileDownloadTasks.localFile(account, document, message);
        FileLoader loader = FileLoader.getInstance(account);
        File file = loader.getPathToAttach(photoSize, null, false, true);
        if (file != null && file.isFile()) return file;
        file = loader.getPathToAttach(photoSize, null, true, true);
        return file != null && file.isFile() ? file : null;
    }

    void load(int account) {
        FileLoader loader = FileLoader.getInstance(account);
        if (document != null) loader.loadFile(document, message, FileLoader.PRIORITY_HIGH, message.shouldEncryptPhotoOrVideo() ? 2 : 0);
        else loader.loadFile(imageLocation, message, "jpg", FileLoader.PRIORITY_HIGH, 0);
    }

    void cancel(int account) {
        if (document != null) FileLoader.getInstance(account).cancelLoadFile(document);
        else FileLoader.getInstance(account).cancelLoadFile(photoSize);
    }
}
