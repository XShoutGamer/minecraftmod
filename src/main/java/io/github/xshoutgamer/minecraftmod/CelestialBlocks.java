package io.github.xshoutgamer.minecraftmod;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CelestialBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MinecraftMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MinecraftMod.MOD_ID);
    public static final DeferredBlock<Block> STONE = stone("celestial_stone", 0, SoundType.STONE);
    public static final DeferredBlock<Block> ENGRAVED = stone("engraved_celestial_stone", 4, SoundType.STONE);
    public static final DeferredBlock<Block> CRYSTAL = stone("celestial_crystal", 9, SoundType.AMETHYST);
    public static final DeferredBlock<Block> CORE = stone("luminous_core", 15, SoundType.AMETHYST);
    public static final DeferredBlock<Block> FRACTURE = stone("celestial_fracture", 8, SoundType.DEEPSLATE);
    public static final DeferredBlock<Block> FRAME = stone("celestial_frame", 2, SoundType.STONE);

    public static final DeferredBlock<Block> SCARLET_STONE = BLOCKS.register("scarlet_stone", () -> new Block(
            BlockBehaviour.Properties.of().strength(2.8F, 7).sound(SoundType.DEEPSLATE)
                    .lightLevel(state -> 4).noLootTable()));

    public static final DeferredBlock<Block> HEAVENFALL_CRYSTAL = BLOCKS.register("heavenfall_crystal", () -> new Block(
            BlockBehaviour.Properties.of().strength(1.6F).sound(SoundType.AMETHYST).lightLevel(state -> 11).noLootTable()));

    public static final DeferredBlock<Block> ARCANE_PLATFORM = BLOCKS.register("arcane_platform", () ->
            new io.github.xshoutgamer.minecraftmod.hex.ArcanePlatform(BlockBehaviour.Properties.of()
                    .strength(1.4F).lightLevel(state -> 8).sound(SoundType.AMETHYST).noLootTable()));

    public static final DeferredBlock<Block> BLIGHT_LOG = BLOCKS.register("blight_log", () ->
            new net.minecraft.world.level.block.RotatedPillarBlock(BlockBehaviour.Properties.of()
                    .strength(2).sound(SoundType.WOOD).lightLevel(state -> 2).noLootTable()));
    public static final DeferredBlock<Block> BLIGHT_PLANKS = corruption("blight_planks", SoundType.WOOD);
    public static final DeferredBlock<Block> BLIGHT_SOIL = corruption("blight_soil", SoundType.GRAVEL);
    public static final DeferredBlock<Block> BLIGHT_SAND = corruption("blight_sand", SoundType.SAND);
    public static final DeferredBlock<Block> BLIGHT_BRICKS = corruption("blight_bricks", SoundType.DEEPSLATE);

    private static DeferredBlock<Block> corruption(String id, SoundType sound) {
        return BLOCKS.register(id, () -> new Block(BlockBehaviour.Properties.of()
                .strength(2).sound(sound).lightLevel(state -> 2).noLootTable()));
    }

    private CelestialBlocks() {}

    private static DeferredBlock<Block> stone(String name, int light, SoundType sound) {
        var block = BLOCKS.register(name, () -> new Block(BlockBehaviour.Properties.of()
                .strength(3.5F, 12).sound(sound).lightLevel(state -> light).requiresCorrectToolForDrops()));
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
