package com.gcroyalatomics.ntmgt.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;

/**
 * NTM's universal bucket (hbm:fluid_bucket) emptied through the item fluid capability (NTM barrels, GT machines,
 * cells) stayed an hbm:fluid_bucket with no fluid: white contents, no recipes, does not stack. NTM's own placing
 * code and its craft remainder give a vanilla bucket, so the capability does the same now.
 */
@Mixin(value = com.hbm.items.machine.ItemFluidBucket.class, remap = false)
public abstract class NtmFluidBucketEmptyMixin {
    public ItemStack emptyContainer(ItemStack stack) {
        return new ItemStack(Items.BUCKET);
    }
}
