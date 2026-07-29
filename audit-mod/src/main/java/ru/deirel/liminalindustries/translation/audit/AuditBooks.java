package ru.deirel.liminalindustries.translation.audit;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

final class AuditBooks {
    private AuditBooks() {
    }

    static AuditBook registryItem(String sourceId, ResourceLocation itemId) {
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        if (item == null || item == Items.AIR) {
            throw new IllegalStateException(
                sourceId + " book item is not registered: " + itemId
            );
        }
        return new AuditBook(sourceId + ":" + itemId, new ItemStack(item));
    }
}
