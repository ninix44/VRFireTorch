package org.vmstudio.firetorch.fabric;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
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
            BlockPos clickedPos = buf.readBlockPos();
            Direction face = buf.readEnum(Direction.class);
            boolean isMainHand = buf.readBoolean();

            server.execute(() -> {
                InteractionHand hand = isMainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                ItemStack stack = player.getItemInHand(hand);

                if ((stack.getItem() == Items.TORCH || stack.getItem() == Items.SOUL_TORCH) && player.distanceToSqr(clickedPos.getCenter()) < 64.0) {
                    var level = player.level();
                    var clickedState = level.getBlockState(clickedPos);

                    if (TntBlock.class.isInstance(clickedState.getBlock())) {
                        TntBlock.explode(level, clickedPos);
                        level.removeBlock(clickedPos, false);
                    } else if (isTorchIgnitable(clickedState)) {
                        boolean replaceInPlace = isReplaceableIgnitable(clickedState);
                        BlockPos firePos = replaceInPlace ? clickedPos : clickedPos.relative(face);
                        if ((level.getBlockState(firePos).isAir() || level.getBlockState(firePos).canBeReplaced())
                            && BaseFireBlock.canBePlacedAt(level, firePos, replaceInPlace ? Direction.UP : face)) {
                            level.setBlockAndUpdate(firePos, BaseFireBlock.getState(level, firePos));
                        }
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

            FireTorchLogic.bridge = (clickedPos, face, isMainHand) -> {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeBlockPos(clickedPos);
                buf.writeEnum(face);
                buf.writeBoolean(isMainHand);
                ClientPlayNetworking.send(FireTorchNetworking.IGNITE_BLOCK_PACKET, buf);
            };

            ClientTickEvents.END_CLIENT_TICK.register(client -> FireTorchLogic.tick());
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
}
