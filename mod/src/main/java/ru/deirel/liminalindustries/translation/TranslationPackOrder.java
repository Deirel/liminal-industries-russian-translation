package ru.deirel.liminalindustries.translation;

import net.minecraft.server.packs.repository.Pack;

import java.util.ArrayList;
import java.util.List;

public final class TranslationPackOrder {
    private static final String MOD_RESOURCES_PACK_ID = "mod_resources";

    private TranslationPackOrder() {
    }

    public static List<Pack> placeBaselineAfterModResources(
        List<Pack> selected
    ) {
        int baselineIndex = indexOf(
            selected,
            LiminalIndustriesTranslationMod.BASELINE_PACK_ID
        );
        int modResourcesIndex = indexOf(selected, MOD_RESOURCES_PACK_ID);
        if (baselineIndex < 0
            || modResourcesIndex < 0
            || baselineIndex == modResourcesIndex + 1) {
            return selected;
        }

        List<Pack> ordered = new ArrayList<>(selected);
        Pack baseline = ordered.remove(baselineIndex);
        modResourcesIndex = indexOf(ordered, MOD_RESOURCES_PACK_ID);
        ordered.add(modResourcesIndex + 1, baseline);
        return List.copyOf(ordered);
    }

    private static int indexOf(List<Pack> packs, String id) {
        for (int index = 0; index < packs.size(); index++) {
            if (id.equals(packs.get(index).getId())) {
                return index;
            }
        }
        return -1;
    }
}
