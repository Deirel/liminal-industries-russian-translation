package ru.deirel.liminalindustries.translation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestInstallerTest {
    private static final Map<String, String> ORIGINAL_CONTENT = Map.of(
        "data.snbt", "{title:\"Original\"}",
        "chapter_groups.snbt", "[{title:\"Group\"}]",
        "chapters/one.snbt", "{title:\"Chapter\"}"
    );
    private static final Map<String, String> TRANSLATED_CONTENT = Map.of(
        "data.snbt", "{title:\"Перевод\"}",
        "chapter_groups.snbt", "[{title:\"Группа\"}]",
        "chapters/one.snbt", "{title:\"Глава\"}"
    );
    private static final Clock CLOCK =
        Clock.fixed(Instant.parse("2026-07-27T12:34:56Z"), ZoneOffset.UTC);

    @TempDir
    Path temporaryDirectory;

    @Test
    void detectsAllDirectoryStates() throws IOException {
        Fixture fixture = fixture();
        assertEquals(QuestState.MISSING, QuestStateDetector.detect(fixture.quests, fixture.manifest));

        writeFiles(fixture.quests, ORIGINAL_CONTENT);
        assertEquals(QuestState.ORIGINAL, QuestStateDetector.detect(fixture.quests, fixture.manifest));

        replaceFiles(fixture.quests, TRANSLATED_CONTENT);
        assertEquals(QuestState.TRANSLATED, QuestStateDetector.detect(fixture.quests, fixture.manifest));

        Files.writeString(fixture.quests.resolve("extra.snbt"), "extra");
        assertEquals(QuestState.UNKNOWN, QuestStateDetector.detect(fixture.quests, fixture.manifest));
    }

    @Test
    void installsTranslationAndCreatesBackup() throws IOException {
        Fixture fixture = fixture();
        writeFiles(fixture.quests, ORIGINAL_CONTENT);

        InstallResult result = fixture.installer(QuestFileMover.system()).install();

        assertEquals(InstallResult.Status.INSTALLED, result.status());
        assertEquals(TRANSLATED_CONTENT, readFiles(fixture.quests));
        assertEquals(ORIGINAL_CONTENT, readFiles(result.backup()));
        assertTrue(result.backup().toString().contains("20260727-123456-000Z"));
    }

    @Test
    void repeatedRunDoesNotReadOrWritePayload() throws IOException {
        Fixture fixture = fixture();
        writeFiles(fixture.quests, TRANSLATED_CONTENT);
        QuestPayload failingPayload = path -> {
            throw new IOException("Payload must not be opened");
        };

        InstallResult result = new QuestInstaller(
            fixture.config, fixture.manifest, failingPayload, QuestFileMover.system(), CLOCK
        ).install();

        assertEquals(InstallResult.Status.ALREADY_INSTALLED, result.status());
        assertFalse(Files.exists(fixture.config.resolve(LiminalIndustriesTranslationMod.MOD_ID)));
    }

    @Test
    void refusesExtraMissingAndChangedFiles() throws IOException {
        Fixture fixture = fixture();

        writeFiles(fixture.quests, ORIGINAL_CONTENT);
        Files.writeString(fixture.quests.resolve("extra.snbt"), "extra");
        assertEquals(InstallResult.Status.REFUSED, fixture.installer(QuestFileMover.system()).install().status());

        replaceFiles(fixture.quests, ORIGINAL_CONTENT);
        Files.delete(fixture.quests.resolve("data.snbt"));
        assertEquals(InstallResult.Status.REFUSED, fixture.installer(QuestFileMover.system()).install().status());

        replaceFiles(fixture.quests, ORIGINAL_CONTENT);
        Files.writeString(fixture.quests.resolve("data.snbt"), "changed");
        assertEquals(InstallResult.Status.REFUSED, fixture.installer(QuestFileMover.system()).install().status());
    }

    @Test
    void restoresOriginalWhenSecondMoveFails() throws IOException {
        Fixture fixture = fixture();
        writeFiles(fixture.quests, ORIGINAL_CONTENT);
        AtomicInteger moves = new AtomicInteger();
        QuestFileMover failingSecondMove = (source, target) -> {
            if (moves.incrementAndGet() == 2) {
                throw new IOException("Injected move failure");
            }
            QuestFileMover.system().move(source, target);
        };

        InstallResult result = fixture.installer(failingSecondMove).install();

        assertEquals(InstallResult.Status.FAILED, result.status());
        assertTrue(result.restored());
        assertEquals(ORIGINAL_CONTENT, readFiles(fixture.quests));
    }

    @Test
    void refusesSymbolicLinks() throws IOException {
        Fixture fixture = fixture();
        writeFiles(fixture.quests, ORIGINAL_CONTENT);
        Path external = temporaryDirectory.resolve("external.snbt");
        Files.writeString(external, "external");
        Files.createSymbolicLink(fixture.quests.resolve("link.snbt"), external);

        assertEquals(QuestState.UNKNOWN, QuestStateDetector.detect(fixture.quests, fixture.manifest));
        assertEquals(InstallResult.Status.REFUSED, fixture.installer(QuestFileMover.system()).install().status());
    }

    @Test
    void rejectsUnsafeManifestPaths() {
        Map<String, String> original = hashesFor(ORIGINAL_CONTENT);
        Map<String, String> translated = hashesFor(TRANSLATED_CONTENT);
        String hash = original.get("data.snbt");
        original.put("../data.snbt", hash);
        translated.put("../data.snbt", hash);

        assertThrows(IllegalArgumentException.class, () ->
            new QuestManifest(1, "1.0.0", "test", original, translated)
        );
    }

    private Fixture fixture() throws IOException {
        Path config = temporaryDirectory.resolve("config-" + System.nanoTime());
        Path payload = temporaryDirectory.resolve("payload-" + System.nanoTime());
        writeFiles(payload, TRANSLATED_CONTENT);
        QuestManifest manifest = new QuestManifest(
            1, "1.0.0", "test", hashesFor(ORIGINAL_CONTENT), hashesFor(TRANSLATED_CONTENT)
        );
        return new Fixture(config, config.resolve("ftbquests/quests"), payload, manifest);
    }

    private static Map<String, String> hashesFor(Map<String, String> content) {
        Map<String, String> hashes = new LinkedHashMap<>();
        content.forEach((path, value) -> hashes.put(path, sha256(value)));
        return hashes;
    }

    private static String sha256(String value) {
        try {
            return Hashing.sha256(new java.io.ByteArrayInputStream(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void writeFiles(Path root, Map<String, String> content) throws IOException {
        for (Map.Entry<String, String> file : content.entrySet()) {
            Path destination = root.resolve(file.getKey());
            Files.createDirectories(destination.getParent());
            Files.writeString(destination, file.getValue());
        }
    }

    private static void replaceFiles(Path root, Map<String, String> content) throws IOException {
        QuestDirectory.deleteRecursively(root);
        writeFiles(root, content);
    }

    private static Map<String, String> readFiles(Path root) throws IOException {
        Map<String, String> content = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                try {
                    content.put(root.relativize(path).toString().replace("\\", "/"), Files.readString(path));
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        }
        return content;
    }

    private record Fixture(Path config, Path quests, Path payload, QuestManifest manifest) {
        private QuestInstaller installer(QuestFileMover mover) {
            QuestPayload source = relative -> open(payload.resolve(relative));
            return new QuestInstaller(config, manifest, source, mover, CLOCK);
        }

        private static InputStream open(Path path) throws IOException {
            return Files.newInputStream(path);
        }
    }
}
