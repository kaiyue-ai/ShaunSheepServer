package com.kaiyue.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class WarExtractor {
    
    /**
     * 解压 war 包到临时目录
     */
    public static Path extractWar(Path warPath) throws IOException {
        // 创建临时目录
        Path tempDir = Files.createTempDirectory("shaunsheep-webapp-");
        
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(warPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path targetPath = tempDir.resolve(entry.getName());
                
                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(zis, targetPath);
                }
                zis.closeEntry();
            }
        }
        
        return tempDir;
    }
}
