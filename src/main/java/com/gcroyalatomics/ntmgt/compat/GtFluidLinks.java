package com.gcroyalatomics.ntmgt.compat;

import com.gcroyalatomics.ntmgt.NtmGt;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.FluidProperty;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.fluids.store.FluidStorageKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fluids NTM and GT both have: GT's material uses NTM's fluid, so GT recipes take and give NTM's fluid and barrels,
 * pipes and machines of both mods share one fluid. GT still registers its own fluid (blocks, buckets, its bedrock
 * deposits and tags name them), but once registries are done the material points at NTM's: GT's is left without
 * recipes, like the GT items NTM items replace; GT's oil / gas bedrock deposits are overridden to NTM's fluids.
 * Only the same substance in the same role; NTM's oil fractions and GT's are different chains and stay apart.
 * Left out on purpose: hydrogen (NTM's is liquid at 13 K: GT would then demand cryo-proof pipes and cells for all
 * its hydrogen), steam, liquid lead and plant oils (owner's call pending).
 * Fluid tags: GT recipes look fluids up by tag c:<fluid path>, so data/c/tags/fluid has c:<NTM path> and
 * c:<GT name> for each (tools/gen_fluid_tags.py).
 */
public final class GtFluidLinks {
    private GtFluidLinks() {}

    /** NTM fluid path → GT material. */
    public static final Map<String, String> LINKS = Map.ofEntries(
            Map.entry("sulfuric_acid", "sulfuric_acid"),
            Map.entry("nitric_acid", "nitric_acid"),
            Map.entry("peroxide", "hydrogen_peroxide"),
            Map.entry("oxygen", "oxygen"),
            Map.entry("chlorine", "chlorine"),
            Map.entry("carbondioxide", "carbon_dioxide"),
            Map.entry("xenon", "xenon"),
            Map.entry("helium4", "helium"),
            Map.entry("helium3", "helium_3"),
            Map.entry("deuterium", "deuterium"),
            Map.entry("tritium", "tritium"),
            Map.entry("air", "air"),
            Map.entry("mercury", "mercury"),
            Map.entry("ethanol", "ethanol"),
            Map.entry("lubricant", "lubricant"),
            Map.entry("diesel", "diesel"),
            Map.entry("gasoline", "gasoline"),
            Map.entry("lpg", "lpg"),
            Map.entry("naphtha", "naphtha"),
            Map.entry("gas", "natural_gas"),
            Map.entry("oil", "oil"),
            Map.entry("fishoil", "fish_oil"),
            Map.entry("coalcreosote", "creosote"),
            Map.entry("uf6", "uranium_hexafluoride"),
            Map.entry("nitroglycerin", "glyceryl_trinitrate"),
            Map.entry("liquid_concrete", "concrete"));

    /** GT's own fluids the links replaced (registered, but no recipe uses them any more). */
    public static List<Fluid> replacedGtFluids() {
        List<Fluid> out = new ArrayList<>();
        if (!com.gcroyalatomics.ntmgt.NtmGtConfig.on(com.gcroyalatomics.ntmgt.NtmGtConfig.SHARED_FLUIDS)) return out;
        for (String gt : LINKS.values()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("gtceu", gt);
            if (BuiltInRegistries.FLUID.containsKey(id)) out.add(BuiltInRegistries.FLUID.get(id));
        }
        return out;
    }

    /** Their buckets (gtceu:<name>_bucket). */
    public static List<Item> replacedGtBuckets() {
        List<Item> out = new ArrayList<>();
        if (!com.gcroyalatomics.ntmgt.NtmGtConfig.on(com.gcroyalatomics.ntmgt.NtmGtConfig.SHARED_FLUIDS)) return out;
        for (String gt : LINKS.values()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("gtceu", gt + "_bucket");
            if (BuiltInRegistries.ITEM.containsKey(id)) out.add(BuiltInRegistries.ITEM.get(id));
        }
        return out;
    }

    /** Creative tabs: GT's replaced buckets are not shown (they stay as Material#getBucket's fallback icon). */
    public static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        var buckets = replacedGtBuckets();
        if (buckets.isEmpty()) return;
        List<net.minecraft.world.item.ItemStack> drop = new ArrayList<>();
        for (var stack : event.getParentEntries()) if (buckets.contains(stack.getItem())) drop.add(stack);
        for (var stack : event.getSearchEntries()) if (buckets.contains(stack.getItem()) && !drop.contains(stack)) drop.add(stack);
        for (var stack : drop) event.remove(stack, net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }

    /** Common setup (registries done, recipes not loaded yet). */
    public static void apply() {
        int linked = 0;
        for (var e : LINKS.entrySet()) {
            Material m = MaterialNames.gt(e.getValue());
            FluidProperty p = m == null ? null : m.getProperty(PropertyKey.FLUID);
            if (p == null || p.getPrimaryKey() == null) {
                NtmGt.LOGGER.warn("[ntmgt] GT material {} has no fluid, {} not linked", e.getValue(), e.getKey());
                continue;
            }
            FluidStorageKey key = p.getPrimaryKey();
            Object entry = p.getEntry(key);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("hbm", e.getKey());
            if (entry == null || !BuiltInRegistries.FLUID.containsKey(id)) {
                NtmGt.LOGGER.warn("[ntmgt] GT {} has no registered fluid or NTM has no {}", e.getValue(), id);
                continue;
            }
            var fluid = BuiltInRegistries.FLUID.get(id);
            ((com.gcroyalatomics.ntmgt.mixin.FluidEntryAccessor) entry).ntmgt$setFluid(() -> fluid);
            linked++;
        }
        NtmGt.LOGGER.info("[ntmgt] {} GT materials use NTM's fluid", linked);
    }
}
