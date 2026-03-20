package org.vmstudio.firetorch.fabric;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseFireBlock;
import org.vmstudio.firetorch.core.client.FireTorchAddonClient;
import org.vmstudio.firetorch.core.client.FireTorchLogic;
import org.vmstudio.firetorch.core.common.FireTorchNetworking;
import org.vmstudio.firetorch.core.server.FireTorchAddonServer;
import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.VisorAPI;

public class FireTorchMod implements ModInitializer {
    @Override
    public void onInitialize() {

        ServerPlayNetworking.registerGlobalReceiver(FireTorchNetworking.IGNITE_BLOCK_PACKET, (server, player, handler, buf, responseSender) -> {
            BlockPos targetPos = buf.readBlockPos();
            boolean isMainHand = buf.readBoolean();

            server.execute(() -> {
                InteractionHand hand = isMainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                ItemStack stack = player.getItemInHand(hand);

                if ((stack.getItem() == Items.TORCH || stack.getItem() == Items.SOUL_TORCH) && player.distanceToSqr(targetPos.getCenter()) < 64.0) {
                    if (player.level().getBlockState(targetPos).isAir() || player.level().getBlockState(targetPos).canBeReplaced()) {
                        player.level().setBlockAndUpdate(targetPos, BaseFireBlock.getState(player.level(), targetPos));
                    }
                }
            });
        });

        if(ModLoader.get().isDedicatedServer()){
            VisorAPI.registerAddon(
                    new FireTorchAddonServer()
            );
        }else{
            VisorAPI.registerAddon(
                    new FireTorchAddonClient()
            );

            FireTorchLogic.bridge = (pos, isMainHand) -> {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeBlockPos(pos);
                buf.writeBoolean(isMainHand);
                ClientPlayNetworking.send(FireTorchNetworking.IGNITE_BLOCK_PACKET, buf);
            };

            ClientTickEvents.END_CLIENT_TICK.register(client -> FireTorchLogic.tick());
        }
    }
}
