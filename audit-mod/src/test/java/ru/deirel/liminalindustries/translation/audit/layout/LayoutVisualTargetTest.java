package ru.deirel.liminalindustries.translation.audit.layout;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutVisualTargetTest {
    @Test
    void matchesRenderedSourceAndResource() {
        LayoutRegion text = new LayoutRegion(
            "text", LayoutRegion.Kind.TEXT, "left", 0,
            0, 0, 10, 10,
            "json_pointer:/text/0/text",
            "assets/tconstruct/book/test/ru_ru/page.json"
        );
        LayoutCapture capture = new LayoutCapture(
            "mantle", "tconstruct:test", "screen", "resource", "entry", 0,
            "source", "ru_ru", 512, 384, 4, List.of(text), List.of(),
            List.of(), List.of(), List.of()
        );

        assertTrue(LayoutAuditRunner.matchesVisualTarget(
            capture,
            "assets/tconstruct/book/test/ru_ru/page.json",
            "json_pointer:/text/0/text",
            "screen"
        ));
        assertFalse(LayoutAuditRunner.matchesVisualTarget(
            capture,
            "assets/tconstruct/book/test/ru_ru/other.json",
            "json_pointer:/text/0/text",
            "screen"
        ));
        assertTrue(LayoutAuditRunner.matchesVisualTarget(
            capture,
            "resource",
            "translation_key:not_exposed_by_the_renderer",
            "screen"
        ));
    }
}
