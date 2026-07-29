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
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public LiminalIndustriesTranslationMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
            this::addTranslationPack
        );
        ModLoadingContext.get().registerExtensionPoint(
            IExtensionPoint.DisplayTest.class,
            () -> new IExtensionPoint.DisplayTest(
                () -> NetworkConstants.IGNORESERVERONLY,
                (remoteVersion, isServer) -> true
            )
        );
    }

    private void addTranslationPack(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        event.addRepositorySource(consumer ->
            ResourcePackLoader.getPackFor(MOD_ID).ifPresent(resources -> {
                Pack pack = Pack.readMetaAndCreate(
                    MOD_ID + "_overrides",
                    Component.literal("Liminal Industries: Russian Translation"),
                    true,
                    id -> resources,
                    PackType.CLIENT_RESOURCES,
                    Pack.Position.TOP,
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
