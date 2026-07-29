package ru.deirel.liminalindustries.translation.audit;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ImmersiveEngineeringManualAuditProvider implements AuditProvider {
    @Override
    public String id() {
        return "immersive_engineering_manual";
    }

    @Override
    public List<AuditSubject> discover(AuditContext context) {
        ResourceManager manager = context.minecraft().getResourceManager();
        Map<ResourceLocation, Resource> englishResources = manager.listResources(
            "manual",
            location -> location.getNamespace().equals("immersiveengineering")
                && location.getPath().contains("/en_us/")
                && location.getPath().endsWith(".txt")
        );
        List<AuditSubject> subjects = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> entry : englishResources.entrySet()) {
            ResourceLocation englishId = entry.getKey();
            List<String> english = readLines(entry.getValue(), englishId);
            ResourceLocation russianId = ResourceLocation.fromNamespaceAndPath(
                englishId.getNamespace(),
                englishId.getPath().replace("/en_us/", "/ru_ru/")
            );
            Optional<Resource> russianResource = manager.getResource(russianId);
            List<String> russian = russianResource
                .map(resource -> readLines(resource, russianId))
                .orElseGet(List::of);
            boolean structureMatches = english.size() == russian.size()
                && lineMarkers(english).equals(lineMarkers(russian));
            for (int index = 0; index < english.size(); index++) {
                String source = english.get(index);
                if (!isTranslatable(source)) {
                    continue;
                }
                boolean localized = structureMatches
                    && index < russian.size()
                    && !russian.get(index).isBlank();
                String rendered = localized ? russian.get(index) : source;
                subjects.add(new AuditSubject(
                    id(),
                    id() + ":" + englishId + ":line:" + index,
                    "manual",
                    englishId.toString(),
                    "line:" + index,
                    Component.literal(rendered),
                    localized,
                    Set.of("IMMERSIVE_ENGINEERING_MANUAL_RESOURCE")
                ));
            }
        }
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

    List<String> readLines(Resource resource, ResourceLocation location) {
        try (var input = resource.open()) {
            List<String> lines = new ArrayList<>(new String(
                input.readAllBytes(),
                StandardCharsets.UTF_8
            ).lines().toList());
            while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
                lines.remove(lines.size() - 1);
            }
            return lines;
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Could not read manual resource " + location,
                exception
            );
        }
    }

    private boolean isTranslatable(String value) {
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            return false;
        }
        return !stripped.matches("(?:<[^>]+>\\s*)+")
            || stripped.contains("<link;");
    }

    private List<List<String>> lineMarkers(List<String> lines) {
        List<List<String>> result = new ArrayList<>();
        for (String line : lines) {
            result.add(markerSequence(List.of(line)));
        }
        return result;
    }

    private List<String> markerSequence(List<String> lines) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            int start = 0;
            while ((start = line.indexOf('<', start)) >= 0) {
                int end = line.indexOf('>', start);
                if (end < 0) {
                    result.add("<invalid>");
                    break;
                }
                String marker = line.substring(start, end + 1);
                if (marker.startsWith("<link;")) {
                    String[] parts = marker.substring(1, marker.length() - 1)
                        .split(";", -1);
                    marker = parts.length >= 3
                        ? parts[0] + ";" + parts[1] + ";*"
                            + (parts.length > 3
                                ? ";" + String.join(
                                    ";",
                                    List.of(parts).subList(3, parts.length)
                                )
                                : "")
                        : "<invalid-link>";
                }
                result.add(marker);
                start = end + 1;
            }
        }
        return result;
    }
}
