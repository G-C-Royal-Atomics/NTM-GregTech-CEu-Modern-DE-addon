package com.gcroyalatomics.ntmgt.mixin;

import com.gcroyalatomics.ntmgt.compat.GtNtmMats;
import com.hbm.inventory.material.NTMMaterial;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** NTM materials made from GT ones (GtNtmMats) are named by GT's own translation, e.g. material.gtceu.bronze. */
@Mixin(value = NTMMaterial.class, remap = false)
public abstract class NtmMaterialNameMixin {
    @ModifyReturnValue(method = "getUnlocalizedName", at = @At("RETURN"))
    private String ntmgt$gtName(String name) {
        String gt = GtNtmMats.unlocalizedName((NTMMaterial) (Object) this);
        return gt != null ? gt : name;
    }
}
