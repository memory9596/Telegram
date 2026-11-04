package org.telegram.messenger;

import android.net.Uri;
import androidx.media3.datasource.DataSourceInputStream;
import androidx.media3.datasource.DataSpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.FilterInputStream;
import fi.iki.elonen.NanoHTTPD;

/** Android 23-25 fallback. Random session tokens are served on loopback only. */
final class VideoStreamingServer extends NanoHTTPD {
    private static VideoStreamingServer instance;
    private VideoStreamingServer() throws IOException { super("127.0.0.1", 0); start(SOCKET_READ_TIMEOUT, true); }
    static synchronized VideoStreamingServer getInstance() throws IOException {
        if (instance == null) instance = new VideoStreamingServer();
        return instance;
    }
    Uri uri(String token) { return Uri.parse("http://127.0.0.1:" + getListeningPort() + "/" + token); }

    @Override public Response serve(IHTTPSession request) {
        if (request.getMethod() != Method.GET && request.getMethod() != Method.HEAD) {
            return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "GET or HEAD required");
        }
        VideoStreamingProvider.Session session = VideoStreamingProvider.session(Uri.parse(request.getUri()));
        if (session == null) return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Stream expired");
        long size = session.document.size;
        String header = request.getHeaders().get("range");
        Range range;
        try { range = Range.parse(header, size); }
        catch (IllegalArgumentException e) {
            Response response = newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "Invalid range");
            response.addHeader("Content-Range", "bytes */" + size);
            return response;
        }
        if (request.getMethod() != Method.HEAD && !session.acquire()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Stream ended");
        }
        java.io.File local = FileDownloadTasks.localFile(session.account, session.document, session.message);
        DataSpec spec = new DataSpec.Builder().setUri(local == null ? session.source : Uri.fromFile(local)).setPosition(range.start).setLength(range.length()).build();
        FilterInputStream stream = new FilterInputStream(new DataSourceInputStream(local == null ? new FileStreamLoadOperation() : new androidx.media3.datasource.FileDataSource(), spec)) {
            private void checkSession() throws IOException {
                if (!session.available() || !FileDownloadTasks.isExternalActive(session.account, session.document, session.owner)) throw new IOException("External playback ended");
                session.touch();
            }
            @Override public int read(byte[] b, int off, int len) throws IOException { checkSession(); return super.read(b, off, len); }
            @Override public int read() throws IOException { checkSession(); return super.read(); }
            private boolean released;
            @Override public void close() throws IOException {
                if (released) return;
                released = true;
                try { super.close(); } finally { session.release(); }
            }
        };
        Response response = newFixedLengthResponse(header == null ? Response.Status.OK : Response.Status.PARTIAL_CONTENT,
                session.document.mime_type == null ? "video/mp4" : session.document.mime_type,
                request.getMethod() == Method.HEAD ? new ByteArrayInputStream(new byte[0]) : stream, range.length());
        response.addHeader("Accept-Ranges", "bytes");
        if (header != null) response.addHeader("Content-Range", "bytes " + range.start + "-" + range.end + "/" + size);
        return response;
    }

    private static final class Range {
        public final long start, end;
        private Range(long start, long end) { this.start = start; this.end = end; }
        public long length() { return end - start + 1; }

        public static Range parse(String header, long size) {
            if (size <= 0) throw new IllegalArgumentException("Empty file");
            if (header == null) return new Range(0, size - 1);
            if (!header.startsWith("bytes=") || header.indexOf(',') >= 0) throw new IllegalArgumentException("Invalid range");
            String value = header.substring(6).trim();
            int dash = value.indexOf('-');
            if (dash < 0 || value.indexOf('-', dash + 1) >= 0) throw new IllegalArgumentException("Invalid range");
            try {
                long start, end;
                if (dash == 0) {
                    long suffix = Long.parseLong(value.substring(1));
                    if (suffix <= 0) throw new IllegalArgumentException("Invalid suffix");
                    start = Math.max(0, size - suffix);
                    end = size - 1;
                } else {
                    start = Long.parseLong(value.substring(0, dash));
                    end = dash == value.length() - 1 ? size - 1 : Long.parseLong(value.substring(dash + 1));
                    if (start < 0 || start >= size || end < start) throw new IllegalArgumentException("Unsatisfiable range");
                    end = Math.min(end, size - 1);
                }
                return new Range(start, end);
            } catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid range", e); }
        }
    }
}
