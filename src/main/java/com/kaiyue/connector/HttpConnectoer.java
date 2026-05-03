package com.kaiyue.connector;

import com.kaiyue.classloader.ClassScanner;
import com.kaiyue.engine.ServletContextImpl;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.EventListener;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 连接器核心类，实现 HttpHandler 接口，桥接 JDK HttpServer 与 Servlet 容器
 * 
 * 职责：
 * 1. 将 HttpExchange 包装为 HttpServletRequestImpl / HttpServletResponseImpl
 * 2. 设置线程上下文类加载器为 WebAppClassLoader，确保 SPI 机制正确工作
 * 3. 委托 ServletContextImpl.process() 处理请求
 */
public class HttpConnectoer implements HttpHandler {

    private static final Logger logger = Logger.getLogger(HttpConnectoer.class.getName());

    /** Servlet 容器上下文，管理 Servlet/Filter/Listener 的注册和路由 */
    public final ServletContextImpl servletContext;

    /** 线程上下文类加载器，用于加载 WAR 包中的类 */
    final ClassLoader classLoader;

    /**
     * 使用系统类加载器创建连接器（嵌入式模式）
     * @param servletClasses  Servlet 类列表
     * @param filterClasses   Filter 类列表
     * @param listenerClasses Listener 类列表
     */
    public HttpConnectoer(List<Class> servletClasses, List<Class<?>> filterClasses, List<Class<? extends EventListener>> listenerClasses) throws ServletException {
        this(ClassLoader.getSystemClassLoader(), servletClasses, filterClasses, listenerClasses);
    }

    /**
     * 使用指定类加载器创建连接器（嵌入式模式）
     * @param classLoader    自定义类加载器（通常为 WebAppClassLoader）
     * @param servletClasses  Servlet 类列表
     * @param filterClasses   Filter 类列表
     * @param listenerClasses Listener 类列表
     */
    public HttpConnectoer(ClassLoader classLoader, List<Class> servletClasses, List<Class<?>> filterClasses, List<Class<? extends EventListener>> listenerClasses) throws ServletException {
        this.classLoader = classLoader;
        this.servletContext = new ServletContextImpl();
        this.servletContext.initialize(servletClasses);
        this.servletContext.initFilters(filterClasses);
        registerListeners(listenerClasses);
    }

    /**
     * 使用 ClassScanner 扫描结果创建连接器（WAR 包模式）
     * @param classLoader WebAppClassLoader，已指向 WAR 包的 classes 和 lib 路径
     * @param scanResult  扫描结果，包含 @WebServlet、@WebFilter、@WebListener 注解的类
     */
    public HttpConnectoer(ClassLoader classLoader, ClassScanner.ScanResult scanResult) throws ServletException {
        this.classLoader = classLoader;
        this.servletContext = new ServletContextImpl();
        this.servletContext.initialize(List.copyOf(scanResult.servlets()));
        this.servletContext.initFilters(List.copyOf(scanResult.filters()));
        registerListeners(scanResult.listeners());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    void registerListeners(List<?> listenerClasses) {
        if (listenerClasses != null) {
            for (Object obj : listenerClasses) {
                if (obj instanceof Class) {
                    Class clazz = (Class) obj;
                    if (EventListener.class.isAssignableFrom(clazz)) {
                        this.servletContext.addListener((Class<? extends EventListener>) clazz);
                    }
                }
            }
        }
    }

    void registerListeners(Set<Class<?>> listenerClasses) {
        if (listenerClasses != null) {
            for (Class<?> clazz : listenerClasses) {
                if (EventListener.class.isAssignableFrom(clazz)) {
                    this.servletContext.addListener((Class<? extends EventListener>) clazz);
                }
            }
        }
    }

    /**
     * 处理 HTTP 请求
     * 1. 包装 HttpExchange 为 Servlet 请求/响应对象
     * 2. 设置线程上下文类加载器为 WebAppClassLoader
     * 3. 委托 ServletContextImpl 进行路由和处理
     * 4. 恢复原始类加载器并关闭连接
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        var adapter = new HttpExchangeAdapter(exchange);
        var response = new HttpServletResponseImpl(adapter);
        var request = new HttpServletRequestImpl(this.servletContext, response, adapter);
        ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(this.classLoader);
            this.servletContext.process(request, response);
            response.flushBuffer();
        } catch (ServletException e) {
            throw new RuntimeException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(oldClassLoader);
            exchange.close();
        }
    }
}
