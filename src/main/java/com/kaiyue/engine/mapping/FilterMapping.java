package com.kaiyue.engine.mapping;

import javax.servlet.Filter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

public class FilterMapping {
    public final Filter filter;
    final List<Pattern> patterns = new ArrayList<>();

    public FilterMapping(String urlPattern, Filter filter) {
        this.filter = filter;
        addMapping(urlPattern);
    }

    /**
     * 添加 URL 映射模式
     */
    public void addMapping(String urlPattern) {
        this.patterns.add(buildPattern(urlPattern));
    }

    /**
     * 批量添加 URL 映射模式
     */
    public void addMappings(String... urlPatterns) {
        for (String urlPattern : urlPatterns) {
            addMapping(urlPattern);
        }
    }

    /**
     * 获取所有 URL 模式
     */
    public Collection<String> getUrlPatterns() {
        List<String> urlPatterns = new ArrayList<>();
        for (Pattern pattern : patterns) {
            urlPatterns.add(pattern.pattern());
        }
        return urlPatterns;
    }

    /**
     * 将 URL 模式转换为正则表达式
     */
    private Pattern buildPattern(String urlPattern) {
        if (urlPattern == null || urlPattern.isEmpty()) {
            throw new IllegalArgumentException("URL pattern cannot be null or empty");
        }

        // 处理根路径
        if ("/".equals(urlPattern)) {
            return Pattern.compile("^/$");
        }

        // 处理扩展名匹配: *.do, *.html 等
        if (urlPattern.startsWith("*.")) {
            String extension = urlPattern.substring(2);
            return Pattern.compile("^.*\\." + Pattern.quote(extension) + "$");
        }

        // 处理路径前缀匹配: /api/*, /user/* 等
        if (urlPattern.endsWith("/*")) {
            String prefix = urlPattern.substring(0, urlPattern.length() - 2);
            return Pattern.compile("^" + Pattern.quote(prefix) + "(/.*)?$");
        }

        // 处理精确匹配
        if (!urlPattern.contains("*")) {
            return Pattern.compile("^" + Pattern.quote(urlPattern) + "$");
        }

        // 其他包含通配符的情况，将 * 替换为 .*
        String regex = Pattern.quote(urlPattern);
        regex = regex.replace(Pattern.quote("*"), ".*");
        return Pattern.compile("^" + regex + "$");
    }

    /**
     * 检查路径是否匹配
     */
    public boolean matches(String path) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(path).matches()) {
                return true;
            }
        }
        return false;
    }
}
