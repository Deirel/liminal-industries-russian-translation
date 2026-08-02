package ru.deirel.liminalindustries.translation.audit.layout;

public record LayoutIssue(
    String screenId,
    String language,
    Rule rule,
    Severity severity,
    Classification classification,
    LayoutRegion text,
    LayoutRegion obstacle,
    String screenshot
) {
    public enum Rule {
        TEXT_OUTSIDE_PAGE,
        TEXT_INTERSECTS_CONTROL,
        TEXT_CLIPPED,
        TEXT_LINES_OVERLAP,
        MISSING_CONTENT,
        MISSING_LANGUAGE_PAGE
    }

    public enum Severity {
        WARN,
        ERROR
    }

    public enum Classification {
        UNCLASSIFIED,
        UPSTREAM_LAYOUT,
        TRANSLATION_LAYOUT,
        UNPAIRED_LANGUAGE
    }

    public LayoutIssue classify(Classification value) {
        return new LayoutIssue(
            screenId,
            language,
            rule,
            severity,
            value,
            text,
            obstacle,
            screenshot
        );
    }

    public LayoutIssue withScreenshot(String value) {
        return new LayoutIssue(
            screenId,
            language,
            rule,
            severity,
            classification,
            text,
            obstacle,
            value
        );
    }
}
