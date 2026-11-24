package com.gcroyalatomics.ntmgt.dev;

import com.hbm.hazard.HazardEntry;
import com.hbm.hazard.HazardSystem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.BufferedWriter;
import java.io.IOException;

/** Dev-only: NTM hazards of GT items next to NTM's own items of the same material (HazardSystem, NTM's own call). */
final class HazardSelfTest {
    private static final String[] ITEMS = {"hbm:ingot_u238", "hbm:ingot_u235", "hbm:ingot_pu239", "hbm:ingot_th232",
            "hbm:ingot_schrabidium", "hbm:powder_schrabidium", "hbm:ingot_polonium", "hbm:ingot_cobalt",
            "uranium_rod", "uranium_238_rod", "uranium_gear", "uranium_235_rod", "plutonium_239_plate",
            "plutonium_rod", "thorium_rod", "thorium_dust", "uraninite_dust", "pitchblende_dust",
            "crushed_uraninite_ore", "uraninite_ore", "polonium_rod", "radium_rod", "actinium_screw",
            "ntmgt:schrabidium_gear", "ntmgt:schrabidium_rod", "ntmgt:solinium_screw", "ntmgt:schraranium_foil",
            "ntmgt:ghiorsium_336_frame", "ntmgt:dura_steel_rod", "lithium_rod", "sodium_dust", "salt_dust",
            "lithium_chloride_dust", "strontium_dust", "steel_rod", "asbestos_dust", "uranium_hexafluoride_dust",
            "uranium_rhodium_dinaquadide_ingot"};

    private HazardSelfTest() {}

    static void run(BufferedWriter w) throws IOException {
        for (String name : ITEMS) {
            ResourceLocation id = name.contains(":") ? ResourceLocation.parse(name)
                    : ResourceLocation.fromNamespaceAndPath("gtceu", name);
            if (!BuiltInRegistries.ITEM.containsKey(id)) continue;
            w.write("hazard\t" + id + "\t" + describe(new ItemStack(BuiltInRegistries.ITEM.get(id))) + "\n");
        }
        for (String name : new String[] {"hbm:ingot_steel", "hbm:ingot_u238", "hbm:plate_copper", "hbm:ingot_schrabidium",
                "hbm:ingot_dura_steel", "hbm:ingot_desh", "hbm:ingot_saturnite", "hbm:ingot_red_copper",
                "hbm:ingot_bscco", "hbm:ingot_zirconium", "ntmgt:schrabidium_gear", "uranium_238_rod"}) {
            ResourceLocation id = name.contains(":") ? ResourceLocation.parse(name)
                    : ResourceLocation.fromNamespaceAndPath("gtceu", name);
            Item item = BuiltInRegistries.ITEM.get(id);
            var entry = com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper.getMaterialEntry(item);
            var gtHazard = com.gregtechceu.gtceu.api.data.chemical.material.properties.HazardProperty
                    .getValidHazardMaterial(new ItemStack(item));
            w.write("formula\t" + id + "\t" + (entry == null || entry.material() == null ? "(no GT material)"
                    : entry.material().getResourceLocation() + " " + entry.material().getChemicalFormula())
                    + "\tGT hazard: " + (gtHazard == null ? "-" : "yes") + "\n");
        }
        int gtRadioactive = 0;
        for (var m : com.gregtechceu.gtceu.api.registry.GTRegistries.MATERIALS) {
            var h = m.getProperty(com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey.HAZARD);
            if (h != null && h.hazardTrigger == com.gregtechceu.gtceu.api.data.chemical.material.properties
                    .HazardProperty.HazardTrigger.ANY) {
                gtRadioactive++;
                w.write("gt-any-hazard\t" + m.getResourceLocation() + "\t" + h.condition.unwrapKey().map(Object::toString).orElse("?") + "\n");
            }
        }
        w.write("gt-radioactive-materials\t" + gtRadioactive + "\n");
        int total = 0, hazardous = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            String ns = BuiltInRegistries.ITEM.getKey(item).getNamespace();
            if (!ns.equals("gtceu") && !ns.equals("ntmgt")) continue;
            total++;
            if (HazardSystem.isStackHazardous(new ItemStack(item))) hazardous++;
        }
        w.write("hazard-total\t" + hazardous + " of " + total + " gtceu+ntmgt items have an NTM hazard\n");
    }

    private static String describe(ItemStack stack) {
        StringBuilder sb = new StringBuilder();
        for (HazardEntry e : HazardSystem.getHazardsFromStack(stack)) {
            String type = e.type.getClass().getSimpleName().replace("HazardType", "").toLowerCase();
            sb.append(type).append('=').append(String.format(java.util.Locale.ROOT, "%.4g", e.baseLevel)).append(' ');
        }
        return sb.isEmpty() ? "-" : sb.toString().trim();
    }
}
