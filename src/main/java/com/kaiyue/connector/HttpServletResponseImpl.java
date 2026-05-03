package com.kaiyue.connector;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class HttpServletResponseImpl implements HttpServletResponse {
    final HttpExchangeResponse exchangeResponse;
    private int status = SC_OK;
    private String characterEncoding = "UTF-8";
    private String contentType;
    private long contentLength = -1;
    private boolean committed = false;
    private PrintWriter writer;
    private ServletOutputStream outputStream;
    private Map<String, String> headers = new LinkedHashMap<>();

    public HttpServletResponseImpl(HttpExchangeResponse exchangeResponse) {
        this.exchangeResponse = exchangeResponse;
    }

    @Override
    public void addCookie(Cookie cookie) {
        StringBuilder sb = new StringBuilder();
        sb.append(cookie.getName()).append("=").append(cookie.getValue());

        if (cookie.getMaxAge() >= 0) {
            sb.append("; Max-Age=").append(cookie.getMaxAge());
        }

        if (cookie.getPath() != null && !cookie.getPath().isEmpty()) {
            sb.append("; Path=").append(cookie.getPath());
        }

        if (cookie.getDomain() != null && !cookie.getDomain().isEmpty()) {
            sb.append("; Domain=").append(cookie.getDomain());
        }

        if (cookie.getSecure()) {
            sb.append("; Secure");
        }

        if (cookie.isHttpOnly()) {
            sb.append("; HttpOnly");
        }

        addHeader("Set-Cookie", sb.toString());
    }

    @Override
    public boolean containsHeader(String name) {
        return headers.containsKey(name) || exchangeResponse.getResponseHeaders().containsKey(name);
    }

    @Override
    public String encodeURL(String url) {
        return url;
    }

    @Override
    public String encodeRedirectURL(String url) {
        return url;
    }

    @Override
    public String encodeUrl(String url) {
        return encodeURL(url);
    }

    @Override
    public String encodeRedirectUrl(String url) {
        return encodeRedirectURL(url);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        if (committed) {
            throw new IllegalStateException("Response already committed");
        }
        this.status = sc;
        exchangeResponse.sendResponseHeaders(sc, -1);
        committed = true;

        OutputStream out = exchangeResponse.getResponseBody();
        out.write(("<html><body><h1>" + sc + " - " + msg + "</h1></body></html>").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    @Override
    public void sendError(int sc) throws IOException {
        sendError(sc, getStatusMessage(sc));
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        if (committed) {
            throw new IllegalStateException("Response already committed");
        }
        setStatus(SC_FOUND);
        setHeader("Location", location);
        exchangeResponse.sendResponseHeaders(SC_FOUND, -1);
        committed = true;
    }

    @Override
    public void setDateHeader(String name, long date) {
        setHeader(name, new Date(date).toString());
    }

    @Override
    public void addDateHeader(String name, long date) {
        addHeader(name, new Date(date).toString());
    }

    @Override
    public void setHeader(String name, String value) {
        if (committed) {
            return;
        }
        headers.put(name, value);
        exchangeResponse.getResponseHeaders().set(name, value);
    }

    @Override
    public void addHeader(String name, String value) {
        if (committed) {
            return;
        }
        if (headers.containsKey(name)) {
            headers.put(name, headers.get(name) + "," + value);
        } else {
            headers.put(name, value);
        }
        exchangeResponse.getResponseHeaders().add(name, value);
    }

    @Override
    public void setIntHeader(String name, int value) {
        setHeader(name, String.valueOf(value));
    }

    @Override
    public void addIntHeader(String name, int value) {
        addHeader(name, String.valueOf(value));
    }

    @Override
    public void setStatus(int sc) {
        if (committed) {
            return;
        }
        this.status = sc;
    }

    @Override
    public void setStatus(int sc, String sm) {
        setStatus(sc);
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public String getHeader(String name) {
        return headers.get(name);
    }

    @Override
    public Collection<String> getHeaders(String name) {
        List<String> values = exchangeResponse.getResponseHeaders().get(name);
        if (values == null) {
            return Collections.emptyList();
        }
        return values;
    }

    @Override
    public Collection<String> getHeaderNames() {
        return headers.keySet();
    }

    @Override
    public String getCharacterEncoding() {
        return characterEncoding;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (writer != null) {
            throw new IllegalStateException("getWriter() has already been called");
        }
        if (outputStream == null) {
            outputStream = new ServletOutputStream() {
                private final OutputStream out = exchangeResponse.getResponseBody();

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener listener) {
                    throw new UnsupportedOperationException("Non-blocking IO not supported");
                }

                @Override
                public void write(int b) throws IOException {
                    out.write(b);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    out.write(b, off, len);
                }

                @Override
                public void flush() throws IOException {
                    out.flush();
                }

                @Override
                public void close() throws IOException {
                    out.close();
                }
            };
        }
        return outputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (outputStream != null) {
            throw new IllegalStateException("getOutputStream() has already been called");
        }
        if (writer == null) {
            final HttpExchangeResponse resp = exchangeResponse;
            OutputStream lazyOut = new OutputStream() {
                private OutputStream actual = null;

                private OutputStream getActual() throws IOException {
                    if (actual == null) {
                        if (!committed) {
                            if (contentType != null) {
                                setHeader("Content-Type", contentType);
                            }
                            resp.sendResponseHeaders(status, 0);
                            committed = true;
                        }
                        actual = resp.getResponseBody();
                    }
                    return actual;
                }

                @Override
                public void write(int b) throws IOException {
                    getActual().write(b);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    getActual().write(b, off, len);
                }

                @Override
                public void flush() throws IOException {
                    getActual().flush();
                }

                @Override
                public void close() throws IOException {
                    getActual().close();
                }
            };
            writer = new PrintWriter(lazyOut, true, Charset.forName(characterEncoding));
        }
        return writer;
    }

    @Override
    public void setCharacterEncoding(String charset) {
        if (committed) {
            return;
        }
        this.characterEncoding = charset;
    }

    @Override
    public void setContentLength(int len) {
        if (committed) {
            return;
        }
        this.contentLength = len;
        setIntHeader("Content-Length", len);
    }

    @Override
    public void setContentLengthLong(long len) {
        if (committed) {
            return;
        }
        this.contentLength = len;
        setHeader("Content-Length", String.valueOf(len));
    }

    @Override
    public void setContentType(String type) {
        if (committed) {
            return;
        }
        this.contentType = type;

        if (type != null && type.contains("charset=")) {
            String[] parts = type.split("charset=");
            if (parts.length > 1) {
                this.characterEncoding = parts[1].trim();
            }
        }

        setHeader("Content-Type", type);
    }

    @Override
    public void setBufferSize(int size) {
        // Buffer size is not controlled in this implementation
    }

    @Override
    public int getBufferSize() {
        return 8192;
    }

    @Override
    public void flushBuffer() throws IOException {
        if (writer != null) {
            writer.flush();
        }
        if (outputStream != null) {
            outputStream.flush();
        }
    }

    @Override
    public void resetBuffer() {
        if (committed) {
            throw new IllegalStateException("Response already committed");
        }
    }

    @Override
    public boolean isCommitted() {
        return committed;
    }

    @Override
    public void reset() {
        if (committed) {
            throw new IllegalStateException("Response already committed");
        }
        status = SC_OK;
        headers.clear();
        characterEncoding = "UTF-8";
        contentType = null;
        contentLength = -1;
        writer = null;
        outputStream = null;
    }

    @Override
    public void setLocale(Locale loc) {
        if (committed) {
            return;
        }
        setHeader("Content-Language", loc.toLanguageTag());
    }

    @Override
    public Locale getLocale() {
        return Locale.getDefault();
    }

    private String getStatusMessage(int statusCode) {
        switch (statusCode) {
            case SC_OK: return "OK";
            case SC_CREATED: return "Created";
            case SC_ACCEPTED: return "Accepted";
            case SC_NO_CONTENT: return "No Content";
            case SC_MOVED_PERMANENTLY: return "Moved Permanently";
            case SC_FOUND: return "Found";
            case SC_BAD_REQUEST: return "Bad Request";
            case SC_UNAUTHORIZED: return "Unauthorized";
            case SC_FORBIDDEN: return "Forbidden";
            case SC_NOT_FOUND: return "Not Found";
            case SC_INTERNAL_SERVER_ERROR: return "Internal Server Error";
            case SC_NOT_IMPLEMENTED: return "Not Implemented";
            case SC_BAD_GATEWAY: return "Bad Gateway";
            case SC_SERVICE_UNAVAILABLE: return "Service Unavailable";
            default: return "Unknown Status";
        }
    }
}
