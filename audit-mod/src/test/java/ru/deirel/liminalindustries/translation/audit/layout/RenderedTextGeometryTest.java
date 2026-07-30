package ru.deirel.liminalindustries.translation.audit.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RenderedTextGeometryTest {
    @Test
    void removesInvisibleAdvanceAtTheEndOfALaidOutLine() {
        assertEquals(
            "visible",
            RenderedTextGeometry.trimTrailingWhitespace("visible \t\u00a0")
        );
        assertEquals(
            "visible text",
            RenderedTextGeometry.trimTrailingWhitespace("visible text")
        );
    }

    @Test
    void appliesPageOffsetAndBothRendererScales() {
        LayoutRegion region = RenderedTextGeometry.region(
            "right-word",
            "right",
            45,
            100,
            50,
            141,
            18,
            0,
            22,
            10,
            31,
            20,
            8,
            0.5,
            2,
            "json_pointer:/pages/1/text"
        );

        assertEquals(492, region.x());
        assertEquals(189, region.y());
        assertEquals(20, region.width());
        assertEquals(8, region.height());
        assertEquals("right", region.page());
        assertEquals("json_pointer:/pages/1/text", region.source());
    }
}
