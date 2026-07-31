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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TranslationAuditIndex {
    public record LanguageRecord(String id, String translationKey, String source) {
    }

    public record BookRecord(String sourceId, ResourceLocation bookId) {
    }

    public record LanguageTarget(
        String translationKey,
        String source,
        String tier,
        String verification
    ) {
    }

    public record RegistryTarget(String registryType, String registryId) {
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

    static Map<String, LanguageTarget> languageTargets() {
        Map<String, LanguageTarget> result = new HashMap<>();
        for (JsonElement element : load().getAsJsonArray("language_targets")) {
            JsonObject record = element.getAsJsonObject();
            LanguageTarget target = new LanguageTarget(
                record.get("translation_key").getAsString(),
                record.get("source").isJsonNull()
                    ? null
                    : record.get("source").getAsString(),
                record.has("tier")
                    ? record.get("tier").getAsString()
                    : "required",
                record.has("verification")
                    ? record.get("verification").getAsString()
                    : "direct"
            );
            if (!target.tier().equals("required")
                && !target.tier().equals("extended")) {
                throw new IllegalStateException(
                    "unsupported translation tier " + target.tier()
                );
            }
            if (!target.verification().equals("direct")
                && !target.verification().equals("runtime_provider")) {
                throw new IllegalStateException(
                    "unsupported translation verification "
                        + target.verification()
                );
            }
            result.put(target.translationKey(), target);
        }
        return Map.copyOf(result);
    }

    static Map<String, String> acceptedSameAsEnglish() {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry :
            load().getAsJsonObject("accepted_same_as_english").entrySet()) {
            result.put(entry.getKey(), entry.getValue().getAsString());
        }
        return Map.copyOf(result);
    }

    static Set<RegistryTarget> registryTargets() {
        Set<RegistryTarget> result = new HashSet<>();
        for (JsonElement element : load().getAsJsonArray("registry_targets")) {
            JsonObject record = element.getAsJsonObject();
            result.add(new RegistryTarget(
                record.get("registry_type").getAsString(),
                record.get("registry_id").getAsString()
            ));
        }
        return Set.copyOf(result);
    }

    static Set<String> targetNamespaces() {
        Set<String> result = new HashSet<>();
        for (JsonElement element : load().getAsJsonArray("target_namespaces")) {
            result.add(element.getAsString());
        }
        return Set.copyOf(result);
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
            if (root.get("schema").getAsInt() != 4) {
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
