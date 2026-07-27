package ru.deirel.liminalindustries.translation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

public final class QuestInstaller {
    private static final DateTimeFormatter BACKUP_TIME =
        DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS'Z'").withZone(ZoneOffset.UTC);

    private final Path configDirectory;
    private final QuestManifest manifest;
    private final QuestPayload payload;
    private final QuestFileMover mover;
    private final Clock clock;

    public QuestInstaller(
        Path configDirectory,
        QuestManifest manifest,
        QuestPayload payload,
        QuestFileMover mover,
        Clock clock
    ) {
        this.configDirectory = configDirectory;
        this.manifest = manifest;
        this.payload = payload;
        this.mover = mover;
        this.clock = clock;
    }

    public InstallResult install() {
        Path quests = configDirectory.resolve("ftbquests/quests");
        QuestState state = QuestStateDetector.detect(quests, manifest);
        LiminalIndustriesTranslationMod.LOGGER.info("Quest directory state: {}", state.name().toLowerCase());

        if (state == QuestState.TRANSLATED) {
            return new InstallResult(InstallResult.Status.ALREADY_INSTALLED, state, null, false);
        }
        if (state != QuestState.ORIGINAL) {
            return new InstallResult(InstallResult.Status.REFUSED, state, null, false);
        }

        Path workRoot = configDirectory.resolve(LiminalIndustriesTranslationMod.MOD_ID);
        Path staging = workRoot.resolve("staging-" + UUID.randomUUID());
        Path stagedQuests = staging.resolve("quests");
        Path backup = null;
        boolean originalMoved = false;
        boolean restored = false;
        try {
            verifyPayload();
            LiminalIndustriesTranslationMod.LOGGER.info("Embedded quest payload verified");
            copyPayload(stagedQuests);
            if (!QuestDirectory.hashes(stagedQuests).equals(manifest.translatedFiles())) {
                throw new IOException("Staged quest payload failed verification");
            }

            backup = uniqueBackupPath(workRoot.resolve("backups"), Instant.now(clock));
            Files.createDirectories(backup.getParent());
            mover.move(quests, backup);
            originalMoved = true;
            try {
                mover.move(stagedQuests, quests);
            } catch (IOException installFailure) {
                restored = restoreBackup(quests, backup);
                throw new InstallationException("Could not move translated quests into place", installFailure, restored);
            }
            LiminalIndustriesTranslationMod.LOGGER.info("Quest translation installed; backup: {}", backup);
            return new InstallResult(InstallResult.Status.INSTALLED, state, backup, false);
        } catch (Exception exception) {
            if (exception instanceof InstallationException installationException) {
                restored = installationException.restored;
            } else if (originalMoved) {
                restored = restoreBackup(quests, backup);
            }
            LiminalIndustriesTranslationMod.LOGGER.error(
                "Quest translation installation failed; backup restored: {}", restored, exception
            );
            return new InstallResult(InstallResult.Status.FAILED, state, backup, restored);
        } finally {
            try {
                QuestDirectory.deleteRecursively(staging);
            } catch (IOException exception) {
                LiminalIndustriesTranslationMod.LOGGER.warn("Could not remove staging directory {}", staging, exception);
            }
        }
    }

    private void verifyPayload() throws IOException {
        for (Map.Entry<String, String> file : manifest.translatedFiles().entrySet()) {
            try (InputStream input = payload.open(file.getKey())) {
                if (!Hashing.sha256(input).equalsIgnoreCase(file.getValue())) {
                    throw new IOException("Embedded quest hash mismatch: " + file.getKey());
                }
            }
        }
    }

    private void copyPayload(Path destination) throws IOException {
        for (String relative : manifest.translatedFiles().keySet()) {
            Path output = destination.resolve(QuestPaths.validateRelativePath(relative));
            Files.createDirectories(output.getParent());
            try (InputStream input = payload.open(relative)) {
                Files.copy(input, output);
            }
        }
    }

    private Path uniqueBackupPath(Path backups, Instant instant) {
        Path candidate = backups.resolve(BACKUP_TIME.format(instant)).resolve("quests");
        int suffix = 2;
        while (Files.exists(candidate.getParent())) {
            candidate = backups.resolve(BACKUP_TIME.format(instant) + "-" + suffix).resolve("quests");
            suffix++;
        }
        return candidate;
    }

    private boolean restoreBackup(Path quests, Path backup) {
        if (backup == null || !Files.exists(backup) || Files.exists(quests)) {
            return false;
        }
        try {
            mover.move(backup, quests);
            return true;
        } catch (IOException restoreFailure) {
            LiminalIndustriesTranslationMod.LOGGER.error(
                "Could not restore quest backup {} to {}", backup, quests, restoreFailure
            );
            return false;
        }
    }

    private static final class InstallationException extends IOException {
        private final boolean restored;

        private InstallationException(String message, Throwable cause, boolean restored) {
            super(message, cause);
            this.restored = restored;
        }
    }
}
