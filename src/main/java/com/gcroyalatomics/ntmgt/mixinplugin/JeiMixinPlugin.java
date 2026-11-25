package com.gcroyalatomics.ntmgt.mixinplugin;

import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps JEI 19.64+ from crashing with GTCEu 8.0's bundled ModularUI 3.3.1 build 8 (not this mod's bug).
 * ModularUI's jei.RecipeGuiLogicMixin modifies the local {@code recipeHeight} of
 * RecipeGuiLogic#getVisibleRecipeLayoutsWithButtons; JEI 19.64 replaced it with an ImmutableSize2i, the required
 * injection finds nothing and the game crashes when JEI builds its recipe screen (inventory open). ModularUI fixed
 * it upstream (ModularUI-Modern #147), GTCEu does not ship that build yet.
 * <p>
 * When the loaded JEI has no such local, that one ModularUI mixin is taken out of ModularUI's mixin config before it
 * is applied, and {@code MuiRecipeSizeMixin} does the same job the upstream fix does. With an older JEI nothing
 * changes. Mixin keeps its config internals in a package opened only to itself, hence Unsafe for the field reads.
 */
public final class JeiMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOG = LoggerFactory.getLogger("ntmgt");
    private static final String GUI_LOGIC = "mezz.jei.gui.recipes.RecipeGuiLogic";
    private static final String MUI_CONFIG = "modularui.mixins.json";
    private static final String MUI_MIXIN = "jei.RecipeGuiLogicMixin";
    private static final String MUI_MIXIN_CLASS = "brachy.modularui.core.mixins." + MUI_MIXIN;

    private boolean newJeiLayout;

    @Override
    public void onLoad(String mixinPackage) {
        newJeiLayout = jeiLacksRecipeHeight();
        if (!newJeiLayout) return;
        try {
            if (dropMuiMixin()) LOG.info("[ntmgt] ModularUI {} replaced by ntmgt's layout fix", MUI_MIXIN);
            else LOG.info("[ntmgt] ModularUI {} not present, nothing to replace", MUI_MIXIN);
        } catch (Throwable t) {
            LOG.warn("[ntmgt] could not take ModularUI's {} out; JEI may crash with this JEI version", MUI_MIXIN, t);
        }
    }

    /**
     * True when JEI's RecipeGuiLogic layout method no longer has the local ModularUI's mixin targets. The class is
     * read straight from JEI's jar: ModLauncher's mixin service cannot hand out untransformed bytecode, and
     * transformed would mean running mixins on it now.
     */
    private static boolean jeiLacksRecipeHeight() {
        try {
            var jei = FMLLoader.getLoadingModList().getModFileById("jei");
            if (jei == null) return false;
            String version = jei.getMods().isEmpty() ? "?" : jei.getMods().get(0).getVersion().toString();
            Path file = jei.getFile().findResource(GUI_LOGIC.replace('.', '/') + ".class");
            if (!Files.exists(file)) {
                LOG.info("[ntmgt] JEI {}: no {}, ModularUI left as is", version, GUI_LOGIC);
                return false;
            }
            ClassNode node = new ClassNode();
            new ClassReader(Files.readAllBytes(file)).accept(node, 0);
            for (MethodNode m : node.methods) {
                if (!m.name.equals("getVisibleRecipeLayoutsWithButtons") || m.localVariables == null) continue;
                boolean old = m.localVariables.stream().anyMatch(v -> v.name.equals("recipeHeight"));
                LOG.info("[ntmgt] JEI {}: recipe layout is {}", version, old ? "old, ModularUI left as is" : "new");
                return !old;
            }
            LOG.info("[ntmgt] JEI {}: layout method not found, ModularUI left as is", version);
        } catch (Throwable t) {
            LOG.warn("[ntmgt] could not read JEI's recipe layout, ModularUI left as is", t);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean dropMuiMixin() throws ReflectiveOperationException {
        Unsafe u = unsafe();
        Class<?> configHandle = Class.forName("org.spongepowered.asm.mixin.transformer.Config");
        Map<String, ?> all = (Map<String, ?>) staticField(u, configHandle, "allConfigs");
        Object handle = all.get(MUI_CONFIG);
        if (handle == null) return false; // no ModularUI
        Object config = field(u, handle, "config");
        boolean removed = false;
        for (String list : new String[]{"mixinClasses", "mixinClassesClient", "mixinClassesServer"}) {
            List<String> names = (List<String>) field(u, config, list);
            if (names != null) removed |= names.remove(MUI_MIXIN);
        }
        // Already prepared (selected in an earlier pass): drop the prepared mixin too.
        removed |= ((List<IMixinInfo>) field(u, config, "pendingMixins")).removeIf(JeiMixinPlugin::isMuiMixin);
        removed |= ((List<IMixinInfo>) field(u, config, "mixins")).removeIf(JeiMixinPlugin::isMuiMixin);
        for (List<IMixinInfo> perTarget : ((Map<String, List<IMixinInfo>>) field(u, config, "mixinMapping")).values())
            removed |= perTarget.removeIf(JeiMixinPlugin::isMuiMixin);
        return removed;
    }

    private static boolean isMuiMixin(IMixinInfo info) {
        return info.getClassName().equals(MUI_MIXIN_CLASS);
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (Unsafe) f.get(null);
    }

    @SuppressWarnings("deprecation")
    private static Object staticField(Unsafe u, Class<?> owner, String name) throws NoSuchFieldException {
        Field f = owner.getDeclaredField(name);
        return u.getObject(u.staticFieldBase(f), u.staticFieldOffset(f));
    }

    private static Object field(Unsafe u, Object owner, String name) throws NoSuchFieldException {
        return u.getObject(owner, u.objectFieldOffset(owner.getClass().getDeclaredField(name)));
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !mixinClassName.endsWith(".MuiRecipeSizeMixin") || newJeiLayout;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
