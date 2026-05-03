package com.kaiyue.connector;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.logging.Logger;

public class SimpleHttpServer implements HttpHandler,AutoCloseable {
    private final Logger logger=Logger.getLogger(getClass().getName());
    // Http服务器
    final HttpServer httpServer;
    // 监听的IP
    final String host;
    // 监听的端口
    final int port;
    public SimpleHttpServer(String host, int port) throws IOException, ServletException {
        this.host = host;
        this.port = port;
        // 监听哪一个IP和端口 backlog是指最大等待队列 (监听本机的所有网卡)
        this.httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
        // 让本类的handler进行处理
        this.httpServer.createContext("/", this);
        this.httpServer.start();
        logger.info("start ShaunSheep http server at " + host + ":" + port);
    }

    public static void main(String[] args) {
        String host="0.0.0.0";
        int port=8080;
        try(SimpleHttpServer connector=new SimpleHttpServer(host,port)){
            for (;;) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private final HttpConnectoer connector=new HttpConnectoer();
    @Override
    // HttpServer已经将http请求解析封装成了exchange
    public void handle(HttpExchange exchange) throws IOException {
        try {
            // 对请求进行处理
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
        this.httpServer.stop(3);
    }
}
