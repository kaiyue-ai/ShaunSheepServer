package com.kaiyue.classloader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebListener;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ClassScanner {

    static final Logger logger = LoggerFactory.getLogger(ClassScanner.class);

    private final WebAppClassLoader classLoader;

    public ClassScanner(WebAppClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * 扫描 WebAppClassLoader 范围内的所有 URL，识别带 @WebServlet、@WebFilter、@WebListener 注解的类
     * @return 扫描结果，包含三类组件的 Class 集合
     */
    public ScanResult scan() throws IOException {
        Set<Class<? extends Servlet>> servlets = new HashSet<>();
        Set<Class<? extends Filter>> filters = new HashSet<>();
        Set<Class<?>> listeners = new HashSet<>();

        List<String> classNames = new ArrayList<>();
        URL[] urls = classLoader.getURLs();
        for (URL url : urls) {
            if (url.getProtocol().equals("file") && !url.getPath().contains("!")) {
                scanDirectory(url, classNames);
            } else if (url.getProtocol().equals("jar")) {
                scanJar(url, classNames);
            }
        }

        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className, false, classLoader);
                if (clazz.isAnnotation()) {
                    continue;
                }
                if (clazz.isInterface()) {
                    continue;
                }
                if (Servlet.class.isAssignableFrom(clazz) && clazz.isAnnotationPresent(WebServlet.class)) {
                    servlets.add((Class<? extends Servlet>) clazz);
                    logger.info("Found servlet: {}", className);
                }
                if (Filter.class.isAssignableFrom(clazz) && clazz.isAnnotationPresent(WebFilter.class)) {
                    filters.add((Class<? extends Filter>) clazz);
                    logger.info("Found filter: {}", className);
                }
                if (clazz.isAnnotationPresent(WebListener.class)) {
                    listeners.add(clazz);
                    logger.info("Found listener: {}", className);
                }
            } catch (ClassNotFoundException e) {
                logger.warn("Cannot load class: {}", className);
            }
        }

        return new ScanResult(servlets, filters, listeners);
    }

    /** 扫描目录下的所有 .class 文件，收集全限定类名 */
    void scanDirectory(URL url, List<String> classNames) {
        try {
            URI uri = url.toURI();
            Path dir = Paths.get(uri);
            if (Files.isDirectory(dir)) {
                try (var stream = Files.walk(dir)) {
                    stream.filter(p -> p.toString().endsWith(".class"))
                            .forEach(p -> {
                                String relative = dir.relativize(p).toString();
                                String className = relative.substring(0, relative.length() - 6)
                                        .replace('/', '.').replace('\\', '.');
                                classNames.add(className);
                            });
                }
            }
        } catch (URISyntaxException | IOException e) {
            logger.warn("Cannot scan directory: {}", url);
        }
    }

    /** 扫描 Jar 包中的所有 .class 文件，收集全限定类名 */
    void scanJar(URL url, List<String> classNames) {
        String jarPath = url.getPath();
        if (jarPath.startsWith("file:")) {
            jarPath = jarPath.substring(5);
        }
        int idx = jarPath.indexOf("!/");
        if (idx != -1) {
            jarPath = jarPath.substring(0, idx);
        }
        try (JarFile jar = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.endsWith(".class")) {
                    String className = name.substring(0, name.length() - 6).replace('/', '.');
                    classNames.add(className);
                }
            }
        } catch (IOException e) {
            logger.warn("Cannot scan jar: {}", jarPath);
        }
    }

    /** 扫描结果，存放扫描到的 Servlet、Filter 和 Listener 的 Class 集合 */
    public record ScanResult(
            Set<Class<? extends Servlet>> servlets,
            Set<Class<? extends Filter>> filters,
            Set<Class<?>> listeners
    ) {
        public boolean isEmpty() {
            return servlets.isEmpty() && filters.isEmpty() && listeners.isEmpty();
        }
    }
}
