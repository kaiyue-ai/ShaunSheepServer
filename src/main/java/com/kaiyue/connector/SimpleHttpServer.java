package com.kaiyue.connector;

import com.kaiyue.classloader.ClassScanner;
import com.kaiyue.classloader.WarUtils;
import com.kaiyue.classloader.WebAppClassLoader;
import com.kaiyue.engine.filter.HelloFilter;
import com.kaiyue.engine.listener.HelloHttpSessionAttributeListener;
import com.kaiyue.engine.servlet.HelloServlet;
import com.kaiyue.engine.servlet.IndexServlet;
import com.kaiyue.engine.servlet.LoginServlet;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Paths;
import java.util.EventListener;
import java.util.List;
import java.util.logging.Logger;

/**
 * ShaunSheepServer 服务器入口类
 * 
 * 基于 JDK 内置 com.sun.net.httpserver.HttpServer 实现，零外部 HTTP 依赖
 * 支持两种启动模式：
 * - 嵌入式模式：使用内嵌的 Servlet/Filter/Listener（默认）
 * - WAR 包模式：通过 --war 参数从外部 WAR 包动态加载组件
 * 
 * 命令行参数：
 *   --host <host>  监听地址（默认 0.0.0.0）
 *   --port <port>  监听端口（默认 8080）
 *   --war  <path>  WAR 包路径
 */
public class SimpleHttpServer implements HttpHandler, AutoCloseable {

    private static final Logger logger = Logger.getLogger(SimpleHttpServer.class.getName());

    final HttpServer httpServer;
    final String host;
    final int port;
    final HttpConnectoer connector;

    public SimpleHttpServer(String host, int port, HttpConnectoer connector) throws IOException {
        this.host = host;
        this.port = port;
        this.connector = connector;
        this.httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.httpServer.createContext("/", this);
        this.httpServer.start();
        logger.info("start ShaunSheep http server at " + host + ":" + port);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            connector.handle(exchange);
        } catch (Exception e) {
            logger.severe("Error handling request: " + e.getMessage());
            e.printStackTrace();
            try {
                exchange.sendResponseHeaders(500, -1);
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void close() throws Exception {
        this.connector.servletContext.invokeServletContextDestroyed();
        this.httpServer.stop(3);
    }

    public static void main(String[] args) {
        String host = "0.0.0.0";
        int port = 8080;
        String warPath = null;

        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host":
                    if (i + 1 < args.length) {
                        host = args[++i];
                    }
                    break;
                case "--port":
                    if (i + 1 < args.length) {
                        port = Integer.parseInt(args[++i]);
                    }
                    break;
                case "--war":
                    if (i + 1 < args.length) {
                        warPath = args[++i];
                    }
                    break;
            }
        }

        try {
            HttpConnectoer connector;
            if (warPath != null) {
                connector = createFromWar(warPath);
            } else {
                connector = createEmbedded();
            }

            // 触发 ServletContextListener.contextInitialized
            connector.servletContext.invokeServletContextInitialized();

            try (SimpleHttpServer server = new SimpleHttpServer(host, port, connector)) {
                logger.info("Server started. Press Ctrl+C to stop.");
                for (; ; ) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static HttpConnectoer createFromWar(String warPath) throws IOException, ServletException {
        // 首先去获取war上下文
        WarUtils.WarContext warCtx = WarUtils.extractWar(Paths.get(warPath));
        logger.info("WAR extracted to: " + warCtx.tempDir());
        logger.info("Classes: " + warCtx.classesPath());
        logger.info("Lib: " + warCtx.libPath());
        // 创建类加载器
        WebAppClassLoader classLoader = new WebAppClassLoader(warCtx.classesPath(), warCtx.libPath());
        ClassScanner scanner = new ClassScanner(classLoader);
        ClassScanner.ScanResult scanResult = scanner.scan();

        logger.info("Found " + scanResult.servlets().size() + " servlets, "
                + scanResult.filters().size() + " filters, "
                + scanResult.listeners().size() + " listeners");

        return new HttpConnectoer(classLoader, scanResult);
    }

    static HttpConnectoer createEmbedded() throws ServletException {
        List<Class> servletClasses = List.of(IndexServlet.class, HelloServlet.class, LoginServlet.class);
        List<Class<?>> filterClasses = List.of(HelloFilter.class);
        List<Class<? extends EventListener>> listenerClasses = List.of(HelloHttpSessionAttributeListener.class);
        return new HttpConnectoer(servletClasses, filterClasses, listenerClasses);
    }
}
