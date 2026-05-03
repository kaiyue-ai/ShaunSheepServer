package com.kaiyue.engine.session;

import com.kaiyue.engine.ServletContextImpl;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HttpSessionImpl implements HttpSession {
    // Session数据
    private final ServletContextImpl servletContext;
    // SessionId
    private final String sessionId;
    // Session最大不活动时间
    private int maxInactiveInterval;
    // Session创建时间
    private final long creationTime;
    // Session最后访问时间
    private long lastAccessedTime;
    // Session属性
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    // Session是否是新创建的
    private boolean isNew;
    // Session是否已失效
    private boolean invalid = false;

    public HttpSessionImpl(ServletContextImpl servletContext, int maxInactiveInterval) {
        this.servletContext = servletContext;
        this.sessionId = UUID.randomUUID().toString().replace("-", "");
        this.maxInactiveInterval = maxInactiveInterval;
        this.creationTime = System.currentTimeMillis();
        this.lastAccessedTime = this.creationTime;
        this.isNew = true;
    }

    // Session访问
    public void access() {
        this.lastAccessedTime = System.currentTimeMillis();
        this.isNew = false;
    }
    // Session是否已失效
    public boolean isInvalid() {
        return invalid;
    }
    // Session是否已过期
    public boolean isExpired() {
        if (maxInactiveInterval <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        return (now - lastAccessedTime) > (maxInactiveInterval * 1000L);
    }

    // Session创建时间
    @Override
    public long getCreationTime() {
        checkInvalid();
        return creationTime;
    }

    // SessionId
    @Override
    public String getId() {
        return sessionId;
    }

    // Session最后访问时间
    @Override
    public long getLastAccessedTime() {
        checkInvalid();
        return lastAccessedTime;
    }

    // SessionServletContext
    @Override
    public ServletContext getServletContext() {
        return servletContext;
    }

    @Override
    public void setMaxInactiveInterval(int interval) {
        this.maxInactiveInterval = interval;
    }

    @Override
    public int getMaxInactiveInterval() {
        return maxInactiveInterval;
    }

    @Override
    @Deprecated
    // SessionContext
    public HttpSessionContext getSessionContext() {
        throw new UnsupportedOperationException("getSessionContext is deprecated");
    }

    @Override
    // Session属性
    public Object getAttribute(String name) {
        checkInvalid();
        return attributes.get(name);
    }

    @Override
    @Deprecated
    public Object getValue(String name) {
        return getAttribute(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        checkInvalid();
        return Collections.enumeration(attributes.keySet());
    }

    @Override
    @Deprecated
    public String[] getValueNames() {
        return attributes.keySet().toArray(new String[0]);
    }

    @Override
    public void setAttribute(String name, Object value) {
        checkInvalid();
        if (value == null) {
            removeAttribute(name);
        } else {
            Object old = attributes.put(name, value);
            if (old == null) {
                // 新增属性，触发 attributeAdded
                servletContext.invokeHttpSessionAttributeAdded(this, name, value);
            } else {
                // 替换属性，触发 attributeReplaced
                servletContext.invokeHttpSessionAttributeReplaced(this, name, old);
            }
        }
    }

    @Override
    @Deprecated
    public void putValue(String name, Object value) {
        setAttribute(name, value);
    }

    @Override
    public void removeAttribute(String name) {
        checkInvalid();
        Object old = attributes.remove(name);
        if (old != null) {
            servletContext.invokeHttpSessionAttributeRemoved(this, name, old);
        }
    }

    @Override
    @Deprecated
    public void removeValue(String name) {
        removeAttribute(name);
    }

    @Override
    public void invalidate() {
        checkInvalid();
        this.invalid = true;
        attributes.clear();
        servletContext.getSessionManager().removeSession(sessionId);
        servletContext.invokeHttpSessionDestroyed(this);
    }

    @Override
    public boolean isNew() {
        checkInvalid();
        return isNew;
    }

    private void checkInvalid() {
        if (invalid) {
            throw new IllegalStateException("Session is invalid");
        }
        if (isExpired()) {
            invalidate();
            throw new IllegalStateException("Session has expired");
        }
    }
}
