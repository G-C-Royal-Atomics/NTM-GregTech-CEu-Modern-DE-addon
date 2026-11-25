package com.gcroyalatomics.ntmgt.compat;

import com.gcroyalatomics.ntmgt.NtmGt;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.hbm.inventory.material.Mats;
import com.hbm.inventory.material.NTMMaterial;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every GT metal and alloy (a material with an ingot; polymers excluded: rubber and plastics count as unmeltable)
 * that NTM does not have becomes an NTM material, smeltable, named and coloured as in GT. NTM's crucible, foundry
 * channels and molds then work with it like with NTM's own metals: GT's items carry the c: tags NTM reads
 * (c:ingots/bronze, c:plates/bronze, ...), so they melt into it and NTM's and GT's molds cast it back into GT's items.
 * An alloy melts into itself; NTM's crucible holds several molten materials side by side, there are no alloying
 * recipes for GT alloys.
 * <p>
 * Ids start at {@link #FIRST_ID} (NTM's own end at 20005), in GT registry-name order, so client and server agree.
 */
public final class GtNtmMats {
    public static final int FIRST_ID = 30_000;
    private static final Map<NTMMaterial, Material> GT = new IdentityHashMap<>();

    private GtNtmMats() {}

    /** PostMaterialEvent, after the NTM-named GT materials exist. Colours are set again in {@link #recolor()}. */
    public static void register() {
        List<Material> todo = new ArrayList<>();
        var excluded = com.gcroyalatomics.ntmgt.NtmGtConfig.get(com.gcroyalatomics.ntmgt.NtmGtConfig.GT_METALS_EXCLUDED);
        for (Material m : GTRegistries.MATERIALS) {
            if (!m.hasProperty(PropertyKey.INGOT) || m.hasProperty(PropertyKey.POLYMER)) continue;
            if (MaterialNames.ntm(m) != null) continue; // NTM has it (or it is one of NTM's metals made a GT material)
            if (excluded.contains(m.getName()) || excluded.contains(m.getResourceLocation().toString())) continue;
            todo.add(m);
        }
        todo.sort(Comparator.comparing(m -> m.getResourceLocation().toString()));
        int id = FIRST_ID;
        for (Material m : todo) {
            if (Mats.matByName.containsKey(m.getName())) continue; // same name from another GT namespace
            NTMMaterial mat = Mats.makeSmeltable(id++, Mats.df(m.getName()), 0xFFFFFF, 0x808080, 0xFFFFFF)
                    .namespace(m.getModid())
                    .m();
            GT.put(mat, m);
            color(mat, m);
        }
        NtmGt.LOGGER.info("[ntmgt] {} GT metals and alloys are NTM materials (crucible, foundry, molds)", GT.size());
    }

    /**
     * Common setup: GT works out averaged colours (alloys without a set colour) only after PostMaterialEvent,
     * so the colours are taken again once every material is final.
     */
    public static void recolor() {
        GT.forEach(GtNtmMats::color);
    }

    private static void color(NTMMaterial mat, Material m) {
        int light = m.getMaterialRGB();
        if (light == -1) return; // not worked out yet
        light &= 0xFFFFFF;
        int secondary = m.getMaterialSecondaryRGB();
        int dark = secondary == -1 || (secondary & 0xFFFFFF) == light ? darker(light) : secondary & 0xFFFFFF;
        mat.setSolidColor(light, dark);
        mat.setMoltenColor(light);
    }

    private static int darker(int rgb) {
        int r = (rgb >> 16 & 0xFF) * 2 / 5, g = (rgb >> 8 & 0xFF) * 2 / 5, b = (rgb & 0xFF) * 2 / 5;
        return r << 16 | g << 8 | b;
    }

    /** GT's name key for an NTM material made from a GT one, else null. */
    public static @Nullable String unlocalizedName(NTMMaterial mat) {
        Material m = GT.get(mat);
        return m == null ? null : m.getUnlocalizedName();
    }

    public static boolean isGt(NTMMaterial mat) {
        return GT.containsKey(mat);
    }
}
