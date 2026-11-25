package io.github.xshoutgamer.minecraftmod.nail;

import io.github.xshoutgamer.minecraftmod.MinecraftMod;
import io.github.xshoutgamer.minecraftmod.client.ImpactPulse;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public record NailImpact(UUID nail, int entityId, ResourceLocation dimension, BlockPos ground) implements CustomPacketPayload {
    private static final Type<NailImpact> TYPE = new Type<>(MinecraftMod.id("nail_impact"));
    private static final StreamCodec<RegistryFriendlyByteBuf, NailImpact> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, NailImpact::nail, ByteBufCodecs.VAR_INT, NailImpact::entityId,
            ResourceLocation.STREAM_CODEC, NailImpact::dimension,
            BlockPos.STREAM_CODEC, NailImpact::ground, NailImpact::new);

    static void register(PayloadRegistrar registrar) {
        registrar.playToClient(TYPE, CODEC, (packet, context) -> ImpactPulse.receive(packet));
    }

    static void send(ServerLevel level, CelestialNail nail) {
        BlockPos target = nail.target();
        var packet = new NailImpact(nail.getUUID(), nail.getId(), level.dimension().location(), target);
        for (var player : level.players()) {
            if (player.distanceToSqr(target.getX() + .5, target.getY() + 1, target.getZ() + .5) < 540 * 540
                    && player.connection != null && player.connection.hasChannel(TYPE)) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }

    @Override
    public Type<NailImpact> type() { return TYPE; }
}
