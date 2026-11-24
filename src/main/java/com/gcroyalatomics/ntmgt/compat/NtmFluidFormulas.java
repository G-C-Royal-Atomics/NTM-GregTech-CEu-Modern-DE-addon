package com.gcroyalatomics.ntmgt.compat;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Chemical formulas for NTM fluids that have no GT material. Real substances carry their real formulas; fictional ones
 * are grounded on NTM's recipes or the closest real relatives (marked with "?"). Invented symbols: Bf (balefire),
 * Pn (pandemonium), Xp / Ed (experience / ender), overline = antimatter.
 */
public final class NtmFluidFormulas {

    private static final Map<String, String> FORMULAS = build(
            "sulfuric_acid_fluid", "H₂SO₄", "acid_fluid", "H₂SO₄", "toxic_fluid", "H₂O(Hg,Pb)?",
            "schrabidic_fluid", "H₂SaO₄", "schrabidic", "H₂SaO₄", "mud_fluid", "PbFe?", "watz_mud", "PbFe?",
            "volcanic_lava_fluid", "(SiO₂)(MgO)(FeO)?", "rad_lava_fluid", "(SiO₂)(MgO)(FeO)U?",
            "airblast", "gt:air", "heavywater", "D₂O", "heavywater_hot", "D₂O",
            "steam", "H₂O", "hotsteam", "H₂O", "superhotsteam", "H₂O", "ultrahotsteam", "H₂O", "spentsteam", "H₂O",
            "coolant", "H₂O(KNO₃)", "coolant_hot", "H₂O(KNO₃)", "cryogel", "H₂O(KNO₃)?",
            "perfluoromethyl", "CF₄", "perfluoromethyl_cold", "CF₄", "perfluoromethyl_hot", "CF₄",
            "mug", "H₂O(C₁₂H₂₂O₁₁)?", "mug_hot", "H₂O(C₁₂H₂₂O₁₁)?", "blood", "H₂O?", "blood_hot", "H₂O?",
            "sodium", "Na", "sodium_hot", "Na", "lead", "Pb", "lead_hot", "Pb",
            "thorium_salt", "(LiF)₂BeF₂ThF₄", "thorium_salt_hot", "(LiF)₂BeF₂ThF₄",
            "thorium_salt_depleted", "(LiF)₂BeF₂?", "hydrogen", "H₂",
            "oil_ds", "C₁₀H₂₂?", "crackoil", "C₁₀H₂₀?", "crackoil_ds", "C₁₀H₂₀?", "coaloil", "C₁₀H₈?",
            "oil_coker", "C₁₀H₂₀?", "hotoil", "gt:oil", "hotoil_ds", "C₁₀H₂₂?", "hotcrackoil", "C₁₀H₂₀?",
            "hotcrackoil_ds", "C₁₀H₂₀?", "heavyoil", "C₂₀H₄₂?", "heavyoil_vacuum", "C₂₀H₄₂?",
            "naphtha_ds", "C₇H₁₆", "naphtha_crack", "C₇H₁₄?", "naphtha_coker", "C₇H₁₄?", "reformate", "C₇H₈?",
            "lightoil", "C₁₂H₂₆?", "lightoil_ds", "C₁₂H₂₆", "lightoil_crack", "C₁₂H₂₄?", "lightoil_vacuum", "C₁₂H₂₆?",
            "bitumen", "C₃₀H₃₈?", "smear", "C₂₅H₅₂?", "heatingoil", "C₁₆H₃₄?", "heatingoil_vacuum", "C₂₀H₄₂?",
            "reclaimed", "C₂₅H₅₂?", "flue", "(N₂)₇(CO₂)(H₂O)?", "gas_coker", "CH₄?", "petroleum", "C₃H₈?",
            "sourgas", "CH₄(H₂S)", "syngas", "COH₂", "oxyhydrogen", "(H₂)₂O₂", "aromatics", "C₆H₆?",
            "unsaturateds", "C₂H₄?", "xylene", "C₈H₁₀", "reformgas", "H₂(CH₄)?", "diesel_reform", "C₁₆H₃₄",
            "diesel_crack", "C₁₂H₂₄?", "diesel_crack_reform", "C₁₆H₃₄?", "kerosene", "C₁₂H₂₆",
            "kerosene_reform", "C₁₂H₂₆?", "petroil", "C₂₅H₅₂?", "petroil_leaded", "C₂₅H₅₂Pb(C₂H₅)₄?",
            "gasoline_leaded", "C₈H₁₈Pb(C₂H₅)₄", "coalgas", "C₈H₁₈?", "coalgas_leaded", "C₈H₁₈Pb(C₂H₅)₄?",
            "woodoil", "C₇H₈O?", "biogas", "CH₄(CO₂)", "biofuel", "C₁₉H₃₆O₂", "sunfloweroil", "C₅₇H₁₀₄O₆",
            "nitan", "C₁₂H₂₆Hg?", "dhc", "C₈D₁₈?", "balefire_fuel", "Bf(C₁₂H₂₆)", "salient", "H₂O(C₃H₇NO₂)?",
            "seedslurry", "H₂O(C₆H₁₀O₅)ₙ?", "colloid", "(SiO₂)ₙ(H₂O)?", "vitriol", "FeSO₄(H₂O)₇",
            "slop", "H₂O(SiO₂)?", "iongel", "H₂O(H₂)?", "solvent", "C₇H₁₆(C₆H₆)", "radiosolvent", "C₂Cl₄?",
            "puf6", "PuF₆", "sas3", "SaS₃", "pain", "Pn(TaO₃)₃?", "death", "OsIr(H₂O₂)?",
            "redmud", "(Fe₂O₃)(Al₂O₃)(TiO₂)(NaOH)Hg?", "fullerene", "C₆₀(C₇H₈)", "egg", "H₂O(C₃H₇NO₂)?",
            "cholesterol", "C₂₇H₄₆O", "chlorocalcite_solution", "KCaCl₃(H₂O)", "chlorocalcite_mix", "KCaCl₃(H₂O)?",
            "chlorocalcite_cleaned", "KCaCl₃(H₂O)", "potassium_chloride", "KCl(H₂O)", "calcium_chloride", "CaCl₂(H₂O)",
            "calcium_solution", "Ca(OH)₂(H₂O)", "sodium_aluminate", "NaAlO₂", "bauxite_solution", "Al(OH)₃(NaOH)?",
            "alumina", "Al₂O₃", "fracksol", "H₂SO₄(C₃H₈)?", "lye", "NaOH", "phosgene", "COCl₂",
            "mustardgas", "C₄H₈Cl₂S", "estradiol", "C₁₈H₂₄O₂", "amat", "H̅", "aschrab", "S̅a̅³²⁶",
            "wastefluid", "H₂O(CsSrI)?", "wastegas", "XeKrI₂?", "xpjuice", "Xp(H₂O)", "enderjuice", "Ed(H₂O)?",
            "stellar_flux", "H₃He?", "plasma_dt", "DT", "plasma_hd", "HD", "plasma_ht", "HT", "plasma_dh3", "DHe³",
            "plasma_xm", "HeO", "plasma_bf", "Bf", "smoke", "C(CO₂)?", "smoke_leaded", "C(CO₂)PbO?",
            "smoke_poison", "C(CO₂)(SO₂)?", "pheromone", "C₁₆H₃₀O", "pheromone_m", "C₁₆H₃₀O(H₂O)?");

    private NtmFluidFormulas() {}

    private static Map<String, String> build(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    public static @Nullable String of(Fluid fluid) {
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        if (!"hbm".equals(id.getNamespace())) return null;
        String f = FORMULAS.get(id.getPath());
        if (f == null || !f.startsWith("gt:")) return f;
        Material mat = MaterialNames.gt(f.substring(3));
        return mat == null ? null : mat.getChemicalFormula();
    }
}
