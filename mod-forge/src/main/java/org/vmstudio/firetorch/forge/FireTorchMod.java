package org.vmstudio.firetorch.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.vmstudio.firetorch.core.client.FireTorchAddonClient;
import org.vmstudio.firetorch.core.client.FireTorchLogic;
import org.vmstudio.firetorch.core.common.FireTorchNetworking;
import org.vmstudio.firetorch.core.common.VisorFireTorch;
import org.vmstudio.firetorch.core.server.FireTorchAddonServer;
import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.VisorAPI;

import java.util.function.Supplier;

@Mod(VisorFireTorch.MOD_ID)
public class FireTorchMod {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        FireTorchNetworking.IGNITE_BLOCK_PACKET,
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    public FireTorchMod(){
        CHANNEL.registerMessage(0, IgnitePacket.class, IgnitePacket::encode, IgnitePacket::decode, IgnitePacket::handle);

        if (!ModLoader.get().isDedicatedServer()) {
            FireTorchLogic.bridge = (pos, isMainHand) -> CHANNEL.sendToServer(new IgnitePacket(pos, isMainHand));
            MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
        }

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

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            FireTorchLogic.tick();
        }
    }

    public static class IgnitePacket {
        private final BlockPos pos;
        private final boolean isMainHand;

        public IgnitePacket(BlockPos pos, boolean isMainHand) {
            this.pos = pos;
            this.isMainHand = isMainHand;
        }

        public static void encode(IgnitePacket msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.pos);
            buf.writeBoolean(msg.isMainHand);
        }

        public static IgnitePacket decode(FriendlyByteBuf buf) {
            return new IgnitePacket(buf.readBlockPos(), buf.readBoolean());
        }

        public static void handle(IgnitePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                var player = ctx.get().getSender();
                if (player == null) return;

                InteractionHand hand = msg.isMainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                ItemStack stack = player.getItemInHand(hand);

                if ((stack.getItem() == Items.TORCH || stack.getItem() == Items.SOUL_TORCH) && player.distanceToSqr(msg.pos.getCenter()) < 64.0) {
                    if (player.level().getBlockState(msg.pos).isAir() || player.level().getBlockState(msg.pos).canBeReplaced()) {
                        player.level().setBlockAndUpdate(msg.pos, BaseFireBlock.getState(player.level(), msg.pos));
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
