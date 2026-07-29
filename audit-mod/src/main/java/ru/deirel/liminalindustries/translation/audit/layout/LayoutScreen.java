package ru.deirel.liminalindustries.translation.audit.layout;

import net.minecraft.client.gui.screens.Screen;

import java.util.function.Supplier;

public record LayoutScreen(
    String engine,
    String book,
    String id,
    String resource,
    String entry,
    Integer page,
    String textSource,
    Supplier<Screen> factory
) {
}
