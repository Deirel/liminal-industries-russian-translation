package ru.deirel.liminalindustries.translation;

import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.util.TextUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.List;

public final class QuestTranslationOverlay {
    private static final QuestTranslationPayload PAYLOAD = loadPayload();

    private QuestTranslationOverlay() {
    }

    public static Component title(QuestObjectBase object) {
        QuestTranslation translation = translationFor(object);
        String translated = translation == null
            ? null
            : translation.translatedTitle(object.getRawTitle());
        return translated == null ? null : TextUtils.parseRawText(translated);
    }

    public static Component subtitle(Quest quest) {
        QuestTranslation translation = translationFor(quest);
        String translated = translation == null
            ? null
            : translation.translatedSubtitle(quest.getRawSubtitle());
        return translated == null ? null : TextUtils.parseRawText(translated);
    }

    public static List<Component> description(Quest quest) {
        QuestTranslation translation = translationFor(quest);
        List<String> translated = translation == null
            ? null
            : translation.translatedDescription(quest.getRawDescription());
        return translated == null
            ? null
            : translated.stream().map(TextUtils::parseRawText).toList();
    }

    private static QuestTranslation translationFor(QuestObjectBase object) {
        if (!isRussianLanguage()) {
            return null;
        }
        return PAYLOAD.translation(object.id);
    }

    private static boolean isRussianLanguage() {
        return "ru_ru".equals(Minecraft.getInstance().getLanguageManager().getSelected());
    }

    private static QuestTranslationPayload loadPayload() {
        try {
            QuestTranslationPayload payload = QuestTranslationPayload.load(
                QuestTranslationOverlay.class
            );
            LiminalIndustriesTranslationMod.LOGGER.info(
                "Loaded {} in-memory quest translations",
                payload.translationCount()
            );
            return payload;
        } catch (IOException exception) {
            LiminalIndustriesTranslationMod.LOGGER.error(
                "Could not load embedded quest translations",
                exception
            );
            return QuestTranslationPayload.empty();
        }
    }
}
