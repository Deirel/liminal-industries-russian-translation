package ru.deirel.liminalindustries.translation.audit.layout;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LayoutAnalyzerTest {
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
            controls
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
}
