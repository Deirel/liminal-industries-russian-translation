package ru.deirel.liminalindustries.translation;

import java.util.List;

record QuestTranslation(String title, String subtitle, List<String> description) {
    QuestTranslation {
        description = List.copyOf(description);
    }

    boolean isEmpty() {
        return title == null && subtitle == null && description.isEmpty();
    }
}
