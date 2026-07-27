package ru.deirel.liminalindustries.translation.mixin;

import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.deirel.liminalindustries.translation.QuestTranslationOverlay;

@Mixin(value = QuestObjectBase.class, remap = false)
abstract class QuestObjectBaseMixin {
    @Inject(method = "getTitle", at = @At("RETURN"), cancellable = true, remap = false)
    private void liminalIndustriesRu$translateTitle(
        CallbackInfoReturnable<Component> callback
    ) {
        Component translation = QuestTranslationOverlay.title((QuestObjectBase) (Object) this);
        if (translation != null) {
            callback.setReturnValue(translation);
        }
    }
}
