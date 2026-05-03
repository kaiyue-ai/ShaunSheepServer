package com.kaiyue.engine.classLoader;

import java.nio.file.Path;

public class Resource {
    private final Path path;
    private final String name;

    public Resource(Path path, String name) {
        this.path = path;
        this.name = name;
    }

    public Path getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "Resource{path=" + path + ", name='" + name + "'}";
    }
}
