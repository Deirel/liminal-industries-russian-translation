package ru.deirel.liminalindustries.translation;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@FunctionalInterface
public interface QuestFileMover {
    void move(Path source, Path target) throws IOException;

    static QuestFileMover system() {
        return (source, target) -> {
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(source, target);
            }
        };
    }
}
