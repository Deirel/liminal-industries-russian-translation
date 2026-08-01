package ru.deirel.liminalindustries.translation.audit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class MantleBookAuditProvider implements AuditProvider {
    private static final String BOOK_TRANSLATION_INDEX =
        "assets/liminal_industries_ru/book-translations.json";
    private static final Set<String> TEXT_FIELDS = Set.of(
        "effects",
        "properties",
        "subText",
        "text",
        "title",
        "tooltip"
    );

    @Override
    public String id() {
        return "tconstruct_books";
    }

    @Override
    public List<AuditSubject> discover(AuditContext context) {
        ResourceManager manager = context.minecraft().getResourceManager();
        Set<ResourceLocation> exactOnlyResources = exactOnlyResources();
        Map<ResourceLocation, Resource> englishResources = manager.listResources(
            "book",
            location -> location.getNamespace().equals("tconstruct")
                && location.getPath().contains("/en_us/")
                && location.getPath().endsWith(".json")
        );
        List<AuditSubject> subjects = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> entry : englishResources.entrySet()) {
            ResourceLocation englishId = entry.getKey();
            JsonElement english = read(entry.getValue(), englishId);
            ResourceLocation russianId = ResourceLocation.fromNamespaceAndPath(
                englishId.getNamespace(),
                englishId.getPath().replace("/en_us/", "/ru_ru/")
            );
            Optional<Resource> russianResource = manager.getResource(russianId);
            JsonElement russian = russianResource
                .map(resource -> read(resource, russianId))
                .orElse(null);
            boolean structureMatches = russian != null
                && normalizeStructure(english, null, false)
                    .equals(normalizeStructure(russian, null, false));
            boolean wholeResourceLocalized = isWholeResourceLocalized(
                english,
                russian,
                exactOnlyResources.contains(russianId)
            );
            collect(
                englishId,
                "",
                null,
                english,
                russian,
                structureMatches,
                wholeResourceLocalized,
                subjects
            );
        }
        collectLanguageEntries(manager, subjects);
        return subjects;
    }

    @Override
    public List<AuditBook> bookStacks() {
        return TranslationAuditIndex.bookRecords(id()).stream()
            .map(record -> AuditBooks.registryItem(
                record.sourceId(),
                record.bookId()
            ))
            .toList();
    }

    private void collect(
        ResourceLocation resourceId,
        String pointer,
        String field,
        JsonElement english,
        JsonElement russian,
        boolean structureMatches,
        boolean wholeResourceLocalized,
        List<AuditSubject> subjects
    ) {
        if (english.isJsonObject()) {
            JsonObject englishObject = english.getAsJsonObject();
            JsonObject russianObject = russian != null && russian.isJsonObject()
                ? russian.getAsJsonObject()
                : null;
            if (englishObject.has("action")
                && "add_group".equals(englishObject.get("action").getAsString())
                && englishObject.has("data")
                && englishObject.get("data").isJsonPrimitive()
                && englishObject.get("data").getAsJsonPrimitive().isString()) {
                JsonElement russianData = russianObject == null
                    ? null
                    : russianObject.get("data");
                addSubject(
                    resourceId,
                    pointer + "/data",
                    englishObject.get("data"),
                    russianData,
                    structureMatches,
                    wholeResourceLocalized,
                    subjects
                );
            }
            for (Map.Entry<String, JsonElement> entry : englishObject.entrySet()) {
                String key = entry.getKey();
                JsonElement russianChild = russianObject == null
                    ? null
                    : russianObject.get(key);
                collect(
                    resourceId,
                    pointer + "/" + escape(key),
                    key,
                    entry.getValue(),
                    russianChild,
                    structureMatches,
                    wholeResourceLocalized,
                    subjects
                );
            }
            return;
        }
        if (english.isJsonArray()) {
            JsonArray englishArray = english.getAsJsonArray();
            JsonArray russianArray = russian != null && russian.isJsonArray()
                ? russian.getAsJsonArray()
                : null;
            for (int index = 0; index < englishArray.size(); index++) {
                collect(
                    resourceId,
                    pointer + "/" + index,
                    field,
                    englishArray.get(index),
                    russianArray != null && index < russianArray.size()
                        ? russianArray.get(index)
                        : null,
                    structureMatches,
                    wholeResourceLocalized,
                    subjects
                );
            }
            return;
        }
        if (field == null
            || !TEXT_FIELDS.contains(field)
            || !english.isJsonPrimitive()
            || !english.getAsJsonPrimitive().isString()
            || english.getAsString().isBlank()) {
            return;
        }
        addSubject(
            resourceId,
            pointer,
            english,
            russian,
            structureMatches,
            wholeResourceLocalized,
            subjects
        );
    }

    private void addSubject(
        ResourceLocation resourceId,
        String pointer,
        JsonElement english,
        JsonElement russian,
        boolean structureMatches,
        boolean wholeResourceLocalized,
        List<AuditSubject> subjects
    ) {
        boolean fieldLocalized = structureMatches
            && russian != null
            && russian.isJsonPrimitive()
            && russian.getAsJsonPrimitive().isString()
            && !russian.getAsString().isBlank();
        boolean localized = wholeResourceLocalized || fieldLocalized;
        subjects.add(new AuditSubject(
            id(),
            id() + ":" + resourceId + ":" + pointer,
            "mantle_book",
            resourceId.toString(),
            pointer,
            Component.literal(
                fieldLocalized ? russian.getAsString() : english.getAsString()
            ),
            localized,
            Set.of("MANTLE_BOOK_RESOURCE")
        ));
    }

    private void collectLanguageEntries(
        ResourceManager manager,
        List<AuditSubject> subjects
    ) {
        Map<ResourceLocation, Resource> englishResources = manager.listResources(
            "book",
            location -> location.getNamespace().equals("tconstruct")
                && location.getPath().endsWith("/en_us/language.lang")
        );
        for (Map.Entry<ResourceLocation, Resource> entry : englishResources.entrySet()) {
            ResourceLocation englishId = entry.getKey();
            Map<String, String> english = readLanguage(
                entry.getValue(), englishId
            );
            ResourceLocation russianId = ResourceLocation.fromNamespaceAndPath(
                englishId.getNamespace(),
                englishId.getPath().replace(
                    "/en_us/language.lang",
                    "/ru_ru/language.lang"
                )
            );
            Map<String, String> russian = manager.getResource(russianId)
                .map(resource -> readLanguage(resource, russianId))
                .orElseGet(Map::of);
            for (Map.Entry<String, String> languageEntry : english.entrySet()) {
                String key = languageEntry.getKey();
                String translation = russian.get(key);
                boolean localized = translation != null
                    && !translation.isBlank();
                subjects.add(new AuditSubject(
                    id(),
                    id() + ":" + englishId + ":" + key,
                    "mantle_book_language",
                    englishId.toString(),
                    key,
                    Component.literal(localized ? translation : key),
                    localized,
                    Set.of("MANTLE_BOOK_LANGUAGE_RESOURCE")
                ));
            }
        }
    }

    static Map<String, String> parseLanguage(Reader reader) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        try (BufferedReader lines = new BufferedReader(reader)) {
            String line;
            int lineNumber = 0;
            while ((line = lines.readLine()) != null) {
                lineNumber++;
                String stripped = line.strip();
                if (stripped.isEmpty() || stripped.startsWith("#")) {
                    continue;
                }
                int separator = line.indexOf('=');
                if (separator < 0) {
                    throw new IllegalArgumentException(
                        "line " + lineNumber + ": expected key=value"
                    );
                }
                String key = line.substring(0, separator).strip();
                if (key.isEmpty()) {
                    throw new IllegalArgumentException(
                        "line " + lineNumber + ": blank key"
                    );
                }
                String previous = values.putIfAbsent(
                    key,
                    line.substring(separator + 1).strip()
                );
                if (previous != null) {
                    throw new IllegalArgumentException(
                        "line " + lineNumber + ": duplicate key " + key
                    );
                }
            }
        }
        return values;
    }

    static Set<ResourceLocation> parseExactOnlyResources(Reader reader) {
        Set<ResourceLocation> resources = new HashSet<>();
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        for (JsonElement element : root.getAsJsonArray("resources")) {
            JsonObject resource = element.getAsJsonObject();
            if (!resource.has("exact_only")
                || !resource.get("exact_only").getAsBoolean()) {
                continue;
            }
            JsonObject output = resource.getAsJsonObject("output");
            resources.add(ResourceLocation.fromNamespaceAndPath(
                output.get("namespace").getAsString(),
                output.get("path").getAsString()
            ));
        }
        return Set.copyOf(resources);
    }

    static boolean isWholeResourceLocalized(
        JsonElement english,
        JsonElement russian,
        boolean exactOnly
    ) {
        return exactOnly && russian != null && !english.equals(russian);
    }

    private Set<ResourceLocation> exactOnlyResources() {
        try (InputStream stream = getClass().getClassLoader()
            .getResourceAsStream(BOOK_TRANSLATION_INDEX)) {
            if (stream == null) {
                throw new IllegalStateException(
                    BOOK_TRANSLATION_INDEX + " is missing"
                );
            }
            return parseExactOnlyResources(new InputStreamReader(
                stream,
                StandardCharsets.UTF_8
            ));
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException(
                "Could not load " + BOOK_TRANSLATION_INDEX,
                exception
            );
        }
    }

    private Map<String, String> readLanguage(
        Resource resource,
        ResourceLocation location
    ) {
        try {
            return parseLanguage(resource.openAsReader());
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException(
                "Could not read Mantle language resource " + location,
                exception
            );
        }
    }

    static JsonElement normalizeStructure(
        JsonElement value,
        String field,
        boolean groupData
    ) {
        if (value.isJsonObject()) {
            JsonObject source = value.getAsJsonObject();
            JsonObject normalized = new JsonObject();
            boolean addGroup = source.has("action")
                && source.get("action").isJsonPrimitive()
                && "add_group".equals(source.get("action").getAsString());
            for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
                String key = entry.getKey();
                normalized.add(
                    key,
                    normalizeStructure(
                        entry.getValue(),
                        key,
                        addGroup && "data".equals(key)
                    )
                );
            }
            return normalized;
        }
        if (value.isJsonArray()) {
            JsonArray normalized = new JsonArray();
            for (JsonElement child : value.getAsJsonArray()) {
                normalized.add(normalizeStructure(child, field, groupData));
            }
            return normalized;
        }
        if ((groupData || (field != null && TEXT_FIELDS.contains(field)))
            && value.isJsonPrimitive()
            && value.getAsJsonPrimitive().isString()
            && !value.getAsString().isBlank()) {
            return new JsonPrimitive("<translated>");
        }
        return value.deepCopy();
    }

    private JsonElement read(Resource resource, ResourceLocation location) {
        try (Reader reader = resource.openAsReader()) {
            return JsonParser.parseReader(reader);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException(
                "Could not read Mantle book resource " + location,
                exception
            );
        }
    }

    private static String escape(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }
}
