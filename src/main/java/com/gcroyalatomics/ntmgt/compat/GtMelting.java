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
    /** Ore form -> {metal, stone} in NTM quanta (NTM's ore rows: ore 144 + 162 stone, raw 96 + 108 stone). */
    private static final Map<String, int[]> ORE_FORMS = Map.of(
            "raw", new int[]{96, 108},
            "raw_ore_block", new int[]{96 * 9, 108 * 9},
            "crushed_ore", new int[]{72, 36},
            "impure_dust", new int[]{72, 36},
            "purified_ore", new int[]{72, 0},
            "refined_ore", new int[]{72, 0},
            "pure_dust", new int[]{72, 0});
    private static final int[] ORE_BLOCK = {144, 162};

    private GtMelting() {}

    public static List<Mats.MaterialStack> of(ItemStack stack) {
        Item item = stack.getItem();
        String ns = BuiltInRegistries.ITEM.getKey(item).getNamespace();
        if (!"gtceu".equals(ns) && !"ntmgt".equals(ns)) return List.of(); // GT's items and the addon's GT materials

        List<MaterialStack> parts;
        MaterialEntry entry = ChemicalHelper.getMaterialEntry(item);
        if (entry != null && entry.material() != null) {
            int[] ore = oreAmounts(entry.tagPrefix());
            if (ore != null) return ore(entry.material(), ore);
            if (isOtherOreForm(entry.tagPrefix())) return List.of();
            parts = List.of(ChemicalHelper.getMaterialStack(entry));
        } else {
            ItemMaterialInfo info = ItemMaterialData.getMaterialInfo(item);
            if (info == null) return List.of();
            parts = info.getMaterials();
        }

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
        if (slag > 0) add(out, Mats.MAT_SLAG, slag);
        return out;
    }

    private static List<Mats.MaterialStack> ore(Material material, int[] amounts) {
        OreProperty ore = material.getProperty(PropertyKey.ORE);
        Material metal = ore != null && ore.getDirectSmeltResult() != null ? ore.getDirectSmeltResult().value() : material;
        NTMMaterial mat = MaterialNames.ntm(metal);
        if (mat == null || !smeltable(mat)) return List.of(); // gem / dust ores: GT processing only
        List<Mats.MaterialStack> out = new ArrayList<>(2);
        out.add(new Mats.MaterialStack(mat, amounts[0]));
        if (amounts[1] > 0) out.add(new Mats.MaterialStack(Mats.MAT_STONE, amounts[1]));
        return out;
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
        if (TagPrefix.ORES.containsKey(prefix)) return ORE_BLOCK;
        ResourceLocation id = GTRegistries.TAG_PREFIXES.getKey(prefix);
        return id == null ? null : ORE_FORMS.get(id.getPath());
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
