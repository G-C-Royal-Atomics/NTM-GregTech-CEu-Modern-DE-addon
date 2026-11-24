package com.gcroyalatomics.ntmgt.compat;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.ItemMaterialData;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What a GT item melts into in the NTM crucible, for the items NTM itself does not know (rods, gears, screws, foils,
 * rings, springs, rotors, pipes, frames, machine parts, ...). The composition is GT's own (the same numbers GT uses
 * for its arc furnace / macerator recycling): one ingot (M) = INGOT.q(1) NTM quanta.
 * An item melts only if every material in it is a smeltable NTM material; ore-processing forms never melt
 * (the crucible must not become a shortcut past ore processing).
 */
public final class GtMelting {
    private static final Set<String> ORE_FORMS = Set.of("raw", "raw_ore_block", "refined_ore", "purified_ore",
            "crushed_ore", "impure_dust", "pure_dust", "rock", "surface_rock");

    private GtMelting() {}

    public static List<Mats.MaterialStack> of(ItemStack stack) {
        Item item = stack.getItem();
        String ns = BuiltInRegistries.ITEM.getKey(item).getNamespace();
        if (!"gtceu".equals(ns) && !"ntmgt".equals(ns)) return List.of(); // GT's items and the addon's GT materials

        List<MaterialStack> parts;
        MaterialEntry entry = ChemicalHelper.getMaterialEntry(item);
        if (entry != null && entry.material() != null) {
            if (isOreForm(entry.tagPrefix())) return List.of();
            parts = List.of(ChemicalHelper.getMaterialStack(entry));
        } else {
            ItemMaterialInfo info = ItemMaterialData.getMaterialInfo(item);
            if (info == null) return List.of();
            parts = info.getMaterials();
        }

        List<Mats.MaterialStack> out = new ArrayList<>(parts.size());
        for (MaterialStack part : parts) {
            if (part.material() == null || part.amount() <= 0) continue;
            NTMMaterial mat = MaterialNames.ntm(part.material());
            if (mat == null || !smeltable(mat)) return List.of();
            int quanta = (int) (part.amount() * MaterialShapes.INGOT.q(1) / GTValues.M);
            if (quanta > 0) out.add(new Mats.MaterialStack(mat, quanta));
        }
        return out;
    }

    private static boolean isOreForm(TagPrefix prefix) {
        if (TagPrefix.ORES.containsKey(prefix)) return true;
        ResourceLocation id = GTRegistries.TAG_PREFIXES.getKey(prefix);
        return id != null && ORE_FORMS.contains(id.getPath());
    }

    private static boolean smeltable(NTMMaterial mat) {
        NTMMaterial.SmeltingBehavior b = mat.smeltsInto.smeltable;
        return b == NTMMaterial.SmeltingBehavior.SMELTABLE || b == NTMMaterial.SmeltingBehavior.ADDITIVE;
    }
}
