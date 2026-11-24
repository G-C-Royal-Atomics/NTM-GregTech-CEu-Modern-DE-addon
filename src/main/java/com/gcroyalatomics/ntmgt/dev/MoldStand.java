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
