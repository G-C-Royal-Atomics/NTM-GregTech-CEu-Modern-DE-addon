package com.gcroyalatomics.ntmgt;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlag;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconSet;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.DustProperty;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.FluidProperty;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.IngotProperty;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.fluids.FluidBuilder;
import com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags.*;

/**
 * NTM metals GT does not have become GT materials (namespace ntmgt), so GT makes rods, gears, screws, foils, rings,
 * springs, frames, rotors, fine wire and a liquid from them and its machines work with them. Their ingots, plates,
 * dusts, nuggets and blocks are NTM's items (linked in NtmGtMaterials). GT element materials NTM has ingots of
 * (zirconium, polonium, ...) get an ingot, a liquid and the same parts.
 */
public final class NtmGtNewMaterials {
    public static final GTRegistrate REGISTRATE = GTRegistrate.create(NtmGt.MOD_ID);

    static final MaterialFlag[] PARTS = {GENERATE_PLATE, GENERATE_ROD, GENERATE_LONG_ROD, GENERATE_BOLT_SCREW,
            GENERATE_GEAR, GENERATE_SMALL_GEAR, GENERATE_FOIL, GENERATE_RING, GENERATE_SPRING, GENERATE_SPRING_SMALL,
            GENERATE_FRAME, GENERATE_ROTOR, GENERATE_FINE_WIRE};

    /** name (= NTM material tag path), solid colour light, solid colour dark (NTM's own colours). */
    private record Metal(String name, int color, int secondary, boolean shiny) {}

    private static final List<Metal> NEW = List.of(
            new Metal("schrabidium", 0x32FFFF, 0x005C5C, true),
            new Metal("solinium", 0xA2E6E0, 0x00433D, true),
            new Metal("schrabidate", 0x77C0D7, 0x39005E, false),
            new Metal("schraranium", 0x24AFAC, 0x2B3227, true),
            new Metal("ghiorsium_336", 0xF4EFE1, 0x2A3306, true),
            new Metal("mingrade", 0xFFBA7D, 0xAF1700, false),
            new Metal("dura_steel", 0x82A59C, 0x06281E, false),
            new Metal("workers_alloy", 0xFF6D6D, 0x720000, false),
            new Metal("starmetal", 0xCCCCEA, 0x11111A, true),
            new Metal("ferrouranium", 0xB7B7C9, 0x101022, false),
            new Metal("tc_alloy", 0xD4D6D6, 0x323D3D, false),
            new Metal("cd_alloy", 0xF7DF8F, 0x604308, false),
            new Metal("arsenic_bronze", 0xDB9462, 0x203331, false),
            new Metal("bscco", 0x767BF1, 0x000000, false),
            new Metal("magnetized_tungsten", 0x22A2A2, 0x0F0F0F, true),
            new Metal("cmbsteel", 0x6F6FB4, 0x000011, false),
            new Metal("dineutronium", 0x7582B9, 0x16000E, true),
            new Metal("gun_metal", 0xFFEF3F, 0xAD3600, false),
            new Metal("weapon_steel", 0xA0A0A0, 0x000000, false),
            new Metal("saturnite", 0x3AC4DA, 0x09282C, true));

    /** GT element materials without solid forms that NTM makes ingots of. */
    private static final List<String> GT_ELEMENTS_INGOT = List.of("actinium", "arsenic", "boron", "cadmium",
            "calcium", "lanthanum", "lithium", "neptunium", "polonium", "radium", "technetium", "zirconium");
    /** GT element materials without solid forms that NTM has (only) dust of. */
    private static final List<String> GT_ELEMENTS_DUST = List.of("astatine", "strontium", "tennessine");

    private NtmGtNewMaterials() {}

    /** Called from the mod constructor: queues the materials into GT's material registration. */
    static void register() {
        for (Metal m : NEW) {
            REGISTRATE.material(m.name(), b -> b
                    .ingot()
                    .liquid()
                    .color(m.color()).secondaryColor(m.secondary())
                    .iconSet(m.shiny() ? MaterialIconSet.SHINY : MaterialIconSet.METALLIC)
                    .flags(PARTS));
        }
    }

    /** PostMaterialEvent, before the NTM items are linked: give GT elements the forms NTM has. */
    static void extendGtElements() {
        for (String name : GT_ELEMENTS_INGOT) {
            Material m = gt(name);
            if (m == null) continue;
            if (!m.hasProperty(PropertyKey.DUST)) m.setProperty(PropertyKey.DUST, new DustProperty());
            if (!m.hasProperty(PropertyKey.INGOT)) m.setProperty(PropertyKey.INGOT, new IngotProperty());
            if (!m.hasProperty(PropertyKey.FLUID)) {
                m.setProperty(PropertyKey.FLUID, new FluidProperty(FluidStorageKeys.LIQUID, new FluidBuilder()));
            }
            m.addFlags(PARTS);
        }
        for (String name : GT_ELEMENTS_DUST) {
            Material m = gt(name);
            if (m != null && !m.hasProperty(PropertyKey.DUST)) m.setProperty(PropertyKey.DUST, new DustProperty());
        }
    }

    private static Material gt(String name) {
        Material m = GTRegistries.MATERIALS.get(ResourceLocation.fromNamespaceAndPath("gtceu", name));
        if (m == null) NtmGt.LOGGER.warn("[ntmgt] GT material {} not found", name);
        return m;
    }
}
