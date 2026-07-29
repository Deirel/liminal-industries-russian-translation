package ru.deirel.liminalindustries.translation.audit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import vazkii.patchouli.api.PatchouliAPI;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class PatchouliAuditProvider implements AuditProvider {
    private static final Set<String> TEXT_FIELDS = Set.of(
        "description",
        "landing_text",
        "link_text",
        "name",
        "subtitle",
        "text",
        "title"
    );

    @Override
    public String id() {
        return "patchouli";
    }

    @Override
    public List<AuditSubject> discover(AuditContext context) {
        ResourceManager manager = context.minecraft().getResourceManager();
        Map<ResourceLocation, Resource> englishResources = manager.listResources(
            "patchouli_books",
            location -> location.getPath().contains("/en_us/")
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
            collect(
                context,
                englishId,
                "",
                english,
                russian,
                subjects
            );
        }
        for (TranslationAuditIndex.LanguageRecord record
            : TranslationAuditIndex.patchouliLanguageRecords()) {
            subjects.add(new AuditSubject(
                id(),
                "patchouli-index:" + record.id(),
                "patchouli",
                record.translationKey(),
                record.id(),
                Component.translatable(record.translationKey()),
                false,
                Set.of("TRANSLATION_AUDIT_INDEX")
            ));
        }
        return subjects;
    }

    @Override
    public List<AuditBook> bookStacks() {
        List<AuditBook> books = new ArrayList<>();
        for (TranslationAuditIndex.BookRecord record
            : TranslationAuditIndex.bookRecords(id())) {
            books.add(new AuditBook(
                record.sourceId() + ":" + record.bookId(),
                PatchouliAPI.get().getBookStack(record.bookId())
            ));
        }
        return List.copyOf(books);
    }

    private void collect(
        AuditContext context,
        ResourceLocation resourceId,
        String pointer,
        JsonElement english,
        JsonElement russian,
        List<AuditSubject> subjects
    ) {
        if (english.isJsonObject()) {
            JsonObject englishObject = english.getAsJsonObject();
            JsonObject russianObject = russian != null && russian.isJsonObject()
                ? russian.getAsJsonObject()
                : null;
            for (Map.Entry<String, JsonElement> entry : englishObject.entrySet()) {
                String key = entry.getKey();
                JsonElement child = entry.getValue();
                JsonElement russianChild = russianObject == null
                    ? null
                    : russianObject.get(key);
                String childPointer = pointer + "/" + escape(key);
                if (TEXT_FIELDS.contains(key)
                    && child.isJsonPrimitive()
                    && child.getAsJsonPrimitive().isString()) {
                    String text = child.getAsString();
                    if (!text.isEmpty() && !text.startsWith("#")) {
                        Component rendered = context.english().values().containsKey(text)
                            ? Component.translatable(text)
                            : Component.literal(
                                russianChild != null
                                    && russianChild.isJsonPrimitive()
                                    && russianChild.getAsJsonPrimitive().isString()
                                    ? russianChild.getAsString()
                                    : text
                            );
                        subjects.add(subject(
                            resourceId,
                            childPointer,
                            rendered,
                            russianChild != null
                                && russianChild.isJsonPrimitive()
                                && russianChild.getAsJsonPrimitive().isString()
                        ));
                    }
                }
                collect(
                    context,
                    resourceId,
                    childPointer,
                    child,
                    russianChild,
                    subjects
                );
            }
        } else if (english.isJsonArray()) {
            JsonArray englishArray = english.getAsJsonArray();
            JsonArray russianArray = russian != null && russian.isJsonArray()
                ? russian.getAsJsonArray()
                : null;
            for (int index = 0; index < englishArray.size(); index++) {
                collect(
                    context,
                    resourceId,
                    pointer + "/" + index,
                    englishArray.get(index),
                    russianArray != null && index < russianArray.size()
                        ? russianArray.get(index)
                        : null,
                    subjects
                );
            }
        }
    }

    private AuditSubject subject(
        ResourceLocation resourceId,
        String pointer,
        Component rendered,
        boolean localizedLiteral
    ) {
        String uid = "patchouli:" + resourceId + ":" + pointer;
        return new AuditSubject(
            id(),
            uid,
            "patchouli",
            resourceId.toString(),
            pointer,
            rendered,
            localizedLiteral,
            Set.of("PATCHOULI_RESOURCE")
        );
    }

    private JsonElement read(Resource resource, ResourceLocation location) {
        try (Reader reader = resource.openAsReader()) {
            return JsonParser.parseReader(reader);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException(
                "Could not read Patchouli resource " + location,
                exception
            );
        }
    }

    private static String escape(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }
}
