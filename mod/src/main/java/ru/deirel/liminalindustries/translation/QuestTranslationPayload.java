package ru.deirel.liminalindustries.translation;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class QuestTranslationPayload {
    private static final String INDEX_RESOURCE = "/liminal_industries_ru/quest-files.list";
    private static final String QUEST_RESOURCE_ROOT = "/liminal_industries_ru/quests/";
    private static final Pattern OBJECT_ID = Pattern.compile("[0-9A-Fa-f]{16}");

    private final Set<Long> objectIds;
    private final Map<Long, QuestTranslation> translations;

    private QuestTranslationPayload(Set<Long> objectIds, Map<Long, QuestTranslation> translations) {
        this.objectIds = Set.copyOf(objectIds);
        this.translations = Map.copyOf(translations);
    }

    static QuestTranslationPayload load(Class<?> resourceOwner) throws IOException {
        Set<Long> objectIds = new HashSet<>();
        Map<Long, QuestTranslation> translations = new HashMap<>();

        // FTB Quests always creates this in-memory default chapter group.
        objectIds.add(0L);

        for (String questFile : readIndex(resourceOwner)) {
            String resourcePath = QUEST_RESOURCE_ROOT + questFile;
            try (InputStream input = resourceOwner.getResourceAsStream(resourcePath)) {
                if (input == null) {
                    throw new IOException("Missing embedded quest translation: " + resourcePath);
                }
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8)
                )) {
                    CompoundTag root = SNBT.readLines(reader.lines().toList());
                    collect(root, objectIds, translations);
                } catch (RuntimeException exception) {
                    throw new IOException("Invalid embedded quest translation: " + resourcePath, exception);
                }
            }
        }

        if (translations.isEmpty()) {
            throw new IOException("Embedded quest translations contain no text");
        }
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

    boolean matchesObjectIds(Set<Long> actualObjectIds) {
        return objectIds.equals(actualObjectIds);
    }

    private static List<String> readIndex(Class<?> resourceOwner) throws IOException {
        try (InputStream input = resourceOwner.getResourceAsStream(INDEX_RESOURCE)) {
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
        Map<Long, QuestTranslation> translations
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
        Map<Long, QuestTranslation> translations
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
        QuestTranslation translation = new QuestTranslation(title, subtitle, description);
        if (!translation.isEmpty()) {
            translations.put(objectId, translation);
        }
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
        return result;
    }
}
