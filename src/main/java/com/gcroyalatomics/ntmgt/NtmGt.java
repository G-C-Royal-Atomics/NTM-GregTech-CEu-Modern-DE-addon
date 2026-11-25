package com.gcroyalatomics.ntmgt;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(NtmGt.MOD_ID)
public final class NtmGt {
    public static final String MOD_ID = "ntmgt";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NtmGt(IEventBus modBus, net.neoforged.fml.ModContainer container) {
        NtmGtConfig.register(container);
        if (NtmGtConfig.on(NtmGtConfig.NTM_METALS_AS_GT)) NtmGtNewMaterials.register();
        modBus.addListener(NtmGtMaterials::onPostMaterial);
        modBus.addListener(com.gcroyalatomics.ntmgt.compat.GtFluidLinks::onCreativeTab);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) ->
                e.enqueueWork(() -> {
                    if (NtmGtConfig.on(NtmGtConfig.SHARED_FLUIDS)) com.gcroyalatomics.ntmgt.compat.GtFluidLinks.apply();
                    com.gcroyalatomics.ntmgt.compat.GtNtmMats.recolor();
                }));
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    com.gcroyalatomics.ntmgt.compat.NtmFormulas::onTooltip);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    com.gcroyalatomics.ntmgt.compat.NtmFormulas::onTagsUpdated);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    com.gcroyalatomics.ntmgt.compat.NtmFluidTooltips::onTooltip);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    com.gcroyalatomics.ntmgt.compat.MoltenIdTooltip::onTooltip);
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
