package ru.deirel.liminalindustries.translation.audit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class TranslationAuditIndex {
    record LanguageRecord(String id, String translationKey, String source) {
    }

    private TranslationAuditIndex() {
    }

    static List<LanguageRecord> patchouliLanguageRecords() {
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
            if (root.get("schema").getAsInt() != 1) {
                throw new IllegalStateException("unsupported translation audit index");
            }
            List<LanguageRecord> result = new ArrayList<>();
            for (JsonElement element : root
                .getAsJsonObject("sources")
                .getAsJsonArray("patchouli")) {
                JsonObject record = element.getAsJsonObject();
                result.add(new LanguageRecord(
                    record.get("id").getAsString(),
                    record.get("translation_key").getAsString(),
                    record.get("source").getAsString()
                ));
            }
            return List.copyOf(result);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException(
                "could not load translation audit index",
                exception
            );
        }
    }
}
