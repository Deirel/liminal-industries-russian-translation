package ru.deirel.liminalindustries.translation.audit.layout;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class LayoutIssueClassifier {
    private LayoutIssueClassifier() {
    }

    public static List<LayoutIssue> classify(
        List<LayoutIssue> english,
        List<LayoutIssue> russian
    ) {
        Set<String> pairedScreens = russian.stream()
            .map(LayoutIssue::screenId)
            .collect(Collectors.toSet());
        return classify(english, russian, pairedScreens);
    }

    public static List<LayoutIssue> classify(
        List<LayoutIssue> english,
        List<LayoutIssue> russian,
        Set<String> englishScreens
    ) {
        Set<String> upstream = new HashSet<>();
        english.forEach(issue -> upstream.add(key(issue)));
        return russian.stream()
            .map(issue -> issue.classify(
                !englishScreens.contains(issue.screenId())
                    ? LayoutIssue.Classification.UNPAIRED_LANGUAGE
                    : upstream.contains(key(issue))
                    ? LayoutIssue.Classification.UPSTREAM_LAYOUT
                    : LayoutIssue.Classification.TRANSLATION_LAYOUT
            ))
            .toList();
    }

    private static String key(LayoutIssue issue) {
        return issue.screenId()
            + "|" + issue.rule()
            + "|" + issue.text().id()
            + "|" + issue.obstacle().id()
            + "|" + Math.round(issue.text().x())
            + "|" + Math.round(issue.text().y());
    }
}
