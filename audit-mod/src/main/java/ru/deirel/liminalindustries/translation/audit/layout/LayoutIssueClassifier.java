package ru.deirel.liminalindustries.translation.audit.layout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class LayoutIssueClassifier {
    private LayoutIssueClassifier() {
    }

    public static List<LayoutIssue> classify(
        List<LayoutIssue> english,
        List<LayoutIssue> russian
    ) {
        Set<String> pairedSubjects = russian.stream()
            .map(LayoutIssueClassifier::subject)
            .collect(Collectors.toSet());
        return classify(english, russian, pairedSubjects);
    }

    public static List<LayoutIssue> classify(
        List<LayoutIssue> english,
        List<LayoutIssue> russian,
        Set<String> englishSubjects
    ) {
        Set<String> upstream = new HashSet<>();
        english.forEach(issue -> upstream.add(key(issue)));
        return russian.stream()
            .map(issue -> issue.classify(
                !englishSubjects.contains(subject(issue))
                    ? LayoutIssue.Classification.UNPAIRED_LANGUAGE
                    : upstream.contains(key(issue))
                    ? LayoutIssue.Classification.UPSTREAM_LAYOUT
                    : LayoutIssue.Classification.TRANSLATION_LAYOUT
            ))
            .toList();
    }

    public static List<LayoutIssue> missingLanguagePages(
        List<LayoutCapture> captures,
        String referenceLanguage,
        String translatedLanguage
    ) {
        Map<String, LayoutCapture> reference = capturesByLogicalPage(
            captures,
            referenceLanguage
        );
        Map<String, LayoutCapture> translated = capturesByLogicalPage(
            captures,
            translatedLanguage
        );
        List<LayoutIssue> result = new ArrayList<>();
        reference.forEach((logicalPage, capture) -> {
            if (!translated.containsKey(logicalPage)) {
                result.add(missingLanguagePage(
                    capture,
                    logicalPage,
                    translatedLanguage,
                    referenceLanguage
                ));
            }
        });
        translated.forEach((logicalPage, capture) -> {
            if (!reference.containsKey(logicalPage)) {
                result.add(missingLanguagePage(
                    capture,
                    logicalPage,
                    referenceLanguage,
                    translatedLanguage
                ));
            }
        });
        return List.copyOf(result);
    }

    private static Map<String, LayoutCapture> capturesByLogicalPage(
        List<LayoutCapture> captures,
        String language
    ) {
        Map<String, LayoutCapture> result = new LinkedHashMap<>();
        captures.stream()
            .filter(capture -> capture.language().equals(language))
            .forEach(capture -> capture.text().stream()
                .filter(region -> !"<runtime>".equals(region.resource()))
                .map(LayoutRegion::logicalPage)
                .filter(java.util.Objects::nonNull)
                .forEach(logicalPage -> result.putIfAbsent(logicalPage, capture)));
        return result;
    }

    private static LayoutIssue missingLanguagePage(
        LayoutCapture present,
        String logicalPage,
        String missingLanguage,
        String presentLanguage
    ) {
        LayoutRegion origin = present.text().stream()
            .filter(region -> logicalPage.equals(region.logicalPage()))
            .findFirst()
            .orElse(null);
        LayoutRegion marker = new LayoutRegion(
            "missing-language-page-" + missingLanguage,
            LayoutRegion.Kind.TEXT,
            "<page>",
            -1,
            0,
            0,
            1,
            1,
            origin == null ? "runtime_capture:" + presentLanguage : origin.source(),
            origin == null ? present.resource() : origin.resource(),
            "Page exists in " + presentLanguage + " but is missing in "
                + missingLanguage,
            logicalPage
        );
        return new LayoutIssue(
            present.screenId(),
            missingLanguage,
            LayoutIssue.Rule.MISSING_LANGUAGE_PAGE,
            LayoutIssue.Severity.ERROR,
            LayoutIssue.Classification.UNPAIRED_LANGUAGE,
            marker,
            marker,
            null
        );
    }

    private static String key(LayoutIssue issue) {
        String key = subject(issue)
            + "|" + issue.rule()
            + "|" + issue.text().id();
        if (issue.text().logicalPage() == null) {
            key += "|" + issue.obstacle().id()
                + "|" + Math.round(issue.text().x())
                + "|" + Math.round(issue.text().y());
        }
        return key;
    }

    private static String subject(LayoutIssue issue) {
        return issue.text().logicalPage() == null
            ? issue.screenId()
            : issue.text().logicalPage();
    }
}
