package com.gcroyalatomics.ntmgt.mixin;

import com.gcroyalatomics.ntmgt.compat.GtMelting;
import com.hbm.inventory.material.Mats;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/** NTM crucible (and everything else asking Mats): GT items NTM has no entry for melt by GT's composition. */
@Mixin(value = Mats.class, remap = false)
public abstract class MatsMixin {
    @ModifyReturnValue(method = "getMaterialsFromItem", at = @At("RETURN"))
    private static List<Mats.MaterialStack> ntmgt$gtComposition(List<Mats.MaterialStack> list, ItemStack stack) {
        if (!list.isEmpty() || stack.isEmpty()) return list;
        List<Mats.MaterialStack> gt = GtMelting.of(stack);
        return gt.isEmpty() ? list : new java.util.ArrayList<>(gt);
    }
}
