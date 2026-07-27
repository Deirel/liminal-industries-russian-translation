package ru.deirel.liminalindustries.translation;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayloadResourcesTest {
    private static final Path TRANSLATION_SOURCES = Path.of("..", "src");
    private static final Path RESOURCE_PACK = TRANSLATION_SOURCES.resolve("resourcepack");
    private static final Path QUESTS = TRANSLATION_SOURCES.resolve("quests");

    @Test
    void languageFilesAreStrictJsonWithoutDuplicateKeys() throws IOException {
        Path assets = RESOURCE_PACK.resolve("assets");
        int files = 0;
        try (var paths = Files.walk(assets)) {
            for (Path path : paths.filter(file -> file.getFileName().toString().equals("ru_ru.json")).toList()) {
                try (Reader input = Files.newBufferedReader(path, StandardCharsets.UTF_8);
                     JsonReader json = new JsonReader(input)) {
                    json.setLenient(false);
                    readValue(json, path);
                    assertEquals(JsonToken.END_DOCUMENT, json.peek(), "Trailing JSON content in " + path);
                }
                files++;
            }
        }
        assertTrue(files > 0, "No Russian language files found");
    }

    @Test
    void embeddedQuestTranslationsAreIndexedAndParseable() throws IOException {
        assertTrue(Files.isRegularFile(QUESTS.resolve("data.snbt")));
        assertTrue(Files.isRegularFile(QUESTS.resolve("chapter_groups.snbt")));
        assertTrue(Files.isDirectory(QUESTS.resolve("chapters")));

        QuestTranslationPayload payload = QuestTranslationPayload.load(
            PayloadResourcesTest.class
        );
        assertEquals(1038, payload.objectIds().size());
        assertTrue(payload.translationCount() > 250);

        long firstQuestId = Long.parseUnsignedLong("74DC667B840746B0", 16);
        QuestTranslation firstQuest = payload.translation(firstQuestId);
        assertNotNull(firstQuest);
        assertEquals("Ломаем стулья", firstQuest.title());
        assertEquals("Добро пожаловать в Liminal Industries!", firstQuest.subtitle());
        assertEquals(5, firstQuest.description().size());

        assertTrue(payload.matchesObjectIds(payload.objectIds()));
        Set<Long> incompleteIds = new HashSet<>(payload.objectIds());
        incompleteIds.remove(firstQuestId);
        assertFalse(payload.matchesObjectIds(incompleteIds));
    }

    private static void readValue(JsonReader json, Path source) throws IOException {
        switch (json.peek()) {
            case BEGIN_OBJECT -> {
                json.beginObject();
                Set<String> names = new HashSet<>();
                while (json.hasNext()) {
                    String name = json.nextName();
                    assertTrue(names.add(name), "Duplicate JSON key '" + name + "' in " + source);
                    readValue(json, source);
                }
                json.endObject();
            }
            case BEGIN_ARRAY -> {
                json.beginArray();
                while (json.hasNext()) {
                    readValue(json, source);
                }
                json.endArray();
            }
            case STRING -> json.nextString();
            case NUMBER -> json.nextString();
            case BOOLEAN -> json.nextBoolean();
            case NULL -> json.nextNull();
            default -> throw new IOException("Unexpected JSON token in " + source + ": " + json.peek());
        }
    }
}
