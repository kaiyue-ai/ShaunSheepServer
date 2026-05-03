package com.kaiyue.classloader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * WAR 包处理工具类
 * 
 * 负责将 WAR 文件解压到临时目录，并返回 WEB-INF/classes 和 WEB-INF/lib 的路径
 * 注册 JVM 关闭钩子确保临时目录在退出时自动清理
 */
public class WarUtils {

    static final Logger logger = LoggerFactory.getLogger(WarUtils.class);

    public static WarContext extractWar(Path warPath) throws IOException {
        if (!Files.isRegularFile(warPath)) {
            throw new IOException("WAR file not found: " + warPath);
        }
        String warName = warPath.getFileName().toString();
        if (warName.endsWith(".war")) {
            warName = warName.substring(0, warName.length() - 4);
        }
        Path tempDir = Files.createTempDirectory(warName + "-");
        logger.info("Extracting war to: {}", tempDir);
        try (ZipFile zipFile = new ZipFile(warPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path entryPath = tempDir.resolve(entry.getName()).normalize();
                if (!entryPath.startsWith(tempDir)) {
                    throw new IOException("Zip entry outside target dir: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    try (InputStream in = zipFile.getInputStream(entry);
                         OutputStream out = Files.newOutputStream(entryPath)) {
                        in.transferTo(out);
                    }
                }
            }
        }
        // Register cleanup hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                deleteDirectory(tempDir);
            } catch (IOException e) {
                logger.warn("Failed to delete temp dir: {}", tempDir);
            }
        }));
        Path classesPath = tempDir.resolve("WEB-INF/classes");
        Path libPath = tempDir.resolve("WEB-INF/lib");
        // 返回war上下文
        return new WarContext(tempDir, classesPath, libPath);
    }

    static void deleteDirectory(Path dir) throws IOException {
        if (Files.isDirectory(dir)) {
            try (var stream = Files.walk(dir)) {
                stream.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException e) {
                                logger.warn("Failed to delete: {}", p);
                            }
                        });
            }
        }
    }

    public record WarContext(Path tempDir, Path classesPath, Path libPath) {
        public boolean hasClasses() {
            return Files.isDirectory(classesPath);
        }

        public boolean hasLib() {
            return Files.isDirectory(libPath);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: java WarUtils <war-file>");
            System.exit(1);
        }
        WarContext ctx = extractWar(Paths.get(args[0]));
        System.out.println("Extracted to: " + ctx.tempDir());
        System.out.println("Classes: " + ctx.classesPath());
        System.out.println("Lib: " + ctx.libPath());
    }
}
