package org.vmstudio.firetorch.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
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
            FireTorchLogic.bridge = (clickedPos, face, isMainHand) -> CHANNEL.sendToServer(new IgnitePacket(clickedPos, face, isMainHand));
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
        private final Direction face;
        private final boolean isMainHand;

        public IgnitePacket(BlockPos pos, Direction face, boolean isMainHand) {
            this.pos = pos;
            this.face = face;
            this.isMainHand = isMainHand;
        }

        public static void encode(IgnitePacket msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.pos);
            buf.writeEnum(msg.face);
            buf.writeBoolean(msg.isMainHand);
        }

        public static IgnitePacket decode(FriendlyByteBuf buf) {
            return new IgnitePacket(buf.readBlockPos(), buf.readEnum(Direction.class), buf.readBoolean());
        }

        public static void handle(IgnitePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                var player = ctx.get().getSender();
                if (player == null) return;

                InteractionHand hand = msg.isMainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                ItemStack stack = player.getItemInHand(hand);

                if ((stack.getItem() == Items.TORCH || stack.getItem() == Items.SOUL_TORCH) && player.distanceToSqr(msg.pos.getCenter()) < 64.0) {
                    var level = player.level();
                    var clickedState = level.getBlockState(msg.pos);

                    if (TntBlock.class.isInstance(clickedState.getBlock())) {
                        TntBlock.explode(level, msg.pos);
                        level.removeBlock(msg.pos, false);
                    } else if (isTorchIgnitable(clickedState)) {
                        boolean replaceInPlace = isReplaceableIgnitable(clickedState);
                        BlockPos firePos = replaceInPlace ? msg.pos : msg.pos.relative(msg.face);
                        if ((level.getBlockState(firePos).isAir() || level.getBlockState(firePos).canBeReplaced())
                            && (replaceInPlace ? canReplaceIgnitableWithFire(level, firePos) : BaseFireBlock.canBePlacedAt(level, firePos, msg.face))) {
                            level.setBlockAndUpdate(firePos, BaseFireBlock.getState(level, firePos));
                        }
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    private static boolean isTorchIgnitable(BlockState state) {
        return state.is(Blocks.TNT)
            || state.is(Blocks.CRAFTING_TABLE)
            || state.is(Blocks.LECTERN)
            || state.is(Blocks.GRASS)
            || state.is(Blocks.TALL_GRASS)
            || state.is(Blocks.FERN)
            || state.is(Blocks.LARGE_FERN)
            || state.is(Blocks.DEAD_BUSH)
            || state.is(BlockTags.SAPLINGS)
            || state.is(BlockTags.LOGS)
            || state.is(BlockTags.PLANKS)
            || state.is(BlockTags.LEAVES)
            || state.is(BlockTags.WOOL)
            || state.is(BlockTags.WOODEN_FENCES)
            || state.is(BlockTags.WOODEN_SLABS)
            || state.is(BlockTags.WOODEN_STAIRS)
            || state.is(BlockTags.WOODEN_DOORS)
            || state.is(BlockTags.WOODEN_TRAPDOORS)
            || state.is(BlockTags.WOODEN_BUTTONS)
            || state.is(BlockTags.WOODEN_PRESSURE_PLATES)
            || state.is(Blocks.BOOKSHELF)
            || state.is(Blocks.HAY_BLOCK)
            || state.getBlock() instanceof TntBlock;
    }

    private static boolean isReplaceableIgnitable(BlockState state) {
        return state.is(Blocks.GRASS)
            || state.is(Blocks.TALL_GRASS)
            || state.is(Blocks.FERN)
            || state.is(Blocks.LARGE_FERN)
            || state.is(Blocks.DEAD_BUSH)
            || state.is(BlockTags.SAPLINGS);
    }

    private static boolean canReplaceIgnitableWithFire(Level level, BlockPos pos) {
        BlockPos belowPos = pos.below();
        BlockState belowState = level.getBlockState(belowPos);
        return belowState.isFaceSturdy(level, belowPos, Direction.UP)
            || BaseFireBlock.canBePlacedAt(level, pos, Direction.UP);
    }
}
