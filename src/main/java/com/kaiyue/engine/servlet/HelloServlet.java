package com.kaiyue.engine.servlet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@WebServlet(
        name = "helloServlet",
        urlPatterns = {"/hello"},
        initParams = {
                @WebInitParam(name = "greeting", value = "Hello")
        }
)
public class HelloServlet extends HttpServlet {

    private String greeting;

    @Override
    public void init() throws ServletException {
        super.init();
        greeting = getServletConfig().getInitParameter("greeting");
        if (greeting == null) {
            greeting = "Hello";
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String name = req.getParameter("name");
        if (name == null || name.trim().isEmpty()) {
            name = "World";
        }

        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();
        // 获取arrtribute 的值
        String filteredBy = (String) req.getAttribute("filteredBy");
        out.println("<p>Filtered by: " + filteredBy + "</p>");
        out.println("<h1>" + greeting + ", " + name + "!</h1>");
        out.flush();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        doGet(req, resp);
    }
}
