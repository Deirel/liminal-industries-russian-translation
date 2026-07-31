package ru.deirel.liminalindustries.translation;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkConstants;
import net.minecraftforge.resource.ResourcePackLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(LiminalIndustriesTranslationMod.MOD_ID)
public final class LiminalIndustriesTranslationMod {
    public static final String MOD_ID = "liminal_industries_ru";
    public static final String BASELINE_PACK_ID = MOD_ID + "_baseline";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public LiminalIndustriesTranslationMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
            this::addBaselineTranslationPack
        );
        ModLoadingContext.get().registerExtensionPoint(
            IExtensionPoint.DisplayTest.class,
            () -> new IExtensionPoint.DisplayTest(
                () -> NetworkConstants.IGNORESERVERONLY,
                (remoteVersion, isServer) -> true
            )
        );
    }

    static Pack.Position translationPackPosition() {
        return Pack.Position.BOTTOM;
    }

    private void addBaselineTranslationPack(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        event.addRepositorySource(consumer ->
            ResourcePackLoader.getPackFor(MOD_ID).ifPresent(resources -> {
                BookTranslationIndex bookIndex;
                try {
                    bookIndex = BookTranslationIndex.load(
                        LiminalIndustriesTranslationMod.class
                    );
                } catch (java.io.IOException exception) {
                    LOGGER.error(
                        "Could not load source-aware book translations",
                        exception
                    );
                    return;
                }
                Pack pack = Pack.readMetaAndCreate(
                    BASELINE_PACK_ID,
                    Component.literal("Liminal Industries: Russian Translation"),
                    true,
                    id -> new SourceAwareBookPackResources(
                        resources,
                        bookIndex,
                        BookSourceResolver.runtime()
                    ),
                    PackType.CLIENT_RESOURCES,
                    translationPackPosition(),
                    PackSource.BUILT_IN
                );
                if (pack == null) {
                    LOGGER.error("Could not create the translation resource pack");
                    return;
                }
                consumer.accept(pack);
            })
        );
    }
}
