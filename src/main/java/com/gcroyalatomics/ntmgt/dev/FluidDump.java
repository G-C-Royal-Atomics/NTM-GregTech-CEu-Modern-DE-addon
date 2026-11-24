package com.gcroyalatomics.ntmgt.dev;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Dev-only (selftest): every gtceu/hbm/ntmgt fluid with temperature and how many GT recipes take/give it. */
final class FluidDump {
    private FluidDump() {}

    static void write(MinecraftServer server) throws IOException {
        Map<Fluid, int[]> use = new HashMap<>();
        for (RecipeHolder<?> h : server.getRecipeManager().getRecipes()) {
            if (!(h.value() instanceof GTRecipe r)) continue;
            count(r.getInputContents(FluidRecipeCapability.CAP), use, 0);
            count(r.getOutputContents(FluidRecipeCapability.CAP), use, 1);
        }
        List<String> lines = new ArrayList<>();
        lines.add("# fluid\ttemperature_K\tgt_recipes_in\tgt_recipes_out\tname");
        for (Fluid f : BuiltInRegistries.FLUID) {
            var id = BuiltInRegistries.FLUID.getKey(f);
            if (!Set.of("gtceu", "hbm", "ntmgt").contains(id.getNamespace())) continue;
            if (f instanceof FlowingFluid ff && ff.getSource() != f) continue;
            int[] u = use.getOrDefault(f, new int[2]);
            lines.add(id + "\t" + f.getFluidType().getTemperature() + "\t" + u[0] + "\t" + u[1] + "\t"
                    + f.getFluidType().getDescription(new FluidStack(f, 1000)).getString());
        }
        Files.write(Path.of("ntmgt-fluids.txt"), lines);
    }

    private static void count(List<Content> contents, Map<Fluid, int[]> use, int i) {
        Set<Fluid> seen = new HashSet<>();
        for (Content c : contents) {
            if (c.content() instanceof SizedFluidIngredient s) {
                for (FluidStack st : s.getFluids()) if (seen.add(st.getFluid())) use.computeIfAbsent(st.getFluid(), k -> new int[2])[i]++;
            }
        }
    }
}
