package io.github.xshoutgamer.minecraftmod;

import io.github.xshoutgamer.minecraftmod.nail.CelestialNail;
import io.github.xshoutgamer.minecraftmod.hex.HexConfig;
import io.github.xshoutgamer.minecraftmod.hex.Heavenfall;
import io.github.xshoutgamer.minecraftmod.hex.HexDomains;
import io.github.xshoutgamer.minecraftmod.hex.HexTerrainCommands;
import io.github.xshoutgamer.minecraftmod.hex.PersonalMagic;
import io.github.xshoutgamer.minecraftmod.hex.ScarletSovereignty;
import io.github.xshoutgamer.minecraftmod.nail.NailCast;
import io.github.xshoutgamer.minecraftmod.nail.NailCooldown;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(MinecraftMod.MOD_ID)
public final class MinecraftMod {
    public static final String MOD_ID = "minecraftmod";
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<CelestialNail>> NAIL = ENTITIES.register("celestial_nail",
            () -> EntityType.Builder.<CelestialNail>of(CelestialNail::new, MobCategory.MISC)
                    .sized(8F, 60F).clientTrackingRange(48).updateInterval(1).fireImmune().build(id("celestial_nail").toString()));

    public static final DeferredHolder<SoundEvent, SoundEvent> SUMMON = sound("summon");
    public static final DeferredHolder<SoundEvent, SoundEvent> GATHER = sound("gather");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHARGE = sound("charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> DESCENT = sound("descent");
    public static final DeferredHolder<SoundEvent, SoundEvent> DIVINE_ARC = sound("divine_arc");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRIMSON_ARC = sound("crimson_arc");
    public static final DeferredHolder<SoundEvent, SoundEvent> GOLDEN_FLOW = sound("golden_flow");
    public static final DeferredHolder<SoundEvent, SoundEvent> RESIDUAL_ARC = sound("residual_arc");
    public static final DeferredHolder<SoundEvent, SoundEvent> RELEASE = sound("release");
    public static final DeferredHolder<SoundEvent, SoundEvent> IMPACT = sound("impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOCKWAVE = sound("shockwave");
    public static final DeferredHolder<SoundEvent, SoundEvent> FRACTURE = sound("fracture");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEBRIS = sound("debris");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUMBLE = sound("rumble");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT = sound("ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISSOLVE = sound("dissolve");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEX_AWAKEN = sound("hex_awaken");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEX_EXPAND = sound("hex_expand");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEX_HUM = sound("hex_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEX_COLLAPSE = sound("hex_collapse");
    public static final DeferredHolder<SoundEvent, SoundEvent> ECLIPSE_SOUND = sound("eclipse_formation");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOID_SOUND = sound("singularity_collapse");
    public static final DeferredHolder<SoundEvent, SoundEvent> DARKHOLD_SOUND = sound("darkhold_cast");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHAOS_SOUND = sound("chaos_dissolve");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEAVENFALL_OPEN = sound("heavenfall_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEAVENFALL_DESCENT = sound("heavenfall_descent");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOVEREIGN_TRANSFORMATION = sound("sovereign_transformation");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOVEREIGN_QUICK = sound("sovereign_quick");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOVEREIGN_CURRENT = sound("sovereign_current");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOVEREIGN_CROWN = sound("sovereign_crown");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOVEREIGN_RELEASE = sound("sovereign_release");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEX_CAST = sound("hex_cast");

    public MinecraftMod(IEventBus bus, ModContainer container) {
        ENTITIES.register(bus);
        SOUNDS.register(bus);
        CelestialBlocks.register(bus);
        bus.addListener(NailCast::register);
        NeoForge.EVENT_BUS.addListener(NailCooldown::register);
        NeoForge.EVENT_BUS.addListener(HexTerrainCommands::register);
        NeoForge.EVENT_BUS.addListener(HexDomains::tick);
        NeoForge.EVENT_BUS.addListener(ScarletSovereignty::tick);
        NeoForge.EVENT_BUS.addListener(Heavenfall::tick);
        NeoForge.EVENT_BUS.addListener(Heavenfall::unload);
        NeoForge.EVENT_BUS.addListener(ScarletSovereignty::damage);
        NeoForge.EVENT_BUS.addListener(ScarletSovereignty::target);
        NeoForge.EVENT_BUS.addListener(ScarletSovereignty::impact);
        NeoForge.EVENT_BUS.addListener(ScarletSovereignty::knockback);
        NeoForge.EVENT_BUS.addListener(ScarletSovereignty::login);
        NeoForge.EVENT_BUS.addListener(ScarletSovereignty::logout);
        NeoForge.EVENT_BUS.addListener(ScarletSovereignty::dimension);
        NeoForge.EVENT_BUS.addListener(PersonalMagic::tick);
        NeoForge.EVENT_BUS.addListener(PersonalMagic::damage);
        NeoForge.EVENT_BUS.addListener(PersonalMagic::login);
        NeoForge.EVENT_BUS.addListener(PersonalMagic::logout);
        NeoForge.EVENT_BUS.addListener(PersonalMagic::stop);
        NeoForge.EVENT_BUS.addListener(HexDomains::temporalTick);
        NeoForge.EVENT_BUS.addListener(HexDomains::adjustDamage);
        NeoForge.EVENT_BUS.addListener(HexDomains::stop);
        NeoForge.EVENT_BUS.addListener(HexDomains::login);
        NeoForge.EVENT_BUS.addListener(HexDomains::logout);
        container.registerConfig(ModConfig.Type.SERVER, HexConfig.SERVER, "minecraftmod-hex-server.toml");
        container.registerConfig(ModConfig.Type.CLIENT, HexConfig.CLIENT, "minecraftmod-hex-client.toml");
        container.registerConfig(ModConfig.Type.SERVER, CelestiaConfig.SERVER_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, CelestiaConfig.CLIENT_SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createFixedRangeEvent(id(name), 640F));
    }
}
