package com.gcroyalatomics.ntmgt;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(NtmGt.MOD_ID)
public final class NtmGt {
    public static final String MOD_ID = "ntmgt";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NtmGt(IEventBus modBus) {
        modBus.addListener(NtmGtMaterials::onPostMaterial);
        if (Boolean.getBoolean("ntmgt.selftest")) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    com.gcroyalatomics.ntmgt.dev.LinkSelfTest::onServerStarted);
        }
        if (Boolean.getBoolean("ntmgt.clienttest")) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    com.gcroyalatomics.ntmgt.dev.ClientJoinTest::onClientTick);
        }
    }
}
