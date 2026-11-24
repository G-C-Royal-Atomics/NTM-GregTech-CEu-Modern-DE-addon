package com.gcroyalatomics.ntmgt.mixin;

import com.gcroyalatomics.ntmgt.compat.GtMolds;
import com.hbm.inventory.container.MenuMachineStrandCaster;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Predicate;

/** Strand caster GUI: the mold slot also takes GT casting molds. */
@Mixin(value = MenuMachineStrandCaster.class, remap = false)
public abstract class StrandCasterMenuMixin {
    @ModifyArg(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/Container;Lcom/hbm/blockentity/machine/BlockEntityMachineStrandCaster;)V",
            at = @At(value = "INVOKE", target = "Lcom/hbm/inventory/slot/SlotFiltered;<init>(Lnet/minecraft/world/Container;IIILjava/util/function/Predicate;)V", ordinal = 0),
            index = 4)
    private Predicate<ItemStack> ntmgt$moldFilter(Predicate<ItemStack> filter) {
        return filter.or(GtMolds::is);
    }
}
