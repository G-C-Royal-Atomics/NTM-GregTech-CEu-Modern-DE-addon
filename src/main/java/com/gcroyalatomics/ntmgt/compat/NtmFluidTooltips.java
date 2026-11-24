package com.gcroyalatomics.ntmgt.compat;

import com.gregtechceu.gtceu.client.TooltipsHandler;
import com.hbm.items.ModDataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Client only. NTM items holding or naming a fluid (canisters, tanks, barrels, fluid icons, fluid identifiers) get
 * GT's fluid lines, the same ones GT shows for any fluid in its own containers: formula (for fluids shared with GT),
 * liquid / gas, temperature, cryogenic warning.
 */
public final class NtmFluidTooltips {
    private NtmFluidTooltips() {}

    public static void onTooltip(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        if (stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("hbm")) return;
        Fluid fluid = Fluids.EMPTY;
        long amount = 1000;
        var content = stack.get(ModDataComponents.FLUID_CONTENT.get());
        if (content != null && content.type() != null) {
            fluid = content.type();
            amount = content.amount();
        } else {
            var id = stack.get(ModDataComponents.FLUID_IDENTIFIER.get());
            if (id != null && id.primary() != null) fluid = id.primary();
        }
        if (fluid == Fluids.EMPTY) return;
        TooltipsHandler.appendFluidTooltips(new FluidStack(fluid, (int) Math.max(1, Math.min(amount, Integer.MAX_VALUE))),
                event.getToolTip()::add, event.getFlags(), event.getContext());
    }
}
