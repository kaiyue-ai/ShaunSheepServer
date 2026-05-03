package com.kaiyue.connector;

import com.kaiyue.engine.ServletContextImpl;
import com.kaiyue.engine.filter.HelloFilter;
import com.kaiyue.engine.listener.HelloHttpSessionAttributeListener;
import com.kaiyue.engine.servlet.HelloServlet;
import com.kaiyue.engine.servlet.IndexServlet;
import com.kaiyue.engine.servlet.LoginServlet;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.EventListener;
import java.util.List;

public class HttpConnectoer implements HttpHandler {
    final ServletContextImpl servletContext;
//    final HttpServer httpServer;

    public HttpConnectoer() throws ServletException {
        this.servletContext = new ServletContextImpl();
        this.servletContext.initialize(List.of(IndexServlet.class, HelloServlet.class, LoginServlet.class));
        this.servletContext.initFilters(List.of(HelloFilter.class));
        List<Class<? extends EventListener>> listenerClasses = List.of(HelloHttpSessionAttributeListener.class);
        for (Class<? extends EventListener> listenerClass : listenerClasses) {
            this.servletContext.addListener(listenerClass);
        }

    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        var adapter = new HttpExchangeAdapter(exchange);
        var response = new HttpServletResponseImpl(adapter);
        // 添加response和adapter的引用
        var request = new HttpServletRequestImpl(this.servletContext,response,adapter);
        try {
            this.servletContext.process(request, response);
            response.flushBuffer();
        } catch (ServletException e) {
            throw new RuntimeException(e);
        } finally {
            exchange.close();
        }
    }
//    void process(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//        String name = request.getParameter("name");
//        String html = "<h1>Hello, " + (name == null ? "world" : name) + ".</h1>";
//        response.setContentType("text/html");
//        PrintWriter pw = response.getWriter();
//        pw.write(html);
//        // Deleted:pw.close();
//    }
}
