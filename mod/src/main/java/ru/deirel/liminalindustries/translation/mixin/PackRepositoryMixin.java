package ru.deirel.liminalindustries.translation.mixin;

import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.deirel.liminalindustries.translation.TranslationPackOrder;

import java.util.Collection;
import java.util.List;

@Mixin(PackRepository.class)
abstract class PackRepositoryMixin {
    @Inject(
        method = {"rebuildSelected", "m_10517_"},
        at = @At("RETURN"),
        cancellable = true,
        remap = false
    )
    private void liminalIndustriesRu$placeTranslationAfterModResources(
        Collection<String> selectedIds,
        CallbackInfoReturnable<List<Pack>> callback
    ) {
        List<Pack> selected = callback.getReturnValue();
        List<Pack> ordered =
            TranslationPackOrder.placeBaselineAfterModResources(selected);
        if (ordered != selected) {
            callback.setReturnValue(ordered);
        }
    }
}
