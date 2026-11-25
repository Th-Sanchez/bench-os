package com.benchos.api.shared.ratelimit;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Bounded body-caching wrapper allowing the rate-limit filter to inspect small JSON auth payloads
 * (for normalized email keying) without exhausting the request stream for Spring MVC controllers.
 */
final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    static final int MAX_CACHED_BYTES = 16 * 1024;

    private final byte[] cachedBody;
    private final boolean fullyCached;
    private final InputStream remainderStream;

    private CachedBodyHttpServletRequest(
            HttpServletRequest request,
            byte[] cachedBody,
            boolean fullyCached,
            InputStream remainderStream
    ) {
        super(request);
        this.cachedBody = cachedBody;
        this.fullyCached = fullyCached;
        this.remainderStream = remainderStream;
    }

    static CachedBodyHttpServletRequest capture(HttpServletRequest request) throws IOException {
        ServletInputStream source = request.getInputStream();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(256);
        byte[] chunk = new byte[1024];
        int remaining = MAX_CACHED_BYTES + 1;
        int read;
        while (remaining > 0 && (read = source.read(chunk, 0, Math.min(chunk.length, remaining))) != -1) {
            buffer.write(chunk, 0, read);
            remaining -= read;
        }
        byte[] captured = buffer.toByteArray();
        boolean complete = captured.length <= MAX_CACHED_BYTES;
        return new CachedBodyHttpServletRequest(request, captured, complete, complete ? null : source);
    }

    boolean isFullyCached() {
        return fullyCached;
    }

    byte[] cachedBodyBytes() {
        return cachedBody;
    }

    @Override
    public ServletInputStream getInputStream() {
        InputStream stream = fullyCached
                ? new ByteArrayInputStream(cachedBody)
                : new SequenceInputStream(new ByteArrayInputStream(cachedBody), remainderStream);
        return new DelegatingServletInputStream(stream);
    }

    @Override
    public BufferedReader getReader() {
        String encoding = getCharacterEncoding();
        Charset charset = (encoding != null && !encoding.isBlank())
                ? Charset.forName(encoding)
                : StandardCharsets.UTF_8;
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    private static final class DelegatingServletInputStream extends ServletInputStream {
        private final InputStream delegate;
        private boolean finished;

        private DelegatingServletInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            int data = delegate.read();
            if (data == -1) {
                finished = true;
            }
            return data;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int count = delegate.read(b, off, len);
            if (count == -1) {
                finished = true;
            }
            return count;
        }

        @Override
        public boolean isFinished() {
            return finished;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("Async read listeners are not supported");
        }
    }
}
