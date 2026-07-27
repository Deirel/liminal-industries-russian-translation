package ru.deirel.liminalindustries.translation;

import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.util.TextUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class QuestTranslationOverlay {
    private static final QuestTranslationPayload PAYLOAD = loadPayload();

    private static BaseQuestFile checkedQuestFile;
    private static boolean compatibleQuestFile;

    private QuestTranslationOverlay() {
    }

    public static Component title(QuestObjectBase object) {
        QuestTranslation translation = translationFor(object);
        return translation == null || translation.title() == null
            ? null
            : TextUtils.parseRawText(translation.title());
    }

    public static Component subtitle(Quest quest) {
        QuestTranslation translation = translationFor(quest);
        return translation == null || translation.subtitle() == null
            ? null
            : TextUtils.parseRawText(translation.subtitle());
    }

    public static List<Component> description(Quest quest) {
        QuestTranslation translation = translationFor(quest);
        return translation == null || translation.description().isEmpty()
            ? null
            : translation.description().stream().map(TextUtils::parseRawText).toList();
    }

    private static QuestTranslation translationFor(QuestObjectBase object) {
        if (!isRussianLanguage() || !isCompatible(object.getQuestFile())) {
            return null;
        }
        return PAYLOAD.translation(object.id);
    }

    private static boolean isRussianLanguage() {
        return "ru_ru".equals(Minecraft.getInstance().getLanguageManager().getSelected());
    }

    private static synchronized boolean isCompatible(BaseQuestFile questFile) {
        if (questFile != checkedQuestFile) {
            Set<Long> actualObjectIds = questFile.getAllObjects().stream()
                .map(object -> object.id)
                .collect(Collectors.toUnmodifiableSet());
            compatibleQuestFile = PAYLOAD.matchesObjectIds(actualObjectIds);
            checkedQuestFile = questFile;

            if (compatibleQuestFile) {
                LiminalIndustriesTranslationMod.LOGGER.info(
                    "Quest translation overlay enabled for {} objects",
                    actualObjectIds.size()
                );
            } else {
                LiminalIndustriesTranslationMod.LOGGER.warn(
                    "Quest translation overlay disabled: unsupported quest book"
                );
            }
        }
        return compatibleQuestFile;
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
