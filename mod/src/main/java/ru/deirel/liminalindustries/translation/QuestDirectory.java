package ru.deirel.liminalindustries.translation;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;

final class QuestDirectory {
    private QuestDirectory() {
    }

    static Map<String, String> hashes(Path root) throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw new IOException("Quest path is not a regular directory: " + root);
        }

        Map<String, String> hashes = new LinkedHashMap<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                throws IOException {
                if (Files.isSymbolicLink(directory)) {
                    throw new IOException("Symbolic link in quest directory: " + directory);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (attributes.isSymbolicLink() || !attributes.isRegularFile()) {
                    throw new IOException("Unsupported entry in quest directory: " + file);
                }
                String relative = root.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
                QuestPaths.validateRelativePath(relative);
                hashes.put(relative, Hashing.sha256(file));
                return FileVisitResult.CONTINUE;
            }
        });
        return hashes;
    }

    static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
