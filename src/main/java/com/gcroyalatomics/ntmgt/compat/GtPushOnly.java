package com.gcroyalatomics.ntmgt.compat;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * GT's rule for fluids: a machine, tank or pipe gives its fluid away only by pushing it (auto-output side, pipes),
 * nothing pulls from it. NTM's duct network pulls from every foreign tank it touches, so a GT super tank without
 * an output side emptied into an NTM duct. NTM ducts have no fluid handler of their own for GT to push into, so a
 * duct takes from a GT block only where GT would push: a GT pipe, or a machine/tank/drum on its enabled fluid output
 * side facing the duct. Filling GT blocks from ducts is not changed.
 */
public final class GtPushOnly {
    private GtPushOnly() {}

    public static boolean isGt(@Nullable BlockEntity be) {
        return be != null && be.getClass().getName().startsWith("com.gregtechceu.gtceu.");
    }

    /** May an NTM duct on {@code side} of this block take fluid from it? */
    public static boolean ductMayTake(@Nullable BlockEntity be, @Nullable Direction side) {
        if (!isGt(be)) return true;
        if (be instanceof com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity<?, ?>) return true;
        var machine = com.gregtechceu.gtceu.api.machine.MetaMachine.getMachine(be.getLevel(), be.getBlockPos());
        if (machine == null || side == null) return false;
        var out = machine.getTrait(com.gregtechceu.gtceu.common.machine.trait.AutoOutputTrait.class);
        return out != null && out.isAutoOutputFluids() && out.getFluidOutputDirection() == side;
    }

    /** Added to NTM's duct-network face cache (FluidNetwork$FaceCache) by a mixin. */
    public interface Face {
        boolean ntmgt$noPull();
    }
}
