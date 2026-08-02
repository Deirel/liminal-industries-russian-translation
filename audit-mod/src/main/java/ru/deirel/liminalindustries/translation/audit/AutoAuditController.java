package ru.deirel.liminalindustries.translation.audit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import ru.deirel.liminalindustries.translation.audit.layout.LayoutAuditRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;

final class AutoAuditController {
    private static final Path REQUEST_PATH = Path.of(
        "liminal-industries-ru-audit",
        "auto-audit-request.json"
    );
    private static final Path RUNNING_PATH = Path.of(
        "liminal-industries-ru-audit",
        "auto-audit-running.json"
    );
    private static final int POLL_INTERVAL_TICKS = 20;

    private static AutoAuditRequest request;
    private static boolean loadingWorld;
    private static boolean reloadingLanguage;
    private static int pollTicks;

    private AutoAuditController() {
    }

    static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (request == null && !loadRequest(minecraft)) {
            return;
        }
        if (minecraft.level == null) {
            openLastWorld(minecraft);
            return;
        }
        startAuditWhenReady(minecraft);
    }

    private static boolean loadRequest(Minecraft minecraft) {
        if (++pollTicks < POLL_INTERVAL_TICKS) {
            return false;
        }
        pollTicks = 0;
        Path path = minecraft.gameDirectory.toPath().resolve(REQUEST_PATH);
        if (!Files.isRegularFile(path)) {
            return false;
        }
        try {
            request = AutoAuditRequest.parse(Files.readString(
                path,
                StandardCharsets.UTF_8
            ));
            Path running = minecraft.gameDirectory.toPath().resolve(RUNNING_PATH);
            Files.move(
                path,
                running,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            );
            LiminalIndustriesTranslationAuditMod.LOGGER.info(
                "Loaded automatic audit request: {}",
                request.audit()
            );
            return true;
        } catch (IOException | IllegalArgumentException exception) {
            LiminalIndustriesTranslationAuditMod.LOGGER.error(
                "Could not load automatic audit request {}",
                path,
                exception
            );
            try {
                Files.deleteIfExists(path);
            } catch (IOException deleteException) {
                exception.addSuppressed(deleteException);
            }
            return false;
        }
    }

    private static void openLastWorld(Minecraft minecraft) {
        if (loadingWorld || !(minecraft.screen instanceof TitleScreen)) {
            return;
        }
        loadingWorld = true;
        LevelStorageSource source = minecraft.getLevelSource();
        try {
            source.loadLevelSummaries(source.findLevelCandidates())
                .whenComplete((summaries, exception) -> minecraft.execute(() -> {
                    if (exception != null) {
                        fail("Could not list local worlds", exception);
                        return;
                    }
                    openLatestCompatibleWorld(minecraft, summaries);
                }));
        } catch (RuntimeException exception) {
            fail("Could not list local worlds", exception);
        }
    }

    private static void openLatestCompatibleWorld(
        Minecraft minecraft,
        List<LevelSummary> summaries
    ) {
        LevelSummary latest = summaries.stream()
            .filter(summary -> !summary.isLocked())
            .filter(LevelSummary::isCompatible)
            .max(Comparator.comparingLong(LevelSummary::getLastPlayed))
            .orElse(null);
        if (latest == null) {
            fail("No compatible local world is available", null);
            return;
        }
        LiminalIndustriesTranslationAuditMod.LOGGER.info(
            "Opening last played world for automatic audit: {}",
            latest.getLevelId()
        );
        minecraft.createWorldOpenFlows().loadLevel(
            minecraft.screen,
            latest.getLevelId()
        );
    }

    private static void startAuditWhenReady(Minecraft minecraft) {
        if (reloadingLanguage || minecraft.player == null) {
            return;
        }
        if (request.audit().equals("texts")) {
            startTextAudit(minecraft);
            return;
        }
        String engine = switch (request.audit()) {
            case "books" -> null;
            case "patchouli", "mantle" -> request.audit();
            case "ie" -> "immersive_engineering";
            default -> throw new IllegalStateException(
                "Unsupported audit mode: " + request.audit()
            );
        };
        if (request.exitWhenDone()) {
            System.setProperty("liminal.layoutAudit.exitWhenDone", "true");
        }
        LayoutAuditRunner.StartResult result = engine == null
            ? LayoutAuditRunner.start()
            : LayoutAuditRunner.start(engine);
        if (result.started()) {
            request = null;
        } else {
            fail(result.message(), null);
        }
    }

    private static void startTextAudit(Minecraft minecraft) {
        if (!"ru_ru".equals(minecraft.getLanguageManager().getSelected())) {
            reloadingLanguage = true;
            minecraft.getLanguageManager().setSelected("ru_ru");
            minecraft.reloadResourcePacks().whenComplete((ignored, exception) ->
                minecraft.execute(() -> {
                    reloadingLanguage = false;
                    if (exception != null) {
                        fail("Could not load ru_ru for automatic audit", exception);
                    }
                })
            );
            return;
        }
        if (!ItemTranslationAudit.isReady()) {
            return;
        }
        ItemTranslationAudit.Result result = ItemTranslationAudit.run();
        LiminalIndustriesTranslationAuditMod.LOGGER.info(result.message());
        boolean exit = request.exitWhenDone();
        request = null;
        if (exit) {
            minecraft.stop();
        }
    }

    private static void fail(String message, Throwable exception) {
        if (exception == null) {
            LiminalIndustriesTranslationAuditMod.LOGGER.error(message);
        } else {
            LiminalIndustriesTranslationAuditMod.LOGGER.error(message, exception);
        }
        request = null;
        loadingWorld = false;
        reloadingLanguage = false;
    }
}
