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
        NtmGtNewMaterials.register();
        modBus.addListener(NtmGtMaterials::onPostMaterial);
        modBus.addListener(com.gcroyalatomics.ntmgt.compat.GtFluidLinks::onCreativeTab);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) ->
                e.enqueueWork(com.gcroyalatomics.ntmgt.compat.GtFluidLinks::apply));
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    com.gcroyalatomics.ntmgt.compat.NtmFormulas::onTooltip);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    com.gcroyalatomics.ntmgt.compat.NtmFormulas::onTagsUpdated);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    com.gcroyalatomics.ntmgt.compat.NtmFluidTooltips::onTooltip);
        }
        if (Boolean.getBoolean("ntmgt.selftest")) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    com.gcroyalatomics.ntmgt.dev.LinkSelfTest::onServerStarted);
        }
        if (Boolean.getBoolean("ntmgt.moldstand")) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    com.gcroyalatomics.ntmgt.dev.MoldStand::onClientTick);
        }
        if (Boolean.getBoolean("ntmgt.clienttest")) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    com.gcroyalatomics.ntmgt.dev.ClientJoinTest::onClientTick);
        }
    }
}
