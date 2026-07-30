package ru.deirel.liminalindustries.translation.audit.layout;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
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
        List<MissingReference> result = new ArrayList<>();
        for (Reference reference : references(page)) {
            if (!exists.test(reference.recipe())) {
                result.add(reference.missing());
            }
        }
        return List.copyOf(result);
    }

    static List<MissingReference> missingResolved(
        JsonObject page,
        Function<ResourceLocation, Object> registeredRecipe,
        Object resolvedRecipe,
        Object resolvedRecipe2
    ) {
        List<Reference> unresolved = new ArrayList<>();
        List<Object> unmatchedResolved = new ArrayList<>(2);
        if (resolvedRecipe != null) {
            unmatchedResolved.add(resolvedRecipe);
        }
        if (resolvedRecipe2 != null) {
            unmatchedResolved.add(resolvedRecipe2);
        }

        for (Reference reference : references(page)) {
            Object registered = registeredRecipe.apply(reference.recipe());
            if (registered == null) {
                unresolved.add(reference);
            } else if (!removeIdentity(unmatchedResolved, registered)) {
                unresolved.add(reference);
            }
        }

        List<MissingReference> result = new ArrayList<>(unresolved.size());
        for (Reference reference : unresolved) {
            if (registeredRecipe.apply(reference.recipe()) == null
                && !unmatchedResolved.isEmpty()) {
                unmatchedResolved.remove(0);
            } else {
                result.add(reference.missing());
            }
        }
        return List.copyOf(result);
    }

    private static List<Reference> references(JsonObject page) {
        if (page == null || !isRecipePage(page)) {
            return List.of();
        }
        List<Reference> result = new ArrayList<>();
        for (String field : FIELDS) {
            JsonElement value = page.get(field);
            if (value == null) {
                continue;
            }
            if (value.isJsonArray()) {
                addReferences(result, field, value.getAsJsonArray());
            } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                addReference(result, field, value.getAsString());
            }
        }
        return List.copyOf(result);
    }

    private static boolean removeIdentity(List<Object> values, Object expected) {
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index) == expected) {
                values.remove(index);
                return true;
            }
        }
        return false;
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

    private static void addReferences(
        List<Reference> result,
        String field,
        JsonArray values
    ) {
        for (int index = 0; index < values.size(); index++) {
            JsonElement value = values.get(index);
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                addReference(result, field + "/" + index, value.getAsString());
            }
        }
    }

    private static void addReference(
        List<Reference> result,
        String pointer,
        String value
    ) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id != null) {
            result.add(new Reference(pointer, id));
        }
    }

    private record Reference(String pointer, ResourceLocation recipe) {
        private MissingReference missing() {
            return new MissingReference(pointer, recipe);
        }
    }

    record MissingReference(String pointer, ResourceLocation recipe) {
    }
}
