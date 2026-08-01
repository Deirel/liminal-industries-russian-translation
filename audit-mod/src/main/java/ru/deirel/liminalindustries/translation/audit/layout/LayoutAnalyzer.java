package ru.deirel.liminalindustries.translation.audit.layout;

import java.util.ArrayList;
import java.util.List;

public final class LayoutAnalyzer {
    private LayoutAnalyzer() {
    }

    public static List<LayoutIssue> analyze(LayoutCapture capture) {
        return analyze(capture, 0);
    }

    public static List<LayoutIssue> analyze(
        LayoutCapture capture,
        double renderingTolerance
    ) {
        List<LayoutIssue> issues = new ArrayList<>();
        for (LayoutRegion text : capture.text()) {
            if (text.kind() == LayoutRegion.Kind.CLIPPED_TEXT) {
                issues.add(issue(
                    capture,
                    LayoutIssue.Rule.TEXT_CLIPPED,
                    text,
                    text
                ));
                continue;
            }
            LayoutRegion page = findPage(capture.pages(), text.page());
            if (page != null && !page.contains(text, renderingTolerance)) {
                issues.add(issue(capture, LayoutIssue.Rule.TEXT_OUTSIDE_PAGE, text, page));
            }
            LayoutRegion scissor = findPage(capture.scissors(), text.page());
            if (scissor != null && !scissor.contains(text, renderingTolerance)) {
                issues.add(issue(capture, LayoutIssue.Rule.TEXT_CLIPPED, text, scissor));
            }
            for (LayoutRegion control : capture.controls()) {
                if (text.intersects(control, renderingTolerance)) {
                    issues.add(issue(
                        capture,
                        LayoutIssue.Rule.TEXT_INTERSECTS_CONTROL,
                        text,
                        control
                    ));
                }
            }
        }
        for (int left = 0; left < capture.text().size(); left++) {
            LayoutRegion first = capture.text().get(left);
            if (first.kind() != LayoutRegion.Kind.TEXT) {
                continue;
            }
            for (int right = left + 1; right < capture.text().size(); right++) {
                LayoutRegion second = capture.text().get(right);
                if (second.kind() == LayoutRegion.Kind.TEXT
                    && samePhysicalPage(first.page(), second.page())
                    && first.line() != second.line()
                    && first.intersects(second, renderingTolerance)) {
                    issues.add(issue(
                        capture,
                        LayoutIssue.Rule.TEXT_LINES_OVERLAP,
                        first,
                        second
                    ));
                }
            }
        }
        for (LayoutRegion missing : capture.missingContent()) {
            LayoutRegion page = findPage(capture.pages(), missing.page());
            issues.add(issue(
                capture,
                LayoutIssue.Rule.MISSING_CONTENT,
                missing,
                page == null ? missing : page
            ));
        }
        return List.copyOf(issues);
    }

    private static LayoutRegion findPage(List<LayoutRegion> regions, String page) {
        return regions.stream()
            .filter(region -> region.page().equals(page))
            .findFirst()
            .orElse(null);
    }

    private static boolean samePhysicalPage(String first, String second) {
        return physicalPage(first).equals(physicalPage(second));
    }

    private static String physicalPage(String page) {
        int separator = page.indexOf('#');
        return separator < 0 ? page : page.substring(0, separator);
    }

    private static LayoutIssue issue(
        LayoutCapture capture,
        LayoutIssue.Rule rule,
        LayoutRegion text,
        LayoutRegion obstacle
    ) {
        return new LayoutIssue(
            capture.screenId(),
            capture.language(),
            rule,
            LayoutIssue.Severity.ERROR,
            LayoutIssue.Classification.UNCLASSIFIED,
            text,
            obstacle,
            null
        );
    }
}
