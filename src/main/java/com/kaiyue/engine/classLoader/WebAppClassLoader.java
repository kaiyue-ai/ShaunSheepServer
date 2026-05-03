package com.kaiyue.engine.classLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import java.util.logging.Logger;

/**
 *  WebAppClassLoader
 */
public class WebAppClassLoader extends URLClassLoader {

    private static final Logger logger = Logger.getLogger(WebAppClassLoader.class.getName());

    // 这是类路径
    final Path classPath;
    // 这是类库路径
    final Path[] libJars;

    // 构造函数
    public WebAppClassLoader(Path classPath, Path libPath) throws IOException {
        super("WebAppClassLoader", createUrls(classPath, libPath), ClassLoader.getSystemClassLoader());
        this.classPath = classPath.toAbsolutePath().normalize();
        this.libJars = Files.list(libPath).filter(p -> p.toString().endsWith(".jar")).map(p -> p.toAbsolutePath().normalize()).sorted().toArray(Path[]::new);
        logger.info("init WebAppClassLoader class path: " + this.classPath);
        Arrays.stream(this.libJars).forEach(p -> {
            logger.info("init WebAppClassLoader jar path: " + p);
        });
    }

    // 扫描类路径
    public void scanClassPath(Consumer<Resource> handler) {
        scanClassPath0(handler, this.classPath, this.classPath);
    }

    // 扫描类路径
    void scanClassPath0(Consumer<Resource> handler, Path basePath, Path path) {
        try {
            Files.list(path).sorted().forEach(p -> {
                if (Files.isDirectory(p)) {
                    scanClassPath0(handler, basePath, p);
                } else if (Files.isRegularFile(p)) {
                    Path subPath = basePath.relativize(p);
                    handler.accept(new Resource(p, subPath.toString().replace('\\', '/')));
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // 扫描类库
    public void scanJar(Consumer<Resource> handler) {
        try {
            for (Path jarPath : this.libJars) {
                scanJar0(handler, jarPath);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // 扫描Jar
    void scanJar0(Consumer<Resource> handler, Path jarPath) throws IOException {
        JarFile jarFile = new JarFile(jarPath.toFile());
        jarFile.stream().filter(entry -> !entry.isDirectory()).forEach(entry -> {
            String name = entry.getName();
            handler.accept(new Resource(jarPath, name));
        });
    }

    // 创建URL
    static URL[] createUrls(Path classPath, Path libPath) throws IOException {
        List<URL> urls = new ArrayList<>();
        urls.add(toDirURL(classPath));
        Files.list(libPath).filter(p -> p.toString().endsWith(".jar")).sorted().forEach(p -> {
            urls.add(toJarURL(p));
        });
        return urls.toArray(URL[]::new);
    }

    // 创建URL
    static URL toDirURL(Path p) {
        try {
            if (Files.isDirectory(p)) {
                String abs = toAbsPath(p);
                if (!abs.endsWith("/")) {
                    abs = abs + "/";
                }
                return URI.create("file://" + abs).toURL();
            }
            throw new IOException("Path is not a directory: " + p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // 创建Jar包的URL（使用 jar: 协议前缀）
    static URL toJarURL(Path p) {
        try {
            if (Files.isRegularFile(p)) {
                String abs = toAbsPath(p);
                return URI.create("jar:file://" + abs + "!/").toURL();
            }
            throw new IOException("Path is not a jar file: " + p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // 转换为绝对路径
    static String toAbsPath(Path p) throws IOException {
        String abs = p.toAbsolutePath().normalize().toString().replace('\\', '/');
        return abs;
    }
}
