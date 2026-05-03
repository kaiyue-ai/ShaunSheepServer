package com.kaiyue.utils;

public class HtmlUtils {

    public static String encodeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
