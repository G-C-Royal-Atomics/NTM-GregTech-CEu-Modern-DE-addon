package com.gcroyalatomics.ntmgt.compat;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import com.hbm.inventory.material.Mats;
import com.hbm.inventory.material.NTMMaterial;

import java.util.HashMap;
import java.util.Map;

/** NTM material names (c: tag paths, NTMMaterial#tagPath) -> GT material ids. Same ALIAS as tools/gen_links.py. */
public final class MaterialNames {
    private static final Map<String, String> ALIAS = Map.of(
            "aluminum", "aluminium",
            "pvc", "polyvinyl_chloride",
            "actinium_227", "actinium",
            "radium_226", "radium",
            "technetium_99", "technetium",
            "neptunium_237", "neptunium",
            "polonium_210", "polonium",
            "americium_241", "americium");

    private static final Map<String, String> INVERSE = new HashMap<>();

    static {
        ALIAS.forEach((ntm, gt) -> INVERSE.put(gt, ntm));
    }

    private MaterialNames() {}

    /** NTM material for a GT one, by name; null if NTM has no such material. */
    public static @Nullable NTMMaterial ntm(Material gt) {
        String name = gt.getName();
        return Mats.matByName.get(INVERSE.getOrDefault(name, name));
    }

    public static @Nullable Material gt(String ntmTagPath) {
        String name = ALIAS.getOrDefault(ntmTagPath, ntmTagPath);
        return GTRegistries.MATERIALS.get(ResourceLocation.fromNamespaceAndPath("gtceu", name));
    }
}
