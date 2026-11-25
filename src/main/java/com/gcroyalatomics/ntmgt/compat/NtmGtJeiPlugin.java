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
        if (!com.gcroyalatomics.ntmgt.NtmGtConfig.on(com.gcroyalatomics.ntmgt.NtmGtConfig.JEI_HIDE_REPLACED)) {
            hidePoured(manager);
            return;
        }
        var fluids = GtFluidLinks.replacedGtFluids().stream().map(f -> new FluidStack(f, 1000)).toList();
        var buckets = GtFluidLinks.replacedGtBuckets().stream().map(ItemStack::new).toList();
        if (!fluids.isEmpty()) manager.removeIngredientsAtRuntime(NeoForgeTypes.FLUID_STACK, fluids);
        if (!buckets.isEmpty()) manager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, buckets);
        hidePoured(manager);
    }

    private static void hidePoured(mezz.jei.api.runtime.IIngredientManager manager) {
        if (!com.gcroyalatomics.ntmgt.NtmGtConfig.on(com.gcroyalatomics.ntmgt.NtmGtConfig.JEI_HIDE_POURED)) return;
        // NTM's poured-in-the-world fluids (sulfuric_acid_fluid, schrabidic_fluid, mud_fluid) under the very name of
        // the working fluid (sulfuric_acid, schrabidic, watz_mud): same name twice in JEI, owner asked to hide them
        var byName = new java.util.HashMap<String, java.util.List<FluidStack>>();
        for (FluidStack fs : manager.getAllIngredients(NeoForgeTypes.FLUID_STACK)) {
            if (!net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fs.getFluid()).getNamespace().equals("hbm"))
                continue;
            byName.computeIfAbsent(fs.getHoverName().getString(), k -> new java.util.ArrayList<>()).add(fs);
        }
        var world = new java.util.ArrayList<FluidStack>();
        for (var group : byName.values()) {
            var poured = group.stream().filter(fs -> fs.getFluid() instanceof com.hbm.blocks.fluid.ClassicFluid).toList();
            if (!poured.isEmpty() && poured.size() < group.size()) world.addAll(poured);
        }
        if (!world.isEmpty()) manager.removeIngredientsAtRuntime(NeoForgeTypes.FLUID_STACK, world);
    }
}
