package ru.deirel.liminalindustries.translation.audit.layout;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LayoutLanguageScreenTest {
    @Test
    void missingRussianPageIsReportedAndBlocksTheAudit() {
        List<LayoutIssue> issues = LayoutIssueClassifier.missingLanguagePages(
            List.of(capture("en_us", "spread/419", "page/redstone")),
            "en_us",
            "ru_ru"
        );

        assertEquals(1, issues.size());
        assertEquals(LayoutIssue.Rule.MISSING_LANGUAGE_PAGE, issues.get(0).rule());
        assertEquals(LayoutIssue.Classification.UNPAIRED_LANGUAGE, issues.get(0).classification());
        assertEquals("ru_ru", issues.get(0).language());
        assertEquals(1, LayoutReportWriter.missingLanguagePageErrors(issues));
        assertEquals(1, LayoutReportWriter.blockingErrors(issues));
    }

    @Test
    void missingEnglishPageIsUnpairedButDoesNotBlockRussianLayout() {
        List<LayoutIssue> issues = LayoutIssueClassifier.missingLanguagePages(
            List.of(capture("ru_ru", "spread/420", "page/extra")),
            "en_us",
            "ru_ru"
        );

        assertEquals(1, issues.size());
        assertEquals("en_us", issues.get(0).language());
        assertEquals(0, LayoutReportWriter.blockingErrors(issues));
    }

    @Test
    void reorderedLanguagePagesProduceNoIssues() {
        assertEquals(
            List.of(),
            LayoutIssueClassifier.missingLanguagePages(
                List.of(
                    capture("en_us", "spread/419", "page/redstone"),
                    capture("ru_ru", "spread/417", "page/redstone")
                ),
                "en_us",
                "ru_ru"
            )
        );
    }

    @Test
    void ignoresRuntimeGeneratedAndEmptyPages() {
        assertEquals(
            List.of(),
            LayoutIssueClassifier.missingLanguagePages(
                List.of(
                    capture("en_us", "spread/1", "page/runtime", "<runtime>"),
                    emptyCapture("en_us", "spread/2", "page/empty")
                ),
                "en_us",
                "ru_ru"
            )
        );
    }

    @Test
    void ignoresUnindexedScreenTextWithoutCrashing() {
        LayoutCapture indexed = capture("en_us", "spread/419", "page/redstone");
        LayoutRegion screenText = new LayoutRegion(
            "screen-text",
            LayoutRegion.Kind.TEXT,
            "left",
            0,
            0,
            0,
            10,
            10
        );
        LayoutCapture mixed = new LayoutCapture(
            indexed.engine(), indexed.book(), indexed.screenId(), indexed.resource(),
            indexed.entry(), indexed.page(), indexed.textSource(), indexed.language(),
            indexed.screenWidth(), indexed.screenHeight(), indexed.guiScale(),
            List.of(screenText, indexed.text().get(0)), indexed.pages(),
            indexed.scissors(), indexed.controls(), indexed.missingContent()
        );

        List<LayoutIssue> issues = LayoutIssueClassifier.missingLanguagePages(
            List.of(mixed),
            "en_us",
            "ru_ru"
        );

        assertEquals(1, issues.size());
        assertEquals("page/redstone", issues.get(0).text().logicalPage());
    }

    private static LayoutCapture capture(
        String language,
        String screen,
        String logicalPage
    ) {
        return capture(language, screen, logicalPage,
            "assets/tconstruct/book/encyclopedia/" + language + "/page.json");
    }

    private static LayoutCapture capture(
        String language,
        String screen,
        String logicalPage,
        String resource
    ) {
        LayoutRegion page = new LayoutRegion(
            "page",
            LayoutRegion.Kind.PAGE,
            "left",
            -1,
            0,
            0,
            100,
            100
        ).withLogicalPage(logicalPage);
        LayoutRegion text = new LayoutRegion(
            "text",
            LayoutRegion.Kind.TEXT,
            "left",
            0,
            0,
            0,
            10,
            10,
            "json_pointer:/text/0/text",
            resource,
            "Text",
            logicalPage
        );
        return new LayoutCapture(
            "mantle",
            "tconstruct:encyclopedia",
            "mantle:tconstruct:encyclopedia:" + screen,
            "assets/tconstruct/book/encyclopedia/" + language + "/page.json",
            "entry",
            1,
            "<runtime>",
            language,
            320,
            240,
            2,
            List.of(text),
            List.of(page),
            List.of(),
            List.of(),
            List.of()
        );
    }

    private static LayoutCapture emptyCapture(
        String language,
        String screen,
        String logicalPage
    ) {
        LayoutCapture capture = capture(language, screen, logicalPage);
        return new LayoutCapture(
            capture.engine(), capture.book(), capture.screenId(), capture.resource(),
            capture.entry(), capture.page(), capture.textSource(), capture.language(),
            capture.screenWidth(), capture.screenHeight(), capture.guiScale(),
            List.of(), capture.pages(), capture.scissors(), capture.controls(),
            capture.missingContent()
        );
    }
}
