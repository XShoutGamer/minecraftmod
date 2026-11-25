package io.github.xshoutgamer.minecraftmod.nail;

import io.github.xshoutgamer.minecraftmod.CelestiaConfig;
import io.github.xshoutgamer.minecraftmod.MinecraftMod;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record NailCast() implements CustomPacketPayload {
    public static final NailCast INSTANCE = new NailCast();
    private static final Type<NailCast> TYPE = new Type<>(MinecraftMod.id("cast_nail"));
    private static final String COOLDOWN_KEY = "minecraftmod.nailCooldown";

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("4");
        NailImpact.register(registrar);
        registrar.playToServer(TYPE, StreamCodec.unit(INSTANCE), (packet, context) -> {
            if (context.player() instanceof ServerPlayer player) cast(player);
        });
    }

    private static void cast(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) return;
        var level = player.serverLevel();
        long now = level.getServer().overworld().getGameTime();
        boolean cooldownEnabled = NailCooldown.enabled(level.getServer());
        // World time is shared across dimensions. Player data survives disconnects.
        var data = player.getPersistentData();
        var persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        if (now < data.getLong("minecraftmod.nextAttempt")
                || (cooldownEnabled && now < persisted.getLong(COOLDOWN_KEY))) return;
        data.putLong("minecraftmod.nextAttempt", now + 4);

        var eye = player.getEyePosition();
        var aim = eye.add(player.getLookAngle().scale(128));
        // Use the same voxel traversal as clipping to reject rays through unloaded chunks.
        var clip = new ClipContext(eye, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
        BlockHitResult hit = BlockGetter.traverseBlocks(eye, aim, clip, (context, pos) -> {
            if (!level.hasChunkAt(pos)) return BlockHitResult.miss(eye, player.getDirection(), pos.immutable());
            var state = level.getBlockState(pos);
            var shape = context.getBlockShape(state, level, pos);
            return level.clipWithInteractionOverride(eye, aim, pos, shape, state);
        }, context -> null);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;
        var target = hit.getBlockPos();
        if (!level.hasChunkAt(target) || !level.getWorldBorder().isWithinBounds(target)
                || !level.mayInteract(player, target) || level.getBlockState(target).is(CelestialNail.IMPACT_IMMUNE)) return;

        // Cap concurrent active sequences around the viewpoint, including other players' casts.
        var nearby = level.getEntities(MinecraftMod.NAIL.get(), player.getBoundingBox().inflate(170),
                nail -> true);
        long active = nearby.stream().filter(nail -> nail.phase().ordinal() < CelestialNail.Phase.EMBEDDED.ordinal()).count();
        if (active >= 3 || nearby.size() >= 12) return;

        var nail = new CelestialNail(MinecraftMod.NAIL.get(), level);
        nail.begin(player, target);
        if (level.addFreshEntity(nail) && cooldownEnabled) {
            persisted.putLong(COOLDOWN_KEY, now + 20L * CelestiaConfig.COOLDOWN.get());
            data.put(Player.PERSISTED_NBT_TAG, persisted);
        }
    }

    @Override
    public Type<NailCast> type() {
        return TYPE;
    }
}
