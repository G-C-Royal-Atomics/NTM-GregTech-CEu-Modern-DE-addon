package com.gcroyalatomics.ntmgt.compat;

import com.gcroyalatomics.ntmgt.NtmGt;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.HazardProperty;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

/**
 * Radiation is NTM's (GtHazards): GT's own radioactivity is removed. GT marks radioactive materials with
 * MaterialBuilder#radioactiveHazard = a HAZARD property, trigger ANY, condition carcinogen, passed on to compounds;
 * that property is dropped (uranium, plutonium, thorium, ... and everything made of them, NTM's linked items too,
 * since GT applies hazards to all items). GT's chemical hazards (asbestos, benzene, chromium skin contact, ...)
 * stay. GT's machine radiation is its environmental hazards, off unless gameplay.environmentalHazards is set.
 */
public final class GtRadiationOff {
    private GtRadiationOff() {}

    static boolean isRadioactivity(HazardProperty hazard) {
        return hazard.hazardTrigger == HazardProperty.HazardTrigger.ANY
                && hazard.condition.unwrapKey().map(k -> k.location().getPath().equals("carcinogen")).orElse(false);
    }

    /** PostMaterialEvent. */
    public static void apply() {
        int removed = 0;
        for (Material m : GTRegistries.MATERIALS) {
            HazardProperty hazard = m.getProperty(PropertyKey.HAZARD);
            if (hazard != null && isRadioactivity(hazard)) {
                m.removeProperty(PropertyKey.HAZARD);
                removed++;
            }
        }
        NtmGt.LOGGER.info("[ntmgt] GT radioactivity removed from {} materials (NTM radiation applies instead)", removed);
    }
}
