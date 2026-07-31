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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RussianLanguageIndex {
    private final Map<String, String> values;
    private final Map<String, String> sources;
    private final Map<String, ResourceOrigin> origins;
    private final Set<ResourceOrigin> resources;
    private final List<String> errors;

    private RussianLanguageIndex(
        Map<String, String> values,
        Map<String, String> sources,
        Map<String, ResourceOrigin> origins,
        Set<ResourceOrigin> resources,
        List<String> errors
    ) {
        this.values = Map.copyOf(values);
        this.sources = Map.copyOf(sources);
        this.origins = Map.copyOf(origins);
        this.resources = Set.copyOf(resources);
        this.errors = List.copyOf(errors);
    }

    static RussianLanguageIndex load(ResourceManager manager, String language) {
        Map<String, String> values = new HashMap<>();
        Map<String, String> sources = new HashMap<>();
        Map<String, ResourceOrigin> origins = new HashMap<>();
        Set<ResourceOrigin> resources = new HashSet<>();
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
                ResourceOrigin origin = new ResourceOrigin(
                    namespace,
                    resource.sourcePackId()
                );
                resources.add(origin);
                readResource(
                    location,
                    resource,
                    origin,
                    values,
                    sources,
                    origins,
                    errors
                );
            }
        }
        return new RussianLanguageIndex(
            values,
            sources,
            origins,
            resources,
            errors
        );
    }

    Map<String, String> values() {
        return values;
    }

    Map<String, String> sources() {
        return sources;
    }

    Map<String, ResourceOrigin> origins() {
        return origins;
    }

    boolean hasResource(ResourceOrigin origin) {
        return resources.contains(origin);
    }

    List<String> errors() {
        return errors;
    }

    static RussianLanguageIndex of(
        Map<String, String> values,
        Map<String, ResourceOrigin> origins,
        Set<ResourceOrigin> resources
    ) {
        Map<String, String> sources = new HashMap<>();
        origins.forEach((key, origin) -> sources.put(key, origin.sourcePack()));
        return new RussianLanguageIndex(
            values,
            sources,
            origins,
            resources,
            List.of()
        );
    }

    private static void readResource(
        ResourceLocation location,
        Resource resource,
        ResourceOrigin origin,
        Map<String, String> values,
        Map<String, String> sources,
        Map<String, ResourceOrigin> origins,
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
                origins.put(entry.getKey(), origin);
            }
        } catch (IOException | RuntimeException exception) {
            errors.add(
                location + " from " + resource.sourcePackId() + ": " + exception.getMessage()
            );
        }
    }

    record ResourceOrigin(String namespace, String sourcePack) {
    }
}
