package ru.deirel.liminalindustries.translation.audit;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TranslationTemplate {
    private static final Pattern PRINTF_ARGUMENT = Pattern.compile("%(?:(\\d+)\\$)?s");
    private static final Pattern MESSAGE_FORMAT_ARGUMENT = Pattern.compile(
        "\\{(\\d+)(?:,[^{}]+)?}"
    );
    private static final Pattern PLACEHOLDER = Pattern.compile(
        "%(?:(?:\\d+)\\$)?s|%%|\\{\\d+(?:,[^{}]+)?}"
    );
    private static final Pattern LETTER = Pattern.compile("\\p{L}");

    private TranslationTemplate() {
    }

    static Set<Integer> referencedArguments(String template) {
        Set<Integer> result = new TreeSet<>();
        Matcher printf = PRINTF_ARGUMENT.matcher(template);
        int sequentialIndex = 0;
        while (printf.find()) {
            String explicitIndex = printf.group(1);
            result.add(explicitIndex == null
                ? sequentialIndex++
                : Integer.parseInt(explicitIndex) - 1);
        }

        Matcher messageFormat = MESSAGE_FORMAT_ARGUMENT.matcher(template);
        while (messageFormat.find()) {
            result.add(Integer.parseInt(messageFormat.group(1)));
        }
        return result;
    }

    static boolean isLanguageNeutral(String template) {
        String fixedText = PLACEHOLDER.matcher(template).replaceAll("");
        return !LETTER.matcher(fixedText).find();
    }
}
