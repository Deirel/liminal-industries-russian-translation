package ru.deirel.liminalindustries.translation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QuestTranslationTest {
    @Test
    void changedTitleDoesNotDisableMatchingSubtitle() {
        QuestTranslation translation = new QuestTranslation(
            field("Original title", "Переведённое название"),
            field("Original subtitle", "Переведённый подзаголовок"),
            List.of()
        );

        assertNull(translation.translatedTitle("Server title"));
        assertEquals(
            "Переведённый подзаголовок",
            translation.translatedSubtitle("Original subtitle")
        );
    }

    @Test
    void descriptionIsMatchedOneLineAtATime() {
        QuestTranslation translation = new QuestTranslation(
            null,
            null,
            List.of(
                field("First", "Первое"),
                field("Second", "Второе"),
                field("Third", "Третье")
            )
        );

        assertEquals(
            List.of("Первое", "User replacement", "Третье"),
            translation.translatedDescription(
                List.of("First", "User replacement", "Third")
            )
        );
    }

    @Test
    void insertedOrRemovedDescriptionLinesAreNotGuessed() {
        QuestTranslation translation = new QuestTranslation(
            null,
            null,
            List.of(
                field("First", "Первое"),
                field("Second", "Второе")
            )
        );

        assertNull(translation.translatedDescription(
            List.of("Inserted", "First", "Second")
        ));
        assertNull(translation.translatedDescription(List.of("Second")));
    }

    @Test
    void reusedIdWithDifferentSourceIsSkipped() {
        QuestTranslationField field = field(
            "Expected source",
            "Ожидаемый перевод"
        );

        assertNull(field.translate("Different object source"));
    }

    private static QuestTranslationField field(
        String source,
        String translation
    ) {
        return new QuestTranslationField(source, translation);
    }
}
