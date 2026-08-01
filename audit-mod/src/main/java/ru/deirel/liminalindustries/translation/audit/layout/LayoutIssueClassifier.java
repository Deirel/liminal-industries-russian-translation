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
