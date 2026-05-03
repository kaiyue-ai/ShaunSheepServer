package com.kaiyue.engine;

import com.kaiyue.engine.session.HttpSessionImpl;
import com.kaiyue.utils.DateUtils;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 * SessionManager - 管理所有 Session
 */
public class SessionManager implements Runnable{
    // ServletContext
    private final ServletContextImpl servletContext;
    // Session
    private final Map<String, HttpSession> sessions = new ConcurrentHashMap<>();
    // 默认 Session 过期时间
    private int inactiveInterval = 1800;
    public SessionManager(ServletContextImpl servletContext, int interval ) {
        this.servletContext = servletContext;
        this.inactiveInterval = interval;
        // 启动Daemon线程:
        Thread t = new Thread( this);
        t.setDaemon(true);
        t.start();
    }
    /**
     * 根据 SessionId 获取 Session
     */
    public HttpSession getSession(String sessionId) {
        // SessionId 不能为空
        if (sessionId == null || sessionId.isEmpty()) {
            return null;
        }
        // 获取 Session
        HttpSession session = sessions.get(sessionId);
        // Session 不存在或者已过期
        if (session != null) {
            HttpSessionImpl impl = (HttpSessionImpl) session;
            if (impl.isInvalid() || impl.isExpired()) {
                sessions.remove(sessionId);
                return null;
            }
            // Session 访问
            impl.access();
        }
        return session;
    }

    /**
     * 创建新的 Session
     */
    public HttpSession createSession() {
        HttpSessionImpl session = new HttpSessionImpl(servletContext, inactiveInterval);
        sessions.put(session.getId(), session);
        return session;
    }

    /**
     * 移除 Session
     */
    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
    }

    /**
     * 获取默认过期时间
     */
    public int getInactiveInterval() {
        return inactiveInterval;
    }

    /**
     * 设置默认过期时间（秒）
     */
    public void setInactiveInterval(int inactiveInterval) {
        this.inactiveInterval = inactiveInterval;
    }

    /**
     * 获取活跃 Session 数量
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }
    public void run() {
        for (; ; ) {
            // 每60秒扫描一次:
            try {
                Thread.sleep(60_000L);
            } catch (InterruptedException e) {
                break;
            }
            // 当前时间:
            long now = System.currentTimeMillis();
            // 遍历Session:
            for (String sessionId : sessions.keySet()) {
                HttpSession session = sessions.get(sessionId);
                // 判断是否过期:
                if (session.getLastAccessedTime() + session.getMaxInactiveInterval() * 1000L < now) {
                    // 删除过期的Session:
                    System.out.println("remove expired session: {"+sessionId+"}, last access time: {"+DateUtils.formatDateTimeGMT(session.getLastAccessedTime())+"}");
                    session.invalidate();
                }
            }
        }
    }
}
