package ru.deirel.liminalindustries.translation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ftb.mods.ftblibrary.snbt.SNBT;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class QuestTranslationPayload {
    private static final String INDEX_RESOURCE = "/liminal_industries_ru/quest-files.list";
    private static final String SOURCE_FIELDS_RESOURCE =
        "/liminal_industries_ru/quest-source-fields.json";
    private static final String QUEST_RESOURCE_ROOT = "/liminal_industries_ru/quests/";
    private static final Pattern OBJECT_ID = Pattern.compile("[0-9A-Fa-f]{16}");

    private final Set<Long> objectIds;
    private final Map<Long, QuestTranslation> translations;

    private QuestTranslationPayload(Set<Long> objectIds, Map<Long, QuestTranslation> translations) {
        this.objectIds = Set.copyOf(objectIds);
        this.translations = Map.copyOf(translations);
    }

    static QuestTranslationPayload load(Class<?> resourceOwner) throws IOException {
        return load(resourceOwner::getResourceAsStream);
    }

    static QuestTranslationPayload load(ResourceReader resources) throws IOException {
        Set<Long> objectIds = new HashSet<>();
        Map<Long, RawQuestTranslation> rawTranslations = new HashMap<>();

        // FTB Quests always creates this in-memory default chapter group.
        objectIds.add(0L);

        for (String questFile : readIndex(resources)) {
            String resourcePath = QUEST_RESOURCE_ROOT + questFile;
            try (InputStream input = resources.open(resourcePath)) {
                if (input == null) {
                    throw new IOException("Missing embedded quest translation: " + resourcePath);
                }
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8)
                )) {
                    CompoundTag root = SNBT.readLines(reader.lines().toList());
                    collect(root, objectIds, rawTranslations);
                } catch (RuntimeException exception) {
                    throw new IOException("Invalid embedded quest translation: " + resourcePath, exception);
                }
            }
        }

        if (rawTranslations.isEmpty()) {
            throw new IOException("Embedded quest translations contain no text");
        }
        Map<Long, QuestSourceFields> sourceFields = readSourceFields(resources);
        Map<Long, QuestTranslation> translations = combine(
            rawTranslations,
            sourceFields
        );
        return new QuestTranslationPayload(objectIds, translations);
    }

    static QuestTranslationPayload empty() {
        return new QuestTranslationPayload(Set.of(), Map.of());
    }

    Set<Long> objectIds() {
        return objectIds;
    }

    QuestTranslation translation(long objectId) {
        return translations.get(objectId);
    }

    int translationCount() {
        return translations.size();
    }

    private static List<String> readIndex(ResourceReader resources) throws IOException {
        try (InputStream input = resources.open(INDEX_RESOURCE)) {
            if (input == null) {
                throw new IOException("Missing embedded quest translation index");
            }
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8)
            )) {
                List<String> files = reader.lines()
                    .filter(line -> !line.isBlank())
                    .toList();
                if (files.isEmpty()) {
                    throw new IOException("Embedded quest translation index is empty");
                }
                for (String file : files) {
                    if (file.startsWith("/") || file.contains("..") || !file.endsWith(".snbt")) {
                        throw new IOException("Unsafe quest translation path: " + file);
                    }
                }
                if (new HashSet<>(files).size() != files.size()) {
                    throw new IOException("Duplicate path in quest translation index");
                }
                return files;
            }
        }
    }

    private static void collect(
        Tag tag,
        Set<Long> objectIds,
        Map<Long, RawQuestTranslation> translations
    ) throws IOException {
        if (tag instanceof CompoundTag compound) {
            collectObject(compound, objectIds, translations);
            for (String key : compound.getAllKeys()) {
                Tag child = compound.get(key);
                if (child != null) {
                    collect(child, objectIds, translations);
                }
            }
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) {
                collect(child, objectIds, translations);
            }
        }
    }

    private static void collectObject(
        CompoundTag compound,
        Set<Long> objectIds,
        Map<Long, RawQuestTranslation> translations
    ) throws IOException {
        if (!compound.contains("id", Tag.TAG_STRING)) {
            return;
        }

        String encodedId = compound.getString("id");
        if (!OBJECT_ID.matcher(encodedId).matches()) {
            return;
        }

        long objectId = Long.parseUnsignedLong(encodedId, 16);
        if (!objectIds.add(objectId)) {
            throw new IOException("Duplicate FTB Quests object ID: " + encodedId);
        }

        String title = text(compound, "title");
        String subtitle = text(compound, "subtitle");
        List<String> description = stringList(compound, "description");
        RawQuestTranslation translation = new RawQuestTranslation(
            title,
            subtitle,
            description
        );
        if (!translation.isEmpty()) {
            translations.put(objectId, translation);
        }
    }

    private static Map<Long, QuestSourceFields> readSourceFields(
        ResourceReader resources
    ) throws IOException {
        try (InputStream input = resources.open(SOURCE_FIELDS_RESOURCE)) {
            if (input == null) {
                throw new IOException("Missing embedded quest source fields");
            }
            try (InputStreamReader reader = new InputStreamReader(
                input,
                StandardCharsets.UTF_8
            )) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (root.get("schema").getAsInt() != 1) {
                    throw new IOException("Unsupported quest source field schema");
                }
                Map<Long, MutableQuestSourceFields> mutable = new HashMap<>();
                Set<String> seen = new HashSet<>();
                for (JsonElement element : root.getAsJsonArray("fields")) {
                    JsonObject field = element.getAsJsonObject();
                    String encodedId = field.get("object_id").getAsString();
                    if (!OBJECT_ID.matcher(encodedId).matches()) {
                        throw new IOException(
                            "Invalid FTB Quests object ID: " + encodedId
                        );
                    }
                    String name = field.get("field").getAsString();
                    int index = field.has("index")
                        ? field.get("index").getAsInt()
                        : -1;
                    String key = encodedId + ":" + name + ":" + index;
                    if (!seen.add(key)) {
                        throw new IOException(
                            "Duplicate quest source field: " + key
                        );
                    }
                    SourceField source = new SourceField(
                        field.get("source").getAsString(),
                        field.get("source_hash").getAsString()
                    );
                    long objectId = Long.parseUnsignedLong(encodedId, 16);
                    MutableQuestSourceFields fields = mutable.computeIfAbsent(
                        objectId,
                        ignored -> new MutableQuestSourceFields()
                    );
                    fields.add(name, index, source);
                }
                Map<Long, QuestSourceFields> result = new HashMap<>();
                for (Map.Entry<Long, MutableQuestSourceFields> entry
                    : mutable.entrySet()) {
                    result.put(
                        entry.getKey(),
                        entry.getValue().freeze(entry.getKey())
                    );
                }
                return Map.copyOf(result);
            } catch (RuntimeException exception) {
                throw new IOException(
                    "Invalid embedded quest source fields",
                    exception
                );
            }
        }
    }

    private static Map<Long, QuestTranslation> combine(
        Map<Long, RawQuestTranslation> translations,
        Map<Long, QuestSourceFields> sources
    ) throws IOException {
        Map<Long, QuestTranslation> result = new HashMap<>();
        for (Map.Entry<Long, RawQuestTranslation> entry : translations.entrySet()) {
            long objectId = entry.getKey();
            RawQuestTranslation raw = entry.getValue();
            QuestSourceFields source = sources.get(objectId);
            if (source == null) {
                throw new IOException(
                    "Missing source metadata for translated quest object "
                        + Long.toUnsignedString(objectId, 16)
                );
            }
            QuestTranslation translation = new QuestTranslation(
                combine(source.title(), raw.title(), objectId, "title"),
                combine(source.subtitle(), raw.subtitle(), objectId, "subtitle"),
                combineDescription(source.description(), raw.description(), objectId)
            );
            if (!translation.isEmpty()) {
                result.put(objectId, translation);
            }
        }
        return Map.copyOf(result);
    }

    private static QuestTranslationField combine(
        SourceField source,
        String translation,
        long objectId,
        String field
    ) throws IOException {
        if (translation == null) {
            if (source != null) {
                throw new IOException(
                    "Source metadata has no matching " + field
                        + " translation for "
                        + Long.toUnsignedString(objectId, 16)
                );
            }
            return null;
        }
        if (source == null) {
            throw new IOException(
                "Missing " + field + " source metadata for "
                    + Long.toUnsignedString(objectId, 16)
            );
        }
        return new QuestTranslationField(
            source.source(),
            source.sourceHash(),
            translation
        );
    }

    private static List<QuestTranslationField> combineDescription(
        List<SourceField> sources,
        List<String> translations,
        long objectId
    ) throws IOException {
        if (sources.size() != translations.size()) {
            throw new IOException(
                "Description source metadata differs for "
                    + Long.toUnsignedString(objectId, 16)
            );
        }
        List<QuestTranslationField> result = new ArrayList<>(translations.size());
        for (int index = 0; index < translations.size(); index++) {
            SourceField source = sources.get(index);
            if (source == null) {
                throw new IOException(
                    "Missing description source metadata at index " + index
                        + " for " + Long.toUnsignedString(objectId, 16)
                );
            }
            result.add(new QuestTranslationField(
                source.source(),
                source.sourceHash(),
                translations.get(index)
            ));
        }
        return Collections.unmodifiableList(result);
    }

    private static String text(CompoundTag compound, String key) {
        return compound.contains(key, Tag.TAG_STRING) ? compound.getString(key) : null;
    }

    private static List<String> stringList(CompoundTag compound, String key) {
        if (!compound.contains(key, Tag.TAG_LIST)) {
            return List.of();
        }

        ListTag values = compound.getList(key, Tag.TAG_STRING);
        List<String> result = new ArrayList<>(values.size());
        for (Tag value : values) {
            if (value instanceof StringTag string) {
                result.add(string.getAsString());
            }
        }
        while (!result.isEmpty() && result.get(result.size() - 1).isEmpty()) {
            result.remove(result.size() - 1);
        }
        return result;
    }

    private record RawQuestTranslation(
        String title,
        String subtitle,
        List<String> description
    ) {
        boolean isEmpty() {
            return title == null && subtitle == null && description.isEmpty();
        }
    }

    private record SourceField(String source, String sourceHash) {
    }

    private record QuestSourceFields(
        SourceField title,
        SourceField subtitle,
        List<SourceField> description
    ) {
    }

    private static final class MutableQuestSourceFields {
        private SourceField title;
        private SourceField subtitle;
        private final List<SourceField> description = new ArrayList<>();

        void add(String field, int index, SourceField source) throws IOException {
            switch (field) {
                case "title" -> {
                    if (index != -1 || title != null) {
                        throw new IOException("Invalid quest title source metadata");
                    }
                    title = source;
                }
                case "subtitle" -> {
                    if (index != -1 || subtitle != null) {
                        throw new IOException("Invalid quest subtitle source metadata");
                    }
                    subtitle = source;
                }
                case "description" -> {
                    if (index < 0) {
                        throw new IOException(
                            "Description source metadata has no index"
                        );
                    }
                    while (description.size() <= index) {
                        description.add(null);
                    }
                    if (description.get(index) != null) {
                        throw new IOException(
                            "Duplicate description source metadata at index "
                                + index
                        );
                    }
                    description.set(index, source);
                }
                default -> throw new IOException(
                    "Unsupported quest source field: " + field
                );
            }
        }

        QuestSourceFields freeze(long objectId) throws IOException {
            for (int index = 0; index < description.size(); index++) {
                if (description.get(index) == null) {
                    throw new IOException(
                        "Missing description source metadata at index " + index
                            + " for " + Long.toUnsignedString(objectId, 16)
                    );
                }
            }
            return new QuestSourceFields(
                title,
                subtitle,
                List.copyOf(description)
            );
        }
    }

    @FunctionalInterface
    interface ResourceReader {
        InputStream open(String path);
    }
}
