package com.gcroyalatomics.ntmgt.compat;

import com.gcroyalatomics.ntmgt.NtmGt;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/** JEI (optional): hides GT's own fluids (and their buckets) that GtFluidLinks replaced with NTM's; no recipe uses them. */
@JeiPlugin
public final class NtmGtJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(NtmGt.MOD_ID, "jei");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        var manager = runtime.getIngredientManager();
        var fluids = GtFluidLinks.replacedGtFluids().stream().map(f -> new FluidStack(f, 1000)).toList();
        var buckets = GtFluidLinks.replacedGtBuckets().stream().map(ItemStack::new).toList();
        if (!fluids.isEmpty()) manager.removeIngredientsAtRuntime(NeoForgeTypes.FLUID_STACK, fluids);
        if (!buckets.isEmpty()) manager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, buckets);
    }
}
