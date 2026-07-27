package ru.deirel.liminalindustries.translation;

import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    }
}
