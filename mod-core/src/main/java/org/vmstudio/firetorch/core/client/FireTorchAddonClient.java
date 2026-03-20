package org.vmstudio.firetorch.core.client;

import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.common.addon.VisorAddon;
import org.vmstudio.firetorch.core.client.overlays.VROverlayFireTorch;
import org.vmstudio.firetorch.core.common.VisorFireTorch;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FireTorchAddonClient implements VisorAddon {
    @Override
    public void onAddonLoad() {
        VisorAPI.addonManager().getRegistries()
                .overlays()
                .registerComponents(
                        List.of(
                                new VROverlayFireTorch(
                                        this,
                                        VROverlayFireTorch.ID
                                )
                        )
                );
    }

    @Override
    public @Nullable String getAddonPackagePath() {
        return "org.vmstudio.firetorch.core.client";
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
