package com.gcroyalatomics.ntmgt.dev;

import com.hbm.inventory.material.Mats;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;

/** Dev-only: what GT items melt into in the NTM crucible (Mats.getSmeltingMaterialsFromItem, the crucible's call). */
final class MeltSelfTest {
    private static final String[] ITEMS = {"steel_rod", "long_steel_rod", "steel_gear", "small_steel_gear",
            "steel_screw", "steel_bolt", "steel_foil", "steel_ring", "steel_spring", "small_steel_spring",
            "steel_rotor", "steel_normal_fluid_pipe", "steel_frame", "lv_machine_hull", "lv_machine_casing",
            "iron_rod", "copper_foil", "titanium_gear", "aluminium_rod", "stainless_steel_rod", "lead_rotor",
            "crushed_copper_ore", "copper_crushed_ore", "tungsten_steel_rod", "copper_single_wire"};

    private MeltSelfTest() {}

    static void run(BufferedWriter w) throws IOException {
        for (String name : ITEMS) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("gtceu", name);
            if (!BuiltInRegistries.ITEM.containsKey(id)) {
                w.write("melt\t" + name + "\t(no such item)\n");
                continue;
            }
            w.write("melt\t" + name + "\t" + describe(new ItemStack(BuiltInRegistries.ITEM.get(id))) + "\n");
        }
        int melts = 0, total = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (!BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("gtceu")) continue;
            total++;
            if (!Mats.getSmeltingMaterialsFromItem(new ItemStack(item)).isEmpty()) melts++;
        }
        w.write("melt-total\t" + melts + " of " + total + " gtceu items melt in the NTM crucible\n");
    }

    private static String describe(ItemStack stack) {
        List<Mats.MaterialStack> mats = Mats.getSmeltingMaterialsFromItem(stack);
        if (mats.isEmpty()) return "-";
        StringBuilder sb = new StringBuilder();
        for (Mats.MaterialStack m : mats) sb.append(m.material.tagPath).append('=').append(m.amount).append(' ');
        return sb.toString().trim();
    }
}
