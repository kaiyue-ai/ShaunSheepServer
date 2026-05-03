package com.kaiyue.engine.servlet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;

@WebServlet(
        name = "indexServlet",
        urlPatterns = {"/"}
)
public class IndexServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // 获取Session:
        HttpSession session = req.getSession();
        // 从Session中取出用户名:
        String username = (String) session.getAttribute("username");
        if (username == null) {
            // 未获取到用户名，说明未登录:
            resp.sendRedirect("/login");
        } else {
            // 获取到用户名，说明已登录:
            String html = "<p>Welcome, " + username + "!</p>";
            resp.setContentType("text/html");
            PrintWriter pw = resp.getWriter();
            pw.write(html);
            pw.close();
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        doGet(req, resp);
    }
}
