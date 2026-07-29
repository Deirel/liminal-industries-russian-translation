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

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class MantleBookAuditProvider implements AuditProvider {
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
            collect(
                englishId,
                "",
                null,
                english,
                russian,
                structureMatches,
                subjects
            );
        }
        return subjects;
    }

    private void collect(
        ResourceLocation resourceId,
        String pointer,
        String field,
        JsonElement english,
        JsonElement russian,
        boolean structureMatches,
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
            subjects
        );
    }

    private void addSubject(
        ResourceLocation resourceId,
        String pointer,
        JsonElement english,
        JsonElement russian,
        boolean structureMatches,
        List<AuditSubject> subjects
    ) {
        boolean localized = structureMatches
            && russian != null
            && russian.isJsonPrimitive()
            && russian.getAsJsonPrimitive().isString()
            && !russian.getAsString().isBlank();
        subjects.add(new AuditSubject(
            id(),
            id() + ":" + resourceId + ":" + pointer,
            "mantle_book",
            resourceId.toString(),
            pointer,
            Component.literal(localized ? russian.getAsString() : english.getAsString()),
            localized,
            Set.of("MANTLE_BOOK_RESOURCE")
        ));
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
