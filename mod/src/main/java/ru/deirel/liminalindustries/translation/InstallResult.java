package ru.deirel.liminalindustries.translation;

import java.nio.file.Path;

public record InstallResult(Status status, QuestState initialState, Path backup, boolean restored) {
    public enum Status {
        INSTALLED,
        ALREADY_INSTALLED,
        REFUSED,
        FAILED
    }
}
