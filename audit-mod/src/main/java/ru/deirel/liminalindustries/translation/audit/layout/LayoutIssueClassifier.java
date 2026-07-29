package ru.deirel.liminalindustries.translation.audit.layout;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LayoutIssueClassifier {
    private LayoutIssueClassifier() {
    }

    public static List<LayoutIssue> classify(
        List<LayoutIssue> english,
        List<LayoutIssue> russian
    ) {
        Set<String> upstream = new HashSet<>();
        english.forEach(issue -> upstream.add(key(issue)));
        return russian.stream()
            .map(issue -> issue.classify(
                upstream.contains(key(issue))
                    ? LayoutIssue.Classification.UPSTREAM_LAYOUT
                    : LayoutIssue.Classification.TRANSLATION_LAYOUT
            ))
            .toList();
    }

    private static String key(LayoutIssue issue) {
        return issue.screenId() + "|" + issue.rule();
    }
}
