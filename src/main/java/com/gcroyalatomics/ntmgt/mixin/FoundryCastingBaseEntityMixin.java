package com.gcroyalatomics.ntmgt.mixin;

import com.gcroyalatomics.ntmgt.compat.GtMolds;
import com.hbm.blockentity.machine.BlockEntityFoundryCastingBase;
import com.hbm.items.machine.ItemMold;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Foundry mold and basin: a GT casting mold in the mold slot counts as an installed mold. */
@Mixin(value = BlockEntityFoundryCastingBase.class, remap = false)
public abstract class FoundryCastingBaseEntityMixin {
    @Inject(method = "getInstalledMold", at = @At("HEAD"), cancellable = true)
    private void ntmgt$gtMold(CallbackInfoReturnable<ItemMold.Mold> cir) {
        BlockEntityFoundryCastingBase self = (BlockEntityFoundryCastingBase) (Object) this;
        ItemMold.Mold mold = GtMolds.of(self.inventory.get(0));
        if (mold != null && mold.size == self.getMoldSize()) cir.setReturnValue(mold);
    }
}
