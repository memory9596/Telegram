package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

import java.io.File;

public class AnimatedFileDrawableStream implements FileLoadOperationStream {

    private FileLoadOperation loadOperation;
    private int dataVersion;
    private TLRPC.Document document;
    private ImageLocation location;
    private Object parentObject;
    private int currentAccount;
    private volatile boolean canceled;
    private final Object sync = new Object();
    private volatile long lastOffset;
    private volatile boolean waitingForLoad;
    private boolean preview;
    private volatile boolean finishedLoadingFile;
    private String finishedFilePath;
    private int loadingPriority;
    private final int cacheType;

    private int debugCanceledCount;
    private boolean debugReportSend;

    public AnimatedFileDrawableStream(TLRPC.Document d, ImageLocation l, Object p, int a, boolean prev, int loadingPriority, int cacheType) {
        document = d;
        location = l;
        parentObject = p;
        currentAccount = a;
        preview = prev;
        this.loadingPriority = loadingPriority;
        this.cacheType = cacheType;
        loadOperation = FileLoader.getInstance(currentAccount).loadStreamFile(this, document, location, parentObject, 0, preview, loadingPriority, cacheType);
        if (loadOperation == null) canceled = true;
    }

    public boolean isFinishedLoadingFile() {
        return finishedLoadingFile;
    }

    public String getFinishedFilePath() {
        return finishedFilePath;
    }

    public int read(int offset, int readLength) {
        synchronized (sync) {
            if (canceled || FileDownloadTasks.isBlocked(currentAccount, document)) {
                debugCanceledCount++;
                if (!debugReportSend && debugCanceledCount > 200) {
                    debugReportSend = true;
                    FileLog.e(new RuntimeException("infinity stream reading!!!"));
                }
                return 0;
            }
        }
        if (readLength == 0) {
            return 0;
        } else {
            long availableLength = 0;
            try {
                while (availableLength == 0) {
                    final int currentDataVersion;
                    synchronized (sync) {
                        if (canceled || FileDownloadTasks.isBlocked(currentAccount, document)) {
                            return 0;
                        }
                        currentDataVersion = dataVersion;
                    }
                    boolean reloaded = false;
                    if (loadOperation == null || loadOperation.isPaused() || lastOffset != offset || preview) {
                        FileLoadOperation loadOperation = FileLoader.getInstance(currentAccount).loadStreamFile(this, document, location, parentObject, offset, preview, loadingPriority, cacheType);
                        if (loadOperation == null) return 0;
                        synchronized (sync) {
                            if (canceled) {
                                loadOperation.removeStreamListener(this);
                                return 0;
                            }
                            if (this.loadOperation != loadOperation) {
                                if (this.loadOperation != null) {
                                    this.loadOperation.removeStreamListener(this);
                                }
                                this.loadOperation = loadOperation;
                                finishedLoadingFile = false;
                                finishedFilePath = null;
                            }
                        }
                        lastOffset = offset;
                        reloaded = true;
                    }
                    long[] result = loadOperation.getDownloadedLengthFromOffset(offset, readLength);
                    if (result[1] == -2 && !reloaded) {
                        lastOffset = -1;
                        continue;
                    }
                    if (result[1] < 0) {
                        return 0;
                    }
                    availableLength = result[0];
                    if (!finishedLoadingFile && result[1] != 0) {
                        File finalFile = loadOperation.getCacheFileFinal();
                        if (finalFile == null) return 0;
                        finishedFilePath = finalFile.getAbsolutePath();
                        finishedLoadingFile = true;
                    }
                    if (availableLength == 0) {
                        if (!preview) {
                            FileLoader.getInstance(currentAccount).setLoadingVideo(document, false, true);
                        }
                        synchronized (sync) {
                            // Notifications between the query and the wait must trigger another query.
                            while (!canceled && dataVersion == currentDataVersion) {
                                waitingForLoad = true;
                                sync.wait();
                            }
                        }
                        waitingForLoad = false;
                    }
                }
                lastOffset = offset + availableLength;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                FileLog.e(e, false);
            } finally {
                waitingForLoad = false;
            }
            return (int) availableLength;
        }
    }

    public void cancel() {
        cancel(true);
    }

    public void cancel(boolean removeLoading) {
        synchronized (sync) {
            if (canceled) {
                return;
            }
            canceled = true;
            dataVersion++;
            sync.notifyAll();
            if (removeLoading && waitingForLoad && !preview) {
                FileLoader.getInstance(currentAccount).removeLoadingVideo(document, false, true);
            }
            if (loadOperation != null) {
                loadOperation.removeStreamListener(this);
            }
            if (parentObject instanceof MessageObject) {
                MessageObject messageObject = (MessageObject) parentObject;
                if (DownloadController.getInstance(messageObject.currentAccount).isDownloading(messageObject.getId()))  {
                    removeLoading = false;
                }
            }
            if (removeLoading) {
                cancelLoadingInternal();
            }
        }
    }

    private void cancelLoadingInternal() {
        if (!FileDownloadTasks.keepsFile(currentAccount, document)) FileLoader.getInstance(currentAccount).cancelLoadFile(document);
        if (location != null) {
            FileLoader.getInstance(currentAccount).cancelLoadFile(location.location, "mp4");
        }
    }

    public void reset() {
        synchronized (sync) {
            canceled = false;
            lastOffset = -1;
        }
    }

    public TLRPC.Document getDocument() {
        return document;
    }

    public ImageLocation getLocation() {
        return location;
    }

    public Object getParentObject() {
        return document;
    }

    public boolean isPreview() {
        return preview;
    }

    public int getCurrentAccount() {
        return currentAccount;
    }

    public boolean isWaitingForLoad() {
        return waitingForLoad;
    }

    @Override
    public void newDataAvailable() {
        synchronized (sync) {
            dataVersion++;
            sync.notifyAll();
        }
    }

    public boolean isCanceled() {
        return canceled;
    }
}
