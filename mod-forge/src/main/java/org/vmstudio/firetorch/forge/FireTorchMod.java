package org.vmstudio.firetorch.forge;

import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.firetorch.core.client.FireTorchAddonClient;
import org.vmstudio.firetorch.core.common.VisorFireTorch;
import org.vmstudio.firetorch.core.server.FireTorchAddonServer;
import net.minecraftforge.fml.common.Mod;

@Mod(VisorFireTorch.MOD_ID)
public class FireTorchMod {
    public FireTorchMod(){
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
