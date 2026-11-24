package com.gcroyalatomics.ntmgt.mixin;

import com.gcroyalatomics.ntmgt.compat.GtMolds;
import com.hbm.blockentity.machine.BlockEntityMachineStrandCaster;
import com.hbm.blocks.machine.MachineStrandCaster;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Strand caster block: right-click with a GT casting mold installs it, the look overlay names it. */
@Mixin(value = MachineStrandCaster.class, remap = false)
public abstract class StrandCasterBlockMixin {
    @Inject(method = "useItemOnAtCore", at = @At("HEAD"), cancellable = true)
    private void ntmgt$installGtMold(ItemStack held, BlockState coreState, Level level, BlockPos core, Player player,
                                     InteractionHand hand, BlockHitResult hit,
                                     CallbackInfoReturnable<InteractionResult> cir) {
        if (!GtMolds.is(held) || !(level.getBlockEntity(core) instanceof BlockEntityMachineStrandCaster caster)) return;
        if (!caster.inventory.get(BlockEntityMachineStrandCaster.SLOT_MOLD).isEmpty()) return;
        if (!level.isClientSide()) {
            caster.inventory.set(BlockEntityMachineStrandCaster.SLOT_MOLD, held.copyWithCount(1));
            held.shrink(1);
            level.playSound(null, core, ModSounds.UPGRADE_PLUG.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            caster.setChanged();
        }
        cir.setReturnValue(InteractionResult.SUCCESS);
    }

    @WrapOperation(method = "buildLookOverlay", at = @At(value = "INVOKE",
            target = "Lcom/hbm/interfaces/ILookOverlay$LookInfo;line(Lnet/minecraft/network/chat/Component;I)Lcom/hbm/interfaces/ILookOverlay$LookInfo;"))
    private ILookOverlay.LookInfo ntmgt$gtMoldLine(ILookOverlay.LookInfo info, Component text, int rgb,
                                                  Operation<ILookOverlay.LookInfo> original,
                                                  @Local BlockEntityMachineStrandCaster caster) {
        if (rgb == 0xFF5555) { // the "foundry.noCast" line
            ItemMold.Mold mold = GtMolds.of(caster.inventory.get(BlockEntityMachineStrandCaster.SLOT_MOLD));
            if (mold != null) return original.call(info, mold.getTitle(), 0x5555FF);
        }
        return original.call(info, text, rgb);
    }
}
