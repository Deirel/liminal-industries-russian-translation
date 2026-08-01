package ru.deirel.liminalindustries.translation.audit.layout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

interface LayoutEngineAdapter {
    String engine();

    List<LayoutScreen> screens();

    default double renderingTolerance() {
        return 0;
    }

    LayoutCapture capture(
        Minecraft minecraft,
        LayoutScreen target,
        Screen screen,
        String language
    );

    default void resetAfterAudit() {
    }
}
