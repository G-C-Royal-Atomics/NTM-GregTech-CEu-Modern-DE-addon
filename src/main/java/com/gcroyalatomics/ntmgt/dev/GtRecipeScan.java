package com.gcroyalatomics.ntmgt.dev;

import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.List;
import java.util.Map;

/** GT machine recipes keep items in their own content lists, not in Recipe#getIngredients. */
final class GtRecipeScan {
    private GtRecipeScan() {}

    static void scan(GTRecipe recipe, String type, Map<String, int[]> in, Map<String, int[]> out) {
        walk(recipe.getInputContents(ItemRecipeCapability.CAP), type, in);
        walk(recipe.getOutputContents(ItemRecipeCapability.CAP), type, out);
    }

    private static void walk(List<Content> contents, String type, Map<String, int[]> map) {
        for (Content c : contents) {
            if (c.content() instanceof SizedIngredient s) {
                for (ItemStack stack : s.getItems()) LinkSelfTest.count(map, stack.getItem(), type);
            }
        }
    }
}
