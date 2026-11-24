package com.gcroyalatomics.ntmgt.compat;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.hbm.inventory.material.MaterialShapes;
import com.hbm.inventory.material.NTMMaterial;
import com.hbm.items.machine.ItemMold;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * GT casting molds as NTM foundry molds. A GT mold put into the NTM foundry mold / basin / strand caster
 * casts the GT form of the poured NTM material: ingot mold -> ingot, gear mold -> gear, pipe molds -> fluid pipes.
 * The output is GT's item for that (prefix, material), which is the NTM item where one is linked (NtmGtMaterials).
 * A material GT has no such form for is refused, so the crucible does not pour it.
 */
public final class GtMolds {
    private record Spec(String prefix, int size) {}

    private static final Map<String, Spec> SPECS = Map.ofEntries(
            Map.entry("ingot_casting_mold", new Spec("ingot", ItemMold.SIZE_SMALL)),
            Map.entry("nugget_casting_mold", new Spec("nugget", ItemMold.SIZE_SMALL)),
            Map.entry("plate_casting_mold", new Spec("plate", ItemMold.SIZE_SMALL)),
            Map.entry("gear_casting_mold", new Spec("gear", ItemMold.SIZE_SMALL)),
            Map.entry("small_gear_casting_mold", new Spec("small_gear", ItemMold.SIZE_SMALL)),
            Map.entry("rotor_casting_mold", new Spec("rotor", ItemMold.SIZE_SMALL)),
            Map.entry("tiny_pipe_casting_mold", new Spec("pipe_tiny_fluid", ItemMold.SIZE_SMALL)),
            Map.entry("small_pipe_casting_mold", new Spec("pipe_small_fluid", ItemMold.SIZE_SMALL)),
            Map.entry("normal_pipe_casting_mold", new Spec("pipe_normal_fluid", ItemMold.SIZE_SMALL)),
            Map.entry("large_pipe_casting_mold", new Spec("pipe_large_fluid", ItemMold.SIZE_SMALL)),
            Map.entry("block_casting_mold", new Spec("block", ItemMold.SIZE_LARGE)),
            Map.entry("huge_pipe_casting_mold", new Spec("pipe_huge_fluid", ItemMold.SIZE_LARGE)));

    private static @Nullable Map<Item, GtMold> byItem;

    private GtMolds() {}

    public static @Nullable ItemMold.Mold of(ItemStack stack) {
        if (stack.isEmpty()) return null;
        return molds().get(stack.getItem());
    }

    public static boolean is(ItemStack stack) {
        return of(stack) != null;
    }

    private static Map<Item, GtMold> molds() {
        if (byItem == null) {
            Map<Item, GtMold> map = new HashMap<>();
            SPECS.forEach((id, spec) -> {
                ResourceLocation itemId = ResourceLocation.fromNamespaceAndPath("gtceu", id);
                TagPrefix prefix = GTRegistries.TAG_PREFIXES.get(ResourceLocation.fromNamespaceAndPath("gtceu", spec.prefix()));
                if (prefix == null || !BuiltInRegistries.ITEM.containsKey(itemId)) return;
                Item item = BuiltInRegistries.ITEM.get(itemId);
                map.put(item, new GtMold(spec.size(), id, item, prefix));
            });
            byItem = map;
        }
        return byItem;
    }

    public static final class GtMold extends ItemMold.Mold {
        private final Item item;
        private final TagPrefix prefix;
        private final int cost;

        GtMold(int size, String name, Item item, TagPrefix prefix) {
            super(size, "gt_" + name);
            this.item = item;
            this.prefix = prefix;
            // GT amount of this form (in M per ingot) -> NTM quanta (INGOT.q(1) per ingot). Steel as the reference:
            // per-material amount overrides do not exist for these prefixes.
            long amount = prefix.getMaterialAmount(MaterialNames.gt("steel"));
            this.cost = (int) (amount * MaterialShapes.INGOT.q(1) / GTValues.M);
        }

        @Override
        public @Nullable ItemStack getOutput(NTMMaterial mat) {
            if (mat == null) return null;
            Material gt = MaterialNames.gt(mat.tagPath);
            if (gt == null) return null;
            ItemStack out = ChemicalHelper.get(prefix, gt);
            return out.isEmpty() ? null : out;
        }

        @Override
        public int getCost() {
            return cost;
        }

        @Override
        public Component getTitle() {
            return Component.translatable(item.getDescriptionId()).append(" x1");
        }
    }
}
