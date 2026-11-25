package io.github.xshoutgamer.minecraftmod;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(MinecraftMod.MOD_ID)
public final class MinecraftMod {
    public static final String MOD_ID = "minecraftmod";
    private static final Logger LOGGER = LogUtils.getLogger();

    public MinecraftMod() {
        LOGGER.info("Minecraft Mod initialized");
    }
}
