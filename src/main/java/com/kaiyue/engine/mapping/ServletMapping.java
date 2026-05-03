package com.kaiyue.engine.mapping;

import javax.servlet.Servlet;
import java.util.regex.Pattern;

/**
 * 这个类相当于一个存储servlet应用程序的仓库
 * 别人拿着path来调用这个仓库，仓库会返回一个Servlet
 */
public class ServletMapping {
    final Pattern pattern;
    public final Servlet servlet;
    public ServletMapping(String pattern, Servlet servlet) {
        this.pattern = Pattern.compile(pattern);
        this.servlet = servlet;
    }
    public boolean matches(String path) {
        return pattern.matcher(path).matches();
    }
}
