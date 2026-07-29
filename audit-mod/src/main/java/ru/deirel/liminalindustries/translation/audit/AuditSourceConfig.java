package ru.deirel.liminalindustries.translation.audit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

final class AuditSourceConfig {
    private AuditSourceConfig() {
    }

    static Set<String> enabledProviders() {
        InputStream stream = AuditSourceConfig.class.getClassLoader()
            .getResourceAsStream("translation-sources.json");
        if (stream == null) {
            throw new IllegalStateException("translation-sources.json is missing");
        }
        try (InputStreamReader reader = new InputStreamReader(
            stream,
            StandardCharsets.UTF_8
        )) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("schema").getAsInt() != 1) {
                throw new IllegalStateException("unsupported translation source schema");
            }
            Set<String> result = new LinkedHashSet<>();
            for (JsonElement element : root.getAsJsonArray("sources")) {
                JsonObject source = element.getAsJsonObject();
                if (!source.has("audit") || source.get("audit").getAsBoolean()) {
                    result.add(source.get("id").getAsString());
                }
            }
            return Set.copyOf(result);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException(
                "could not load translation source configuration",
                exception
            );
        }
    }
}
