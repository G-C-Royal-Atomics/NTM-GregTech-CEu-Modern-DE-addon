package com.gcroyalatomics.ntmgt.mixin;

import com.gcroyalatomics.ntmgt.compat.GtMelting;
import com.hbm.integration.recipeviewer.CrucibleSmeltingPage;
import com.hbm.integration.recipeviewer.PageIds;
import com.hbm.inventory.material.Mats;
import com.hbm.items.machine.ItemScraps;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

/**
 * NTM's crucible smelting page (JEI) lists what NTM's own tables melt; GT parts melt by GT's composition (GtMelting),
 * so each GT item that melts gets a row: the item, and the molten metals it gives.
 */
@Mixin(value = CrucibleSmeltingPage.class, remap = false)
public abstract class CrucibleSmeltingPageMixin {
    @ModifyReturnValue(method = "rows", at = @At("RETURN"))
    private List<CrucibleSmeltingPage.Recipe> ntmgt$gtParts(List<CrucibleSmeltingPage.Recipe> rows) {
        List<CrucibleSmeltingPage.Recipe> out = new ArrayList<>(rows);
        CrucibleSmeltingPage page = (CrucibleSmeltingPage) (Object) this;
        // Items NTM's own tables already list (GT ingots, plates, ... of metals NTM knows by their c: tags).
        java.util.Set<Item> known = new java.util.HashSet<>();
        for (var ingredient : Mats.smeltingCatalog().keySet())
            for (ItemStack stack : ingredient.getItems()) known.add(stack.getItem());
        for (Item item : BuiltInRegistries.ITEM) {
            var key = BuiltInRegistries.ITEM.getKey(item);
            if (!key.getNamespace().equals("gtceu") && !key.getNamespace().equals("ntmgt")) continue;
            if (known.contains(item)) continue;
            List<Mats.MaterialStack> melts = GtMelting.of(new ItemStack(item));
            if (melts.isEmpty()) continue;
            List<ItemStack> molten = new ArrayList<>(melts.size());
            for (Mats.MaterialStack m : melts) molten.add(ItemScraps.create(m, true));
            out.add(new CrucibleSmeltingPage.Recipe(PageIds.derived(page.id(), "ntmgt", PageIds.segment(key)),
                    List.of(new ItemStack(item)), molten));
        }
        return out;
    }
}
