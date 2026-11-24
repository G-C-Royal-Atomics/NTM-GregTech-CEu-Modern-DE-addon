package com.gcroyalatomics.ntmgt.mixin.client;

import com.gcroyalatomics.ntmgt.compat.NtmFluidFormulas;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.client.TooltipsHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/** GT shows the yellow formula line only for fluids with a GT material; NTM fluids get theirs from NtmFluidFormulas. */
@Mixin(value = TooltipsHandler.class, remap = false)
public abstract class FluidTooltipFormulaMixin {

    @Inject(method = "appendFluidTooltips", at = @At("HEAD"))
    private static void ntmgt$formula(FluidStack fluidStack, Consumer<Component> tooltips, TooltipFlag flag,
                                      Item.TooltipContext context, CallbackInfo ci) {
        if (ChemicalHelper.getMaterial(fluidStack.getFluid()) != null) return;
        String f = NtmFluidFormulas.of(fluidStack.getFluid());
        if (f != null) tooltips.accept(Component.literal(f).withStyle(ChatFormatting.YELLOW));
    }
}
