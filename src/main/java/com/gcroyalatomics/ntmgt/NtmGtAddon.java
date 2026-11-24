package com.gcroyalatomics.ntmgt;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

@GTAddon(NtmGt.MOD_ID)
public final class NtmGtAddon implements IGTAddon {
    @Override
    public GTRegistrate getRegistrate() {
        return NtmGtNewMaterials.REGISTRATE;
    }
}
