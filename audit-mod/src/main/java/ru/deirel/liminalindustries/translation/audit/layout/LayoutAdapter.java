package ru.deirel.liminalindustries.translation.audit.layout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

public interface LayoutAdapter {
    String engine();

    List<LayoutScreen> screens(Minecraft minecraft);

    LayoutCapture capture(
        Minecraft minecraft,
        LayoutScreen target,
        Screen screen,
        String language
    );

    default void resetAfterAudit(Minecraft minecraft) {
    }
}
