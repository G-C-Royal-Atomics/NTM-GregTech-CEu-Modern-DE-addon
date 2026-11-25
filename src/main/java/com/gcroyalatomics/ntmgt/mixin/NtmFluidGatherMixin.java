package com.gcroyalatomics.ntmgt.mixin;

import com.gcroyalatomics.ntmgt.compat.GtPushOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NTM's duct network does not pull from GT blocks; they push on their own (GtPushOnly). */
@Mixin(targets = "com.hbm.api.fluidmk2.FluidNetwork$FluidGather", remap = false)
public abstract class NtmFluidGatherMixin {
    @Unique private boolean ntmgt$gt;

    @Inject(method = "foreign", at = @At("HEAD"))
    private void ntmgt$face(@Coerce Object face, @Coerce Object endpoint, CallbackInfo ci) {
        ntmgt$gt = face instanceof GtPushOnly.Face f && f.ntmgt$noPull();
    }

    @ModifyVariable(method = "foreign", at = @At("STORE"), ordinal = 0)
    private long ntmgt$noPull(long available) {
        return ntmgt$gt ? 0L : available;
    }
}
