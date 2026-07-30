package ru.deirel.liminalindustries.translation.audit.layout;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchouliRecipeReferencesTest {
    @Test
    void findsMissingSingleAndMultiRecipeReferences() {
        var page = JsonParser.parseString("""
            {
              "type": "botania:crafting_multi",
              "recipe": "botania:present",
              "recipes": [
                "botania:missing",
                "botania:also_present"
              ]
            }
            """).getAsJsonObject();
        Set<ResourceLocation> present = Set.of(
            ResourceLocation.parse("botania:present"),
            ResourceLocation.parse("botania:also_present")
        );

        List<PatchouliRecipeReferences.MissingReference> missing =
            PatchouliRecipeReferences.missing(page, present::contains);

        assertEquals(1, missing.size());
        assertEquals("recipes/0", missing.get(0).pointer());
        assertEquals(
            ResourceLocation.parse("botania:missing"),
            missing.get(0).recipe()
        );
    }

    @Test
    void ignoresRecipeNamedFieldsOnNonRecipePages() {
        var page = JsonParser.parseString("""
            {
              "type": "multiblock",
              "recipe": "botania:not_a_recipe_reference"
            }
            """).getAsJsonObject();

        assertTrue(PatchouliRecipeReferences.missing(page, ignored -> false).isEmpty());
    }

    @Test
    void acceptsNamespacedPatchouliRecipePages() {
        var page = JsonParser.parseString("""
            {
              "type": "patchouli:crafting",
              "recipe": "example:missing"
            }
            """).getAsJsonObject();

        assertEquals(
            ResourceLocation.parse("example:missing"),
            PatchouliRecipeReferences.missing(page, ignored -> false)
                .get(0)
                .recipe()
        );
    }

    @Test
    void findsUnresolvedRecipeFromTheSourcePage() {
        ResourceLocation id = ResourceLocation.parse("botania:fertilizer_dye");
        var page = JsonParser.parseString("""
            {
              "type": "crafting",
              "recipe": "botania:fertilizer_dye"
            }
            """).getAsJsonObject();

        List<PatchouliRecipeReferences.MissingReference> missing =
            PatchouliRecipeReferences.missingResolved(
                page,
                ignored -> null,
                null,
                null
            );

        assertEquals(1, missing.size());
        assertEquals(id, missing.get(0).recipe());
    }

    @Test
    void rejectsARegisteredRecipeThatThePageCouldNotResolve() {
        ResourceLocation id = ResourceLocation.parse("botania:fertilizer_dye");
        Object wrongType = new Object();
        var page = JsonParser.parseString("""
            {
              "type": "crafting",
              "recipe": "botania:fertilizer_dye"
            }
            """).getAsJsonObject();

        List<PatchouliRecipeReferences.MissingReference> missing =
            PatchouliRecipeReferences.missingResolved(
                page,
                ignored -> wrongType,
                null,
                null
            );

        assertEquals(1, missing.size());
        assertEquals(id, missing.get(0).recipe());
        assertTrue(
            PatchouliRecipeReferences.missingResolved(
                page,
                ignored -> wrongType,
                wrongType,
                null
            ).isEmpty()
        );
    }

    @Test
    void keepsResolvedSecondRecipeMatchedAfterPatchouliShiftsItsSlot() {
        ResourceLocation presentId = ResourceLocation.parse("botania:present");
        Object present = new Object();
        var page = JsonParser.parseString("""
            {
              "type": "crafting",
              "recipe": "botania:missing",
              "recipe2": "botania:present"
            }
            """).getAsJsonObject();
        Map<ResourceLocation, Object> registered = Map.of(presentId, present);

        List<PatchouliRecipeReferences.MissingReference> missing =
            PatchouliRecipeReferences.missingResolved(
                page,
                registered::get,
                present,
                null
            );

        assertEquals(1, missing.size());
        assertEquals(
            ResourceLocation.parse("botania:missing"),
            missing.get(0).recipe()
        );
    }
}
