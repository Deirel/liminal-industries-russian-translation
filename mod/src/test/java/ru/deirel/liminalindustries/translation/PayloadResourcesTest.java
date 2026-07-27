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
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayloadResourcesTest {
    private static final Path TRANSLATION_SOURCES = Path.of("..", "src");
    private static final Path RESOURCE_PACK = TRANSLATION_SOURCES.resolve("resourcepack");
    private static final Path QUESTS = TRANSLATION_SOURCES.resolve("quests");
    private static final Path GENERATED_RESOURCES = Path.of("build", "resources", "main");

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
    void embeddedQuestsMatchManifestExactly() throws IOException {
        Path manifestPath = GENERATED_RESOURCES.resolve(
            "liminal_industries_ru/payload-manifest.json"
        );
        QuestManifest manifest;
        try (Reader input = Files.newBufferedReader(manifestPath, StandardCharsets.UTF_8)) {
            manifest = QuestManifest.read(input);
        }

        assertTrue(Files.isRegularFile(QUESTS.resolve("data.snbt")));
        assertTrue(Files.isRegularFile(QUESTS.resolve("chapter_groups.snbt")));
        assertTrue(Files.isDirectory(QUESTS.resolve("chapters")));
        assertFalse(manifest.originalFiles().isEmpty());
        assertEquals(normalized(manifest.translatedFiles()), QuestDirectory.hashes(QUESTS));
    }

    private static Map<String, String> normalized(Map<String, String> hashes) {
        return hashes.entrySet().stream().collect(java.util.stream.Collectors.toMap(
            Map.Entry::getKey,
            entry -> entry.getValue().toLowerCase(java.util.Locale.ROOT)
        ));
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
