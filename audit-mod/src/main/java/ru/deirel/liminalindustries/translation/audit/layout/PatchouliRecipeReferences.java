package ru.deirel.liminalindustries.translation.audit.layout;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

final class PatchouliRecipeReferences {
    private static final List<String> FIELDS = List.of(
        "recipe",
        "recipe2",
        "recipes"
    );

    private PatchouliRecipeReferences() {
    }

    static List<MissingReference> missing(
        JsonObject page,
        Predicate<ResourceLocation> exists
    ) {
        if (page == null || !isRecipePage(page)) {
            return List.of();
        }
        List<MissingReference> result = new ArrayList<>();
        for (String field : FIELDS) {
            JsonElement value = page.get(field);
            if (value == null) {
                continue;
            }
            if (value.isJsonArray()) {
                addMissing(result, field, value.getAsJsonArray(), exists);
            } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                addMissing(result, field, value.getAsString(), exists);
            }
        }
        return List.copyOf(result);
    }

    static List<MissingReference> missingResolved(
        ResourceLocation recipe,
        Object resolvedRecipe,
        ResourceLocation recipe2,
        Object resolvedRecipe2
    ) {
        List<MissingReference> result = new ArrayList<>(2);
        if (recipe != null && resolvedRecipe == null) {
            result.add(new MissingReference("recipe", recipe));
        }
        if (recipe2 != null && resolvedRecipe2 == null) {
            result.add(new MissingReference("recipe2", recipe2));
        }
        return List.copyOf(result);
    }

    private static boolean isRecipePage(JsonObject page) {
        JsonElement type = page.get("type");
        if (type == null || !type.isJsonPrimitive()) {
            return false;
        }
        String value = type.getAsString();
        if (value.startsWith("patchouli:")) {
            value = value.substring("patchouli:".length());
        }
        return value.equals("crafting")
            || value.equals("smelting")
            || value.equals("blasting")
            || value.equals("smoking")
            || value.equals("campfire_cooking")
            || value.equals("stonecutting")
            || value.equals("smithing")
            || value.startsWith("botania:");
    }

    private static void addMissing(
        List<MissingReference> result,
        String field,
        JsonArray values,
        Predicate<ResourceLocation> exists
    ) {
        for (int index = 0; index < values.size(); index++) {
            JsonElement value = values.get(index);
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                addMissing(result, field + "/" + index, value.getAsString(), exists);
            }
        }
    }

    private static void addMissing(
        List<MissingReference> result,
        String pointer,
        String value,
        Predicate<ResourceLocation> exists
    ) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id != null && !exists.test(id)) {
            result.add(new MissingReference(pointer, id));
        }
    }

    record MissingReference(String pointer, ResourceLocation recipe) {
    }
}
