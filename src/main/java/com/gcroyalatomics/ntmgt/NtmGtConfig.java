package com.gcroyalatomics.ntmgt;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * The addon's settings, in three files under config/:
 * <ul>
 * <li>ntmgt-startup.toml: what changes registration (new materials, item and fluid links); needs a game restart;</li>
 * <li>ntmgt-common.toml: game rules (crucible melting, slag, ores, GT molds, ducts, hazards); applied while running;</li>
 * <li>ntmgt-client.toml: tooltips and JEI.</li>
 * </ul>
 * Values read before their file is loaded give the default.
 */
public final class NtmGtConfig {
    private NtmGtConfig() {}

    // ------------------------------------------------------------------ startup

    private static final ModConfigSpec.Builder S = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue NTM_METALS_AS_GT = S
            .comment("NTM metals GT does not have (schrabidium, desh, ...) become GT materials, so GT makes rods,",
                    "gears, screws, foils, ... of them.")
            .define("materials.ntmMetalsAsGtMaterials", true);
    public static final ModConfigSpec.BooleanValue LINK_NTM_ITEMS = S
            .comment("NTM's ingots, plates, dusts, nuggets, blocks are GT's own items for the same material",
                    "(no GT duplicates; GT recipes use the NTM item).")
            .define("materials.linkNtmItemsIntoGt", true);
    public static final ModConfigSpec.BooleanValue GT_METALS_AS_NTM = S
            .comment("Every GT metal and alloy NTM does not have becomes an NTM material: it melts in the crucible,",
                    "flows in its GT colour and casts in NTM's and GT's molds.")
            .define("materials.gtMetalsAsNtmMaterials", true);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> GT_METALS_EXCLUDED = S
            .comment("GT materials (registry names, e.g. \"bronze\" or \"gtceu:bronze\") not made NTM materials.")
            .defineListAllowEmpty("materials.gtMetalsExcluded", List.of(), () -> "", o -> o instanceof String);
    public static final ModConfigSpec.BooleanValue SHARED_FLUIDS = S
            .comment("GT materials whose fluid NTM also has (oil, sulfuric acid, ...) use NTM's fluid.")
            .define("fluids.sharedFluids", true);
    public static final ModConfigSpec.BooleanValue GT_RADIATION_OFF = S
            .comment("GT's own radioactivity is removed from materials NTM irradiates (NTM's radiation applies).")
            .define("hazards.gtRadioactivityOff", true);
    public static final ModConfigSpec.BooleanValue GT_FORMULAS = S
            .comment("Chemical formulas for the NTM metals made GT materials (GT shows them under item names).")
            .define("materials.formulas", true);
    static final ModConfigSpec STARTUP = S.build();

    // ------------------------------------------------------------------ common

    private static final ModConfigSpec.Builder C = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue GT_ITEMS_MELT = C
            .comment("GT items NTM does not know (rods, gears, frames, machine parts, ...) melt in NTM's crucible",
                    "by GT's own composition.")
            .define("melting.gtItemsMelt", true);
    public static final ModConfigSpec.BooleanValue HOT_INGOTS_MELT = C
            .comment("GT hot ingots melt too (off: they only cool in GT's vacuum freezer).")
            .define("melting.hotIngotsMelt", false);
    public static final ModConfigSpec.BooleanValue UNMELTABLE_TO_SLAG = C
            .comment("The part of a GT item that does not melt (rubber, plastics, ...) becomes slag;",
                    "off: such items do not melt at all.")
            .define("melting.unmeltableToSlag", true);
    public static final ModConfigSpec.BooleanValue CABLE_INSULATION_SLAG = C
            .comment("A GT cable's rubber insulation (which GT leaves out of the cable's composition) becomes slag.")
            .define("melting.cableInsulationToSlag", true);
    public static final ModConfigSpec.BooleanValue ORES_MELT = C
            .comment("GT ores and ore products melt like NTM's ores (metal + stone).")
            .define("melting.ores.enabled", true);
    public static final ModConfigSpec.IntValue ORE_METAL = quanta("melting.ores.oreMetal", 144,
            "Metal from an ore block, in NTM quanta (72 = one ingot). NTM's own ores: 144.");
    public static final ModConfigSpec.IntValue ORE_STONE = quanta("melting.ores.oreStone", 162,
            "Stone from an ore block. NTM's own ores: 162.");
    public static final ModConfigSpec.IntValue RAW_METAL = quanta("melting.ores.rawMetal", 96,
            "Metal from a raw ore (the raw ore block: 9 times as much). NTM's own raw ores: 96.");
    public static final ModConfigSpec.IntValue RAW_STONE = quanta("melting.ores.rawStone", 108,
            "Stone from a raw ore. NTM's own raw ores: 108.");
    public static final ModConfigSpec.IntValue CRUSHED_METAL = quanta("melting.ores.crushedMetal", 72,
            "Metal from crushed ore and impure dust.");
    public static final ModConfigSpec.IntValue CRUSHED_STONE = quanta("melting.ores.crushedStone", 36,
            "Stone from crushed ore and impure dust.");
    public static final ModConfigSpec.IntValue CLEAN_METAL = quanta("melting.ores.cleanMetal", 72,
            "Metal from purified ore, refined ore and pure dust (no stone).");
    public static final ModConfigSpec.BooleanValue GT_MOLDS = C
            .comment("GT casting molds work in NTM's foundry mold, basin and strand caster.")
            .define("foundry.gtMoldsInNtmFoundry", true);
    public static final ModConfigSpec.BooleanValue DUCTS_OUTPUT_SIDE_ONLY = C
            .comment("NTM fluid ducts take from GT tanks and machines only on their set output side",
                    "(off: from any side, as NTM does with other mods).")
            .define("fluids.ductsTakeFromGtOutputSideOnly", true);
    public static final ModConfigSpec.BooleanValue GT_HAZARDS = C
            .comment("GT items get NTM's hazards (radiation, heat, ...) from their materials.",
                    "Applied on world load / /reload.")
            .define("hazards.ntmHazardsOnGtItems", true);
    static final ModConfigSpec COMMON = C.build();

    private static ModConfigSpec.IntValue quanta(String path, int def, String comment) {
        return C.comment(comment).defineInRange(path, def, 0, 100_000);
    }

    // ------------------------------------------------------------------ client

    private static final ModConfigSpec.Builder K = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ITEM_FORMULAS = K
            .comment("Chemical formulas under NTM item names.")
            .define("tooltips.itemFormulas", true);
    public static final ModConfigSpec.BooleanValue FLUID_TOOLTIPS = K
            .comment("Formula and temperature lines for NTM fluids (in containers, JEI, GT tanks).")
            .define("tooltips.fluidLines", true);
    public static final ModConfigSpec.ConfigValue<String> MATERIAL_IDS = K
            .comment("NTM material id under molten metal and scraps (JEI crucible/casting pages), the name NTM's",
                    "recipe files use ({\"material\": \"bronze\"}): \"advanced\" = with F3+H, \"always\", \"never\".")
            .define("tooltips.materialIds", "advanced", o -> o instanceof String v
                    && (v.equals("advanced") || v.equals("always") || v.equals("never")));
    public static final ModConfigSpec.BooleanValue JEI_HIDE_REPLACED = K
            .comment("JEI hides GT's fluids and buckets replaced by NTM's (fluids.sharedFluids).")
            .define("jei.hideReplacedGtFluids", true);
    public static final ModConfigSpec.BooleanValue JEI_HIDE_POURED = K
            .comment("JEI hides NTM's poured-in-the-world fluids that have the same name as the working fluid.")
            .define("jei.hideDuplicateNtmFluids", true);
    static final ModConfigSpec CLIENT = K.build();

    static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.STARTUP, STARTUP); // loaded right here: the mod constructor uses it
        container.registerConfig(ModConfig.Type.COMMON, COMMON);
        container.registerConfig(ModConfig.Type.CLIENT, CLIENT);
    }

    /** The value, or its default while its file is not loaded yet. */
    public static boolean on(ModConfigSpec.BooleanValue value) {
        return loaded(value) ? value.get() : value.getDefault();
    }

    public static int get(ModConfigSpec.IntValue value) {
        return loaded(value) ? value.get() : value.getDefault();
    }

    public static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        return loaded(value) ? value.get() : value.getDefault();
    }

    private static boolean loaded(ModConfigSpec.ConfigValue<?> value) {
        ModConfigSpec spec = value == null ? null : specOf(value);
        return spec != null && spec.isLoaded();
    }

    private static ModConfigSpec specOf(ModConfigSpec.ConfigValue<?> value) {
        if (STARTUP_VALUES.contains(value)) return STARTUP;
        if (CLIENT_VALUES.contains(value)) return CLIENT;
        return COMMON;
    }

    private static final List<ModConfigSpec.ConfigValue<?>> STARTUP_VALUES = List.of(NTM_METALS_AS_GT, LINK_NTM_ITEMS,
            GT_METALS_AS_NTM, GT_METALS_EXCLUDED, SHARED_FLUIDS, GT_RADIATION_OFF, GT_FORMULAS);
    private static final List<ModConfigSpec.ConfigValue<?>> CLIENT_VALUES = List.of(ITEM_FORMULAS, FLUID_TOOLTIPS, MATERIAL_IDS,
            JEI_HIDE_REPLACED, JEI_HIDE_POURED);
}
