package com.kaiyue.connector;

import com.kaiyue.engine.ServletContextImpl;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.*;

public class HttpServletRequestImpl implements HttpServletRequest {
    final HttpExchangeRequest exchangeRequest;
    private Map<String, Object> attributes = new HashMap<>();
    private String characterEncoding = "UTF-8";

    public HttpServletRequestImpl(HttpExchangeRequest exchangeRequest) {
        this.exchangeRequest = exchangeRequest;
    }

    public HttpServletRequestImpl(ServletContextImpl servletContext, HttpServletResponse response, HttpExchangeRequest exchangeRequest) {
        this.servletContext = servletContext;
        this.response = response;
        this.exchangeRequest = exchangeRequest;
    }

    @Override
    public String getAuthType() {
        return null;
    }
    // 引用ServletContextImpl:
    ServletContextImpl servletContext;
    // 引用HttpServletResponse:
    HttpServletResponse response;

    @Override
    public HttpSession getSession(boolean create) {
        String sessionId = null;
        // 获取所有Cookie:
        Cookie[] cookies = getCookies();
        if (cookies != null) {
            // 查找JSESSIONID:
            for (Cookie cookie : cookies) {
                if ("JSESSIONID".equals(cookie.getName())) {
                    // 拿到Session ID:
                    sessionId = cookie.getValue();
                    break;
                }
            }
        }
        // 未获取到SessionID，且create=false，返回null:
        if (sessionId == null && !create) {
            return null;
        }
        // 未获取到SessionID，但create=true，创建新的Session:
        if (sessionId == null) {
            // 如果Header已经发送，则无法创建Session，因为无法添加Cookie:
            if (this.response.isCommitted()) {
                throw new IllegalStateException("Cannot create session for response is commited.");
            }
            // 如果没有session就创建一个session
            HttpSession session = this.servletContext.getSessionManager().createSession();
            sessionId = session.getId();
            // 构造一个名为JSESSIONID的Cookie:
            String cookieValue = "JSESSIONID=" + sessionId + "; Path=/; SameSite=Strict; HttpOnly";
            // 添加到HttpServletResponse的Header:
            this.response.addHeader("Set-Cookie", cookieValue);
            return session;
        }
        // 返回一个Session对象:
        return this.servletContext.getSessionManager().getSession(sessionId);
    }

    @Override
    public HttpSession getSession() {
        return getSession(true);
    }
    @Override
    public Cookie[] getCookies() {
        String cookieHeader = getHeader("Cookie");
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return new Cookie[0];
        }

        List<Cookie> cookies = new ArrayList<>();
        String[] cookiePairs = cookieHeader.split(";");
        for (String pair : cookiePairs) {
            String[] parts = pair.trim().split("=", 2);
            if (parts.length == 2) {
                cookies.add(new Cookie(parts[0].trim(), parts[1].trim()));
            } else if (parts.length == 1) {
                cookies.add(new Cookie(parts[0].trim(), ""));
            }
        }
        return cookies.toArray(new Cookie[0]);
    }

    @Override
    public long getDateHeader(String name) {
        String value = getHeader(name);
        if (value == null) {
            return -1;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid date header: " + value);
        }
    }

    @Override
    public String getHeader(String name) {
        List<String> values = exchangeRequest.getRequestHeaders().get(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        List<String> values = exchangeRequest.getRequestHeaders().get(name);
        if (values == null) {
            return Collections.emptyEnumeration();
        }
        return Collections.enumeration(values);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> names = exchangeRequest.getRequestHeaders().keySet();
        return Collections.enumeration(names);
    }

    @Override
    public int getIntHeader(String name) {
        String value = getHeader(name);
        if (value == null) {
            return -1;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Invalid integer header: " + value);
        }
    }

    @Override
    public String getMethod() {
        return exchangeRequest.getRequestMethod();
    }

    @Override
    public String getPathInfo() {
        URI uri = exchangeRequest.getRequestURI();
        String path = uri.getPath();
        if (path == null || path.isEmpty()) {
            return null;
        }
        return path;
    }

    @Override
    public String getPathTranslated() {
        return null;
    }

    @Override
    public String getContextPath() {
        return "";
    }

    @Override
    public String getQueryString() {
        URI uri = exchangeRequest.getRequestURI();
        return uri.getQuery();
    }

    @Override
    public String getRemoteUser() {
        return null;
    }

    @Override
    public boolean isUserInRole(String role) {
        return false;
    }

    @Override
    public Principal getUserPrincipal() {
        return null;
    }

    @Override
    public String getRequestedSessionId() {
        return null;
    }

    @Override
    public String getRequestURI() {
        URI uri = exchangeRequest.getRequestURI();
        return uri.getPath();
    }

    @Override
    public StringBuffer getRequestURL() {
        URI uri = exchangeRequest.getRequestURI();
        InetSocketAddress localAddr = exchangeRequest.getLocalAddress();
        String scheme = "http";
        int port = localAddr.getPort();

        StringBuffer url = new StringBuffer();
        url.append(scheme).append("://");
        url.append(localAddr.getHostString());
        if (port != 80 && port != 443) {
            url.append(":").append(port);
        }
        url.append(uri.getPath());
        if (uri.getQuery() != null) {
            url.append("?").append(uri.getQuery());
        }
        return url;
    }

    @Override
    public String getServletPath() {
        return "";
    }

    @Override
    public String changeSessionId() {
        throw new IllegalStateException("No session support");
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromUrl() {
        return false;
    }

    @Override
    public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
        return false;
    }

    @Override
    public void login(String username, String password) throws ServletException {
        throw new UnsupportedOperationException("Login not supported");
    }

    @Override
    public void logout() throws ServletException {
        throw new UnsupportedOperationException("Logout not supported");
    }

    @Override
    public Collection<Part> getParts() throws IOException, ServletException {
        return Collections.emptyList();
    }

    @Override
    public Part getPart(String name) throws IOException, ServletException {
        return null;
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException {
        throw new UnsupportedOperationException("Upgrade not supported");
    }

    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(attributes.keySet());
    }

    @Override
    public String getCharacterEncoding() {
        return characterEncoding;
    }

    @Override
    public void setCharacterEncoding(String env) throws UnsupportedEncodingException {
        this.characterEncoding = env;
    }

    @Override
    public int getContentLength() {
        String contentLength = getHeader("Content-Length");
        if (contentLength == null) {
            return -1;
        }
        try {
            return Integer.parseInt(contentLength);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public long getContentLengthLong() {
        String contentLength = getHeader("Content-Length");
        if (contentLength == null) {
            return -1;
        }
        try {
            return Long.parseLong(contentLength);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public String getContentType() {
        return getHeader("Content-Type");
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        byte[] body = exchangeRequest.getRequestBody();
        InputStream inputStream = new ByteArrayInputStream(body);

        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                try {
                    return inputStream.available() == 0;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                throw new UnsupportedOperationException("Non-blocking IO not supported");
            }

            @Override
            public int read() throws IOException {
                return inputStream.read();
            }

            @Override
            public int available() throws IOException {
                return inputStream.available();
            }
        };
    }

    @Override
    public String getParameter(String name) {
        Map<String, String[]> params = getParameterMap();
        String[] values = params.get(name);
        if (values == null || values.length == 0) {
            return null;
        }
        return values[0];
    }

    @Override
    public Enumeration<String> getParameterNames() {
        Map<String, String[]> params = getParameterMap();
        return Collections.enumeration(params.keySet());
    }

    @Override
    public String[] getParameterValues(String name) {
        Map<String, String[]> params = getParameterMap();
        return params.get(name);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        String query = getQueryString();
        if (query == null || query.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String[]> paramMap = new HashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] parts = pair.split("=", 2);
            String key = decode(parts[0]);
            String value = parts.length > 1 ? decode(parts[1]) : "";

            if (paramMap.containsKey(key)) {
                String[] existing = paramMap.get(key);
                String[] newArray = new String[existing.length + 1];
                System.arraycopy(existing, 0, newArray, 0, existing.length);
                newArray[existing.length] = value;
                paramMap.put(key, newArray);
            } else {
                paramMap.put(key, new String[]{value});
            }
        }
        return paramMap;
    }

    private String decode(String value) {
        try {
            return java.net.URLDecoder.decode(value, characterEncoding);
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    @Override
    public String getProtocol() {
        return "HTTP/1.1";
    }

    @Override
    public String getScheme() {
        return "http";
    }

    @Override
    public String getServerName() {
        InetSocketAddress localAddr = exchangeRequest.getLocalAddress();
        return localAddr.getHostString();
    }

    @Override
    public int getServerPort() {
        InetSocketAddress localAddr = exchangeRequest.getLocalAddress();
        return localAddr.getPort();
    }

    @Override
    public BufferedReader getReader() throws IOException {
        byte[] body = exchangeRequest.getRequestBody();
        InputStream inputStream = new ByteArrayInputStream(body);
        return new BufferedReader(new InputStreamReader(inputStream, characterEncoding));
    }

    @Override
    public String getRemoteAddr() {
        InetSocketAddress remoteAddr = exchangeRequest.getRemoteAddress();
        return remoteAddr.getAddress().getHostAddress();
    }

    @Override
    public String getRemoteHost() {
        InetSocketAddress remoteAddr = exchangeRequest.getRemoteAddress();
        return remoteAddr.getHostString();
    }

    @Override
    public void setAttribute(String name, Object o) {
        Object old = attributes.put(name, o);
        if (old == null) {
            if (this.servletContext != null) {
                servletContext.invokeServletRequestAttributeAdded(this, name, o);
            }
        } else {
            if (this.servletContext != null) {
                servletContext.invokeServletRequestAttributeReplaced(this, name, o);
            }
        }
    }

    @Override
    public void removeAttribute(String name) {
        Object old = attributes.remove(name);
        if (old != null && this.servletContext != null) {
            servletContext.invokeServletRequestAttributeRemoved(this, name, old);
        }
    }

    @Override
    public Locale getLocale() {
        String acceptLanguage = getHeader("Accept-Language");
        if (acceptLanguage == null || acceptLanguage.isEmpty()) {
            return Locale.getDefault();
        }

        String[] locales = acceptLanguage.split(",");
        if (locales.length > 0) {
            String localeStr = locales[0].trim().split(";")[0];
            String[] parts = localeStr.split("-");
            if (parts.length == 2) {
                return new Locale(parts[0], parts[1]);
            } else if (parts.length == 1) {
                return new Locale(parts[0]);
            }
        }
        return Locale.getDefault();
    }

    @Override
    public Enumeration<Locale> getLocales() {
        String acceptLanguage = getHeader("Accept-Language");
        if (acceptLanguage == null || acceptLanguage.isEmpty()) {
            return Collections.enumeration(Collections.singletonList(Locale.getDefault()));
        }

        List<Locale> locales = new ArrayList<>();
        String[] localeStrings = acceptLanguage.split(",");
        for (String localeStr : localeStrings) {
            localeStr = localeStr.trim().split(";")[0];
            String[] parts = localeStr.split("-");
            if (parts.length == 2) {
                locales.add(new Locale(parts[0], parts[1]));
            } else if (parts.length == 1) {
                locales.add(new Locale(parts[0]));
            }
        }
        return Collections.enumeration(locales);
    }

    @Override
    public boolean isSecure() {
        return false;
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        return null;
    }

    @Override
    public String getRealPath(String path) {
        return null;
    }

    @Override
    public int getRemotePort() {
        InetSocketAddress remoteAddr = exchangeRequest.getRemoteAddress();
        return remoteAddr.getPort();
    }

    @Override
    public String getLocalName() {
        InetSocketAddress localAddr = exchangeRequest.getLocalAddress();
        return localAddr.getHostString();
    }

    @Override
    public String getLocalAddr() {
        InetSocketAddress localAddr = exchangeRequest.getLocalAddress();
        return localAddr.getAddress().getHostAddress();
    }

    @Override
    public int getLocalPort() {
        InetSocketAddress localAddr = exchangeRequest.getLocalAddress();
        return localAddr.getPort();
    }

    @Override
    public ServletContext getServletContext() {
        return this.servletContext;
    }

    @Override
    public AsyncContext startAsync() throws IllegalStateException {
        throw new UnsupportedOperationException("Async not supported");
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
        throw new UnsupportedOperationException("Async not supported");
    }

    @Override
    public boolean isAsyncStarted() {
        return false;
    }

    @Override
    public boolean isAsyncSupported() {
        return false;
    }

    @Override
    public AsyncContext getAsyncContext() {
        throw new IllegalStateException("Async not started");
    }

    @Override
    public DispatcherType getDispatcherType() {
        return DispatcherType.REQUEST;
    }
}
