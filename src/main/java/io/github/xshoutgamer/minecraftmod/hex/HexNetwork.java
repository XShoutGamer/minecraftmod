package io.github.xshoutgamer.minecraftmod.hex;

import io.github.xshoutgamer.minecraftmod.MinecraftMod;
import io.github.xshoutgamer.minecraftmod.client.HexClient;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class HexNetwork {
    public static final int TOGGLE = 0, PRESS = 1, RELEASE = 2;
    public static final int AWAKENING = 0, EXPANDING = 1, ACTIVE = 2, ENHANCED = 3, COLLAPSING = 4;
    public static final int HAND_FX = 20, ORB_FX = 21, BARRIER_FX = 22, RUNE_FX = 23;

    private HexNetwork() {}

    public static void register(PayloadRegistrar registrar) {
        var optional = registrar.optional();
        optional.playToServer(Input.TYPE, Input.CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                if (packet.action() >= PersonalMagic.QUICK && packet.action() <= PersonalMagic.LIBRARY_SELECT) PersonalMagic.input(player, packet);
                else HexDomains.input(player, packet);
            }
        });
        optional.playToClient(Scene.TYPE, Scene.CODEC, (packet, context) -> HexClient.receive(packet));
    }

    public record Input(int action, int spell, boolean ultra) implements CustomPacketPayload {
        public static final Type<Input> TYPE = new Type<>(MinecraftMod.id("hex_input"));
        static final StreamCodec<RegistryFriendlyByteBuf, Input> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Input::action, ByteBufCodecs.VAR_INT, Input::spell,
                ByteBufCodecs.BOOL, Input::ultra, Input::new);

        @Override
        public Type<Input> type() { return TYPE; }
    }

    public record Scene(ResourceLocation dimension, UUID owner, Vec3 origin, Vec3 target, long start,
                        int duration, int kind, int variant, float radius) implements CustomPacketPayload {
        public static final Type<Scene> TYPE = new Type<>(MinecraftMod.id("hex_scene"));
        static final StreamCodec<RegistryFriendlyByteBuf, Scene> CODEC = StreamCodec.of((buf, scene) -> {
            buf.writeResourceLocation(scene.dimension);
            UUIDUtil.STREAM_CODEC.encode(buf, scene.owner);
            buf.writeVec3(scene.origin);
            buf.writeVec3(scene.target);
            buf.writeVarLong(scene.start);
            buf.writeVarInt(scene.duration);
            buf.writeVarInt(scene.kind);
            buf.writeVarInt(scene.variant);
            buf.writeFloat(scene.radius);
        }, buf -> new Scene(buf.readResourceLocation(), UUIDUtil.STREAM_CODEC.decode(buf), buf.readVec3(), buf.readVec3(),
                buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat()));

        @Override
        public Type<Scene> type() { return TYPE; }

        void send(ServerPlayer player) {
            if (player.connection != null && player.connection.hasChannel(TYPE)) PacketDistributor.sendToPlayer(player, this);
        }
    }
}
