package org.vmstudio.firetorch.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.client.player.VRLocalPlayer;
import org.vmstudio.visor.api.client.player.pose.PlayerPoseClient;
import org.vmstudio.visor.api.client.player.pose.PlayerPoseType;
import org.vmstudio.visor.api.common.HandType;
import org.vmstudio.visor.api.common.player.VRPose;

public class FireTorchLogic {

    public interface NetworkBridge {
        void sendIgniteEvent(BlockPos pos, boolean isMainHand);
    }

    public static NetworkBridge bridge;

    private static int mainHandTicks = 0;
    private static int offHandTicks = 0;

    private static BlockPos mainHandTarget = null;
    private static BlockPos offHandTarget = null;

    private static final int TARGET_IGNITE_TIME = 60;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;

        VRLocalPlayer vrPlayer = VisorAPI.client().getVRLocalPlayer();
        if (vrPlayer == null || !VisorAPI.clientState().playMode().canPlayVR()) return;

        PlayerPoseClient pose = vrPlayer.getPoseData(PlayerPoseType.TICK);

        processHand(mc, InteractionHand.MAIN_HAND, HandType.MAIN, pose.getMainHand(), true);
        processHand(mc, InteractionHand.OFF_HAND, HandType.OFFHAND, pose.getOffhand(), false);
    }

    private static void processHand(Minecraft mc, InteractionHand hand, HandType handType, VRPose pose, boolean isMain) {
        ItemStack stack = mc.player.getItemInHand(hand);

        if (stack.isEmpty() || (stack.getItem() != Items.TORCH && stack.getItem() != Items.SOUL_TORCH)) {
            resetTimers(isMain);
            return;
        }

        Vec3 torchPos = getTorchPos(pose);

        BlockPos targetPos = getFireTarget(mc.level, torchPos);

        if (targetPos != null) {
            BlockPos lastTarget = isMain ? mainHandTarget : offHandTarget;

            if (targetPos.equals(lastTarget)) {
                int ticks = isMain ? ++mainHandTicks : ++offHandTicks;

                if (ticks % 5 == 0) {
                    mc.level.addParticle(ParticleTypes.LARGE_SMOKE,
                        torchPos.x + (mc.level.random.nextDouble() - 0.5) * 0.1,
                        torchPos.y + 0.1,
                        torchPos.z + (mc.level.random.nextDouble() - 0.5) * 0.1,
                        0, 0.05, 0);
                }

                if (ticks % 15 == 0) {
                    mc.player.playSound(SoundEvents.FIRE_AMBIENT, 0.3f, 1.0f + (mc.level.random.nextFloat() * 0.5f));
                    VisorAPI.client().getInputManager().triggerHapticPulse(handType, 150f, 0.3f, 0.05f);
                }

                if (ticks >= TARGET_IGNITE_TIME) {
                    VisorAPI.client().getInputManager().triggerHapticPulse(handType, 320f, 1.0f, 0.3f);
                    mc.player.playSound(SoundEvents.FLINTANDSTEEL_USE, 1.0f, 1.0f);

                    for (int i = 0; i < 10; i++) {
                        mc.level.addParticle(ParticleTypes.FLAME,
                            targetPos.getX() + 0.5 + (mc.level.random.nextDouble() - 0.5) * 0.5,
                            targetPos.getY() + 0.1,
                            targetPos.getZ() + 0.5 + (mc.level.random.nextDouble() - 0.5) * 0.5,
                            0, 0.05, 0);
                    }

                    if (bridge != null) bridge.sendIgniteEvent(targetPos, isMain);
                    resetTimers(isMain);
                }
            } else {
                if (isMain) {
                    mainHandTarget = targetPos;
                    mainHandTicks = 1;
                } else {
                    offHandTarget = targetPos;
                    offHandTicks = 1;
                }
            }
        } else {
            resetTimers(isMain);
        }
    }

    private static void resetTimers(boolean isMain) {
        if (isMain) {
            mainHandTicks = 0;
            mainHandTarget = null;
        } else {
            offHandTicks = 0;
            offHandTarget = null;
        }
    }

    private static Vec3 getTorchPos(VRPose handPose) {
        Vector3f offset = new Vector3f(0, 0.2f, -0.1f);
        Vector3f tipJoml = handPose.getCustomVector(offset).add(handPose.getPosition());
        return new Vec3(tipJoml.x(), tipJoml.y(), tipJoml.z());
    }

    private static BlockPos getFireTarget(Level level, Vec3 tipVec) {
        BlockPos tipPos = BlockPos.containing(tipVec.x, tipVec.y, tipVec.z);
        BlockState state = level.getBlockState(tipPos);

        if (isFlammable(state)) {
            for (Direction dir : Direction.values()) {
                BlockPos adj = tipPos.relative(dir);
                if (level.getBlockState(adj).isAir() || level.getBlockState(adj).canBeReplaced()) {
                    return adj;
                }
            }
        }
        else if (state.isAir() || state.canBeReplaced()) {
            for (Direction dir : Direction.values()) {
                BlockPos adj = tipPos.relative(dir);
                BlockState adjState = level.getBlockState(adj);

                if (isFlammable(adjState)) {
                    VoxelShape shape = adjState.getShape(level, adj);
                    if (!shape.isEmpty()) {
                        AABB expandedHitbox = shape.bounds().move(adj).inflate(0.15);

                        if (expandedHitbox.contains(tipVec)) {
                            return tipPos;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isFlammable(BlockState state) {
        if (state.ignitedByLava()) {
            return true;
        }
//        if (state.is(BlockTags.INFINIBURN_OVERWORLD) || state.is(BlockTags.INFINIBURN_NETHER)) { // is it necessary?
//            return true;
//        }

        return state.is(BlockTags.LOGS) ||
            state.is(BlockTags.PLANKS) ||
            state.is(BlockTags.LEAVES) ||
            state.is(BlockTags.WOOL) ||
            state.is(BlockTags.WOODEN_FENCES) ||
            state.is(BlockTags.WOODEN_SLABS) ||
            state.is(BlockTags.WOODEN_STAIRS) ||
            state.is(BlockTags.WOODEN_DOORS) ||
            state.is(BlockTags.WOODEN_TRAPDOORS) ||
            state.is(Blocks.TNT) ||
            state.is(Blocks.BOOKSHELF) ||
            state.is(Blocks.HAY_BLOCK);
    }
}
