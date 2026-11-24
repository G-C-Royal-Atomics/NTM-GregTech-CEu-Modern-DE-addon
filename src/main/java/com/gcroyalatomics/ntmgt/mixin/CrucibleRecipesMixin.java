package com.gcroyalatomics.ntmgt.mixin;

import com.gcroyalatomics.ntmgt.compat.GtMolds;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.material.Mats;
import com.hbm.inventory.material.NTMMaterial;
import com.hbm.inventory.recipes.CrucibleRecipes;
import com.hbm.items.machine.ItemMold;
import com.hbm.items.machine.ItemScraps;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

/** NTM's casting page (JEI) lists its casts from moldRecipes(): the GT molds' casts are added, the same way. */
@Mixin(value = CrucibleRecipes.class, remap = false)
public abstract class CrucibleRecipesMixin {
    @ModifyReturnValue(method = "moldRecipes", at = @At("RETURN"))
    private static List<CrucibleRecipes.Cast> ntmgt$gtMolds(List<CrucibleRecipes.Cast> casts) {
        List<CrucibleRecipes.Cast> out = new ArrayList<>(casts);
        for (NTMMaterial material : Mats.orderedList) {
            if (material.smeltable != NTMMaterial.SmeltingBehavior.SMELTABLE) continue; // as NTM's own list
            for (GtMolds.GtMold mold : GtMolds.all()) {
                ItemStack result = mold.getOutput(material);
                if (result == null) continue;
                out.add(new CrucibleRecipes.Cast(
                        ItemScraps.create(new Mats.MaterialStack(material, mold.getCost()), true),
                        new ItemStack(mold.item()),
                        new ItemStack(mold.size == ItemMold.SIZE_SMALL ? ModBlocks.FOUNDRY_MOLD.get()
                                : ModBlocks.FOUNDRY_BASIN.get()),
                        result));
            }
        }
        return out;
    }
}
