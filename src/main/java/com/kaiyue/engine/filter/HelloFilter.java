package com.kaiyue.engine.filter;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.logging.Logger;
@WebFilter(
        filterName = "helloFilter",           // Filter 名称
        urlPatterns = {"/hello", "/greeting"}, // 匹配的 URL 路径
        initParams = {                         // 初始化参数
                @WebInitParam(name = "message", value = "Hello from Filter!")
        }
)

public class HelloFilter implements Filter {

    private static final Logger logger = Logger.getLogger(HelloFilter.class.getName());

    private String message = "Hello from Filter!";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String msg = filterConfig.getInitParameter("message");
        if (msg != null) {
            this.message = msg;
        }
        logger.info("HelloFilter initialized");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        // 请求前处理
        logger.info("Before filter - Request URI: " + ((HttpServletRequest) request).getRequestURI());

        // 可以修改请求
        request.setAttribute("filteredBy", "HelloFilter");
        // 如果请求name的参数为123，那么直接返回错误
        if ("123".equals(request.getParameter("name"))) {
            response.setContentType("text/plain");
            response.getWriter().write("Error: Invalid name parameter!");
            return;
        }
        // 继续处理请求链
        chain.doFilter(request, response);

        // 响应后处理
        logger.info("After filter - Response completed");
    }

    @Override
    public void destroy() {
        logger.info("HelloFilter destroyed");
    }
}
