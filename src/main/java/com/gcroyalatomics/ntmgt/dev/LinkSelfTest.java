package com.gcroyalatomics.ntmgt.dev;

import com.gcroyalatomics.ntmgt.NtmGt;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/**
 * Dev-only check, enabled by -Dntmgt.selftest=true (set in the runServer config, never in a real game).
 * Writes run/ntmgt-selftest.txt: for every NTM item, how many recipes of each type accept it as an input
 * and produce it, and which GT duplicates still exist.
 */
public final class LinkSelfTest {
    private LinkSelfTest() {}

    public static void onServerStarted(ServerStartedEvent event) {
        var recipes = event.getServer().getRecipeManager().getRecipes();
        var access = event.getServer().registryAccess();
        Map<String, int[]> in = new TreeMap<>(), out = new TreeMap<>();
        for (RecipeHolder<?> holder : recipes) {
            var recipe = holder.value();
            String type = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).toString();
            for (Ingredient ing : recipe.getIngredients()) {
                for (ItemStack s : ing.getItems()) count(in, s.getItem(), type);
            }
            ItemStack res = recipe.getResultItem(access);
            if (!res.isEmpty()) count(out, res.getItem(), type);
            if (recipe instanceof com.gregtechceu.gtceu.api.recipe.GTRecipe gt) GtRecipeScan.scan(gt, type, in, out);
        }
        Path file = Path.of("ntmgt-selftest.txt");
        try (BufferedWriter w = Files.newBufferedWriter(file)) {
            w.write("# item\tdirection\trecipe type\tcount\n");
            for (var e : in.entrySet()) w.write(e.getKey() + "\tin\t" + e.getValue()[0] + "\n");
            for (var e : out.entrySet()) w.write(e.getKey() + "\tout\t" + e.getValue()[0] + "\n");
            for (Item item : BuiltInRegistries.ITEM) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (id.getNamespace().equals("gtceu")) w.write("gtitem\t" + id.getPath() + "\n");
                if (id.getNamespace().equals("ntmgt")) w.write("ntmgtitem\t" + id.getPath() + "\n");
            }
            MoldSelfTest.run(event.getServer().overworld(), w);
            MeltSelfTest.run(w);
        } catch (IOException | RuntimeException e) {
            NtmGt.LOGGER.error("[ntmgt] selftest write failed", e);
        }
        NtmGt.LOGGER.info("[ntmgt] selftest written to {}", file.toAbsolutePath());
        event.getServer().halt(false); // dev run only: stop once the report is written
    }

    static void count(Map<String, int[]> map, Item item, String type) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (!id.getNamespace().equals("hbm")) return;
        map.computeIfAbsent(id + "\t" + type, k -> new int[1])[0]++;
    }
}
