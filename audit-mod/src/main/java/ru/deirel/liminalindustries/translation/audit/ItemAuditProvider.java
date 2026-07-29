package ru.deirel.liminalindustries.translation.audit;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class ItemAuditProvider implements AuditProvider {
    @Override
    public String id() {
        return "items";
    }

    @Override
    public List<AuditSubject> discover(AuditContext context) {
        IIngredientManager ingredients = context.jeiRuntime().getIngredientManager();
        IIngredientHelper<ItemStack> helper = ingredients.getIngredientHelper(
            VanillaTypes.ITEM_STACK
        );
        Collection<ItemStack> jeiStacks = ingredients.getAllIngredients(
            VanillaTypes.ITEM_STACK
        );
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        for (ItemStack stack : jeiStacks) {
            if (!stack.isEmpty()) {
                addCandidate(candidates, helper, stack, "JEI");
            }
        }
        for (Item item : ForgeRegistries.ITEMS) {
            if (item != Items.AIR) {
                ItemStack stack = new ItemStack(item);
                if (!stack.isEmpty()) {
                    addCandidate(candidates, helper, stack, "REGISTRY");
                }
            }
        }

        List<AuditSubject> subjects = new ArrayList<>();
        for (Candidate candidate : candidates.values()) {
            ItemStack stack = candidate.stack();
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            subjects.add(new AuditSubject(
                id(),
                candidate.uid(),
                "item",
                itemId.toString(),
                stack.getDescriptionId(),
                stack.getHoverName(),
                candidate.sources()
            ));
        }
        return subjects;
    }

    private static void addCandidate(
        Map<String, Candidate> candidates,
        IIngredientHelper<ItemStack> helper,
        ItemStack stack,
        String source
    ) {
        String uid;
        try {
            uid = helper.getUniqueId(stack, UidContext.Ingredient);
        } catch (RuntimeException exception) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            uid = "unresolved:" + itemId + ":" + Integer.toHexString(stack.hashCode());
        }
        String auditUid = uid;
        Candidate candidate = candidates.computeIfAbsent(
            auditUid,
            ignored -> new Candidate(auditUid, stack.copy(), new TreeSet<>())
        );
        candidate.sources().add(source);
    }

    private record Candidate(String uid, ItemStack stack, Set<String> sources) {
    }
}
