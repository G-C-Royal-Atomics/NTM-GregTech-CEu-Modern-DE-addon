package com.gcroyalatomics.ntmgt.mixin.jei;

import com.gcroyalatomics.ntmgt.compat.MuiJeiLayout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Function;

/**
 * JEI 19.64+ only (mixinplugin.JeiMixinPlugin applies it after taking ModularUI's broken RecipeGuiLogicMixin out):
 * the recipe size JEI lays a page out with comes from the ModularUI category's largest recipe.
 */
@Mixin(targets = "mezz.jei.gui.recipes.RecipeGuiLogic", remap = false)
public abstract class MuiRecipeSizeMixin {
    @ModifyArg(method = "getVisibleRecipeLayoutsWithButtons",
            at = @At(value = "INVOKE", target = "Ljava/util/Optional;map(Ljava/util/function/Function;)Ljava/util/Optional;"))
    private Function<Object, Object> ntmgt$muiRecipeSize(Function<Object, Object> original) {
        return layout -> MuiJeiLayout.size(layout, original.apply(layout));
    }
}
