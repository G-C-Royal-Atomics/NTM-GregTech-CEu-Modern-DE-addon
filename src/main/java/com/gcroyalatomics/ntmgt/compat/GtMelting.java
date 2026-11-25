package com.gcroyalatomics.ntmgt.compat;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.ItemMaterialData;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.OreProperty;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.ItemMaterialInfo;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialEntry;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialStack;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.hbm.inventory.material.Mats;
import com.hbm.inventory.material.MaterialShapes;
import com.hbm.inventory.material.NTMMaterial;
import com.gcroyalatomics.ntmgt.NtmGtConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What a GT item melts into in the NTM crucible, for the items NTM itself does not know (rods, gears, screws, foils,
 * rings, springs, rotors, pipes, frames, machine parts, ores, ...). The composition is GT's own (the same numbers GT
 * uses for its arc furnace / macerator recycling): one ingot (M) = INGOT.q(1) NTM quanta. Every GT metal and alloy is
 * an NTM material (GtNtmMats); what does not melt (rubber, plastics, gems, dusts NTM has no metal for) turns into the
 * same amount of slag. An item with no metal in it does not melt.
 * <p>
 * GT ores melt as NTM's own: an ore block gives NTM's ore amounts (2 ingots and stone), raw ore and the raw ore
 * block NTM's raw amounts; crushed ore and impure dust give an ingot with some stone, the cleaned forms a clean
 * ingot. The metal is GT's direct smelting result where GT has one (chalcopyrite -> copper), else the ore's own.
 */
public final class GtMelting {
    private static boolean on(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue v) {
        return NtmGtConfig.on(v);
    }

    private static int get(net.neoforged.neoforge.common.ModConfigSpec.IntValue v) {
        return NtmGtConfig.get(v);
    }

    /** Ore form -> its {metal, stone} amounts in NTM quanta (config melting.ores; NTM's ore rows by default). */
    private static int @Nullable [] oreForm(String path) {
        return switch (path) {
            case "raw" -> new int[]{get(NtmGtConfig.RAW_METAL), get(NtmGtConfig.RAW_STONE)};
            case "raw_ore_block" -> new int[]{9 * get(NtmGtConfig.RAW_METAL), 9 * get(NtmGtConfig.RAW_STONE)};
            case "crushed_ore", "impure_dust" -> new int[]{get(NtmGtConfig.CRUSHED_METAL), get(NtmGtConfig.CRUSHED_STONE)};
            case "purified_ore", "refined_ore", "pure_dust" -> new int[]{get(NtmGtConfig.CLEAN_METAL), 0};
            default -> null;
        };
    }
    /** Cable size -> rubber ingots of insulation (GT's WireRecipeHandler). */
    private static final Map<String, Integer> CABLE_INSULATION = Map.of("cable_gt_single", 1, "cable_gt_double", 1,
            "cable_gt_quadruple", 2, "cable_gt_octal", 3, "cable_gt_hex", 5);

    private GtMelting() {}

    public static List<Mats.MaterialStack> of(ItemStack stack) {
        Item item = stack.getItem();
        String ns = BuiltInRegistries.ITEM.getKey(item).getNamespace();
        if (!"gtceu".equals(ns) && !"ntmgt".equals(ns)) return List.of(); // GT's items and the addon's GT materials
        if (!on(NtmGtConfig.GT_ITEMS_MELT)) return List.of();

        List<MaterialStack> parts;
        MaterialEntry entry = ChemicalHelper.getMaterialEntry(item);
        if (entry != null && entry.material() != null) {
            int[] ore = oreAmounts(entry.tagPrefix());
            if (ore != null) return on(NtmGtConfig.ORES_MELT) ? ore(entry.material(), ore) : List.of();
            if (isOtherOreForm(entry.tagPrefix())) return List.of();
            if (isHotIngot(entry.tagPrefix()) && !on(NtmGtConfig.HOT_INGOTS_MELT)) return List.of(); // cools in GT's vacuum freezer, not melted again
        }
        // GT's full recycling composition first (a cable: its wire's metal and the rubber insulation),
        // else the item's own material (rod, gear, ...).
        ItemMaterialInfo info = ItemMaterialData.getMaterialInfo(item);
        if (info != null && !info.getMaterials().isEmpty()) parts = info.getMaterials();
        else if (entry != null && entry.material() != null) parts = List.of(ChemicalHelper.getMaterialStack(entry));
        else return List.of();

        List<Mats.MaterialStack> out = new ArrayList<>(parts.size() + 1);
        int slag = 0;
        for (MaterialStack part : parts) {
            if (part.material() == null || part.amount() <= 0) continue;
            int quanta = (int) (part.amount() * MaterialShapes.INGOT.q(1) / GTValues.M);
            if (quanta <= 0) continue;
            NTMMaterial mat = MaterialNames.ntm(part.material());
            if (mat != null && smeltable(mat)) add(out, mat, quanta);
            else slag += quanta;
        }
        if (out.isEmpty()) return List.of(); // nothing in it melts
        if (entry != null && on(NtmGtConfig.CABLE_INSULATION_SLAG)) slag += insulation(entry.tagPrefix());
        if (slag > 0 && !on(NtmGtConfig.UNMELTABLE_TO_SLAG)) return List.of(); // part of it would not melt
        if (slag > 0) add(out, Mats.MAT_SLAG, slag);
        return out;
    }

    private static List<Mats.MaterialStack> ore(Material material, int[] amounts) {
        OreProperty ore = material.getProperty(PropertyKey.ORE);
        Material metal = ore != null && ore.getDirectSmeltResult() != null ? ore.getDirectSmeltResult().value() : material;
        NTMMaterial mat = MaterialNames.ntm(metal);
        if (mat == null || !smeltable(mat)) return List.of(); // gem / dust ores: GT processing only
        if (amounts[0] <= 0) return List.of();
        List<Mats.MaterialStack> out = new ArrayList<>(2);
        out.add(new Mats.MaterialStack(mat, amounts[0]));
        if (amounts[1] > 0) out.add(new Mats.MaterialStack(Mats.MAT_STONE, amounts[1]));
        return out;
    }

    /**
     * A cable's insulation, in quanta: GT coats cables with rubber fluid (WireRecipeHandler.INSULATION_AMOUNT
     * ingots of it per cable size) and does not count it in the cable's composition.
     */
    private static int insulation(TagPrefix prefix) {
        ResourceLocation id = GTRegistries.TAG_PREFIXES.getKey(prefix);
        int ingots = id == null ? 0 : CABLE_INSULATION.getOrDefault(id.getPath(), 0);
        return ingots * MaterialShapes.INGOT.q(1);
    }

    private static void add(List<Mats.MaterialStack> out, NTMMaterial mat, int quanta) {
        for (Mats.MaterialStack s : out) {
            if (s.material == mat) {
                s.amount += quanta;
                return;
            }
        }
        out.add(new Mats.MaterialStack(mat, quanta));
    }

    private static int @Nullable [] oreAmounts(TagPrefix prefix) {
        if (TagPrefix.ORES.containsKey(prefix)) return new int[]{get(NtmGtConfig.ORE_METAL), get(NtmGtConfig.ORE_STONE)};
        ResourceLocation id = GTRegistries.TAG_PREFIXES.getKey(prefix);
        return id == null ? null : oreForm(id.getPath());
    }

    private static boolean isHotIngot(TagPrefix prefix) {
        ResourceLocation id = GTRegistries.TAG_PREFIXES.getKey(prefix);
        return id != null && id.getPath().equals("hot_ingot");
    }

    /** Indicator rocks: not ore to melt. */
    private static boolean isOtherOreForm(TagPrefix prefix) {
        ResourceLocation id = GTRegistries.TAG_PREFIXES.getKey(prefix);
        return id != null && (id.getPath().equals("rock") || id.getPath().equals("surface_rock"));
    }

    private static boolean smeltable(NTMMaterial mat) {
        NTMMaterial.SmeltingBehavior b = mat.smeltsInto.smeltable;
        return b == NTMMaterial.SmeltingBehavior.SMELTABLE || b == NTMMaterial.SmeltingBehavior.ADDITIVE;
    }
}
