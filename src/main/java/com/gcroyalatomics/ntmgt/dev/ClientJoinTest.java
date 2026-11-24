package com.gcroyalatomics.ntmgt.dev;

import com.gcroyalatomics.ntmgt.NtmGt;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Dev-only (-Dntmgt.clienttest=true, runClientTest): logs once the player is in the world, opens the inventory, quits 200 ticks later. */
public final class ClientJoinTest {
    private static int ticksInWorld = -1;

    private ClientJoinTest() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (++ticksInWorld == 0) NtmGt.LOGGER.info("[ntmgt] clienttest: player is in the world");
        // Opening a screen in the world starts JEI's runtime (where JEI + ModularUI crashed in the real instance).
        if (ticksInWorld == 60) mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
        if (ticksInWorld == 120) mc.setScreen(null);
        if (ticksInWorld == 200) {
            NtmGt.LOGGER.info("[ntmgt] clienttest: 200 ticks in the world without a crash, quitting");
            mc.stop();
        }
    }
}
