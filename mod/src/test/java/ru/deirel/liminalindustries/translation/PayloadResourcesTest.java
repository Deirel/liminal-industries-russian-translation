package ru.deirel.liminalindustries.translation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayloadResourcesTest {
    private static final Path TRANSLATION_SOURCES = Path.of(
        requiredProperty("translation.payload")
    );
    private static final Path RESOURCE_PACK = TRANSLATION_SOURCES.resolve("resourcepack");
    private static final Path QUESTS = TRANSLATION_SOURCES.resolve("quests");
    private static final Path COMPATIBILITY_PACK = Path.of(
        requiredProperty("translation.compatibilityPack")
    );
    private static final int EXPECTED_QUEST_OBJECT_IDS = Integer.parseInt(
        requiredProperty("translation.expectedQuestObjectIds")
    );
    private static final int EXPECTED_QUEST_TRANSLATIONS = Integer.parseInt(
        requiredProperty("translation.expectedQuestTranslations")
    );

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
    void sourcePayloadContainsNoEnglishCompatibilityOverrides() throws IOException {
        try (var paths = Files.walk(RESOURCE_PACK)) {
            assertFalse(
                paths.filter(Files::isRegularFile).anyMatch(path ->
                    path.getFileName().toString().equals("en_us.json")
                        || path.toString().contains("/en_us/")
                ),
                "English compatibility resources must not enter the generated Russian payload"
            );
        }
        assertTrue(
            Files.isRegularFile(COMPATIBILITY_PACK.resolve("pack.mcmeta")),
            "The optional compatibility pack must remain independently packageable"
        );
        try (var paths = Files.walk(COMPATIBILITY_PACK.resolve("assets"))) {
            assertTrue(
                paths.filter(Files::isRegularFile).anyMatch(path ->
                    path.getFileName().toString().equals("en_us.json")
                        || path.toString().contains("/en_us/")
                ),
                "Expected technical English resources in the compatibility pack"
            );
        }
    }

    @Test
    void managedPackEmbedsCompatibilityResources() throws IOException {
        String english = "assets/botania/patchouli_books/lexicon/en_us/"
            + "entries/alfhomancy/intro.json";
        try (InputStream stream = PayloadResourcesTest.class
            .getClassLoader()
            .getResourceAsStream(english)) {
            assertNotNull(stream, "English compatibility resource is missing");
            JsonObject entry = JsonParser.parseReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
            ).getAsJsonObject();
            assertEquals(
                "spotlight",
                entry.getAsJsonArray("pages")
                    .get(2)
                    .getAsJsonObject()
                    .get("type")
                    .getAsString()
            );
        }

    }

    @Test
    void translationPackUsesBottomAsItsFallbackPosition() {
        assertEquals(
            Pack.Position.BOTTOM,
            LiminalIndustriesTranslationMod.translationPackPosition()
        );
    }

    @Test
    void packagedBooksHaveSourceAwareCompatibilityMetadata()
        throws IOException {
        BookTranslationIndex index = BookTranslationIndex.load(
            PayloadResourcesTest.class
        );

        assertTrue(index.size() > 0, "No source-aware book resources indexed");
        assertNotNull(index.rule(ResourceLocation.fromNamespaceAndPath(
            "thermal",
            "patchouli_books/guidebook/ru_ru/entries/technology/"
                + "augments/dynamo_output.json"
        )));
    }

    @Test
    void embeddedQuestTranslationsAreIndexedAndParseable() throws IOException {
        assertTrue(Files.isRegularFile(QUESTS.resolve("data.snbt")));
        assertTrue(Files.isRegularFile(QUESTS.resolve("chapter_groups.snbt")));
        assertTrue(Files.isDirectory(QUESTS.resolve("chapters")));

        QuestTranslationPayload payload = QuestTranslationPayload.load(
            PayloadResourcesTest.class
        );
        assertEquals(EXPECTED_QUEST_OBJECT_IDS, payload.objectIds().size());
        assertEquals(EXPECTED_QUEST_TRANSLATIONS, payload.translationCount());

        long firstQuestId = Long.parseUnsignedLong("74DC667B840746B0", 16);
        QuestTranslation firstQuest = payload.translation(firstQuestId);
        assertNotNull(firstQuest);
        assertEquals(
            "Ломаем стулья",
            firstQuest.translatedTitle("Punching Chairs")
        );
        assertEquals(
            "Добро пожаловать в Liminal Industries!",
            firstQuest.translatedSubtitle("Welcome to Liminal Industries!")
        );
        assertEquals(5, firstQuest.description().size());

        assertEquals(null, firstQuest.translatedTitle("User title"));
        assertEquals(null, payload.translation(0x1234L));
    }

    @Test
    void incompleteDescriptionSourceMetadataFailsPayloadLoad() throws IOException {
        JsonObject metadata = questSourceMetadata();
        JsonArray fields = metadata.getAsJsonArray("fields");
        boolean removed = false;
        for (int index = 0; index < fields.size(); index++) {
            JsonObject field = fields.get(index).getAsJsonObject();
            if ("74DC667B840746B0".equals(
                    field.get("object_id").getAsString()
                )
                && "description".equals(field.get("field").getAsString())
                && field.get("index").getAsInt() == 4) {
                fields.remove(index);
                removed = true;
                break;
            }
        }
        assertTrue(removed, "Expected description metadata fixture");

        IOException error = assertThrows(
            IOException.class,
            () -> loadWithMetadata(metadata)
        );
        assertTrue(error.getMessage().contains(
            "Description source metadata differs"
        ));
    }

    @Test
    void nonContiguousDescriptionSourceMetadataFailsPayloadLoad()
        throws IOException {
        JsonObject metadata = questSourceMetadata();
        JsonArray fields = metadata.getAsJsonArray("fields");
        boolean removed = false;
        for (int index = 0; index < fields.size(); index++) {
            JsonObject field = fields.get(index).getAsJsonObject();
            if ("74DC667B840746B0".equals(
                    field.get("object_id").getAsString()
                )
                && "description".equals(field.get("field").getAsString())
                && field.get("index").getAsInt() == 2) {
                fields.remove(index);
                removed = true;
                break;
            }
        }
        assertTrue(removed, "Expected description metadata fixture");

        IOException error = assertThrows(
            IOException.class,
            () -> loadWithMetadata(metadata)
        );
        assertTrue(error.getMessage().contains(
            "Missing description source metadata at index 2"
        ));
    }

    @Test
    void extraDescriptionSourceMetadataFailsPayloadLoad() throws IOException {
        JsonObject metadata = questSourceMetadata();
        JsonObject extra = new JsonObject();
        extra.addProperty("object_id", "74DC667B840746B0");
        extra.addProperty("field", "description");
        extra.addProperty("source", "Unexpected source");
        extra.addProperty(
            "source_hash",
            QuestTranslationField.sourceHash("Unexpected source")
        );
        extra.addProperty("index", 5);
        metadata.getAsJsonArray("fields").add(extra);

        IOException error = assertThrows(
            IOException.class,
            () -> loadWithMetadata(metadata)
        );
        assertTrue(error.getMessage().contains(
            "Description source metadata differs"
        ));
    }

    @Test
    void translationResourcesLoadBeforeThermal() throws IOException {
        try (InputStream input = PayloadResourcesTest.class.getResourceAsStream(
            "/META-INF/mods.toml"
        )) {
            assertNotNull(input);
            String modsToml = new String(
                input.readAllBytes(),
                StandardCharsets.UTF_8
            );
            assertTrue(modsToml.contains("""
                modId = "thermal"
                mandatory = false
                versionRange = "[0,)"
                ordering = "BEFORE"
                side = "CLIENT"
                """));
        }
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing system property: " + name);
        }
        return value;
    }

    private static JsonObject questSourceMetadata() throws IOException {
        try (InputStream input = PayloadResourcesTest.class.getResourceAsStream(
            "/liminal_industries_ru/quest-source-fields.json"
        )) {
            assertNotNull(input);
            try (InputStreamReader reader = new InputStreamReader(
                input,
                StandardCharsets.UTF_8
            )) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    private static QuestTranslationPayload loadWithMetadata(
        JsonObject metadata
    ) throws IOException {
        byte[] encoded = (metadata + "\n").getBytes(StandardCharsets.UTF_8);
        return QuestTranslationPayload.load(path ->
            path.endsWith("/quest-source-fields.json")
                ? new ByteArrayInputStream(encoded)
                : PayloadResourcesTest.class.getResourceAsStream(path)
        );
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
