package ru.deirel.liminalindustries.translation.mixin;

import dev.ftb.mods.ftbquests.quest.Quest;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.deirel.liminalindustries.translation.QuestTranslationOverlay;

import java.util.List;

@Mixin(value = Quest.class, remap = false)
abstract class QuestMixin {
    @Inject(method = "getSubtitle", at = @At("RETURN"), cancellable = true, remap = false)
    private void liminalIndustriesRu$translateSubtitle(
        CallbackInfoReturnable<Component> callback
    ) {
        Component translation = QuestTranslationOverlay.subtitle((Quest) (Object) this);
        if (translation != null) {
            callback.setReturnValue(translation);
        }
    }

    @Inject(method = "getDescription", at = @At("RETURN"), cancellable = true, remap = false)
    private void liminalIndustriesRu$translateDescription(
        CallbackInfoReturnable<List<Component>> callback
    ) {
        List<Component> translation = QuestTranslationOverlay.description((Quest) (Object) this);
        if (translation != null) {
            callback.setReturnValue(translation);
        }
    }
}
