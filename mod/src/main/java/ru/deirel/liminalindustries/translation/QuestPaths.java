package ru.deirel.liminalindustries.translation;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

final class QuestPaths {
    private QuestPaths() {
    }

    static Path validateRelativePath(String value) {
        if (value == null || value.isBlank() || value.contains("\\") || value.contains("//")) {
            throw new IllegalArgumentException("Unsafe manifest path: " + value);
        }
        try {
            Path path = Path.of(value);
            if (path.isAbsolute() || path.getNameCount() == 0) {
                throw new IllegalArgumentException("Unsafe manifest path: " + value);
            }
            for (Path segment : path) {
                String name = segment.toString();
                if (name.isEmpty() || name.equals(".") || name.equals("..")) {
                    throw new IllegalArgumentException("Unsafe manifest path: " + value);
                }
            }
            if (!path.normalize().equals(path)) {
                throw new IllegalArgumentException("Unsafe manifest path: " + value);
            }
            return path;
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException("Unsafe manifest path: " + value, exception);
        }
    }
}
