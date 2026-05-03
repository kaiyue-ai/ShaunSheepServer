package com.kaiyue.classloader;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class WebAppClassLoader extends URLClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    public WebAppClassLoader(Path classPath, Path libPath) throws IOException {
        super("WebAppClassLoader", createUrls(classPath, libPath), ClassLoader.getSystemClassLoader());
    }

    static URL[] createUrls(Path classPath, Path libPath) throws IOException {
        List<URL> urls = new ArrayList<>();
        urls.add(toDirURL(classPath));
        if (libPath != null && Files.isDirectory(libPath)) {
            try (Stream<Path> list = Files.list(libPath)) {
                list.filter(p -> p.toString().endsWith(".jar"))
                        .sorted()
                        .forEach(p -> urls.add(toJarURL(p)));
            }
        }
        return urls.toArray(URL[]::new);
    }

    static URL toDirURL(Path p) {
        try {
            return new URL("file:" + p.toAbsolutePath().normalize().toString().replace('\\', '/') + "/");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static URL toJarURL(Path p) {
        try {
            return new URL("jar:file:" + p.toAbsolutePath().normalize().toString().replace('\\', '/') + "!/");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
