package ru.citeck.ecos.apps.app.application;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class FileUtils {

    public static List<Path> findFiles(File root, String pattern) {

        if (!pattern.contains(":")) {
            pattern = "glob:" + pattern;
        }

        Path rootPath = root.toPath();
        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(pattern);

        List<Path> filePaths = new ArrayList<>();

        try {
            Files.walkFileTree(Paths.get(root.getAbsolutePath()), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                    Path relative = rootPath.relativize(path);
                    if (pathMatcher.matches(relative)) {
                        filePaths.add(path);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return filePaths;
    }
}
