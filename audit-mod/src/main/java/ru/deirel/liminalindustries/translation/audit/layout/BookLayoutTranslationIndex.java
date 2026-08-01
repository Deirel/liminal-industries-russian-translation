package ru.deirel.liminalindustries.translation.audit.layout;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BookLayoutTranslationIndex {
    private static final String PATH =
        "assets/liminal_industries_ru/book-translations.json";

    private final Map<String, List<Field>> fieldsByResource;

    private BookLayoutTranslationIndex(Map<String, List<Field>> fieldsByResource) {
        this.fieldsByResource = Map.copyOf(fieldsByResource);
    }

    static BookLayoutTranslationIndex load(Class<?> owner) {
        try (InputStream stream = owner.getClassLoader().getResourceAsStream(PATH)) {
            if (stream == null) {
                throw new IllegalStateException(PATH + " is missing");
            }
            return parse(stream);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load " + PATH, exception);
        }
    }

    static BookLayoutTranslationIndex parse(InputStream stream) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(
            stream,
            StandardCharsets.UTF_8
        )) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("schema").getAsInt() != 1) {
                throw new IOException("Unsupported book translation index");
            }
            Map<String, List<Field>> fields = new LinkedHashMap<>();
            for (JsonElement element : root.getAsJsonArray("resources")) {
                JsonObject resource = element.getAsJsonObject();
                JsonObject output = resource.getAsJsonObject("output");
                String path = "assets/" + output.get("namespace").getAsString()
                    + "/" + output.get("path").getAsString();
                List<Field> values = new ArrayList<>();
                for (JsonElement fieldElement : resource.getAsJsonArray("fields")) {
                    JsonObject field = fieldElement.getAsJsonObject();
                    values.add(new Field(
                        source(field),
                        field.get("source").getAsString(),
                        field.get("translation").getAsString()
                    ));
                }
                fields.put(path, List.copyOf(values));
            }
            return new BookLayoutTranslationIndex(fields);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid book translation index", exception);
        }
    }

    String source(String resource, String translation) {
        List<Field> matches = fieldsByResource
            .getOrDefault(resource, List.of())
            .stream()
            .filter(field -> field.english().equals(translation)
                || field.translation().equals(translation))
            .toList();
        return matches.size() == 1 ? matches.get(0).source() : null;
    }

    String source(String resource, String raw, String rendered) {
        String source = source(resource, raw);
        return source != null ? source : source(resource, rendered);
    }

    static String source(JsonElement resource, String raw, String rendered) {
        if (resource == null) {
            return null;
        }
        List<String> matches = new ArrayList<>();
        collectMatches(resource, "", raw, rendered, matches);
        List<String> distinct = matches.stream().distinct().toList();
        return distinct.size() == 1 ? "json_pointer:" + distinct.get(0) : null;
    }

    private static void collectMatches(
        JsonElement value,
        String pointer,
        String raw,
        String rendered,
        List<String> matches
    ) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String text = value.getAsString();
            if (text.equals(raw) || text.equals(rendered)) {
                matches.add(pointer.isEmpty() ? "/" : pointer);
            }
            return;
        }
        if (value.isJsonArray()) {
            for (int index = 0; index < value.getAsJsonArray().size(); index++) {
                collectMatches(
                    value.getAsJsonArray().get(index),
                    pointer + "/" + index,
                    raw,
                    rendered,
                    matches
                );
            }
            return;
        }
        if (value.isJsonObject()) {
            value.getAsJsonObject().entrySet().forEach(entry -> collectMatches(
                entry.getValue(),
                pointer + "/" + escapePointer(entry.getKey()),
                raw,
                rendered,
                matches
            ));
        }
    }

    private static String escapePointer(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }

    private static String source(JsonObject field) throws IOException {
        if (field.has("pointer")) {
            return "json_pointer:" + field.get("pointer").getAsString();
        }
        if (field.has("key")) {
            return "property_key:" + field.get("key").getAsString();
        }
        if (field.has("line")) {
            return "line:" + field.get("line").getAsInt();
        }
        throw new IOException("Book translation field has no address");
    }

    private record Field(String source, String english, String translation) {
    }
}
