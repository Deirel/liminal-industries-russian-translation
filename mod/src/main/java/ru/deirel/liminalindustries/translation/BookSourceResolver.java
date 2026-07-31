package ru.deirel.liminalindustries.translation;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.locating.IModFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

interface BookSourceResolver {
    record Resolution(byte[] bytes, String error) {
        static Resolution found(byte[] bytes) {
            return new Resolution(bytes, null);
        }

        static Resolution missing(String error) {
            return new Resolution(null, error);
        }

        boolean found() {
            return bytes != null;
        }
    }

    Resolution resolve(
        BookTranslationIndex.ResourceKey resource,
        String expectedSha256
    );

    static BookSourceResolver runtime() {
        return effectiveThenFallback(
            effectiveClientPacks(),
            installedMods()
        );
    }

    static BookSourceResolver effectiveThenFallback(
        BookSourceResolver effective,
        BookSourceResolver fallback
    ) {
        return (resource, expectedSha256) -> {
            Resolution resolved = effective.resolve(resource, expectedSha256);
            return resolved.found()
                ? resolved
                : fallback.resolve(resource, expectedSha256);
        };
    }

    static BookSourceResolver effectiveClientPacks() {
        return (resource, expectedSha256) -> {
            List<Pack> selected;
            try {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft == null
                    || minecraft.getResourcePackRepository() == null) {
                    return Resolution.missing(
                        "effective resource stack is unavailable"
                    );
                }
                selected = new ArrayList<>(
                    minecraft.getResourcePackRepository().getSelectedPacks()
                );
            } catch (RuntimeException exception) {
                return Resolution.missing(
                    "effective resource stack is unavailable: "
                        + exception.getMessage()
                );
            }
            if ("lang/en_us.json".equals(resource.path())) {
                return resolveEffectiveLanguage(selected, resource);
            }
            Collections.reverse(selected);
            for (Pack pack : selected) {
                if (isTranslationBaseline(pack)) {
                    continue;
                }
                Resolution candidate = read(pack, resource);
                if (candidate.found()) {
                    return candidate;
                }
            }
            return Resolution.missing(
                "effective upstream resource is missing"
            );
        };
    }

    static BookSourceResolver installedMods() {
        return (resource, expectedSha256) -> {
            Map<Path, Path> matches = new LinkedHashMap<>();
            ModList.get().forEachModFile(modFile -> {
                Path candidate = findResource(modFile, resource);
                if (Files.isRegularFile(candidate)) {
                    matches.putIfAbsent(
                        modFile.getFilePath().toAbsolutePath().normalize(),
                        candidate
                    );
                }
            });
            if (matches.isEmpty()) {
                return Resolution.missing("upstream resource is missing");
            }
            if (matches.size() > 1) {
                if (expectedSha256 != null) {
                    for (Path path : matches.values()) {
                        try {
                            byte[] bytes = Files.readAllBytes(path);
                            if (expectedSha256.equals(
                                BookTranslationAdapter.sha256(bytes)
                            )) {
                                return Resolution.found(bytes);
                            }
                        } catch (IOException exception) {
                            return Resolution.missing(
                                "could not read upstream resource: "
                                    + exception.getMessage()
                            );
                        }
                    }
                }
                if ("lang/en_us.json".equals(resource.path())) {
                    return mergeLanguageFiles(matches);
                }
                return Resolution.missing(
                    "upstream resource is ambiguous across "
                        + matches.size() + " mod files"
                );
            }
            try {
                return Resolution.found(
                    Files.readAllBytes(matches.values().iterator().next())
                );
            } catch (IOException exception) {
                return Resolution.missing(
                    "could not read upstream resource: " + exception.getMessage()
                );
            }
        };
    }

    private static Resolution resolveEffectiveLanguage(
        List<Pack> selected,
        BookTranslationIndex.ResourceKey resource
    ) {
        JsonObject merged = new JsonObject();
        boolean found = false;
        for (Pack pack : selected) {
            if (isTranslationBaseline(pack)) {
                continue;
            }
            Resolution candidate = read(pack, resource);
            if (!candidate.found()) {
                continue;
            }
            found = true;
            try {
                JsonObject document = JsonParser.parseString(
                    new String(
                        candidate.bytes(),
                        java.nio.charset.StandardCharsets.UTF_8
                    )
                ).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry :
                    document.entrySet()) {
                    merged.add(entry.getKey(), entry.getValue());
                }
            } catch (RuntimeException exception) {
                return Resolution.missing(
                    "effective language resource is invalid: "
                        + exception.getMessage()
                );
            }
        }
        return found
            ? Resolution.found(
                new Gson().toJson(merged).getBytes(
                    java.nio.charset.StandardCharsets.UTF_8
                )
            )
            : Resolution.missing(
                "effective upstream language resource is missing"
            );
    }

    private static Resolution read(
        Pack pack,
        BookTranslationIndex.ResourceKey resource
    ) {
        try (PackResources resources = pack.open()) {
            IoSupplier<InputStream> supplier = resources.getResource(
                PackType.CLIENT_RESOURCES,
                resource.location()
            );
            if (supplier == null) {
                return Resolution.missing("resource is absent from " + pack.getId());
            }
            try (InputStream stream = supplier.get()) {
                return Resolution.found(stream.readAllBytes());
            }
        } catch (IOException | RuntimeException exception) {
            return Resolution.missing(
                "could not read " + pack.getId() + ": "
                    + exception.getMessage()
            );
        }
    }

    private static boolean isTranslationBaseline(Pack pack) {
        return (
            LiminalIndustriesTranslationMod.MOD_ID + "_baseline"
        ).equals(pack.getId());
    }

    private static Resolution mergeLanguageFiles(Map<Path, Path> matches) {
        JsonObject merged = new JsonObject();
        Set<String> ambiguous = new HashSet<>();
        try {
            for (Path path : matches.values()) {
                JsonObject document = JsonParser.parseString(
                    Files.readString(path)
                ).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry :
                    document.entrySet()) {
                    if (ambiguous.contains(entry.getKey())) {
                        continue;
                    }
                    JsonElement previous = merged.get(entry.getKey());
                    if (previous == null) {
                        merged.add(entry.getKey(), entry.getValue());
                    } else if (!previous.equals(entry.getValue())) {
                        merged.remove(entry.getKey());
                        ambiguous.add(entry.getKey());
                    }
                }
            }
            return Resolution.found(
                new Gson().toJson(merged).getBytes(
                    java.nio.charset.StandardCharsets.UTF_8
                )
            );
        } catch (IOException | RuntimeException exception) {
            return Resolution.missing(
                "could not merge upstream language resources: "
                    + exception.getMessage()
            );
        }
    }

    private static Path findResource(
        IModFile modFile,
        BookTranslationIndex.ResourceKey resource
    ) {
        String[] path = resource.path().split("/");
        String[] segments = new String[path.length + 2];
        segments[0] = "assets";
        segments[1] = resource.namespace();
        System.arraycopy(path, 0, segments, 2, path.length);
        return modFile.findResource(segments);
    }
}
