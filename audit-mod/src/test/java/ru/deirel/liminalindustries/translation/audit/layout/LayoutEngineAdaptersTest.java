package ru.deirel.liminalindustries.translation.audit.layout;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LayoutEngineAdaptersTest {
    @Test
    void exposesRegisteredAdaptersForCommandRegistration() {
        assertEquals(
            List.of("patchouli", "mantle", "immersive_engineering"),
            LayoutEngineAdapters.engines()
        );
    }

    @Test
    void createsAdapterByEngine() {
        assertEquals("mantle", LayoutEngineAdapters.create("mantle").engine());
    }

    @Test
    void keepsRenderingToleranceAdapterSpecific() {
        assertEquals(
            0,
            LayoutEngineAdapters.create("patchouli").renderingTolerance()
        );
        assertEquals(
            1,
            LayoutEngineAdapters.create("mantle").renderingTolerance()
        );
        assertEquals(
            1,
            LayoutEngineAdapters.create("immersive_engineering")
                .renderingTolerance()
        );
    }

    @Test
    void rejectsUnknownEngine() {
        assertThrows(
            IllegalArgumentException.class,
            () -> LayoutEngineAdapters.create("unknown")
        );
    }
}
