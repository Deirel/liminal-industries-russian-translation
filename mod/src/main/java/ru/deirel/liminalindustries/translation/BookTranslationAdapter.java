package ru.deirel.liminalindustries.translation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class BookTranslationAdapter {
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    record Result(
        byte[] bytes,
        int translatedFields,
        List<String> skippedFieldIds,
        boolean exactSource
    ) {
    }

    private BookTranslationAdapter() {
    }

    static Result adapt(
        BookTranslationIndex.Rule rule,
        byte[] source,
        byte[] exactOutput
    ) {
        if (rule.sourceSha256() != null
            && sha256(source).equals(rule.sourceSha256())) {
            if (exactOutput == null) {
                throw new IllegalArgumentException(
                    "Exact translated resource is missing for " + rule.output()
                );
            }
            return new Result(
                exactOutput,
                rule.fields().size(),
                List.of(),
                true
            );
        }
        return switch (rule.format()) {
            case JSON -> adaptJson(rule, source, exactOutput);
            case LINES -> adaptLines(rule, source);
            case PROPERTIES -> adaptProperties(rule, source);
            case LANGUAGE_JSON -> adaptLanguageJson(rule, source, exactOutput);
        };
    }

    static String sha256(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static Result adaptJson(
        BookTranslationIndex.Rule rule,
        byte[] source,
        byte[] baseline
    ) {
        if (baseline == null) {
            throw new IllegalArgumentException(
                "Translated JSON resource is missing for " + rule.output()
            );
        }
        JsonElement document = JsonParser.parseString(
            new String(source, StandardCharsets.UTF_8)
        );
        JsonElement baselineDocument = JsonParser.parseString(
            new String(baseline, StandardCharsets.UTF_8)
        );
        Map<String, String> strings = new LinkedHashMap<>();
        collectStrings(document, "", strings);
        Set<String> claimed = new HashSet<>();
        Set<String> applied = new HashSet<>();
        Set<String> translatedFields = rule.fields().stream()
            .map(BookTranslationIndex.Field::pointer)
            .map(BookTranslationAdapter::semanticField)
            .collect(java.util.stream.Collectors.toSet());

        for (BookTranslationIndex.Field field : rule.fields()) {
            String current = strings.get(field.pointer());
            if (field.source().equals(current)) {
                setPointer(document, field.pointer(), field.translation());
                claimed.add(field.pointer());
                applied.add(field.id());
            }
        }
        for (BookTranslationIndex.Field field : rule.fields()) {
            if (applied.contains(field.id())) {
                continue;
            }
            JsonElement expectedParent = pointerParent(
                baselineDocument,
                field.pointer()
            );
            JsonElement currentParent = pointerParentOrNull(
                document,
                field.pointer()
            );
            String semanticField = semanticField(field.pointer());
            if (currentParent != null
                && shape(expectedParent, translatedFields).equals(
                    shape(currentParent, translatedFields)
                )) {
                continue;
            }
            List<String> candidates = strings.entrySet().stream()
                .filter(entry -> !claimed.contains(entry.getKey()))
                .filter(entry -> field.source().equals(entry.getValue()))
                .filter(entry -> semanticField.equals(
                    semanticField(entry.getKey())
                ))
                .filter(entry -> shape(expectedParent, translatedFields).equals(
                    shape(
                        pointerParent(document, entry.getKey()),
                        translatedFields
                    )
                ))
                .map(Map.Entry::getKey)
                .toList();
            if (candidates.size() == 1) {
                String pointer = candidates.get(0);
                setPointer(document, pointer, field.translation());
                claimed.add(pointer);
                applied.add(field.id());
            }
        }
        return result(
            (GSON.toJson(document) + "\n").getBytes(StandardCharsets.UTF_8),
            rule.fields(),
            applied
        );
    }

    private static Result adaptLines(
        BookTranslationIndex.Rule rule,
        byte[] source
    ) {
        String text = new String(source, StandardCharsets.UTF_8);
        TextDocument document = TextDocument.parse(text);
        List<String> lines = new ArrayList<>(document.lines());
        Set<Integer> claimed = new HashSet<>();
        Set<String> applied = new HashSet<>();
        for (BookTranslationIndex.Field field : rule.fields()) {
            int line = field.line();
            if (line < lines.size() && field.source().equals(lines.get(line))) {
                lines.set(line, field.translation());
                claimed.add(line);
                applied.add(field.id());
            }
        }
        for (BookTranslationIndex.Field field : rule.fields()) {
            if (applied.contains(field.id())) {
                continue;
            }
            List<Integer> candidates = new ArrayList<>();
            for (int index = 0; index < lines.size(); index++) {
                if (!claimed.contains(index)
                    && field.source().equals(lines.get(index))) {
                    candidates.add(index);
                }
            }
            if (candidates.size() == 1) {
                int line = candidates.get(0);
                lines.set(line, field.translation());
                claimed.add(line);
                applied.add(field.id());
            }
        }
        return result(
            document.render(lines).getBytes(StandardCharsets.UTF_8),
            rule.fields(),
            applied
        );
    }

    private static Result adaptProperties(
        BookTranslationIndex.Rule rule,
        byte[] source
    ) {
        String text = new String(source, StandardCharsets.UTF_8);
        TextDocument document = TextDocument.parse(text);
        List<String> lines = new ArrayList<>(document.lines());
        Map<String, Integer> keyLines = new HashMap<>();
        Map<String, String> values = new HashMap<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            String stripped = line.strip();
            if (stripped.isEmpty() || stripped.startsWith("#")) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator < 0) {
                throw new IllegalArgumentException(
                    "Expected key=value at line " + (index + 1)
                );
            }
            String key = line.substring(0, separator).strip();
            if (key.isEmpty() || keyLines.put(key, index) != null) {
                throw new IllegalArgumentException(
                    "Invalid or duplicate key " + key
                );
            }
            values.put(key, line.substring(separator + 1).strip());
        }
        Set<String> applied = new HashSet<>();
        for (BookTranslationIndex.Field field : rule.fields()) {
            Integer line = keyLines.get(field.key());
            if (line != null && field.source().equals(values.get(field.key()))) {
                lines.set(line, field.key() + "=" + field.translation());
                applied.add(field.id());
            }
        }
        return result(
            document.render(lines).getBytes(StandardCharsets.UTF_8),
            rule.fields(),
            applied
        );
    }

    private static Result adaptLanguageJson(
        BookTranslationIndex.Rule rule,
        byte[] source,
        byte[] baseline
    ) {
        if (baseline == null) {
            throw new IllegalArgumentException(
                "Translated language resource is missing for " + rule.output()
            );
        }
        JsonObject english = JsonParser.parseString(
            new String(source, StandardCharsets.UTF_8)
        ).getAsJsonObject();
        JsonObject russian = JsonParser.parseString(
            new String(baseline, StandardCharsets.UTF_8)
        ).getAsJsonObject();
        Set<String> applied = new HashSet<>();
        for (BookTranslationIndex.Field field : rule.fields()) {
            JsonElement current = english.get(field.key());
            boolean literalFallback = current == null
                && field.key().equals(field.source());
            if (literalFallback
                || current != null
                    && current.isJsonPrimitive()
                    && current.getAsJsonPrimitive().isString()
                    && field.source().equals(current.getAsString())) {
                applied.add(field.id());
            } else {
                russian.remove(field.key());
            }
        }
        if (applied.size() == rule.fields().size()) {
            return new Result(
                baseline,
                applied.size(),
                List.of(),
                true
            );
        }
        return result(
            (GSON.toJson(russian) + "\n").getBytes(StandardCharsets.UTF_8),
            rule.fields(),
            applied
        );
    }

    private static Result result(
        byte[] bytes,
        List<BookTranslationIndex.Field> fields,
        Set<String> applied
    ) {
        List<String> skipped = fields.stream()
            .map(BookTranslationIndex.Field::id)
            .filter(id -> !applied.contains(id))
            .toList();
        return new Result(bytes, applied.size(), skipped, false);
    }

    private static void collectStrings(
        JsonElement value,
        String pointer,
        Map<String, String> output
    ) {
        if (value.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry :
                value.getAsJsonObject().entrySet()) {
                collectStrings(
                    entry.getValue(),
                    pointer + "/" + escape(entry.getKey()),
                    output
                );
            }
        } else if (value.isJsonArray()) {
            JsonArray values = value.getAsJsonArray();
            for (int index = 0; index < values.size(); index++) {
                collectStrings(
                    values.get(index),
                    pointer + "/" + index,
                    output
                );
            }
        } else if (value.isJsonPrimitive()
            && value.getAsJsonPrimitive().isString()) {
            output.put(pointer, value.getAsString());
        }
    }

    private static void setPointer(
        JsonElement document,
        String pointer,
        String replacement
    ) {
        List<String> tokens = pointerTokens(pointer);
        JsonElement current = document;
        for (String token : tokens.subList(0, tokens.size() - 1)) {
            current = current.isJsonArray()
                ? current.getAsJsonArray().get(Integer.parseInt(token))
                : current.getAsJsonObject().get(token);
        }
        String finalToken = tokens.get(tokens.size() - 1);
        if (current.isJsonArray()) {
            current.getAsJsonArray().set(
                Integer.parseInt(finalToken),
                GSON.toJsonTree(replacement)
            );
        } else {
            current.getAsJsonObject().addProperty(finalToken, replacement);
        }
    }

    private static JsonElement pointerParent(
        JsonElement document,
        String pointer
    ) {
        JsonElement current = document;
        List<String> tokens = pointerTokens(pointer);
        for (String token : tokens.subList(0, tokens.size() - 1)) {
            current = current.isJsonArray()
                ? current.getAsJsonArray().get(Integer.parseInt(token))
                : current.getAsJsonObject().get(token);
            if (current == null) {
                throw new IllegalArgumentException(
                    "JSON pointer is missing: " + pointer
                );
            }
        }
        return current;
    }

    private static JsonElement pointerParentOrNull(
        JsonElement document,
        String pointer
    ) {
        try {
            return pointerParent(document, pointer);
        } catch (
            IllegalArgumentException
            | IndexOutOfBoundsException exception
        ) {
            return null;
        }
    }

    private static String shape(
        JsonElement value,
        Set<String> translatedFields
    ) {
        if (value.isJsonObject()) {
            JsonObject result = new JsonObject();
            value.getAsJsonObject().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.add(
                    entry.getKey(),
                    shapeValue(
                        entry.getValue(),
                        translatedFields.contains(entry.getKey())
                    )
                ));
            return GSON.toJson(result);
        }
        return GSON.toJson(shapeValue(value, value.isJsonArray()));
    }

    private static JsonElement shapeValue(
        JsonElement value,
        boolean maskStrings
    ) {
        if (maskStrings
            && value.isJsonPrimitive()
            && value.getAsJsonPrimitive().isString()) {
            return GSON.toJsonTree("<text>");
        }
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement child : value.getAsJsonArray()) {
                result.add(shapeValue(child, maskStrings));
            }
            return result;
        }
        if (value.isJsonObject()) {
            JsonObject result = new JsonObject();
            value.getAsJsonObject().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.add(
                    entry.getKey(),
                    shapeValue(entry.getValue(), maskStrings)
                ));
            return result;
        }
        return value.deepCopy();
    }

    private static String semanticField(String pointer) {
        List<String> tokens = pointerTokens(pointer);
        for (int index = tokens.size() - 1; index >= 0; index--) {
            if (!tokens.get(index).chars().allMatch(Character::isDigit)) {
                return tokens.get(index);
            }
        }
        return "";
    }

    private static List<String> pointerTokens(String pointer) {
        if (pointer == null || !pointer.startsWith("/") || pointer.length() == 1) {
            throw new IllegalArgumentException("Invalid JSON pointer " + pointer);
        }
        return List.of(pointer.substring(1).split("/", -1)).stream()
            .map(token -> token.replace("~1", "/").replace("~0", "~"))
            .toList();
    }

    private static String escape(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }

    private record TextDocument(
        List<String> lines,
        List<String> endings
    ) {
        private TextDocument {
            lines = List.copyOf(lines);
            endings = List.copyOf(endings);
            if (lines.size() != endings.size()) {
                throw new IllegalArgumentException(
                    "Text lines and endings differ"
                );
            }
        }

        static TextDocument parse(String text) {
            List<String> lines = new ArrayList<>();
            List<String> endings = new ArrayList<>();
            int start = 0;
            for (int index = 0; index < text.length(); index++) {
                char value = text.charAt(index);
                if (value != '\r' && value != '\n') {
                    continue;
                }
                lines.add(text.substring(start, index));
                if (value == '\r'
                    && index + 1 < text.length()
                    && text.charAt(index + 1) == '\n') {
                    endings.add("\r\n");
                    index++;
                } else {
                    endings.add(String.valueOf(value));
                }
                start = index + 1;
            }
            if (start < text.length() || lines.isEmpty()) {
                lines.add(text.substring(start));
                endings.add("");
            }
            return new TextDocument(lines, endings);
        }

        String render(List<String> replacements) {
            if (replacements.size() != lines.size()) {
                throw new IllegalArgumentException(
                    "Replacement line count differs"
                );
            }
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < replacements.size(); index++) {
                result.append(replacements.get(index));
                result.append(endings.get(index));
            }
            return result.toString();
        }
    }
}
