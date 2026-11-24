package com.gcroyalatomics.ntmgt.mixin.jei;

import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

/**
 * Keeps JEI's recipe view from crashing with GTCEu 8.0's bundled ModularUI 3.3.1 (not this mod's bug).
 * ModularUI's accessor jei.RecipeSlotAccessor sets RecipeSlot.allIngredients / displayIngredients, fields JEI dropped
 * by 19.51 (ModularUI's JeiRecipeViewerSlot writes them when it swaps a slot's contents); LDLib2
 * (GTCEu's dependency) needs JEI 19.51+, so no JEI version satisfies both, and loading RecipeSlot (any R/U in JEI)
 * then fails with MixinTransformerError. The fields are put back (applied before ModularUI's mixins, priority 100) so
 * the accessor finds them; what ModularUI writes there is unused by JEI, which shows the slot's contents as built. Optional config: without JEI nothing happens.
 */
@Mixin(targets = "mezz.jei.library.gui.ingredients.RecipeSlot", priority = 100, remap = false)
public abstract class RecipeSlotFieldMixin {
    @SuppressWarnings("unused")
    private List<?> allIngredients;
    @SuppressWarnings("unused")
    private List<?> displayIngredients;
}
