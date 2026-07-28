package ru.deirel.liminalindustries.translation.audit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class RussianLanguageIndex {
    private final Map<String, String> values;
    private final Map<String, String> sources;
    private final List<String> errors;

    private RussianLanguageIndex(
        Map<String, String> values,
        Map<String, String> sources,
        List<String> errors
    ) {
        this.values = Map.copyOf(values);
        this.sources = Map.copyOf(sources);
        this.errors = List.copyOf(errors);
    }

    static RussianLanguageIndex load(ResourceManager manager, String language) {
        Map<String, String> values = new HashMap<>();
        Map<String, String> sources = new HashMap<>();
        List<String> errors = new ArrayList<>();

        List<String> namespaces = new ArrayList<>(manager.getNamespaces());
        Collections.sort(namespaces);
        for (String namespace : namespaces) {
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                namespace,
                "lang/" + language + ".json"
            );
            List<Resource> stack = manager.getResourceStack(location);
            for (Resource resource : stack) {
                readResource(location, resource, values, sources, errors);
            }
        }
        return new RussianLanguageIndex(values, sources, errors);
    }

    Map<String, String> values() {
        return values;
    }

    Map<String, String> sources() {
        return sources;
    }

    List<String> errors() {
        return errors;
    }

    private static void readResource(
        ResourceLocation location,
        Resource resource,
        Map<String, String> values,
        Map<String, String> sources,
        List<String> errors
    ) {
        try (Reader reader = resource.openAsReader()) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                errors.add(location + " from " + resource.sourcePackId() + " is not an object");
                return;
            }
            JsonObject object = parsed.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (!entry.getValue().isJsonPrimitive()
                    || !entry.getValue().getAsJsonPrimitive().isString()) {
                    errors.add(
                        location + " from " + resource.sourcePackId()
                            + " has a non-string value for " + entry.getKey()
                    );
                    continue;
                }
                values.put(entry.getKey(), entry.getValue().getAsString());
                sources.put(entry.getKey(), resource.sourcePackId());
            }
        } catch (IOException | RuntimeException exception) {
            errors.add(
                location + " from " + resource.sourcePackId() + ": " + exception.getMessage()
            );
        }
    }
}
