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

    @Test
    void upstreamMissingContentFailsOnceAcrossBothLanguages() {
        LayoutRegion marker = new LayoutRegion(
            "missing-recipe",
            LayoutRegion.Kind.TEXT,
            "right",
            1,
            0,
            0,
            1,
            1
        );
        LayoutIssue english = missingIssue(
            "en_us",
            marker,
            LayoutIssue.Classification.UPSTREAM_LAYOUT
        );
        LayoutIssue russian = missingIssue(
            "ru_ru",
            marker,
            LayoutIssue.Classification.TRANSLATION_LAYOUT
        );

        assertEquals(
            1,
            LayoutReportWriter.missingContentErrors(List.of(english, russian))
        );
        assertEquals(
            1,
            LayoutReportWriter.blockingErrors(List.of(english, russian))
        );
    }

    private static LayoutIssue missingIssue(
        String language,
        LayoutRegion marker,
        LayoutIssue.Classification classification
    ) {
        return new LayoutIssue(
            "patchouli:botania:lexicon:entry/botania:basics/flowers/6",
            language,
            LayoutIssue.Rule.MISSING_CONTENT,
            LayoutIssue.Severity.ERROR,
            classification,
            marker,
            marker,
            null
        );
    }
}
