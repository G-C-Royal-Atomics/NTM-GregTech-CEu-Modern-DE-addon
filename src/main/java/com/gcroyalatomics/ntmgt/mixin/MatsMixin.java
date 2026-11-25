package com.gcroyalatomics.ntmgt.mixin;

import com.gcroyalatomics.ntmgt.compat.GtMelting;
import com.hbm.inventory.material.Mats;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * NTM crucible (and everything else asking Mats): GT items NTM has no entry for melt by GT's composition, and so
 * do GT items NTM knows by tag whose composition has an unmeltable part (it becomes slag).
 */
@Mixin(value = Mats.class, remap = false)
public abstract class MatsMixin {
    @ModifyReturnValue(method = "getMaterialsFromItem", at = @At("RETURN"))
    private static List<Mats.MaterialStack> ntmgt$gtComposition(List<Mats.MaterialStack> list, ItemStack stack) {
        if (stack.isEmpty()) return list;
        List<Mats.MaterialStack> gt = GtMelting.of(stack);
        if (gt.isEmpty()) return list;
        // NTM knows the item by a c: tag (a GT cable is a wire to it): GT's composition still wins when it has
        // something unmeltable in it (the cable's rubber), so that part becomes slag.
        if (!list.isEmpty() && gt.stream().noneMatch(m -> m.material == Mats.MAT_SLAG)) return list;
        return new java.util.ArrayList<>(gt);
    }
}
