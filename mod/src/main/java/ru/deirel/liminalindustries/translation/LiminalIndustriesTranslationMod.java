package ru.deirel.liminalindustries.translation;

import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

@Mod(LiminalIndustriesTranslationMod.MOD_ID)
public final class LiminalIndustriesTranslationMod {
    public static final String MOD_ID = "liminal_industries_ru";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public LiminalIndustriesTranslationMod() {
        ModLoadingContext.get().registerExtensionPoint(
            IExtensionPoint.DisplayTest.class,
            () -> new IExtensionPoint.DisplayTest(
                () -> NetworkConstants.IGNORESERVERONLY,
                (remoteVersion, isServer) -> true
            )
        );
        installQuestTranslation();
    }

    private void installQuestTranslation() {
        try (InputStream input = getClass().getResourceAsStream("/liminal_industries_ru/payload-manifest.json")) {
            if (input == null) {
                throw new IOException("Embedded payload manifest is missing");
            }
            QuestManifest manifest = QuestManifest.read(
                new InputStreamReader(input, StandardCharsets.UTF_8)
            );
            InstallResult result = new QuestInstaller(
                FMLPaths.CONFIGDIR.get(),
                manifest,
                new ClasspathQuestPayload(),
                QuestFileMover.system(),
                Clock.systemUTC()
            ).install();
            switch (result.status()) {
                case INSTALLED -> LOGGER.info("Quest translation result: installed");
                case ALREADY_INSTALLED -> LOGGER.info("Quest translation result: already installed");
                case REFUSED -> LOGGER.warn("Quest translation result: refused");
                case FAILED -> LOGGER.error("Quest translation result: failed");
            }
        } catch (Exception exception) {
            LOGGER.error("Quest translation result: failed", exception);
        }
    }
}
