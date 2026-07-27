package ru.deirel.liminalindustries.translation;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class QuestManifest {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-fA-F]{64}");
    private static final Set<String> REQUIRED_FILES = Set.of("data.snbt", "chapter_groups.snbt");

    private int schemaVersion;
    private String translationVersion;
    private String modpackVersion;
    private Map<String, String> originalFiles;
    private Map<String, String> translatedFiles;

    public static QuestManifest read(Reader reader) throws IOException {
        try {
            QuestManifest manifest = new Gson().fromJson(reader, QuestManifest.class);
            if (manifest == null) {
                throw new IOException("Payload manifest is empty");
            }
            manifest.validate();
            return manifest;
        } catch (JsonParseException | IllegalArgumentException exception) {
            throw new IOException("Invalid payload manifest", exception);
        }
    }

    public QuestManifest(
        int schemaVersion,
        String translationVersion,
        String modpackVersion,
        Map<String, String> originalFiles,
        Map<String, String> translatedFiles
    ) {
        this.schemaVersion = schemaVersion;
        this.translationVersion = translationVersion;
        this.modpackVersion = modpackVersion;
        this.originalFiles = new LinkedHashMap<>(originalFiles);
        this.translatedFiles = new LinkedHashMap<>(translatedFiles);
        validate();
    }

    private QuestManifest() {
    }

    private void validate() {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Unsupported manifest schema: " + schemaVersion);
        }
        requireText(translationVersion, "translationVersion");
        requireText(modpackVersion, "modpackVersion");
        validateFiles(originalFiles, "originalFiles");
        validateFiles(translatedFiles, "translatedFiles");
        if (!originalFiles.keySet().equals(translatedFiles.keySet())) {
            throw new IllegalArgumentException("Original and translated file sets differ");
        }
        if (!originalFiles.keySet().containsAll(REQUIRED_FILES)
            || originalFiles.keySet().stream().noneMatch(path -> path.startsWith("chapters/"))) {
            throw new IllegalArgumentException("Manifest does not contain the required quest files");
        }
        originalFiles = Map.copyOf(originalFiles);
        translatedFiles = Map.copyOf(translatedFiles);
    }

    private static void validateFiles(Map<String, String> files, String field) {
        Objects.requireNonNull(files, field);
        if (files.isEmpty()) {
            throw new IllegalArgumentException(field + " is empty");
        }
        files.forEach((path, hash) -> {
            QuestPaths.validateRelativePath(path);
            if (hash == null || !SHA_256.matcher(hash).matches()) {
                throw new IllegalArgumentException("Invalid SHA-256 for " + path);
            }
        });
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is empty");
        }
    }

    public String translationVersion() {
        return translationVersion;
    }

    public String modpackVersion() {
        return modpackVersion;
    }

    public Map<String, String> originalFiles() {
        return originalFiles;
    }

    public Map<String, String> translatedFiles() {
        return translatedFiles;
    }
}
