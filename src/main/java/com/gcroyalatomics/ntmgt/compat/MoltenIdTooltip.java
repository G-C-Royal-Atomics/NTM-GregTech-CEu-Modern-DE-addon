package com.gcroyalatomics.ntmgt.compat;

import com.gcroyalatomics.ntmgt.NtmGtConfig;
import com.hbm.inventory.material.Mats;
import com.hbm.items.machine.ItemScraps;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * The NTM material id of molten metal and scraps (JEI's crucible / casting pages show molten metal as these),
 * the name NTM's recipe files use: {"material": "bronze"} in hbm:crucible and hbm:material_distribution recipes.
 * Shown with advanced tooltips (F3+H), or always (client config tooltips.materialIds).
 */
public final class MoltenIdTooltip {
    private MoltenIdTooltip() {}

    public static void onTooltip(ItemTooltipEvent event) {
        String mode = NtmGtConfig.get(NtmGtConfig.MATERIAL_IDS);
        if (mode.equals("never") || !(event.getItemStack().getItem() instanceof ItemScraps)) return;
        if (mode.equals("advanced") && !event.getFlags().isAdvanced()) return;
        Mats.MaterialStack mats = ItemScraps.getMats(event.getItemStack());
        if (mats == null) return;
        event.getToolTip().add(Component.literal("NTM material: " + mats.material.tagPath)
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
