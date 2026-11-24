package com.gcroyalatomics.ntmgt.dev;

import com.gcroyalatomics.ntmgt.NtmGt;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Dev-only (-Dntmgt.clienttest=true, runClientTest): logs once the player is in the world, quits 200 ticks later. */
public final class ClientJoinTest {
    private static int ticksInWorld = -1;

    private ClientJoinTest() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (++ticksInWorld == 0) NtmGt.LOGGER.info("[ntmgt] clienttest: player is in the world");
        if (ticksInWorld == 200) {
            NtmGt.LOGGER.info("[ntmgt] clienttest: 200 ticks in the world without a crash, quitting");
            mc.stop();
        }
    }
}
