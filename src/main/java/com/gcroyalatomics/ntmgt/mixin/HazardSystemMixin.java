package com.gcroyalatomics.ntmgt.mixin;

import com.gcroyalatomics.ntmgt.compat.GtHazards;
import com.hbm.hazard.HazardSystem;
import net.minecraft.core.RegistryAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** After NTM loads its hazard data (server start), give GT items NTM hazards by their composition. */
@Mixin(value = HazardSystem.class, remap = false)
public abstract class HazardSystemMixin {
    @Inject(method = "applyDataPack", at = @At("RETURN"))
    private static void ntmgt$gtHazards(RegistryAccess registries, CallbackInfo ci) {
        GtHazards.apply();
    }
}
