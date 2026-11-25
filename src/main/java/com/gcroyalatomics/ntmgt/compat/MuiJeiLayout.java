package com.gcroyalatomics.ntmgt.compat;

import brachy.modularui.integration.jei.recipe.ModularUIJeiCategory;
import com.gcroyalatomics.ntmgt.NtmGt;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import net.minecraft.client.renderer.Rect2i;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Page layout for ModularUI recipe categories (GT's) on JEI 19.64+, in place of ModularUI's own mixin that this JEI
 * breaks (see mixinplugin.JeiMixinPlugin): the size JEI uses for every recipe on a page grows to the category's
 * largest recipe, keeping JEI's border and buttons around it, as ModularUI's upstream fix does. JEI's layout types
 * are internal and differ between versions, so they are reached by name.
 */
public final class MuiJeiLayout {
    private static Method recipeLayout, width, height;
    private static Constructor<?> size;
    private static boolean failed;

    private MuiJeiLayout() {
    }

    /** {@code layoutWithButtons} is an IRecipeLayoutWithButtons, {@code original} the ImmutableSize2i JEI computed. */
    public static Object size(Object layoutWithButtons, Object original) {
        if (failed || original == null) return original;
        try {
            if (size == null) {
                ClassLoader cl = original.getClass().getClassLoader();
                recipeLayout = Class.forName("mezz.jei.gui.recipes.IRecipeLayoutWithButtons", false, cl).getMethod("getRecipeLayout");
                Class<?> sizeClass = original.getClass();
                width = sizeClass.getMethod("width");
                height = sizeClass.getMethod("height");
                size = sizeClass.getConstructor(int.class, int.class);
            }
            IRecipeLayoutDrawable<?> layout = (IRecipeLayoutDrawable<?>) recipeLayout.invoke(layoutWithButtons);
            if (!(layout.getRecipeCategory() instanceof ModularUIJeiCategory<?> mui)) return original;
            Rect2i rect = layout.getRect();
            int extraW = (int) width.invoke(original) - rect.getWidth();
            int extraH = (int) height.invoke(original) - rect.getHeight();
            return size.newInstance(mui.getMaxWidth() + extraW, mui.getMaxHeight() + extraH);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            failed = true;
            NtmGt.LOGGER.warn("[ntmgt] JEI layout fix for ModularUI categories is off: {}", e.toString());
            return original;
        }
    }
}
