package ru.deirel.liminalindustries.translation.audit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class TranslationAuditIndex {
    public record LanguageRecord(String id, String translationKey, String source) {
    }

    public record BookRecord(String sourceId, ResourceLocation bookId) {
    }

    public record ScreenRecord(
        String engine,
        ResourceLocation bookId,
        String resource,
        String entry,
        Integer page,
        String textSourceType,
        String textSource,
        String source
    ) {
    }

    private TranslationAuditIndex() {
    }

    static List<LanguageRecord> patchouliLanguageRecords() {
        JsonObject root = load();
        List<LanguageRecord> result = new ArrayList<>();
        for (JsonElement element : root
            .getAsJsonObject("sources")
            .getAsJsonObject("patchouli")
            .getAsJsonArray("language_records")) {
            JsonObject record = element.getAsJsonObject();
            result.add(new LanguageRecord(
                record.get("id").getAsString(),
                record.get("translation_key").getAsString(),
                record.get("source").getAsString()
            ));
        }
        return List.copyOf(result);
    }

    static String version() {
        return load().get("version").getAsString();
    }

    static List<BookRecord> bookRecords(String sourceId) {
        JsonObject root = load();
        JsonObject source = root
            .getAsJsonObject("sources")
            .getAsJsonObject(sourceId);
        if (source == null) {
            return List.of();
        }
        List<BookRecord> result = new ArrayList<>();
        for (JsonElement element : source.getAsJsonArray("books")) {
            result.add(new BookRecord(
                sourceId,
                ResourceLocation.parse(element.getAsString())
            ));
        }
        return List.copyOf(result);
    }

    static List<BookRecord> allBookRecords() {
        JsonObject root = load();
        List<BookRecord> result = new ArrayList<>();
        for (String sourceId : root.getAsJsonObject("sources").keySet()) {
            result.addAll(bookRecords(sourceId));
        }
        return List.copyOf(result);
    }

    public static List<ScreenRecord> screenRecords(String engine) {
        JsonObject root = load();
        List<ScreenRecord> result = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("book_screens")) {
            JsonObject record = element.getAsJsonObject();
            if (!engine.equals(record.get("engine").getAsString())) {
                continue;
            }
            JsonObject textSource = record.getAsJsonObject("text_source");
            result.add(new ScreenRecord(
                engine,
                ResourceLocation.parse(record.get("book").getAsString()),
                record.get("resource").getAsString(),
                record.get("entry").getAsString(),
                record.has("page") && !record.get("page").isJsonNull()
                    ? record.get("page").getAsInt()
                    : null,
                textSource.get("type").getAsString(),
                textSource.get("value").getAsString(),
                record.get("source").getAsString()
            ));
        }
        return List.copyOf(result);
    }

    private static JsonObject load() {
        InputStream stream = TranslationAuditIndex.class.getClassLoader()
            .getResourceAsStream("translation-audit-index.json");
        if (stream == null) {
            throw new IllegalStateException("translation-audit-index.json is missing");
        }
        try (InputStreamReader reader = new InputStreamReader(
            stream,
            StandardCharsets.UTF_8
        )) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("schema").getAsInt() != 3) {
                throw new IllegalStateException("unsupported translation audit index");
            }
            return root;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException(
                "could not load translation audit index",
                exception
            );
        }
    }
}
