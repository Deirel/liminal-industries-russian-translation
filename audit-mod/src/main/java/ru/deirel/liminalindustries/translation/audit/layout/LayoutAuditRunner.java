package ru.deirel.liminalindustries.translation.audit.layout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.deirel.liminalindustries.translation.audit.LiminalIndustriesTranslationAuditMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class LayoutAuditRunner {
    private static final int STABLE_FRAMES = 3;
    private static LayoutAuditRunner active;

    private final Minecraft minecraft;
    private final Screen returnScreen;
    private final String originalLanguage;
    private final List<LayoutEngineAdapter> adapters;
    private final Map<String, LayoutEngineAdapter> adaptersByEngine;
    private final String adapterScope;
    private final List<LayoutCapture> captures = new ArrayList<>();
    private final List<LayoutIssue> englishIssues = new ArrayList<>();
    private final List<LayoutIssue> russianIssues = new ArrayList<>();
    private final List<VisualTarget> visualTargets = new ArrayList<>();
    private final Map<String, VisualMatch> visualMatches = new LinkedHashMap<>();
    private List<LayoutScreen> targets = List.of();
    private int targetIndex;
    private int stableFrames;
    private String language;
    private boolean reloading;
    private boolean complete;
    private String completionMessage;

    private LayoutAuditRunner(
        Minecraft minecraft,
        List<LayoutEngineAdapter> adapters
    ) {
        this.minecraft = minecraft;
        this.returnScreen = minecraft.screen;
        this.originalLanguage = minecraft.getLanguageManager().getSelected();
        this.adapters = List.copyOf(adapters);
        this.adaptersByEngine = adapterIndex();
        this.adapterScope = this.adapters.stream()
            .map(LayoutEngineAdapter::engine)
            .collect(Collectors.joining(", "));
    }

    public static StartResult start() {
        return start(null);
    }

    public static StartResult start(String engine) {
        Minecraft minecraft = Minecraft.getInstance();
        if (active != null) {
            return new StartResult(false, "Аудит верстки уже выполняется.");
        }
        if (minecraft.level == null) {
            return new StartResult(false, "Сначала войдите в локальный мир.");
        }
        List<LayoutEngineAdapter> adapters;
        try {
            adapters = engine == null
                ? LayoutEngineAdapters.createAll()
                : List.of(LayoutEngineAdapters.create(engine));
        } catch (IllegalArgumentException exception) {
            return new StartResult(false, exception.getMessage());
        }
        LayoutAuditRunner runner = new LayoutAuditRunner(minecraft, adapters);
        active = runner;
        try {
            runner.prepareOutput();
        } catch (RuntimeException exception) {
            active = null;
            LiminalIndustriesTranslationAuditMod.LOGGER.error(
                "Не удалось подготовить каталог аудита верстки",
                exception
            );
            return new StartResult(
                false,
                "Не удалось подготовить каталог аудита верстки."
            );
        }
        runner.switchLanguage("en_us");
        return new StartResult(
            true,
            "Аудит верстки [" + runner.adapterScope
                + "] запущен: en_us, затем ru_ru."
        );
    }

    public static void onRendered(Screen screen) {
        if (active != null) {
            active.rendered(screen);
        }
    }

    public static void tickAutoStart() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!Boolean.getBoolean("liminal.layoutAudit.auto")
            || active != null
            || minecraft.level == null) {
            return;
        }
        StartResult result = start();
        if (!result.started()) {
            LiminalIndustriesTranslationAuditMod.LOGGER.error(result.message());
        }
        System.clearProperty("liminal.layoutAudit.auto");
    }

    private void switchLanguage(String requested) {
        reloading = true;
        language = requested;
        minecraft.getLanguageManager().setSelected(requested);
        CompletableFuture<Void> reload = minecraft.reloadResourcePacks();
        reload.whenComplete((ignored, exception) -> minecraft.execute(() -> {
            if (exception != null) {
                fail("Не удалось загрузить язык " + requested, exception);
                return;
            }
            try {
                preparePass();
            } catch (RuntimeException prepareException) {
                fail(
                    "Не удалось подготовить экраны для " + requested,
                    prepareException
                );
            }
        }));
    }

    private void preparePass() {
        reloading = false;
        targets = adapters.stream()
            .flatMap(adapter -> adapter.screens().stream())
            .toList();
        targetIndex = 0;
        if (targets.isEmpty()) {
            fail("Не найдено ни одного экрана книги для " + language, null);
            return;
        }
        openCurrent();
    }

    private void openCurrent() {
        stableFrames = 0;
        LayoutScreen target = targets.get(targetIndex);
        try {
            Screen screen = target.factory().get();
            minecraft.setScreen(screen);
        } catch (RuntimeException exception) {
            fail("Не удалось открыть экран " + target.id(), exception);
        }
    }

    private void rendered(Screen rendered) {
        if (complete || reloading || targetIndex >= targets.size()) {
            return;
        }
        LayoutScreen target = targets.get(targetIndex);
        if (rendered != minecraft.screen) {
            return;
        }
        if (++stableFrames < STABLE_FRAMES) {
            return;
        }
        try {
            LayoutEngineAdapter adapter = adapter(target);
            LayoutCapture capture = adapter.capture(
                minecraft,
                target,
                rendered,
                language
            );
            captures.add(capture);
            List<LayoutIssue> issues = new ArrayList<>(LayoutAnalyzer.analyze(
                capture,
                adapter.renderingTolerance()
            ));
            if (!issues.isEmpty()) {
                String screenshot = screenshotName(capture);
                takeScreenshot(screenshot);
                issues.replaceAll(issue -> issue.withScreenshot("screenshots/" + screenshot));
            }
            captureVisualTargets(capture);
            (language.equals("en_us") ? englishIssues : russianIssues).addAll(issues);
        } catch (RuntimeException exception) {
            fail("Ошибка захвата экрана " + target.id(), exception);
            return;
        }
        targetIndex++;
        if (targetIndex < targets.size()) {
            minecraft.execute(this::openCurrent);
        } else if (language.equals("en_us")) {
            minecraft.execute(() -> switchLanguage("ru_ru"));
        } else {
            minecraft.execute(this::finish);
        }
    }

    private void finish() {
        List<LayoutIssue> classified = new ArrayList<>();
        englishIssues.stream()
            .map(issue -> issue.classify(LayoutIssue.Classification.UPSTREAM_LAYOUT))
            .forEach(classified::add);
        Set<String> englishSubjects = captures.stream()
            .filter(capture -> capture.language().equals("en_us"))
            .flatMap(capture -> Stream.concat(
                Stream.of(capture.screenId()),
                Stream.concat(
                    capture.pages().stream(),
                    capture.text().stream()
                )
                    .map(LayoutRegion::logicalPage)
                    .filter(Objects::nonNull)
            ))
            .collect(Collectors.toSet());
        classified.addAll(LayoutIssueClassifier.classify(
            englishIssues,
            russianIssues,
            englishSubjects
        ));
        classified.addAll(LayoutIssueClassifier.missingLanguagePages(
            captures,
            "en_us",
            "ru_ru"
        ));
        try {
            Path output = LayoutReportWriter.write(
                minecraft.gameDirectory.toPath(),
                captures,
                classified
            );
            writeVisualChecks(output.resolveSibling("visual-checks.tsv"));
            long translationFailures = classified.stream()
                .filter(issue ->
                    issue.classification()
                        == LayoutIssue.Classification.TRANSLATION_LAYOUT
                )
                .filter(issue -> issue.severity() == LayoutIssue.Severity.ERROR)
                .count();
            long missingContentFailures =
                LayoutReportWriter.missingContentErrors(classified);
            long missingPageFailures =
                LayoutReportWriter.missingTranslatedPageErrors(classified);
            long failures = LayoutReportWriter.blockingErrors(classified);
            completionMessage = "Аудит верстки [" + adapterScope + "] "
                + (failures == 0 ? "пройден" : "не пройден")
                + ": " + captures.size() + " экранов, "
                + failures + " ошибок (перевод: " + translationFailures
                + ", содержимое: " + missingContentFailures
                + ", страницы языка: " + missingPageFailures
                + "). Отчёт: " + output.toAbsolutePath();
            complete = true;
            restore();
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal(completionMessage));
            }
            LiminalIndustriesTranslationAuditMod.LOGGER.info(completionMessage);
            if (Boolean.getBoolean("liminal.layoutAudit.exitWhenDone")) {
                minecraft.stop();
            }
            active = null;
        } catch (IOException exception) {
            fail("Не удалось записать отчет аудита верстки", exception);
        }
    }

    private void takeScreenshot(String name) {
        Path directory = minecraft.gameDirectory.toPath()
            .resolve(LayoutReportWriter.REPORT_PATH)
            .getParent();
        try {
            Files.createDirectories(directory);
            Screenshot.grab(
                directory.toFile(),
                name,
                minecraft.getMainRenderTarget(),
                ignored -> {
                }
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Could not prepare screenshot directory", exception);
        }
    }

    private void prepareOutput() {
        Path report = minecraft.gameDirectory.toPath()
            .resolve(LayoutReportWriter.REPORT_PATH);
        loadVisualTargets(report.resolveSibling("visual-audit-targets.tsv"));
        Path directory = report.getParent()
            .resolve("screenshots");
        try {
            Files.deleteIfExists(report);
            Files.deleteIfExists(report.resolveSibling("book-layout-audit.html"));
            Files.deleteIfExists(report.resolveSibling("visual-checks.tsv"));
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Could not remove stale layout audit report",
                exception
            );
        }
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException exception) {
                    throw new IllegalStateException(
                        "Could not remove stale screenshot " + path,
                        exception
                    );
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Could not clean screenshot directory " + directory,
                exception
            );
        }
    }

    private String screenshotName(LayoutCapture capture) {
        return (capture.language() + "-" + capture.screenId())
            .replaceAll("[^a-zA-Z0-9._-]+", "_")
            + ".png";
    }

    private void loadVisualTargets(Path path) {
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int index = 1; index < lines.size(); index++) {
                if (lines.get(index).isBlank()) {
                    continue;
                }
                String[] columns = lines.get(index).split("\\t", -1);
                if (columns.length != 4) {
                    throw new IllegalArgumentException(
                        "Invalid visual target at line " + (index + 1)
                    );
                }
                visualTargets.add(new VisualTarget(
                    columns[0], columns[1], columns[2], columns[3]
                ));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read visual audit targets", exception);
        }
    }

    private void captureVisualTargets(LayoutCapture capture) {
        if (!capture.language().equals("ru_ru")) {
            return;
        }
        for (VisualTarget target : visualTargets) {
            if (visualMatches.containsKey(target.id())
                || !matchesVisualTarget(
                    capture, target.resource(), target.source(), target.screen()
                )) {
                continue;
            }
            String screenshot = "visual-"
                + target.id().replaceAll("[^a-zA-Z0-9._-]+", "_")
                + ".png";
            takeScreenshot(screenshot);
            visualMatches.put(
                target.id(),
                new VisualMatch(capture.screenId(), "screenshots/" + screenshot)
            );
        }
    }

    static boolean matchesVisualTarget(
        LayoutCapture capture,
        String resource,
        String source,
        String screen
    ) {
        if (!screen.isEmpty() && !capture.screenId().endsWith(screen)) {
            return false;
        }
        boolean captureResource = resource.isEmpty()
            || resource.equals(capture.resource())
            || capture.resource().endsWith(resource);
        if (source.isEmpty() || (!screen.isEmpty() && captureResource)) {
            return captureResource;
        }
        return capture.text().stream().anyMatch(region -> {
            boolean regionResource = resource.isEmpty()
                || resource.equals(region.resource())
                || (region.resource() != null && region.resource().endsWith(resource));
            return source.equals(region.source()) && (captureResource || regionResource);
        });
    }

    private void writeVisualChecks(Path path) throws IOException {
        StringBuilder output = new StringBuilder(
            "id\tstatus\tscreenshot\tscreen\tresource\tsource\ttarget_screen\n"
        );
        for (VisualTarget target : visualTargets) {
            VisualMatch match = visualMatches.get(target.id());
            output.append(target.id()).append('\t')
                .append(match == null ? "UNMATCHED" : "MATCHED").append('\t')
                .append(match == null ? "" : match.screenshot()).append('\t')
                .append(match == null ? "" : match.screen()).append('\t')
                .append(target.resource()).append('\t')
                .append(target.source()).append('\t')
                .append(target.screen()).append('\n');
        }
        Files.writeString(path, output, StandardCharsets.UTF_8);
    }

    private record VisualTarget(
        String id,
        String resource,
        String source,
        String screen
    ) {
    }

    private record VisualMatch(String screen, String screenshot) {
    }

    private void fail(String message, Throwable exception) {
        completionMessage = message;
        complete = true;
        restore();
        if (exception == null) {
            LiminalIndustriesTranslationAuditMod.LOGGER.error(message);
        } else {
            LiminalIndustriesTranslationAuditMod.LOGGER.error(message, exception);
        }
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal(message));
        }
        if (Boolean.getBoolean("liminal.layoutAudit.exitWhenDone")) {
            minecraft.stop();
        }
        active = null;
    }

    private void restore() {
        CompletableFuture<Void> reload = null;
        if (!minecraft.getLanguageManager().getSelected().equals(originalLanguage)) {
            minecraft.getLanguageManager().setSelected(originalLanguage);
            reload = minecraft.reloadResourcePacks();
        }
        minecraft.setScreen(returnScreen);
        resetAdapterState();
        if (reload != null) {
            reload.whenComplete((ignored, exception) ->
                minecraft.execute(this::resetAdapterState)
            );
        }
    }

    private void resetAdapterState() {
        for (LayoutEngineAdapter adapter : adapters) {
            try {
                adapter.resetAfterAudit();
            } catch (RuntimeException exception) {
                LiminalIndustriesTranslationAuditMod.LOGGER.warn(
                    "Не удалось сбросить состояние книг после аудита: {}",
                    adapter.engine(),
                    exception
                );
            }
        }
    }

    private LayoutEngineAdapter adapter(LayoutScreen target) {
        LayoutEngineAdapter adapter = adaptersByEngine.get(target.engine());
        if (adapter == null) {
            throw new IllegalStateException(
                "No layout adapter for engine " + target.engine()
            );
        }
        return adapter;
    }

    private Map<String, LayoutEngineAdapter> adapterIndex() {
        Map<String, LayoutEngineAdapter> result = new LinkedHashMap<>();
        for (LayoutEngineAdapter adapter : adapters) {
            if (result.put(adapter.engine(), adapter) != null) {
                throw new IllegalStateException(
                    "Duplicate layout adapter " + adapter.engine()
                );
            }
        }
        return Map.copyOf(result);
    }

    public record StartResult(boolean started, String message) {
    }
}
