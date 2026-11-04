package org.telegram.messenger;

public interface FileLoadOperationStream {
    void newDataAvailable();

    default long getStreamStartOffset() {
        return 0;
    }

    default long getStreamEndOffset() {
        return Long.MAX_VALUE;
    }
}
