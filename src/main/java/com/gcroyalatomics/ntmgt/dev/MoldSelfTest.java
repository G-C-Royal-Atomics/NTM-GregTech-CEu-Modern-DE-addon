package com.gcroyalatomics.ntmgt.dev;

import com.gcroyalatomics.ntmgt.compat.GtMolds;
import com.hbm.blockentity.machine.BlockEntityFoundryCastingBase;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.material.Mats;
import com.hbm.inventory.material.Mats.MaterialStack;
import com.hbm.inventory.material.NTMMaterial;
import com.hbm.items.machine.ItemMold;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.io.BufferedWriter;
import java.io.IOException;

/** Dev-only: real NTM foundry mold/basin in the world, a GT casting mold in it, metal poured, ticks until the cast. */
final class MoldSelfTest {
    private static final String[] MOLDS = {
            "ingot", "nugget", "plate", "gear", "small_gear", "rotor",
            "tiny_pipe", "small_pipe", "normal_pipe", "large_pipe", "block", "huge_pipe"};
    private static final String[] MATS = {"steel", "iron", "copper", "aluminum", "titanium", "tungsten", "lead",
            "uranium_238", "schrabidium", "stone"};

    private MoldSelfTest() {}

    static void run(ServerLevel level, BufferedWriter w) throws IOException {
        BlockPos base = level.getSharedSpawnPos().atY(250);
        level.getChunk(base);
        int i = 0;
        for (String moldName : MOLDS) {
            ItemStack moldStack = new ItemStack(BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath("gtceu", moldName + "_casting_mold")));
            ItemMold.Mold mold = GtMolds.of(moldStack);
            if (mold == null) {
                w.write("mold\t" + moldName + "\tNOT A MOLD\n");
                continue;
            }
            Block block = (mold.size == ItemMold.SIZE_LARGE ? ModBlocks.FOUNDRY_BASIN : ModBlocks.FOUNDRY_MOLD).get();
            for (String matName : MATS) {
                NTMMaterial mat = Mats.matByName.get(matName);
                BlockPos pos = base.offset(i++ % 16, 0, i / 16);
                level.setBlockAndUpdate(pos, block.defaultBlockState());
                if (!(level.getBlockEntity(pos) instanceof BlockEntityFoundryCastingBase cast)) {
                    w.write("mold\t" + moldName + "\t" + matName + "\tNO BLOCK ENTITY\n");
                    continue;
                }
                cast.inventory.set(0, moldStack.copy());
                boolean accepts = mat != null
                        && cast.standardCheck(level, pos, Direction.UP, new MaterialStack(mat, 1));
                String result = "-";
                if (accepts) {
                    cast.type = mat;
                    cast.amount = cast.getCapacity();
                    for (int t = 0; t < 400 && cast.inventory.get(1).isEmpty(); t++) cast.tickServer();
                    ItemStack out = cast.inventory.get(1);
                    result = out.isEmpty() ? "NO OUTPUT" : out.getCount() + "x" + BuiltInRegistries.ITEM.getKey(out.getItem());
                }
                w.write("mold\t" + moldName + "\t" + (mold.size == ItemMold.SIZE_LARGE ? "basin" : "mold")
                        + "\tcap=" + cast.getCapacity() + "\t" + matName + "\taccepts=" + accepts + "\t" + result + "\n");
            }
        }
    }
}
