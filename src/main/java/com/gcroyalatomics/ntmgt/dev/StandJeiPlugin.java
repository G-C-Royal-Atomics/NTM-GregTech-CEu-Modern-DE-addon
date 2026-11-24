package com.gcroyalatomics.ntmgt.dev;

import com.gcroyalatomics.ntmgt.NtmGt;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Dev-only: hands JEI's runtime to the in-game stand (MoldStand); does nothing outside -Dntmgt.moldstand. */
@JeiPlugin
public final class StandJeiPlugin implements IModPlugin {
    static volatile @Nullable IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(NtmGt.MOD_ID, "stand");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        if (Boolean.getBoolean("ntmgt.moldstand")) runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }
}
