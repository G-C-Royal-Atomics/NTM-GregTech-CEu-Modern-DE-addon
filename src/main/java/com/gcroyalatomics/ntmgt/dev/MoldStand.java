package com.gcroyalatomics.ntmgt.dev;

import com.gcroyalatomics.ntmgt.NtmGt;
import com.gcroyalatomics.ntmgt.compat.GtMolds;
import com.hbm.blockentity.machine.BlockEntityCrucible;
import com.hbm.blockentity.machine.BlockEntityFoundryCastingBase;
import com.hbm.blocks.multiblock.BlockMultiblockCore;
import com.hbm.blockentity.machine.BlockEntityMachineStrandCaster;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.container.MenuMachineStrandCaster;
import com.hbm.inventory.material.Mats;
import com.hbm.inventory.material.NTMMaterial;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemMold;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;

/**
 * Dev-only stand (-Dntmgt.moldstand=true, runMoldStand, tools/stand.sh): in a real client, a player puts every GT
 * casting mold into NTM's foundry mold / basin by a real right click (MultiPlayerGameMode#useItemOn: the same packet as
 * a click), looks at it (look overlay, renderer), metal is poured, the cast is taken out by a right click; NTM's own
 * mold as control; wrong-size and second-mold clicks; the strand caster is placed by the player, gets a GT mold by
 * click, by its GUI (slot clicks) and by shift-click; NTM's crucible melts GT parts and pours them into a GT mold. Every step writes PASS/FAIL to ntmgt-stand.txt in the game dir;
 * a crash leaves the file without its last line "RESULT ...". World actions run on the server thread.
 */
public final class MoldStand {
    private static final String[] GT_MOLDS = {"ingot", "nugget", "plate", "gear", "small_gear", "rotor",
            "tiny_pipe", "small_pipe", "normal_pipe", "large_pipe", "block", "huge_pipe"};
    private static final int TIMEOUT_TICKS = 20 * 60 * 16;

    private static final BlockPos ORIGIN = new BlockPos(0, 200, 0);
    private static final BlockPos SMALL = ORIGIN.offset(2, 1, 3);
    private static final BlockPos LARGE = ORIGIN.offset(5, 1, 3);
    private static final BlockPos CASTER_FLOOR = ORIGIN.offset(12, 0, 4);
    private static final BlockPos CRUCIBLE_FLOOR = ORIGIN.offset(4, 0, 12);

    private static final List<String> lines = new ArrayList<>();
    private static final Deque<Step> steps = new ArrayDeque<>();
    private static int ticks = -1, wait, fails, shots;
    private static boolean done;
    private static BlockPos casterCore, crucibleCore, crucibleMold;

    private record Step(String name, Runnable action, int waitAfter) {}

    private MoldStand() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (done || mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) {
            if (!done && ++ticks > 20 * 120) finish(mc, "timeout before the world; screen=" + screen(mc));
            return;
        }
        if (ticks < 1000) { // first tick in the world
            ticks = 1000;
            mc.options.pauseOnLostFocus = false;
            plan();
            wait = 40;
        }
        ticks++;
        if (ticks - 1000 > TIMEOUT_TICKS) {
            finish(mc, "timeout in the world; screen=" + screen(mc) + ", steps left=" + steps.size());
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        Step step = steps.poll();
        if (step == null) {
            finish(mc, null);
            return;
        }
        try {
            step.action().run();
        } catch (Throwable t) {
            check(false, step.name() + ": exception " + t);
            NtmGt.LOGGER.error("[ntmgt] stand step {} failed", step.name(), t);
        }
        wait = step.waitAfter();
    }

    // ---------------------------------------------------------------- plan

    private static void plan() {
        step("setup", MoldStand::setup, 20);
        step("control: NTM's own ingot mold into the foundry mold", () -> {
            reset(SMALL);
            hold(new ItemStack(ModItems.byName("ingot")));
        }, 5);
        step("control click", () -> click(SMALL), 10);
        step("control check", () -> {
            check(serverSlot(SMALL, 0).getItem() == ModItems.byName("ingot"), "NTM ingot mold installed by click");
            reset(SMALL);
        }, 5);

        for (String name : GT_MOLDS) {
            Item item = gtItem(name + "_casting_mold");
            ItemMold.Mold mold = GtMolds.of(new ItemStack(item));
            if (mold == null) {
                step("gt " + name, () -> check(false, "gtceu:" + name + "_casting_mold is not a GT mold"), 0);
                continue;
            }
            BlockPos at = mold.size == ItemMold.SIZE_LARGE ? LARGE : SMALL;
            BlockPos other = at == LARGE ? SMALL : LARGE;
            String label = name + " mold";
            step(label + ": hold", () -> {
                reset(at);
                reset(other);
                hold(new ItemStack(item, 2));
            }, 5);
            step(label + ": wrong-size block click", () -> click(other), 10);
            step(label + ": wrong-size check", () -> check(serverSlot(other, 0).isEmpty(),
                    label + " not taken by the " + (other == LARGE ? "basin" : "foundry mold")), 2);
            step(label + ": click", () -> click(at), 10);
            step(label + ": installed", () -> {
                check(serverSlot(at, 0).getItem() == item, label + " installed by click (server)");
                check(heldCount() == 1, label + " one taken from the hand (hand " + heldCount() + ")");
                BlockEntity cbe = Minecraft.getInstance().level.getBlockEntity(at);
                check(cbe instanceof BlockEntityFoundryCastingBase c && c.inventory.get(0).getItem() == item,
                        label + " seen by the client");
                look(at);
            }, 20); // frames with the crosshair on it: look overlay + renderer
            step(label + ": second click", () -> click(at), 10);
            step(label + ": second check", () -> {
                check(heldCount() == 1, label + " second click on an installed mold takes nothing");
                if (shots < 2) shot("mold-" + name);
            }, 2);
            step(label + ": pour", () -> pour(at, "steel"), 1);
            step(label + ": cast", () -> {}, 0);
            waitUntil(label + ": cast done", () -> !serverSlot(at, 1).isEmpty(), 20 * 30);
            step(label + ": output", () -> {
                ItemStack out = serverSlot(at, 1);
                ItemStack expected = mold.getOutput(Mats.matByName.get("steel"));
                check(expected != null && out.getItem() == expected.getItem(),
                        label + " casts " + id(out) + " (expected " + (expected == null ? "-" : id(expected)) + ")");
                hold(ItemStack.EMPTY);
            }, 5);
            step(label + ": take out", () -> click(at), 10);
            step(label + ": taken", () -> {
                check(serverSlot(at, 1).isEmpty(), label + " cast taken out by click");
                check(invCount(mold.getOutput(Mats.matByName.get("steel")).getItem()) > 0,
                        label + " cast is in the inventory");
                clearInventory();
            }, 2);
        }

        // strand caster: placed by the player, mold by click, GUI, GUI slot click, shift-click
        crucibleChain();
        gtMaterials();
        jei();
        formulas();
        fluids();

        step("caster: go", () -> goTo(CASTER_FLOOR.offset(0, 1, -4)), 5);
        step("caster: hold", () -> hold(new ItemStack(ModBlocks.MACHINE_STRAND_CASTER.get())), 5);
        step("caster: place", () -> clickFace(CASTER_FLOOR, Direction.UP), 20);
        step("caster: find", () -> {
            casterCore = onServer(() -> {
                ServerLevel level = server().overworld();
                for (BlockPos p : BlockPos.betweenClosed(CASTER_FLOOR.offset(-4, 0, -4), CASTER_FLOOR.offset(4, 4, 4)))
                    if (level.getBlockEntity(p) instanceof BlockEntityMachineStrandCaster) return p.immutable();
                return null;
            });
            check(casterCore != null, "strand caster placed by the player (core " + casterCore + ")");
            if (casterCore == null) steps.removeIf(s -> s.name().startsWith("caster"));
        }, 2);
        Item ingotMold = gtItem("ingot_casting_mold");
        step("caster: hold mold", () -> hold(new ItemStack(ingotMold, 2)), 5);
        step("caster: click mold", () -> click(casterCore), 10);
        step("caster: mold check", () -> {
            check(casterSlot(BlockEntityMachineStrandCaster.SLOT_MOLD).getItem() == ingotMold,
                    "strand caster: GT ingot mold installed by click");
            look(casterCore);
        }, 20);
        step("caster: pour check", () -> check(onServer(() -> {
            BlockEntityMachineStrandCaster c = caster();
            NTMMaterial steel = Mats.matByName.get("steel");
            for (BlockPos p : c.getMetalPourPos())
                if (c.standardCheck(server().overworld(), p, Direction.UP, new Mats.MaterialStack(steel, 1))) return true;
            return false;
        }), "strand caster with GT mold accepts steel"), 2);
        step("caster: screenshot", () -> shot("caster"), 2);
        step("caster: empty hand", () -> hold(ItemStack.EMPTY), 5);
        step("caster: open GUI", () -> click(casterCore), 15);
        step("caster: GUI open", () -> {
            Minecraft mc = Minecraft.getInstance();
            check(mc.player.containerMenu instanceof MenuMachineStrandCaster, "strand caster GUI opened ("
                    + mc.player.containerMenu.getClass().getSimpleName() + ", screen " + screen(mc) + ")");
        }, 20);
        step("caster: take mold out by GUI", () -> slotClick(0, ClickType.QUICK_MOVE), 10);
        step("caster: mold out", () -> check(casterSlot(BlockEntityMachineStrandCaster.SLOT_MOLD).isEmpty(),
                "strand caster: mold shift-clicked out of the GUI"), 2);
        step("caster: GUI pick mold", () -> {
            int slot = menuSlotOf(ingotMold);
            check(slot >= 0, "GT mold found in the GUI's player slots (" + slot + ")");
            if (slot >= 0) slotClick(slot, ClickType.PICKUP);
        }, 5);
        step("caster: GUI place mold", () -> slotClick(0, ClickType.PICKUP), 10);
        step("caster: GUI placed", () -> {
            check(casterSlot(BlockEntityMachineStrandCaster.SLOT_MOLD).getItem() == ingotMold,
                    "strand caster: GT mold put into the mold slot by GUI click");
            ItemStack carried = Minecraft.getInstance().player.containerMenu.getCarried();
            if (!carried.isEmpty()) slotClick(menuSlotOf(ItemStack.EMPTY.getItem()), ClickType.PICKUP);
        }, 5);
        step("caster: shift-click mold in", () -> {
            onServer(() -> { // the slot click put both molds in (NTM's slot stacks them); a fresh one for shift-click
                caster().inventory.set(BlockEntityMachineStrandCaster.SLOT_MOLD, ItemStack.EMPTY);
                caster().setChanged();
                player().getInventory().add(new ItemStack(ingotMold));
                player().containerMenu.broadcastChanges();
                return null;
            });
        }, 5);
        step("caster: shift-click", () -> {
            int slot = menuSlotOf(ingotMold);
            if (slot >= 0) slotClick(slot, ClickType.QUICK_MOVE);
            else check(false, "no GT mold left for shift-click");
        }, 10);
        step("caster: shift-click result", () -> check(true, "strand caster: shift-click of a GT mold, no crash (mold slot "
                + id(casterSlot(BlockEntityMachineStrandCaster.SLOT_MOLD)) + ")"), 2);
        step("caster: close", () -> Minecraft.getInstance().player.closeContainer(), 10);
    }

    /**
     * The whole chain: the player places NTM's crucible, puts a GT gear mold into a foundry mold behind it, GT steel
     * rods go in (one by the crucible GUI's shift-click, seven thrown in on top), heat, they melt (GT parts by GT's
     * composition: 8 x 36 = 288 = one gear), the crucible pours (no recipe template: out the back), the mold casts a
     * GT steel gear, the player takes it out.
     */
    /** Every GT metal/alloy is an NTM material: melts into itself, slag for the rest, GT ores like NTM's. */
    private static void gtMaterials() {
        step("gt mats: bronze is NTM", () -> {
            NTMMaterial bronze = Mats.matByName.get("bronze");
            check(bronze != null && com.gcroyalatomics.ntmgt.compat.GtNtmMats.isGt(bronze),
                    "GT bronze is an NTM material (" + bronze + ")");
            if (bronze == null) return;
            String name = net.minecraft.network.chat.Component.translatable(bronze.getUnlocalizedName()).getString();
            check(name.equals("Bronze"), "NTM bronze is named by GT: " + name);
            int gtRgb = com.gcroyalatomics.ntmgt.compat.MaterialNames.gt("bronze").getMaterialRGB() & 0xFFFFFF;
            check(bronze.solidColorLight == gtRgb && bronze.moltenColor == gtRgb,
                    String.format("NTM bronze colour %06X / molten %06X = GT %06X", bronze.solidColorLight,
                            bronze.moltenColor, gtRgb));
            check(bronze.solidColorLight != bronze.solidColorDark, "NTM bronze has a dark colour (slag texture)");
            check(com.hbm.client.render.SlagTextures.texture(bronze) != null, "NTM bronze has a slag texture");
            var mc = Minecraft.getInstance();
            var molten = com.hbm.items.machine.ItemScraps.create(new Mats.MaterialStack(bronze, 72), true);
            var lines = molten.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player,
                    net.minecraft.world.item.TooltipFlag.ADVANCED).stream().map(net.minecraft.network.chat.Component::getString).toList();
            check(lines.contains("NTM material: bronze"), "molten bronze tooltip (F3+H) names its id: " + lines);
        }, 2);
        step("gt mats: melting", () -> {
            meltCheck("bronze_ingot", "bronze 72");
            meltCheck("stainless_steel_gear", "stainless_steel 288");
            meltCheck("copper_single_cable", "copper 36, slag 72");
            meltHasSlag("lv_machine_hull", "steel ");
            meltCheck("rubber_ring", "");
            meltCheck("hot_tungsten_steel_ingot", "");
            // config: ntmgt-common.toml melting.hotIngotsMelt / melting.unmeltableToSlag, read while running
            var cfg = com.gcroyalatomics.ntmgt.NtmGtConfig.class;
            com.gcroyalatomics.ntmgt.NtmGtConfig.HOT_INGOTS_MELT.set(true);
            meltCheck("hot_tungsten_steel_ingot", "tungsten_steel 72");
            com.gcroyalatomics.ntmgt.NtmGtConfig.HOT_INGOTS_MELT.set(false);
            com.gcroyalatomics.ntmgt.NtmGtConfig.UNMELTABLE_TO_SLAG.set(false);
            meltCheck("copper_single_cable", "");
            com.gcroyalatomics.ntmgt.NtmGtConfig.UNMELTABLE_TO_SLAG.set(true);
            check(cfg != null, "config values switched back");
            meltCheck("chalcopyrite_ore", "copper 144, stone 162");
            meltCheck("raw_chalcopyrite", "copper 96, stone 108");
            meltCheck("purified_chalcopyrite_ore", "copper 72");
        }, 2);
        step("gt mats: molds", () -> {
            NTMMaterial bronze = Mats.matByName.get("bronze");
            ItemMold.Mold ingot = ItemMold.molds.stream().filter(m -> m.name.equals("ingot")).findFirst().orElse(null);
            ItemStack fromNtm = ingot == null ? null : ingot.getOutput(bronze);
            check(fromNtm != null && id(fromNtm).equals("gtceu:bronze_ingot"),
                    "NTM ingot mold casts bronze into " + (fromNtm == null ? "-" : id(fromNtm)));
            ItemStack fromGt = GtMolds.of(new ItemStack(gtItem("gear_casting_mold"))).getOutput(bronze);
            check(fromGt != null && id(fromGt).equals("gtceu:bronze_gear"),
                    "GT gear mold casts bronze into " + (fromGt == null ? "-" : id(fromGt)));
        }, 2);
        step("gt mats: pour bronze", () -> {
            reset(SMALL);
            onServer(() -> {
                if (server().overworld().getBlockEntity(SMALL) instanceof BlockEntityFoundryCastingBase c) {
                    c.inventory.set(0, new ItemStack(gtItem("gear_casting_mold")));
                    c.setChanged();
                }
                return null;
            });
            pour(SMALL, "bronze");
        }, 1);
        waitUntil("gt mats: bronze cast", () -> !serverSlot(SMALL, 1).isEmpty(), 20 * 30);
        step("gt mats: bronze gear", () -> {
            check(id(serverSlot(SMALL, 1)).equals("gtceu:bronze_gear"),
                    "molten bronze cast in the GT gear mold: " + id(serverSlot(SMALL, 1)));
            reset(SMALL);
        }, 2);
    }

    /** Melts into the given metal plus slag for its unmeltable part (amounts are GT's recycling data). */
    private static void meltHasSlag(String gtId, String metalPrefix) {
        List<Mats.MaterialStack> melts = Mats.getSmeltingMaterialsFromItem(new ItemStack(gtItem(gtId)));
        String got = String.join(", ", melts.stream().map(m -> m.material.tagPath + " " + m.amount).toList());
        check(got.contains(metalPrefix) && melts.stream().anyMatch(m -> m.material == Mats.MAT_SLAG),
                "crucible melts gtceu:" + gtId + " into [" + got + "] (" + metalPrefix.trim() + " and slag)");
    }

    private static void meltCheck(String gtId, String expected) {
        List<Mats.MaterialStack> melts = Mats.getSmeltingMaterialsFromItem(new ItemStack(gtItem(gtId)));
        String got = String.join(", ", melts.stream().map(m -> m.material.tagPath + " " + m.amount).toList());
        check(got.equals(expected), "crucible melts gtceu:" + gtId + " into [" + got + "] (expected [" + expected + "])");
    }

    private static void crucibleChain() {
        Item rod = gtItem("steel_rod");
        Item gearMold = gtItem("gear_casting_mold");
        Item gear = gtItem("steel_gear");
        step("crucible: go", () -> goTo(CRUCIBLE_FLOOR.offset(0, 1, -4)), 5);
        step("crucible: hold", () -> hold(new ItemStack(ModBlocks.MACHINE_CRUCIBLE.get())), 5);
        step("crucible: place", () -> clickFace(CRUCIBLE_FLOOR, Direction.UP), 20);
        step("crucible: find", () -> {
            crucibleCore = onServer(() -> {
                ServerLevel level = server().overworld();
                for (BlockPos p : BlockPos.betweenClosed(CRUCIBLE_FLOOR.offset(-3, 0, -3), CRUCIBLE_FLOOR.offset(3, 4, 3)))
                    if (level.getBlockEntity(p) instanceof BlockEntityCrucible) return p.immutable();
                return null;
            });
            check(crucibleCore != null, "crucible placed by the player (core " + crucibleCore + ")");
            if (crucibleCore == null) {
                steps.removeIf(s -> s.name().startsWith("crucible"));
                return;
            }
            Direction back = onServer(() -> BlockMultiblockCore.coreFacing(
                    server().overworld().getBlockState(crucibleCore))).getOpposite();
            // the crucible pours 1.875 blocks out and traces down: the mold sits 2 blocks behind, one lower
            crucibleMold = crucibleCore.relative(back, 2).below();
            onServer(() -> {
                ServerLevel level = server().overworld();
                level.setBlockAndUpdate(crucibleMold, ModBlocks.FOUNDRY_MOLD.get().defaultBlockState());
                for (int dy = 1; dy <= 3; dy++) level.setBlockAndUpdate(crucibleMold.above(dy), Blocks.AIR.defaultBlockState());
                return null;
            });
            check(true, "crucible faces " + back.getOpposite() + ", foundry mold behind it at " + crucibleMold);
        }, 5);
        step("crucible: go to mold", () -> goToNear(crucibleMold), 5);
        step("crucible: hold gear mold", () -> hold(new ItemStack(gearMold)), 5);
        step("crucible: click gear mold", () -> click(crucibleMold), 10);
        step("crucible: gear mold check", () -> check(serverSlot(crucibleMold, 0).getItem() == gearMold,
                "GT gear mold installed in the mold behind the crucible"), 2);
        step("crucible: go to crucible", () -> goToNear(crucibleCore), 5);
        step("crucible: hold rod", () -> hold(new ItemStack(rod)), 5);
        step("crucible: GUI", () -> {
            Minecraft.getInstance().player.getInventory().selected = 1; // empty hand opens the GUI
            onServer(() -> {
                player().getInventory().selected = 1;
                return null;
            });
        }, 5);
        step("crucible: open GUI", () -> click(crucibleCore), 15);
        step("crucible: GUI open", () -> {
            var menu = Minecraft.getInstance().player.containerMenu;
            check(menu.getClass().getSimpleName().contains("Crucible"),
                    "crucible GUI opened (" + menu.getClass().getSimpleName() + ")");
            int slot = menuSlotOf(rod);
            check(slot >= 0, "GT steel rod in the crucible GUI's player slots");
            if (slot >= 0) slotClick(slot, ClickType.QUICK_MOVE);
        }, 10);
        step("crucible: rod in", () -> {
            check(onServer(() -> crucible().inventory.stream().anyMatch(st -> st.is(rod))),
                    "GT steel rod shift-clicked into the crucible");
            Minecraft.getInstance().player.closeContainer();
            Minecraft.getInstance().player.getInventory().selected = 0;
            onServer(() -> {
                player().getInventory().selected = 0;
                return null;
            });
        }, 10);
        step("crucible: throw rods", () -> onServer(() -> {
            ServerLevel level = server().overworld();
            var e = new net.minecraft.world.entity.item.ItemEntity(level, crucibleCore.getX() + 0.5,
                    crucibleCore.getY() + 0.7, crucibleCore.getZ() + 0.5, new ItemStack(rod, 7));
            e.setDeltaMovement(Vec3.ZERO);
            e.setNoGravity(true);
            level.addFreshEntity(e);
            return null;
        }), 40);
        step("crucible: rods taken", () -> check(onServer(() -> crucible().inventory.stream()
                .filter(st -> st.is(rod)).count() >= 4), "GT steel rods thrown on top went into the crucible ("
                + onServer(() -> crucible().inventory.stream().filter(st -> st.is(rod)).count()) + " slots)"), 2);
        step("crucible: screenshot", () -> {
            look(crucibleCore);
            shot("crucible");
        }, 2);
        waitUntil("crucible: melt and cast", () -> onServer(() -> {
            crucible().heat = com.hbm.data.MachineData.CRUCIBLE_MAX_HEAT.get(); // as a heater under it would
            crucible().setChanged();
            return server().overworld().getBlockEntity(crucibleMold) instanceof BlockEntityFoundryCastingBase c
                    && !c.inventory.get(1).isEmpty();
        }), 20 * 120);
        step("crucible: cast", () -> {
            ItemStack out = serverSlot(crucibleMold, 1);
            check(out.is(gear), "GT steel rods melted and poured by the crucible cast " + id(out) + " (expected gtceu:steel_gear)");
            look(crucibleMold);
            shot("crucible-cast");
            hold(ItemStack.EMPTY);
        }, 5);
        step("crucible: go take", () -> goToNear(crucibleMold), 5);
        step("crucible: take out", () -> click(crucibleMold), 10);
        step("crucible: taken", () -> {
            check(invCount(gear) == 1, "cast GT steel gear taken out by click");
            clearInventory();
        }, 2);
    }

    /** JEI: NTM's casting page has the GT molds' casts, its crucible page the GT parts' melting; both shown. */
    private static final BlockPos FLUID_ROW = ORIGIN.offset(0, 1, 18);

    /**
     * Shared fluids between the two mods' pipes and tanks: a GT pipe carries NTM's fluids (liquid and gas) into a
     * GT drum and into an NTM barrel; an NTM duct carries from an NTM barrel into a GT drum and from a GT drum into
     * an NTM barrel. A GT recipe's NTM fluid is what goes in.
     */
    private static void fluids() {
        net.minecraft.world.level.material.Fluid acid = fluid("hbm:sulfuric_acid"), chlorine = fluid("hbm:chlorine"),
                oil = fluid("hbm:oil");
        BlockPos p1 = FLUID_ROW, d1 = p1.east();
        BlockPos p2 = FLUID_ROW.offset(0, 0, -2), d2 = p2.east();
        BlockPos p3 = FLUID_ROW.offset(4, 0, 0), b3 = p3.east();
        BlockPos b4 = FLUID_ROW.offset(8, 0, 0), u4 = b4.east(), d4 = u4.east();
        BlockPos d5 = FLUID_ROW.offset(12, 0, 0), u5 = d5.east(), b5 = u5.east();
        BlockPos b6 = FLUID_ROW.offset(0, 0, -5), u6 = b6.east(), d6 = u6.east();   // drum holds water
        BlockPos b7 = FLUID_ROW.offset(4, 0, -5), u7 = b7.east(), t7 = u7.east();   // super tank locked to water
        BlockPos t9 = FLUID_ROW.offset(4, 0, -8), u9 = t9.east(), b9 = u9.east();   // super tank, no output side
        BlockPos d10 = FLUID_ROW.offset(4, 0, -11), u10 = d10.east(), b10 = u10.east(); // GT drum (never pushes)
        BlockPos b11 = FLUID_ROW.offset(4, 0, -14), u11 = b11.east(), r11 = u11.east(); // NTM acid -> GT chem reactor
        BlockPos p8 = FLUID_ROW.offset(8, 0, -5), b8 = p8.east();                   // acid pipe at an oil barrel
        net.minecraft.world.level.material.Fluid water = net.minecraft.world.level.material.Fluids.WATER;
        step("fluids: build", () -> onServer(() -> {
            ServerLevel level = server().overworld();
            level.setBlockAndUpdate(p1, block("gtceu:stainless_steel_normal_fluid_pipe"));
            level.setBlockAndUpdate(d1, block("gtceu:steel_drum"));
            level.setBlockAndUpdate(p2, block("gtceu:stainless_steel_normal_fluid_pipe"));
            level.setBlockAndUpdate(d2, block("gtceu:steel_drum"));
            level.setBlockAndUpdate(p3, block("gtceu:stainless_steel_normal_fluid_pipe"));
            level.setBlockAndUpdate(b3, block("hbm:barrel_steel"));
            barrel(b3).tank.setTankType(oil);
            level.setBlockAndUpdate(b4, block("hbm:barrel_steel"));
            level.setBlockAndUpdate(u4, block("hbm:fluid_duct_paintable"));
            level.setBlockAndUpdate(d4, block("gtceu:steel_drum"));
            barrel(b4).tank.setTankType(oil);
            barrel(b4).tank.setFill(8000);
            barrel(b4).setMode(com.hbm.blockentity.machine.storage.BlockEntityBarrel.MODE_SEND);
            level.setBlockAndUpdate(d5, block("gtceu:lv_super_tank"));
            level.setBlockAndUpdate(u5, block("hbm:fluid_duct_paintable"));
            level.setBlockAndUpdate(b5, block("hbm:barrel_steel"));
            barrel(b5).tank.setTankType(oil);
            barrel(b5).setMode(com.hbm.blockentity.machine.storage.BlockEntityBarrel.MODE_RECEIVE);
            level.setBlockAndUpdate(b6, block("hbm:barrel_steel"));
            level.setBlockAndUpdate(u6, block("hbm:fluid_duct_paintable"));
            level.setBlockAndUpdate(d6, block("gtceu:steel_drum"));
            level.setBlockAndUpdate(b7, block("hbm:barrel_steel"));
            level.setBlockAndUpdate(u7, block("hbm:fluid_duct_paintable"));
            level.setBlockAndUpdate(t7, block("gtceu:lv_super_tank"));
            level.setBlockAndUpdate(p8, block("gtceu:stainless_steel_normal_fluid_pipe"));
            level.setBlockAndUpdate(b8, block("hbm:barrel_steel"));
            for (BlockPos b : List.of(b6, b7)) {
                barrel(b).tank.setTankType(oil);
                barrel(b).tank.setFill(8000);
                barrel(b).setMode(com.hbm.blockentity.machine.storage.BlockEntityBarrel.MODE_SEND);
            }
            barrel(b8).tank.setTankType(oil);
            cap(d6).fill(new net.neoforged.neoforge.fluids.FluidStack(water, 1000),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            try {
                var m = com.gregtechceu.gtceu.common.machine.storage.QuantumTankMachine.class
                        .getDeclaredMethod("setLocked", net.neoforged.neoforge.fluids.FluidStack.class);
                m.setAccessible(true);
                m.invoke(com.gregtechceu.gtceu.api.machine.MetaMachine.getMachine(level, t7),
                        new net.neoforged.neoforge.fluids.FluidStack(water, 1000));
            } catch (ReflectiveOperationException ex) {
                throw new RuntimeException(ex);
            }
            var tank = (com.gregtechceu.gtceu.common.machine.storage.QuantumTankMachine)
                    com.gregtechceu.gtceu.api.machine.MetaMachine.getMachine(level, d5);
            tank.autoOutput.setFluidOutputDirection(Direction.EAST);
            tank.autoOutput.setAllowAutoOutputFluids(true);
            for (BlockPos pipe : List.of(p1, p2, p3, p8)) { // as a player-placed pipe: joined to the tank, open end
                var be = (com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity<?, ?>) level.getBlockEntity(pipe);
                be.setConnection(Direction.EAST, true, false);
                be.setConnection(Direction.WEST, true, false);
            }
            level.setBlockAndUpdate(t9, block("gtceu:lv_super_tank"));
            level.setBlockAndUpdate(d10, block("gtceu:steel_drum"));
            for (BlockPos p : List.of(u9, u10)) level.setBlockAndUpdate(p, block("hbm:fluid_duct_paintable"));
            for (BlockPos p : List.of(b9, b10)) {
                level.setBlockAndUpdate(p, block("hbm:barrel_steel"));
                barrel(p).tank.setTankType(oil);
                barrel(p).setMode(com.hbm.blockentity.machine.storage.BlockEntityBarrel.MODE_RECEIVE);
            }
            for (BlockPos p : List.of(t9, d10)) cap(p).fill(new net.neoforged.neoforge.fluids.FluidStack(oil, 4000),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            level.setBlockAndUpdate(b11, block("hbm:barrel_tcalloy"));
            barrel(b11).tank.setTankType(acid);
            barrel(b11).tank.setFill(4000);
            barrel(b11).setMode(com.hbm.blockentity.machine.storage.BlockEntityBarrel.MODE_SEND);
            level.setBlockAndUpdate(u11, block("hbm:fluid_duct_paintable"));
            level.setBlockAndUpdate(r11, block("gtceu:lv_chemical_reactor"));
            var reactor = (com.gregtechceu.gtceu.api.machine.SimpleTieredMachine)
                    com.gregtechceu.gtceu.api.machine.MetaMachine.getMachine(level, r11);
            reactor.importItems.setStackInSlot(0, new ItemStack(BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse("gtceu:salt_dust")), 2));
            reactor.getTrait(com.gregtechceu.gtceu.common.machine.trait.ProgrammableCircuitSlotTrait.class)
                    .setCurrentCircuit(1);
            reactor.energyContainer.setEnergyStored(reactor.energyContainer.getEnergyCapacity());
            com.hbm.blocks.network.FluidPipeBlock.retypeNode(level, u11.asLong(), acid);
            com.hbm.blocks.network.FluidPipeBlock.refreshAround(level, u11);
            for (BlockPos duct : List.of(u4, u5, u6, u7, u9, u10)) {
                com.hbm.blocks.network.FluidPipeBlock.retypeNode(level, duct.asLong(), oil);
                com.hbm.blocks.network.FluidPipeBlock.refreshAround(level, duct);
            }
            cap(d5).fill(new net.neoforged.neoforge.fluids.FluidStack(oil, 8000),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            int a = cap(p1).fill(new net.neoforged.neoforge.fluids.FluidStack(acid, 1000),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            int c = cap(p2).fill(new net.neoforged.neoforge.fluids.FluidStack(chlorine, 1000),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            int o = cap(p3).fill(new net.neoforged.neoforge.fluids.FluidStack(oil, 1000),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            cap(p8).fill(new net.neoforged.neoforge.fluids.FluidStack(acid, 1000),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            check(a > 0 && c > 0 && o > 0, "fluids: GT pipes take NTM acid " + a + ", chlorine (gas) " + c
                    + ", crude oil " + o + " mB");
            return null;
        }), 5);
        waitUntil("fluids: GT pipe → GT drum, NTM acid", () -> amount(d1, acid) > 0, 20 * 20);
        waitUntil("fluids: GT pipe → GT drum, NTM chlorine", () -> amount(d2, chlorine) > 0, 20 * 20);
        waitUntil("fluids: GT pipe → NTM barrel, oil", () -> amount(b3, oil) > 0, 20 * 20);
        waitUntil("fluids: NTM barrel → NTM duct → GT drum", () -> amount(d4, oil) > 0, 20 * 30);
        waitUntil("fluids: GT super tank (auto-output) → NTM duct → NTM barrel", () -> amount(b5, oil) > 0, 20 * 30);
        waitTicks(20 * 15); // the negative cases get time to go wrong
        step("fluids: report", () -> {
            check(amount(d1, acid) > 0, "GT pipe → GT drum: NTM sulfuric acid " + amount(d1, acid) + " mB");
            check(amount(d2, chlorine) > 0, "GT pipe → GT drum: NTM chlorine " + amount(d2, chlorine) + " mB");
            check(amount(b3, oil) > 0, "GT pipe → NTM barrel: oil " + amount(b3, oil) + " mB");
            check(amount(d4, oil) > 0, "NTM barrel → NTM duct → GT drum: oil " + amount(d4, oil) + " mB");
            check(amount(b5, oil) > 0, "GT super tank (auto-output) → NTM duct → NTM barrel: oil "
                    + amount(b5, oil) + " mB");
            check(amount(d6, oil) == 0 && amount(d6, water) == 1000 && amount(b6, oil) == 8000,
                    "NTM oil duct at a GT drum holding water: drum oil " + amount(d6, oil) + ", water "
                            + amount(d6, water) + "; barrel keeps " + amount(b6, oil) + " mB");
            check(amount(t7, oil) == 0 && amount(b7, oil) == 8000, "NTM oil duct at a GT super tank locked to water: "
                    + "tank oil " + amount(t7, oil) + ", barrel keeps " + amount(b7, oil) + " mB");
            check(amount(b8, acid) == 0 && amount(b8, oil) == 0, "GT pipe with NTM acid at an NTM oil barrel: barrel "
                    + "acid " + amount(b8, acid) + ", pipe keeps " + amount(p8, acid) + " mB");
            check(amount(b9, oil) == 0 && amount(t9, oil) == 4000, "NTM duct does not pull from a GT super tank "
                    + "without an output side: barrel " + amount(b9, oil) + ", tank keeps " + amount(t9, oil) + " mB");
            check(amount(b10, oil) == 0 && amount(d10, oil) == 4000, "NTM duct does not pull from a GT drum: barrel "
                    + amount(b10, oil) + ", drum keeps " + amount(d10, oil) + " mB");
            String reactorState = onServer(() -> {
                var reactor = com.gregtechceu.gtceu.api.machine.MetaMachine.getMachine(server().overworld(), r11)
                        instanceof com.gregtechceu.gtceu.api.machine.SimpleTieredMachine m ? m : null;
                if (reactor == null) return "no GT machine at " + r11;
                var hcl = reactor.exportFluids.getFluidInTank(0);
                return hcl.getAmount() + " mB of " + BuiltInRegistries.FLUID.getKey(hcl.getFluid())
                        + ", acid in machine " + reactor.importFluids.getFluidInTank(0).getAmount()
                        + ", salt left " + reactor.importItems.getStackInSlot(0).getCount();
            });
            check(reactorState.contains("hydrochloric") && !reactorState.startsWith("0 mB"),
                    "NTM barrel -> NTM duct -> GT chemical reactor (2 salt, circuit 1, LV): " + reactorState
                            + ", barrel left " + amount(b11, acid));
            itemContainers(acid, oil);
            goTo(FLUID_ROW.offset(6, 0, -3));
        }, 5);
        step("fluids: shot", () -> {
            look(FLUID_ROW.offset(6, 0, 0));
            shot("fluids");
        }, 10);
    }

    /** Fluid items: GT cells hold NTM's fluids; an NTM canister's fluid can be poured into GT's drum. */
    private static void itemContainers(net.minecraft.world.level.material.Fluid acid,
                                       net.minecraft.world.level.material.Fluid oil) {
        var cell = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("gtceu:universal_fluid_cell")));
        var h = cell.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM);
        int got = h == null ? -1 : h.fill(new net.neoforged.neoforge.fluids.FluidStack(acid, 1000),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        check(got > 0, "GT universal cell takes NTM sulfuric acid: " + got + " mB");
        // NTM's canisters are filled by NTM's own loaders; a full one must hand its fluid to GT (cells slots, drums)
        // NTM's canister / gas tank, filled with a fluid GT shares, must hand it to GT (cell slots, drums)
        var empties = new java.util.ArrayList<Item>();
        com.hbm.capability.FluidContainerRows.forEachFamilyFill((empty, family) -> empties.add(empty));
        for (String fid : List.of("hbm:diesel", "hbm:oxygen")) {
            net.minecraft.world.level.material.Fluid want = fluid(fid);
            ItemStack full = ItemStack.EMPTY;
            for (Item e : empties) {
                if (com.hbm.capability.FluidContainerRows.fillAmount(new ItemStack(e), want) <= 0) continue;
                full = com.hbm.capability.FluidContainerRows.filled(new ItemStack(e), want);
                break;
            }
            net.neoforged.neoforge.fluids.capability.IFluidHandlerItem fh = full.isEmpty() ? null
                    : full.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM);
            var drained = fh == null ? net.neoforged.neoforge.fluids.FluidStack.EMPTY : fh.drain(1000,
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE);
            check(!full.isEmpty() && drained.getFluid() == want && drained.getAmount() > 0, "NTM container ("
                    + BuiltInRegistries.ITEM.getKey(full.getItem()) + ") with " + fid + " gives GT "
                    + drained.getAmount() + " mB of " + BuiltInRegistries.FLUID.getKey(drained.getFluid()));
        }
    }

    private static void waitTicks(int ticks) {
        int[] left = {ticks / 5};
        steps.addLast(new Step("wait", new Runnable() {
            @Override
            public void run() {
                if (--left[0] > 0) steps.addFirst(new Step("wait", this, 5));
            }
        }, 5));
    }

    private static net.minecraft.world.level.material.Fluid fluid(String id) {
        return BuiltInRegistries.FLUID.get(ResourceLocation.parse(id));
    }

    private static net.minecraft.world.level.block.state.BlockState block(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState();
    }

    private static com.hbm.blockentity.machine.storage.BlockEntityBarrel barrel(BlockPos pos) {
        return (com.hbm.blockentity.machine.storage.BlockEntityBarrel) server().overworld().getBlockEntity(pos);
    }

    private static net.neoforged.neoforge.fluids.capability.IFluidHandler cap(BlockPos pos) {
        net.neoforged.neoforge.fluids.capability.IFluidHandler h = null;
        for (Direction d : new Direction[]{Direction.UP, null, Direction.WEST, Direction.EAST}) {
            h = server().overworld().getCapability(
                    net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, pos, d);
            if (h != null) break;
        }
        if (h == null) throw new IllegalStateException("no fluid handler at " + pos);
        return h;
    }

    private static int amount(BlockPos pos, net.minecraft.world.level.material.Fluid fluid) {
        return onServer(() -> {
            var h = cap(pos);
            int n = 0;
            for (int i = 0; i < h.getTanks(); i++) {
                var st = h.getFluidInTank(i);
                if (st.getFluid() == fluid) n += st.getAmount();
            }
            return n;
        });
    }

    /** Formula line (yellow, line 2) in real client tooltips: ours for NTM-only materials, GT's for linked items. */
    private static void formulas() {
        java.util.Map<String, String> expect = new java.util.LinkedHashMap<>();
        expect.put("hbm:ingot_pc", "(C₁₆H₁₄O₃)ₙ");
        expect.put("hbm:ingot_pu_mix", "(Pu²³⁹)₂Pu²⁴⁰");
        expect.put("hbm:ingot_australium", "Aus");
        expect.put("hbm:ingot_schrabidium", "Sa³²⁶");
        expect.put("hbm:gem_sodalite", null);
        step("formulas: tooltips", () -> {
            var mc = Minecraft.getInstance();
            for (var e : expect.entrySet()) {
                var stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(e.getKey())));
                var lines = stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player,
                        net.minecraft.world.item.TooltipFlag.Default.NORMAL);
                String second = lines.size() > 1 ? lines.get(1).getString() : "";
                long same = lines.stream().filter(l -> l.getString().equals(second)).count();
                boolean yellow = lines.size() > 1 && lines.get(1).getStyle().getColor() != null
                        && lines.get(1).getStyle().getColor().getValue() == 0xFFFF55;
                boolean ok = e.getValue() == null ? !second.isEmpty() : second.equals(e.getValue());
                check(ok && yellow && same == 1, "tooltip " + e.getKey() + ": line 2 '" + second + "' (yellow "
                        + yellow + ", " + same + "x)");
            }
            var icon = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("hbm:fluid_icon")));
            icon.set(com.hbm.items.ModDataComponents.FLUID_CONTENT.get(),
                    new com.hbm.inventory.fluid.FluidStackNTM(fluid("hbm:sulfuric_acid"), 1000));
            var text = icon.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player,
                    net.minecraft.world.item.TooltipFlag.Default.NORMAL).stream().map(l -> l.getString()).toList();
            check(text.stream().anyMatch(l -> l.contains("H₂SO₄")) && text.stream().anyMatch(l -> l.contains("K")
                            && l.toLowerCase().contains("temp")), "NTM fluid icon (sulfuric acid): " + text);
            var kero = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("hbm:fluid_icon")));
            kero.set(com.hbm.items.ModDataComponents.FLUID_CONTENT.get(),
                    new com.hbm.inventory.fluid.FluidStackNTM(fluid("hbm:kerosene"), 1000));
            var kt = kero.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player,
                    net.minecraft.world.item.TooltipFlag.Default.NORMAL).stream().map(l -> l.getString()).toList();
            check(kt.contains("C₁₂H₂₆"), "NTM-only fluid icon (kerosene): " + kt);
            var duct = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("hbm:fluid_duct_paintable")));
            duct.set(com.hbm.items.ModDataComponents.FLUID_CONTENT.get(),
                    new com.hbm.inventory.fluid.FluidStackNTM(fluid("hbm:sulfuric_acid"), 0, 0));
            var dt = duct.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player,
                    net.minecraft.world.item.TooltipFlag.Default.NORMAL).stream().map(l -> l.getString()).toList();
            check(dt.stream().noneMatch(l -> l.contains("H₂SO₄") || l.contains("Temperature")),
                    "NTM acid duct item has no fluid formula/temperature: " + dt);
        }, 5);
    }

    private static void jei() {
        ItemStack gearMold = new ItemStack(gtItem("gear_casting_mold"));
        ItemStack gear = new ItemStack(gtItem("steel_gear"));
        waitUntil("jei: runtime", () -> StandJeiPlugin.runtime != null, 20 * 60);
        step("jei: casting", () -> {
            var rt = StandJeiPlugin.runtime;
            var type = new mezz.jei.api.recipe.RecipeType<>(com.hbm.integration.recipeviewer.RecipePages.CASTING.id(),
                    com.hbm.integration.recipeviewer.CastingPage.Recipe.class);
            var focus = rt.getJeiHelpers().getFocusFactory().createFocus(
                    mezz.jei.api.recipe.RecipeIngredientRole.INPUT, mezz.jei.api.constants.VanillaTypes.ITEM_STACK, gearMold);
            var rows = rt.getRecipeManager().createRecipeLookup(type).limitFocus(List.of(focus)).get().toList();
            check(!rows.isEmpty() && rows.stream().anyMatch(r -> r.result().is(gear.getItem())),
                    "JEI casting page: GT gear mold has " + rows.size() + " casts, steel gear among them");
            rt.getRecipesGui().showRecipes(rt.getRecipeManager().getRecipeCategory(type), rows, List.of(focus));
        }, 30);
        step("jei: casting shot", () -> shot("jei-casting"), 5);
        step("jei: smelting", () -> {
            Minecraft.getInstance().setScreen(null);
            var rt = StandJeiPlugin.runtime;
            var type = new mezz.jei.api.recipe.RecipeType<>(
                    com.hbm.integration.recipeviewer.RecipePages.CRUCIBLE_SMELTING.id(),
                    com.hbm.integration.recipeviewer.CrucibleSmeltingPage.Recipe.class);
            var focus = rt.getJeiHelpers().getFocusFactory().createFocus(
                    mezz.jei.api.recipe.RecipeIngredientRole.INPUT, mezz.jei.api.constants.VanillaTypes.ITEM_STACK, gear);
            var rows = rt.getRecipeManager().createRecipeLookup(type).limitFocus(List.of(focus)).get().toList();
            long all = rt.getRecipeManager().createRecipeLookup(type).get().count();
            check(rows.size() == 1, "JEI crucible page: GT steel gear melts (" + rows.size() + " rows; page has "
                    + all + " rows)");
            rt.getRecipesGui().showRecipes(rt.getRecipeManager().getRecipeCategory(type), rows, List.of(focus));
        }, 30);
        step("jei: smelting shot", () -> shot("jei-smelting"), 5);
        step("jei: gt page", () -> {
            Minecraft.getInstance().setScreen(null);
            var rt = StandJeiPlugin.runtime;
            var type = rt.getJeiHelpers().getRecipeType(ResourceLocation.fromNamespaceAndPath("gtceu", "macerator"));
            check(type.isPresent(), "JEI has GT's macerator page");
            if (type.isEmpty()) return;
            var steel = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("hbm:ingot_steel")));
            var focus = rt.getJeiHelpers().getFocusFactory().createFocus(
                    mezz.jei.api.recipe.RecipeIngredientRole.INPUT, mezz.jei.api.constants.VanillaTypes.ITEM_STACK, steel);
            long rows = rt.getRecipeManager().createRecipeLookup(type.get()).limitFocus(List.of(focus)).get().count();
            check(rows > 0, "JEI GT macerator: NTM steel ingot is an input (" + rows + " rows)");
            showPage(rt, type.get(), focus);
        }, 30);
        step("jei: gt page shot", () -> shot("jei-gt-macerator"), 5);
        step("jei: modularui layout", () -> {
            // JEI 19.64+ breaks ModularUI's RecipeGuiLogicMixin (crash on inventory open); ntmgt swaps in its own.
            String v = net.neoforged.fml.ModList.get().getModFileById("jei").versionString();
            boolean newJei = new org.apache.maven.artifact.versioning.DefaultArtifactVersion(v)
                    .compareTo(new org.apache.maven.artifact.versioning.DefaultArtifactVersion("19.64")) >= 0;
            Class<?> logic;
            try {
                logic = Class.forName("mezz.jei.gui.recipes.RecipeGuiLogic");
            } catch (ClassNotFoundException e) {
                check(false, "JEI RecipeGuiLogic not found");
                return;
            }
            var names = java.util.Arrays.stream(logic.getDeclaredMethods()).map(java.lang.reflect.Method::getName).toList();
            boolean ours = names.stream().anyMatch(n -> n.contains("ntmgt$muiRecipeSize"));
            boolean mui = names.stream().anyMatch(n -> n.contains("modularui$useCategoryMaxHeightForLayout"));
            check(newJei ? ours && !mui : mui && !ours, "JEI " + v + " recipe layout: ntmgt fix " + ours
                    + ", ModularUI mixin " + mui + "; GT page open: " + Minecraft.getInstance().screen);
        }, 5);
        step("jei: shared fluid uses", () -> {
            Minecraft.getInstance().setScreen(null);
            var rt = StandJeiPlugin.runtime;
            var ft = net.neoforged.neoforge.fluids.FluidStack.class;
            var type = mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK;
            var named = new java.util.ArrayList<String>();
            for (var fs : rt.getIngredientManager().getAllIngredients(type)) {
                if (!fs.getHoverName().getString().equals("Sulfuric Acid")) continue;
                var focus = rt.getJeiHelpers().getFocusFactory().createFocus(
                        mezz.jei.api.recipe.RecipeIngredientRole.INPUT, type, fs);
                long cats = rt.getRecipeManager().createRecipeCategoryLookup().limitFocus(List.of(focus)).get().count();
                named.add(BuiltInRegistries.FLUID.getKey(fs.getFluid()) + " " + fs.getComponentsPatch() + " uses in "
                        + cats + " pages");
            }
            // NTM's poured-in-the-world hbm:sulfuric_acid_fluid is hidden; GT's linked one too
            check(named.size() == 1 && named.get(0).startsWith("hbm:sulfuric_acid {}") && !named.get(0).contains(" 0 pages"),
                    "JEI 'Sulfuric Acid' fluids: " + named);
            for (String fid : List.of("hbm:pheromone_m", "hbm:sulfuric_acid", "hbm:hydrogen")) {
                var fs = new net.neoforged.neoforge.fluids.FluidStack(fluid(fid), 1000);
                var made = new java.util.ArrayList<String>();
                for (var role : List.of(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT,
                        mezz.jei.api.recipe.RecipeIngredientRole.INPUT)) {
                    var focus = rt.getJeiHelpers().getFocusFactory().createFocus(role, type, fs);
                    made.add(role + " " + rt.getRecipeManager().createRecipeCategoryLookup().limitFocus(List.of(focus))
                            .get().map(c -> c.getRecipeType().getUid().toString()).toList());
                }
                check(!made.get(0).endsWith("[]"), "JEI R/U for " + fid + ": " + made);
            }
        }, 30);
        step("jei: duplicate names dump", () -> {
            var rt = StandJeiPlugin.runtime;
            var byName = new java.util.TreeMap<String, java.util.List<String>>();
            for (var fs : rt.getIngredientManager().getAllIngredients(mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK))
                byName.computeIfAbsent(fs.getHoverName().getString(), k -> new java.util.ArrayList<>())
                        .add("fluid " + BuiltInRegistries.FLUID.getKey(fs.getFluid()) + " " + fs.getComponentsPatch());
            for (ItemStack st : rt.getIngredientManager().getAllIngredients(mezz.jei.api.constants.VanillaTypes.ITEM_STACK)) {
                String n = st.getHoverName().getString();
                if (!BuiltInRegistries.ITEM.getKey(st.getItem()).getNamespace().matches("hbm|gtceu")) continue;
                if (!(n.contains("Bucket") || n.contains("Cell") || n.contains("Canister") || n.contains("Tank")
                        || st.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM) != null))
                    continue;
                var h = st.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM);
                String in = h == null || h.getTanks() == 0 ? "" : " holds " + BuiltInRegistries.FLUID.getKey(
                        h.getFluidInTank(0).getFluid());
                byName.computeIfAbsent(n, k -> new java.util.ArrayList<>())
                        .add("item " + BuiltInRegistries.ITEM.getKey(st.getItem()) + in);
            }
            try (var w = java.nio.file.Files.newBufferedWriter(Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("ntmgt-jei-names.txt"))) {
                for (var e : byName.entrySet()) w.write(e.getKey() + "\t" + e.getValue() + "\n");
            } catch (java.io.IOException e) { throw new RuntimeException(e); }
        }, 10);
        step("jei: buckets of NTM fluids", () -> {
            var rt = StandJeiPlugin.runtime;
            var out = new java.util.ArrayList<String>();
            boolean ok = true;
            for (ItemStack st : rt.getIngredientManager().getAllIngredients(mezz.jei.api.constants.VanillaTypes.ITEM_STACK)) {
                String name = st.getHoverName().getString();
                if (!name.contains("Sulfuric Acid") || !name.contains("Bucket") || name.contains("Diluted")) continue;
                var copy = st.copy();
                var h = copy.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM);
                String res = "no handler";
                if (h != null) {
                    var drained = h.drain(1000, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                    ItemStack after = h.getContainer();
                    res = drained.getAmount() + " mB -> " + BuiltInRegistries.ITEM.getKey(after.getItem()) + " "
                            + after.getComponentsPatch() + " stack " + after.getMaxStackSize();
                    ok &= after.isEmpty() || after.getItem() == net.minecraft.world.item.Items.BUCKET
                            && after.getComponentsPatch().isEmpty();
                }
                out.add(BuiltInRegistries.ITEM.getKey(st.getItem()) + " " + st.getComponentsPatch() + ": " + res);
            }
            check(ok, "JEI 'Sulfuric Acid' buckets empty into a plain bucket: " + out);
        }, 10);
        step("jei: close", () -> Minecraft.getInstance().setScreen(null), 5);
    }

    private static <T> void showPage(mezz.jei.api.runtime.IJeiRuntime rt, mezz.jei.api.recipe.RecipeType<T> type,
                                     mezz.jei.api.recipe.IFocus<?> focus) {
        List<T> rows = rt.getRecipeManager().createRecipeLookup(type).limitFocus(List.of(focus)).get().toList();
        rt.getRecipesGui().showRecipes(rt.getRecipeManager().getRecipeCategory(type), rows, List.of(focus));
    }

    // ---------------------------------------------------------------- actions

    private static void goTo(BlockPos feet) {
        onServer(() -> {
            player().teleportTo(server().overworld(), feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0, 30);
            return null;
        });
    }

    /** Stand two blocks away from the block on a free side (within reach, not inside a multiblock). */
    private static void goToNear(BlockPos target) {
        BlockPos spot = onServer(() -> {
            ServerLevel level = server().overworld();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                for (int dist = 2; dist <= 3; dist++) {
                    BlockPos feet = new BlockPos(target.getX(), ORIGIN.getY() + 1, target.getZ()).relative(d, dist);
                    if (level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()) return feet;
                }
            }
            return new BlockPos(target.getX(), ORIGIN.getY() + 1, target.getZ() - 2);
        });
        goTo(spot);
    }

    private static void setup() {
        onServer(() -> {
            MinecraftServer server = server();
            ServerLevel level = server.overworld();
            ServerPlayer sp = player();
            for (BlockPos p : BlockPos.betweenClosed(ORIGIN.offset(-2, -1, -2), ORIGIN.offset(20, 8, 20))) {
                level.setBlockAndUpdate(p, p.getY() <= ORIGIN.getY() ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
            }
            level.setBlockAndUpdate(SMALL, ModBlocks.FOUNDRY_MOLD.get().defaultBlockState());
            level.setBlockAndUpdate(LARGE, ModBlocks.FOUNDRY_BASIN.get().defaultBlockState());
            sp.setGameMode(GameType.SURVIVAL);
            sp.getInventory().clearContent();
            sp.getInventory().selected = 0;
            sp.teleportTo(level, ORIGIN.getX() + 4.5, ORIGIN.getY() + 1, ORIGIN.getZ() + 0.5, 0, 30);
            level.setDayTime(6000);
            return null;
        });
        check(true, "setup: platform, foundry mold " + SMALL + ", basin " + LARGE);
    }

    private static void hold(ItemStack stack) {
        onServer(() -> {
            ServerPlayer sp = player();
            sp.getInventory().selected = 0;
            sp.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
            sp.inventoryMenu.broadcastChanges();
            return null;
        });
    }

    private static void click(BlockPos pos) {
        clickFace(pos, Direction.UP);
    }

    /** A real right click: aim at the block, MultiPlayerGameMode#useItemOn (client handling + packet to the server). */
    private static void clickFace(BlockPos pos, Direction face) {
        Minecraft mc = Minecraft.getInstance();
        look(pos);
        Vec3 hit = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
    }

    private static void look(BlockPos pos) {
        var p = Minecraft.getInstance().player;
        Vec3 d = Vec3.atCenterOf(pos).subtract(p.getEyePosition());
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        p.setYRot((float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90));
        p.setXRot((float) -Math.toDegrees(Math.atan2(d.y, h)));
        p.yRotO = p.getYRot();
        p.xRotO = p.getXRot();
    }

    private static void slotClick(int slot, ClickType type) {
        Minecraft mc = Minecraft.getInstance();
        mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, slot, 0, type, mc.player);
    }

    private static int menuSlotOf(Item item) {
        var menu = Minecraft.getInstance().player.containerMenu;
        for (int i = 1; i < menu.slots.size(); i++) {
            var s = menu.slots.get(i);
            if (!(s.container instanceof net.minecraft.world.entity.player.Inventory)) continue;
            if (item == ItemStack.EMPTY.getItem() ? !s.hasItem() : s.getItem().is(item)) return i;
        }
        return -1;
    }

    private static void reset(BlockPos pos) {
        onServer(() -> {
            if (server().overworld().getBlockEntity(pos) instanceof BlockEntityFoundryCastingBase c) {
                c.inventory.set(0, ItemStack.EMPTY);
                c.inventory.set(1, ItemStack.EMPTY);
                c.amount = 0;
                c.type = null;
                c.setChanged();
            }
            return null;
        });
    }

    /** Pour like a crucible spout does: standardCheck, then fill to capacity. */
    private static void pour(BlockPos pos, String material) {
        boolean accepted = onServer(() -> {
            ServerLevel level = server().overworld();
            if (!(level.getBlockEntity(pos) instanceof BlockEntityFoundryCastingBase c)) return false;
            NTMMaterial mat = Mats.matByName.get(material);
            if (!c.standardCheck(level, pos, Direction.UP, new Mats.MaterialStack(mat, 1))) return false;
            c.type = mat;
            c.amount = c.getCapacity();
            c.setChanged();
            return true;
        });
        check(accepted, "pour " + material + " into " + pos + " accepted");
    }

    private static void waitUntil(String name, BooleanSupplier condition, int maxTicks) {
        int[] waited = {0};
        steps.addLast(new Step(name, new Runnable() {
            @Override
            public void run() {
                if (condition.getAsBoolean()) return;
                if (++waited[0] > maxTicks / 5) {
                    check(false, name + ": not within " + maxTicks + " ticks");
                    return;
                }
                steps.addFirst(new Step(name, this, 5));
            }
        }, 5));
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        shots++;
        Screenshot.grab(mc.gameDirectory, "ntmgt-stand-" + name + ".png", mc.getMainRenderTarget(), msg -> {});
    }

    private static void clearInventory() {
        onServer(() -> {
            player().getInventory().clearContent();
            player().inventoryMenu.broadcastChanges();
            return null;
        });
    }

    // ---------------------------------------------------------------- reads

    private static ItemStack serverSlot(BlockPos pos, int slot) {
        return onServer(() -> server().overworld().getBlockEntity(pos) instanceof BlockEntityFoundryCastingBase c
                ? c.inventory.get(slot).copy() : ItemStack.EMPTY);
    }

    private static BlockEntityMachineStrandCaster caster() {
        return (BlockEntityMachineStrandCaster) server().overworld().getBlockEntity(casterCore);
    }

    private static BlockEntityCrucible crucible() {
        return (BlockEntityCrucible) server().overworld().getBlockEntity(crucibleCore);
    }

    private static ItemStack casterSlot(int slot) {
        return onServer(() -> caster().inventory.get(slot).copy());
    }

    private static int heldCount() {
        return onServer(() -> player().getMainHandItem().getCount());
    }

    private static int invCount(Item item) {
        return onServer(() -> player().getInventory().countItem(item));
    }

    private static MinecraftServer server() {
        return Minecraft.getInstance().getSingleplayerServer();
    }

    private static ServerPlayer player() {
        return server().getPlayerList().getPlayers().getFirst();
    }

    private static <T> T onServer(Callable<T> task) {
        return server().submit(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).join();
    }

    private static Item gtItem(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("gtceu", path));
    }

    private static String id(ItemStack stack) {
        return stack.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static String screen(Minecraft mc) {
        return mc.screen == null ? "none" : mc.screen.getClass().getSimpleName();
    }

    // ---------------------------------------------------------------- result

    private static void step(String name, Runnable action, int waitAfter) {
        steps.addLast(new Step(name, action, waitAfter));
    }

    private static void check(boolean ok, String text) {
        if (!ok) fails++;
        lines.add((ok ? "PASS " : "FAIL ") + text);
        NtmGt.LOGGER.info("[ntmgt] stand {} {}", ok ? "PASS" : "FAIL", text);
    }

    private static void finish(Minecraft mc, String error) {
        if (done) return;
        done = true;
        if (error != null) check(false, error);
        lines.add("RESULT " + (fails == 0 ? "PASS" : "FAIL") + " (" + fails + " failed of " + lines.size() + ")");
        try {
            Files.write(Path.of(mc.gameDirectory.getPath(), "ntmgt-stand.txt"), lines);
        } catch (Exception e) {
            NtmGt.LOGGER.error("[ntmgt] stand result not written", e);
        }
        ExitWatch.start();
        mc.stop();
    }
}
