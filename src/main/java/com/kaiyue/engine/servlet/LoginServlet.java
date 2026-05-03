package com.kaiyue.engine.servlet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@WebServlet(urlPatterns = "/login")
public class LoginServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String username = req.getParameter("username");
        String password = req.getParameter("password");
        if (loginOk(username, password)) {
            // 登录成功，获取Session:
            HttpSession session = req.getSession();
            // 将用户名放入Session:
            session.setAttribute("username", username);
            // 返回首页:
            resp.sendRedirect("/");
        } else {
            // 登录失败:
            resp.sendRedirect("/error");
        }
    }

    private boolean loginOk(String username, String password) {
        // 这里仅作演示，写死一个用户名与密码:
        return "admin".equals(username) && "admin".equals(password);
    }
}
