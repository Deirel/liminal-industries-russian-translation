package ru.deirel.liminalindustries.translation;

import java.io.IOException;
import java.io.InputStream;

final class ClasspathQuestPayload implements QuestPayload {
    private static final String ROOT = "/liminal_industries_ru/quests/";

    @Override
    public InputStream open(String relativePath) throws IOException {
        QuestPaths.validateRelativePath(relativePath);
        InputStream input = getClass().getResourceAsStream(ROOT + relativePath);
        if (input == null) {
            throw new IOException("Missing embedded quest file: " + relativePath);
        }
        return input;
    }
}
