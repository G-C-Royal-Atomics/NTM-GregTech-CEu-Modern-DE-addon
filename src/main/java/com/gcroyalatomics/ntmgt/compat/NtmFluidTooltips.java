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

    private static final net.minecraft.resources.ResourceLocation ICON =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("hbm", "fluid_icon");

    public static void onTooltip(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        if (stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("hbm")) return;
        Fluid fluid = Fluids.EMPTY;
        long amount = 1000;
        var content = stack.get(ModDataComponents.FLUID_CONTENT.get());
        // only items that hold or name a fluid: ducts, barrels and machines carry FLUID_CONTENT / FLUID_IDENTIFIER
        // just as their set type (amount 0), and those are not the fluid
        boolean icon = stack.getItem() == BuiltInRegistries.ITEM.get(ICON);
        if (content != null && content.type() != null && (content.amount() > 0 || icon)) {
            fluid = content.type();
            amount = Math.max(1, content.amount());
        } else if (stack.getItem() instanceof com.hbm.items.machine.FluidIdentifierItem) {
            var id = stack.get(ModDataComponents.FLUID_IDENTIFIER.get());
            if (id != null && id.primary() != null) fluid = id.primary();
        }
        if (fluid == Fluids.EMPTY) return;
        TooltipsHandler.appendFluidTooltips(new FluidStack(fluid, (int) Math.max(1, Math.min(amount, Integer.MAX_VALUE))),
                event.getToolTip()::add, event.getFlags(), event.getContext());
    }
}
