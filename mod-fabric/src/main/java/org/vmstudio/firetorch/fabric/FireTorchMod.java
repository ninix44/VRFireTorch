package org.vmstudio.firetorch.fabric;

import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.firetorch.core.client.FireTorchAddonClient;
import org.vmstudio.firetorch.core.server.FireTorchAddonServer;
import net.fabricmc.api.ModInitializer;

public class FireTorchMod implements ModInitializer {
    @Override
    public void onInitialize() {
        if(ModLoader.get().isDedicatedServer()){
            VisorAPI.registerAddon(
                    new FireTorchAddonServer()
            );
        }else{
            VisorAPI.registerAddon(
                    new FireTorchAddonClient()
            );
        }
    }
}
