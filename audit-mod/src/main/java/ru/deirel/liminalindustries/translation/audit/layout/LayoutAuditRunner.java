package ru.deirel.liminalindustries.translation.audit.layout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.deirel.liminalindustries.translation.audit.LiminalIndustriesTranslationAuditMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class LayoutAuditRunner {
    private static final int STABLE_FRAMES = 3;
    private static LayoutAuditRunner active;

    private final Minecraft minecraft;
    private final Screen returnScreen;
    private final String originalLanguage;
    private final PatchouliLayoutAdapter adapter = new PatchouliLayoutAdapter();
    private final List<LayoutCapture> captures = new ArrayList<>();
    private final List<LayoutIssue> englishIssues = new ArrayList<>();
    private final List<LayoutIssue> russianIssues = new ArrayList<>();
    private List<LayoutScreen> targets = List.of();
    private int targetIndex;
    private int stableFrames;
    private String language;
    private boolean reloading;
    private boolean complete;
    private String completionMessage;

    private LayoutAuditRunner(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.returnScreen = minecraft.screen;
        this.originalLanguage = minecraft.getLanguageManager().getSelected();
    }

    public static StartResult start() {
        Minecraft minecraft = Minecraft.getInstance();
        if (active != null) {
            return new StartResult(false, "Аудит верстки уже выполняется.");
        }
        if (minecraft.level == null) {
            return new StartResult(false, "Сначала войдите в локальный мир.");
        }
        LayoutAuditRunner runner = new LayoutAuditRunner(minecraft);
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
        return new StartResult(true, "Аудит верстки запущен: en_us, затем ru_ru.");
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
            preparePass();
        }));
    }

    private void preparePass() {
        reloading = false;
        targets = adapter.screens();
        targetIndex = 0;
        if (targets.isEmpty()) {
            fail("Не найдено ни одного экрана книги для " + language, null);
            return;
        }
        openCurrent();
    }

    private void openCurrent() {
        stableFrames = 0;
        Screen screen = targets.get(targetIndex).factory().get();
        minecraft.setScreen(screen);
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
            LayoutCapture capture = adapter.capture(
                minecraft,
                target,
                rendered,
                language
            );
            captures.add(capture);
            List<LayoutIssue> issues = new ArrayList<>(LayoutAnalyzer.analyze(capture));
            if (!issues.isEmpty()) {
                String screenshot = screenshotName(capture);
                takeScreenshot(screenshot);
                issues.replaceAll(issue -> issue.withScreenshot("screenshots/" + screenshot));
            }
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
        Set<String> englishScreens = captures.stream()
            .filter(capture -> capture.language().equals("en_us"))
            .map(LayoutCapture::screenId)
            .collect(Collectors.toSet());
        classified.addAll(LayoutIssueClassifier.classify(
            englishIssues,
            russianIssues,
            englishScreens
        ));
        try {
            Path output = LayoutReportWriter.write(
                minecraft.gameDirectory.toPath(),
                captures,
                classified
            );
            long translationFailures = classified.stream()
                .filter(issue ->
                    issue.classification()
                        == LayoutIssue.Classification.TRANSLATION_LAYOUT
                )
                .filter(issue -> issue.severity() == LayoutIssue.Severity.ERROR)
                .count();
            long missingContentFailures =
                LayoutReportWriter.missingContentErrors(classified);
            long failures = LayoutReportWriter.blockingErrors(classified);
            completionMessage = "Аудит верстки "
                + (failures == 0 ? "пройден" : "не пройден")
                + ": " + captures.size() + " экранов, "
                + failures + " ошибок (перевод: " + translationFailures
                + ", содержимое: " + missingContentFailures
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
        Path directory = report.getParent()
            .resolve("screenshots");
        try {
            Files.deleteIfExists(report);
            Files.deleteIfExists(report.resolveSibling("book-layout-audit.html"));
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
        try {
            adapter.resetAfterAudit();
        } catch (RuntimeException exception) {
            LiminalIndustriesTranslationAuditMod.LOGGER.warn(
                "Не удалось сбросить состояние книг после аудита: Patchouli",
                exception
            );
        }
    }

    public record StartResult(boolean started, String message) {
    }
}
