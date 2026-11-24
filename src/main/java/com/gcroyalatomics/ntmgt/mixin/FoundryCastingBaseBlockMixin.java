package com.gcroyalatomics.ntmgt.mixin;

import com.gcroyalatomics.ntmgt.compat.GtMolds;
import com.hbm.blockentity.machine.BlockEntityFoundryCastingBase;
import com.hbm.blocks.machine.FoundryCastingBase;
import com.hbm.interfaces.ILookOverlay;
import com.hbm.items.machine.ItemMold;
import com.hbm.sound.ModSounds;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import com.hbm.backport.InteractionResult; // NTM DE's own type (1.21.2-style backport), not vanilla's
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Foundry mold and basin blocks: right-click with a GT casting mold installs it, the look overlay names it. */
@Mixin(value = FoundryCastingBase.class, remap = false)
public abstract class FoundryCastingBaseBlockMixin {
    @Inject(method = "backport$useItemOn", at = @At("HEAD"), cancellable = true)
    private void ntmgt$installGtMold(ItemStack held, BlockState state, Level level, BlockPos pos, Player player,
                                     InteractionHand hand, BlockHitResult hit,
                                     CallbackInfoReturnable<InteractionResult> cir) {
        ItemMold.Mold mold = GtMolds.of(held);
        if (mold == null || !(level.getBlockEntity(pos) instanceof BlockEntityFoundryCastingBase cast)) return;
        // NTM's own order: a finished cast is taken out first.
        if (!cast.inventory.get(1).isEmpty() || !cast.inventory.get(0).isEmpty()) return;
        if (mold.size != cast.getMoldSize()) return;
        if (!level.isClientSide()) {
            cast.inventory.set(0, held.copyWithCount(1));
            held.shrink(1);
            level.playSound(null, pos, ModSounds.UPGRADE_PLUG.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            cast.setChanged();
        }
        cir.setReturnValue(InteractionResult.SUCCESS);
    }

    @WrapOperation(method = "buildLookOverlay", at = @At(value = "INVOKE",
            target = "Lcom/hbm/interfaces/ILookOverlay$LookInfo;line(Lnet/minecraft/network/chat/Component;I)Lcom/hbm/interfaces/ILookOverlay$LookInfo;"))
    private ILookOverlay.LookInfo ntmgt$gtMoldLine(ILookOverlay.LookInfo info, Component text, int rgb,
                                                  Operation<ILookOverlay.LookInfo> original,
                                                  @Local BlockEntityFoundryCastingBase cast) {
        if (rgb == 0xFF5555) { // the "foundry.noCast" line
            ItemMold.Mold mold = GtMolds.of(cast.inventory.get(0));
            if (mold != null) return original.call(info, mold.getTitle(), 0x5555FF);
        }
        return original.call(info, text, rgb);
    }
}
