package com.gcroyalatomics.ntmgt.mixin;

import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

/** GT's FluidStorage.FluidEntry (package-private): GtFluidLinks points a GT material at NTM's fluid. */
@Mixin(targets = "com.gregtechceu.gtceu.api.fluids.store.FluidStorage$FluidEntry", remap = false)
public interface FluidEntryAccessor {
    @Accessor("fluid")
    void ntmgt$setFluid(Supplier<? extends Fluid> fluid);
}
