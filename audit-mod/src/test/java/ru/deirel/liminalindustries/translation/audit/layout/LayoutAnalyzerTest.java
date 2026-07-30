package ru.deirel.liminalindustries.translation.audit.layout;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutAnalyzerTest {
    @org.junit.jupiter.api.Test
    void ignoresSubpixelFloatingPointNoiseAtPageAndControlEdges() {
        LayoutRegion page = region("page", LayoutRegion.Kind.PAGE, 0, 0, 116, 156);
        LayoutRegion text = region(
            "text",
            LayoutRegion.Kind.TEXT,
            0,
            0,
            116.0000002,
            8
        );
        LayoutRegion touchingControl = region(
            "button",
            LayoutRegion.Kind.CONTROL,
            116.0000001,
            0,
            10,
            8
        );

        assertTrue(page.contains(text));
        assertFalse(text.intersects(touchingControl));
    }

    @ParameterizedTest
    @MethodSource("defects")
    void detectsObjectiveGeometryDefects(
        LayoutRegion text,
        List<LayoutRegion> pages,
        List<LayoutRegion> scissors,
        List<LayoutRegion> controls,
        LayoutIssue.Rule expected
    ) {
        LayoutCapture capture = new LayoutCapture(
            "fixture",
            "fixture:book",
            "fixture:screen",
            "fixture.json",
            "entry",
            0,
            "/text",
            "ru_ru",
            320,
            240,
            2,
            List.of(text),
            pages,
            scissors,
            controls,
            List.of()
        );

        assertEquals(
            expected,
            LayoutAnalyzer.analyze(capture).get(0).rule()
        );
    }

    @org.junit.jupiter.api.Test
    void classifiesOnlyRussianDefectsSeparatelyFromUpstreamDefects() {
        LayoutRegion text = region("word", LayoutRegion.Kind.TEXT, 0, 0, 10, 9);
        LayoutRegion obstacle = region(
            "button",
            LayoutRegion.Kind.CONTROL,
            0,
            0,
            10,
            9
        );
        LayoutIssue english = new LayoutIssue(
            "shared",
            "en_us",
            LayoutIssue.Rule.TEXT_INTERSECTS_CONTROL,
            LayoutIssue.Severity.ERROR,
            LayoutIssue.Classification.UNCLASSIFIED,
            text,
            obstacle,
            null
        );
        LayoutIssue sharedRussian = new LayoutIssue(
            "shared",
            "ru_ru",
            LayoutIssue.Rule.TEXT_INTERSECTS_CONTROL,
            LayoutIssue.Severity.ERROR,
            LayoutIssue.Classification.UNCLASSIFIED,
            text,
            obstacle,
            null
        );
        LayoutIssue russianOnly = new LayoutIssue(
            "russian-only",
            "ru_ru",
            LayoutIssue.Rule.TEXT_OUTSIDE_PAGE,
            LayoutIssue.Severity.ERROR,
            LayoutIssue.Classification.UNCLASSIFIED,
            text,
            obstacle,
            null
        );

        List<LayoutIssue> classified = LayoutIssueClassifier.classify(
            List.of(english),
            List.of(sharedRussian, russianOnly)
        );

        assertEquals(
            LayoutIssue.Classification.UPSTREAM_LAYOUT,
            classified.get(0).classification()
        );
        assertEquals(
            LayoutIssue.Classification.TRANSLATION_LAYOUT,
            classified.get(1).classification()
        );
    }

    @org.junit.jupiter.api.Test
    void doesNotHideDifferentRussianRegionBehindSameEnglishRule() {
        LayoutRegion englishText = region(
            "english-word",
            LayoutRegion.Kind.TEXT,
            0,
            0,
            10,
            9
        );
        LayoutRegion russianText = region(
            "english-word",
            LayoutRegion.Kind.TEXT,
            0,
            80,
            10,
            9
        );
        LayoutRegion page = region(
            "page",
            LayoutRegion.Kind.PAGE,
            0,
            0,
            100,
            100
        );
        LayoutIssue english = issue(
            "shared",
            "en_us",
            LayoutIssue.Rule.TEXT_OUTSIDE_PAGE,
            englishText,
            page
        );
        LayoutIssue russian = issue(
            "shared",
            "ru_ru",
            LayoutIssue.Rule.TEXT_OUTSIDE_PAGE,
            russianText,
            page
        );

        LayoutIssue classified = LayoutIssueClassifier.classify(
            List.of(english),
            List.of(russian),
            Set.of("shared")
        ).get(0);

        assertEquals(
            LayoutIssue.Classification.TRANSLATION_LAYOUT,
            classified.classification()
        );
    }

    @org.junit.jupiter.api.Test
    void marksRussianScreenWithoutEnglishCounterpartAsUnpaired() {
        LayoutRegion text = region("word", LayoutRegion.Kind.TEXT, 0, 0, 10, 9);
        LayoutRegion page = region("page", LayoutRegion.Kind.PAGE, 0, 0, 100, 100);
        LayoutIssue russian = issue(
            "russian-only",
            "ru_ru",
            LayoutIssue.Rule.TEXT_OUTSIDE_PAGE,
            text,
            page
        );

        LayoutIssue classified = LayoutIssueClassifier.classify(
            List.of(),
            List.of(russian),
            Set.of()
        ).get(0);

        assertEquals(
            LayoutIssue.Classification.UNPAIRED_LANGUAGE,
            classified.classification()
        );
    }

    private static Stream<Arguments> defects() {
        LayoutRegion page = region("page", LayoutRegion.Kind.PAGE, 0, 0, 100, 100);
        LayoutRegion scissor = region(
            "scissor",
            LayoutRegion.Kind.SCISSOR,
            0,
            0,
            100,
            80
        );
        LayoutRegion control = region(
            "button",
            LayoutRegion.Kind.CONTROL,
            10,
            70,
            40,
            20
        );
        return Stream.of(
            Arguments.of(
                region("outside", LayoutRegion.Kind.TEXT, 95, 10, 10, 9),
                List.of(page),
                List.of(),
                List.of(),
                LayoutIssue.Rule.TEXT_OUTSIDE_PAGE
            ),
            Arguments.of(
                region("clipped", LayoutRegion.Kind.TEXT, 10, 75, 20, 9),
                List.of(page),
                List.of(scissor),
                List.of(),
                LayoutIssue.Rule.TEXT_CLIPPED
            ),
            Arguments.of(
                region("collision", LayoutRegion.Kind.TEXT, 10, 75, 20, 9),
                List.of(page),
                List.of(),
                List.of(control),
                LayoutIssue.Rule.TEXT_INTERSECTS_CONTROL
            )
        );
    }

    private static LayoutRegion region(
        String id,
        LayoutRegion.Kind kind,
        double x,
        double y,
        double width,
        double height
    ) {
        return new LayoutRegion(id, kind, "left", 0, x, y, width, height);
    }

    private static LayoutIssue issue(
        String screen,
        String language,
        LayoutIssue.Rule rule,
        LayoutRegion text,
        LayoutRegion obstacle
    ) {
        return new LayoutIssue(
            screen,
            language,
            rule,
            LayoutIssue.Severity.ERROR,
            LayoutIssue.Classification.UNCLASSIFIED,
            text,
            obstacle,
            null
        );
    }
}
