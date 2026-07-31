package ru.deirel.liminalindustries.translation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

record QuestTranslation(
    QuestTranslationField title,
    QuestTranslationField subtitle,
    List<QuestTranslationField> description
) {
    QuestTranslation {
        description = Collections.unmodifiableList(new ArrayList<>(description));
    }

    boolean isEmpty() {
        return title == null && subtitle == null && description.isEmpty();
    }

    String translatedTitle(String currentSource) {
        return title == null ? null : title.translate(currentSource);
    }

    String translatedSubtitle(String currentSource) {
        return subtitle == null ? null : subtitle.translate(currentSource);
    }

    List<String> translatedDescription(List<String> currentSource) {
        List<String> result = new ArrayList<>(currentSource);
        boolean changed = false;
        int limit = Math.min(description.size(), result.size());
        for (int index = 0; index < limit; index++) {
            QuestTranslationField field = description.get(index);
            if (field == null) {
                continue;
            }
            String translated = field.translate(currentSource.get(index));
            if (translated != null) {
                result.set(index, translated);
                changed = true;
            }
        }
        return changed ? List.copyOf(result) : null;
    }
}
