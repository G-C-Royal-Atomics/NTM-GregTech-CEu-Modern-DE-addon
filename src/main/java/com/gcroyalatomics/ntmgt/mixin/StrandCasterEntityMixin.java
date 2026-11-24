package com.gcroyalatomics.ntmgt.mixin;

import com.gcroyalatomics.ntmgt.compat.GtMolds;
import com.hbm.blockentity.machine.BlockEntityMachineStrandCaster;
import com.hbm.items.machine.ItemMold;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Strand caster: accepts and uses GT casting molds like NTM molds. */
@Mixin(value = BlockEntityMachineStrandCaster.class, remap = false)
public abstract class StrandCasterEntityMixin {
    @Inject(method = "getInstalledMold", at = @At("HEAD"), cancellable = true)
    private void ntmgt$gtMold(CallbackInfoReturnable<ItemMold.Mold> cir) {
        BlockEntityMachineStrandCaster self = (BlockEntityMachineStrandCaster) (Object) this;
        ItemMold.Mold mold = GtMolds.of(self.inventory.get(BlockEntityMachineStrandCaster.SLOT_MOLD));
        if (mold != null) cir.setReturnValue(mold);
    }

    @Inject(method = "canPlaceItem", at = @At("HEAD"), cancellable = true)
    private void ntmgt$acceptGtMold(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slot == BlockEntityMachineStrandCaster.SLOT_MOLD && GtMolds.is(stack)) cir.setReturnValue(true);
    }
}
