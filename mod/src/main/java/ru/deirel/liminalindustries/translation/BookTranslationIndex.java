package ru.deirel.liminalindustries.translation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BookTranslationIndex {
    static final ResourceLocation LOCATION = ResourceLocation.fromNamespaceAndPath(
        LiminalIndustriesTranslationMod.MOD_ID,
        "book-translations.json"
    );

    enum Format {
        JSON,
        LINES,
        PROPERTIES,
        LANGUAGE_JSON
    }

    record ResourceKey(String namespace, String path) {
        ResourceLocation location() {
            return ResourceLocation.fromNamespaceAndPath(namespace, path);
        }
    }

    record Field(
        String id,
        String source,
        String pointer,
        Integer line,
        String key,
        String translation
    ) {
    }

    record Rule(
        Format format,
        ResourceKey source,
        String sourceSha256,
        ResourceLocation output,
        List<Field> fields,
        boolean exactOnly,
        List<String> fieldIds
    ) {
    }

    private final Map<ResourceLocation, Rule> rules;

    private BookTranslationIndex(Map<ResourceLocation, Rule> rules) {
        this.rules = Map.copyOf(rules);
    }

    static BookTranslationIndex load(Class<?> owner) throws IOException {
        String path = "assets/" + LOCATION.getNamespace() + "/" + LOCATION.getPath();
        try (InputStream stream = owner.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException(path + " is missing");
            }
            return parse(stream);
        }
    }

    static BookTranslationIndex parse(InputStream stream) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(
            stream,
            StandardCharsets.UTF_8
        )) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("schema").getAsInt() != 1) {
                throw new IOException("Unsupported book translation index");
            }
            Map<ResourceLocation, Rule> rules = new LinkedHashMap<>();
            for (JsonElement element : root.getAsJsonArray("resources")) {
                JsonObject value = element.getAsJsonObject();
                JsonObject sourceValue = value.getAsJsonObject("source");
                JsonObject outputValue = value.getAsJsonObject("output");
                ResourceKey source = new ResourceKey(
                    sourceValue.get("namespace").getAsString(),
                    sourceValue.get("path").getAsString()
                );
                ResourceLocation output = ResourceLocation.fromNamespaceAndPath(
                    outputValue.get("namespace").getAsString(),
                    outputValue.get("path").getAsString()
                );
                List<Field> fields = new ArrayList<>();
                for (JsonElement fieldElement : value.getAsJsonArray("fields")) {
                    JsonObject field = fieldElement.getAsJsonObject();
                    fields.add(new Field(
                        field.get("id").getAsString(),
                        field.get("source").getAsString(),
                        optionalString(field, "pointer"),
                        field.has("line") ? field.get("line").getAsInt() : null,
                        optionalString(field, "key"),
                        field.get("translation").getAsString()
                    ));
                }
                fields.sort(Comparator.comparing(Field::id));
                List<String> fieldIds = new ArrayList<>();
                if (value.has("field_ids")) {
                    for (JsonElement id : value.getAsJsonArray("field_ids")) {
                        fieldIds.add(id.getAsString());
                    }
                } else {
                    fields.stream().map(Field::id).forEach(fieldIds::add);
                }
                fieldIds.sort(String::compareTo);
                Rule rule = new Rule(
                    Format.valueOf(value.get("format").getAsString().toUpperCase()),
                    source,
                    optionalString(sourceValue, "sha256"),
                    output,
                    List.copyOf(fields),
                    value.has("exact_only")
                        && value.get("exact_only").getAsBoolean(),
                    List.copyOf(fieldIds)
                );
                if (rules.put(output, rule) != null) {
                    throw new IOException("Duplicate book output resource " + output);
                }
            }
            return new BookTranslationIndex(rules);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid book translation index", exception);
        }
    }

    Rule rule(ResourceLocation output) {
        return rules.get(output);
    }

    int size() {
        return rules.size();
    }

    private static String optionalString(JsonObject value, String key) {
        return value.has(key) ? value.get(key).getAsString() : null;
    }
}
