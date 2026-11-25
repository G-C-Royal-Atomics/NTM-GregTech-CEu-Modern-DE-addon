package com.gcroyalatomics.ntmgt.mixin;

import com.gcroyalatomics.ntmgt.compat.GtPushOnly;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Remembers which block a duct face looks at, so the network can tell a GT block (see GtPushOnly). */
@Mixin(targets = "com.hbm.api.fluidmk2.FluidNetwork$FaceCache", remap = false)
public abstract class NtmFluidFaceCacheMixin implements GtPushOnly.Face {
    @Unique private ServerLevel ntmgt$level;
    @Unique private BlockPos ntmgt$pos;
    @Unique private Direction ntmgt$side;

    @Inject(method = "foreignCache", at = @At("HEAD"))
    private void ntmgt$where(ServerLevel level, BlockPos pos, Direction side, CallbackInfoReturnable<?> cir) {
        ntmgt$level = level;
        ntmgt$pos = pos.immutable();
        ntmgt$side = side;
    }

    @Override
    public boolean ntmgt$noPull() {
        return ntmgt$level != null && ntmgt$level.isLoaded(ntmgt$pos)
                && !GtPushOnly.ductMayTake(ntmgt$level.getBlockEntity(ntmgt$pos), ntmgt$side);
    }
}
