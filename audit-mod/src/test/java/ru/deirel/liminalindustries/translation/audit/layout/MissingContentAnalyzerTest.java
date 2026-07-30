package ru.deirel.liminalindustries.translation.audit.layout;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MissingContentAnalyzerTest {
    @Test
    void reportsMissingExpectedContent() {
        LayoutRegion page = new LayoutRegion(
            "page",
            LayoutRegion.Kind.PAGE,
            "left",
            0,
            0,
            0,
            100,
            100
        );
        LayoutRegion missing = new LayoutRegion(
            "missing-recipe",
            LayoutRegion.Kind.TEXT,
            "left",
            1,
            0,
            0,
            1,
            1
        );
        LayoutCapture capture = new LayoutCapture(
            "patchouli",
            "botania:lexicon",
            "missing",
            "entry.json",
            "entry",
            0,
            "/text",
            "ru_ru",
            320,
            240,
            2,
            List.of(),
            List.of(page),
            List.of(),
            List.of(),
            List.of(missing)
        );

        LayoutIssue issue = LayoutAnalyzer.analyze(capture).get(0);

        assertEquals(LayoutIssue.Rule.MISSING_CONTENT, issue.rule());
        assertEquals("missing-recipe", issue.text().id());
    }
}
