package org.vmstudio.firetorch.core.server;

import org.vmstudio.visor.api.common.addon.VisorAddon;
import org.vmstudio.firetorch.core.common.VisorFireTorch;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FireTorchAddonServer implements VisorAddon {
    @Override
    public void onAddonLoad() {

    }

    @Override
    public @Nullable String getAddonPackagePath() {
        return "org.vmstudio.firetorch.core.server";
    }

    @Override
    public @NotNull String getAddonId() {
        return VisorFireTorch.MOD_ID;
    }

    @Override
    public @NotNull Component getAddonName() {
        return Component.literal(VisorFireTorch.MOD_NAME);
    }

    @Override
    public String getModId() {
        return VisorFireTorch.MOD_ID;
    }
}
