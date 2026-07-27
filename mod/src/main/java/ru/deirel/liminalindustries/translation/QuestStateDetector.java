package ru.deirel.liminalindustries.translation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;

public final class QuestStateDetector {
    private QuestStateDetector() {
    }

    public static QuestState detect(Path quests, QuestManifest manifest) {
        if (!Files.exists(quests, LinkOption.NOFOLLOW_LINKS)) {
            return QuestState.MISSING;
        }
        try {
            Map<String, String> hashes = QuestDirectory.hashes(quests);
            if (hashes.equals(manifest.originalFiles())) {
                return QuestState.ORIGINAL;
            }
            if (hashes.equals(manifest.translatedFiles())) {
                return QuestState.TRANSLATED;
            }
        } catch (IOException | IllegalArgumentException exception) {
            return QuestState.UNKNOWN;
        }
        return QuestState.UNKNOWN;
    }
}
