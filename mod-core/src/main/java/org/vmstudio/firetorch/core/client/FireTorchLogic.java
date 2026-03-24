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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
        void sendIgniteEvent(BlockPos clickedPos, Direction face, boolean isMainHand);
    }

    public static NetworkBridge bridge;

    private static int mainHandTicks = 0;
    private static int offHandTicks = 0;

    private static BlockPos mainHandTarget = null;
    private static BlockPos offHandTarget = null;

    private static final int TARGET_IGNITE_TIME = 60;
    private static final double MAX_TORCH_REACH = 0.08;
    private static final double INTERACTION_BOX_INFLATE = 0.015;
    private static final double MAX_BOX_DISTANCE = 0.01;
    private static final double REPLACEABLE_INTERACTION_BOX_INFLATE = 0.0;
    private static final double REPLACEABLE_MAX_BOX_DISTANCE = 0.0;

    private record IgniteTarget(BlockPos clickedPos, Direction face, BlockPos firePos) { }

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

        Vec3 torchBasePos = getTorchBasePos(pose);
        Vec3 torchPos = getTorchTipPos(pose);

        IgniteTarget target = getIgniteTarget(mc, torchBasePos, torchPos);

        if (target != null) {
            BlockPos targetPos = target.firePos();
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

                    if (bridge != null) bridge.sendIgniteEvent(target.clickedPos(), target.face(), isMain);
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

    private static Vec3 getTorchBasePos(VRPose handPose) {
        Vector3f offset = new Vector3f(0, 0.08f, 0.02f);
        Vector3f baseJoml = handPose.getCustomVector(offset).add(handPose.getPosition());
        return new Vec3(baseJoml.x(), baseJoml.y(), baseJoml.z());
    }

    private static Vec3 getTorchTipPos(VRPose handPose) {
        Vector3f offset = new Vector3f(0, 0.2f, -0.1f);
        Vector3f tipJoml = handPose.getCustomVector(offset).add(handPose.getPosition());
        return new Vec3(tipJoml.x(), tipJoml.y(), tipJoml.z());
    }

    private static IgniteTarget getIgniteTarget(Minecraft mc, Vec3 baseVec, Vec3 tipVec) {
        Level level = mc.level;
        if (level == null || mc.player == null) {
            return null;
        }

        IgniteTarget replaceableTarget = getReplaceableIgniteTarget(level, tipVec);
        if (replaceableTarget != null) {
            return replaceableTarget;
        }

        Vec3 rayDir = tipVec.subtract(baseVec);
        if (rayDir.lengthSqr() < 1.0E-6) {
            return null;
        }

        Vec3 rayEnd = tipVec.add(rayDir.normalize().scale(MAX_TORCH_REACH));
        BlockHitResult hitResult = level.clip(new ClipContext(baseVec, rayEnd, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            IgniteTarget clippedTarget = createIgniteTarget(level, hitResult.getBlockPos(), hitResult.getDirection());
            if (clippedTarget != null) {
                return clippedTarget;
            }
        }

        BlockPos centerPos = BlockPos.containing(tipVec.x, tipVec.y, tipVec.z);
        IgniteTarget bestTarget = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos candidate = centerPos.offset(x, y, z);
                    BlockState state = level.getBlockState(candidate);
                    if (state.isAir() || !isTorchIgnitable(state)) {
                        continue;
                    }

                    VoxelShape shape = state.getShape(level, candidate);
                    AABB bounds = shape.isEmpty() ? new AABB(candidate) : shape.bounds().move(candidate);
                    AABB interactionBox = bounds.inflate(INTERACTION_BOX_INFLATE);
                    if (!interactionBox.contains(tipVec) && distanceToBox(tipVec, interactionBox) > MAX_BOX_DISTANCE) {
                        continue;
                    }

                    Direction face = getNearestFaceToBox(tipVec, bounds);
                    IgniteTarget candidateTarget = createIgniteTarget(level, candidate, face);
                    if (candidateTarget == null) {
                        continue;
                    }

                    double distance = distanceToBox(tipVec, bounds);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestTarget = candidateTarget;
                    }
                }
            }
        }

        return bestTarget;
    }

    private static IgniteTarget getReplaceableIgniteTarget(Level level, Vec3 tipVec) {
        BlockPos centerPos = BlockPos.containing(tipVec.x, tipVec.y, tipVec.z);
        IgniteTarget bestTarget = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos candidate = centerPos.offset(x, y, z);
                    BlockState state = level.getBlockState(candidate);
                    if (!isReplaceableIgnitable(state)) {
                        continue;
                    }

                    AABB interactionBox = new AABB(candidate).inflate(REPLACEABLE_INTERACTION_BOX_INFLATE);
                    double distance = distanceToBox(tipVec, interactionBox);
                    if (!interactionBox.contains(tipVec) && distance > REPLACEABLE_MAX_BOX_DISTANCE) {
                        continue;
                    }

                    IgniteTarget candidateTarget = createIgniteTarget(level, candidate, Direction.UP);
                    if (candidateTarget != null && distance < bestDistance) {
                        bestDistance = distance;
                        bestTarget = candidateTarget;
                    }
                }
            }
        }

        return bestTarget;
    }

    private static IgniteTarget createIgniteTarget(Level level, BlockPos clickedPos, Direction face) {
        BlockState state = level.getBlockState(clickedPos);
        if (state.isAir() || !isTorchIgnitable(state)) {
            return null;
        }

        boolean replaceInPlace = isReplaceableIgnitable(state);
        BlockPos firePos = replaceInPlace ? clickedPos : clickedPos.relative(face);
        if (!(level.getBlockState(firePos).isAir() || level.getBlockState(firePos).canBeReplaced())) {
            return null;
        }

        if (replaceInPlace) {
            if (!canReplaceIgnitableWithFire(level, firePos)) {
                return null;
            }
        } else if (!BaseFireBlock.canBePlacedAt(level, firePos, face)) {
            return null;
        }

        return new IgniteTarget(clickedPos, face, firePos);
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

    private static Direction getNearestFaceToBox(Vec3 tipVec, AABB box) {
        double west = Math.abs(tipVec.x - box.minX);
        double east = Math.abs(box.maxX - tipVec.x);
        double down = Math.abs(tipVec.y - box.minY);
        double up = Math.abs(box.maxY - tipVec.y);
        double north = Math.abs(tipVec.z - box.minZ);
        double south = Math.abs(box.maxZ - tipVec.z);

        Direction closest = Direction.UP;
        double best = up;

        if (down < best) {best = down; closest = Direction.DOWN; }
        if (north < best) { best = north; closest = Direction.NORTH; }
        if (south < best) { best = south; closest = Direction.SOUTH; }
        if (west < best) { best = west; closest = Direction.WEST; }
        if (east < best) { closest = Direction.EAST; }

        return closest;
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

    private static double distanceToBox(Vec3 point, AABB box) {
        double dx = Math.max(Math.max(box.minX - point.x, 0.0), point.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - point.y, 0.0), point.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - point.z, 0.0), point.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
