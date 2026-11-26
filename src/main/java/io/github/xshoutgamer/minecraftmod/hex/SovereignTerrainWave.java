package io.github.xshoutgamer.minecraftmod.hex;

import java.util.Deque;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

final class SovereignTerrainWave {
    private final BlockPos center;
    private final int radius;
    private final long start;
    private final Deque<BlockPos> work;

    SovereignTerrainWave(BlockPos center, int radius, long start) {
        this.center = center;
        this.radius = radius;
        this.start = start;
        work = HexTerrain.volume(center, radius, HexConfig.Volume.SPHERE);
    }

    boolean tick(ServerPlayer player, UUID event, long expires, long now) {
        if (!HexTerrain.canRewrite(player)) return false;
        var journal = HexTerrain.get(player.serverLevel());
        int budget = HexConfig.SOVEREIGN_TERRAIN_BUDGET.get();
        int changes = 0;
        float t = Math.clamp((now - start) / 58F, 0, 1);
        double front = Math.max(1, radius * (1 - (1 - t) * (1 - t)));
        int highlights = 0;
        for (int i = 0; i < budget * 10 && changes < budget && !work.isEmpty() && !journal.full(); i++) {
            var pos = work.getFirst();
            if (Math.ceil(Math.sqrt(center.distSqr(pos))) > front) break;
            work.removeFirst();
            if (journal.rewriteBlock(player, pos, HexConfig.Rewrite.SCARLET, event, expires) == 0) continue;
            changes++;
            if (now % 4 == 0 && highlights < 5 && changes % Math.max(1, budget / 5) == 1) {
                var at = Vec3.atCenterOf(pos);
                HexDomains.scene(player, ScarletSovereignty.TERRAIN_FX, 0, at, at, now, 42, 4);
                highlights++;
            }
        }
        if (journal.full()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "The Scarlet Reality Wave filled the terrain journal. Genesis or /hexterrain keep can free space."), true);
            return false;
        }
        return !work.isEmpty();
    }
}
