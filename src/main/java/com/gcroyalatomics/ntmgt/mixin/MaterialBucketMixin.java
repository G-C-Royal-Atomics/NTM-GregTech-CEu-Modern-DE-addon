package com.gcroyalatomics.ntmgt.mixin;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Material#getBucket for a material whose fluid is NTM's now (GtFluidLinks): NTM's fluids mostly have no bucket,
 * and GT uses this bucket as an icon (JEI bedrock fluid page: oil, which threw and took GT's whole JEI plugin down)
 * and as a recipe result. Falls back to GT's own bucket of that material.
 */
@Mixin(value = Material.class, remap = false)
public abstract class MaterialBucketMixin {
    @ModifyReturnValue(method = "getBucket", at = @At("RETURN"))
    private Item ntmgt$gtBucket(Item bucket) {
        if (bucket != Items.AIR) return bucket;
        Material self = (Material) (Object) this;
        ResourceLocation own = ResourceLocation.fromNamespaceAndPath(self.getModid(), self.getName() + "_bucket");
        return BuiltInRegistries.ITEM.containsKey(own) ? BuiltInRegistries.ITEM.get(own) : bucket;
    }
}
