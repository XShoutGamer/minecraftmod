package io.github.xshoutgamer.minecraftmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.xshoutgamer.minecraftmod.CelestiaConfig;
import io.github.xshoutgamer.minecraftmod.MinecraftMod;
import io.github.xshoutgamer.minecraftmod.nail.CelestialNail;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import static io.github.xshoutgamer.minecraftmod.client.NailMesh.*;

public final class NailRenderer extends EntityRenderer<CelestialNail> {
    private static final ResourceLocation STONE = MinecraftMod.id("textures/entity/celestial_stone.png");
    private static final ResourceLocation ENERGY = MinecraftMod.id("textures/entity/celestial_energy.png");
    private static final RenderType SOLID = RenderType.entityTranslucent(STONE);
    private static final RenderType GLOW = CelestialRenderTypes.energy(ENERGY);
    private static final RenderType DUST =
            RenderType.entityTranslucent(MinecraftMod.id("textures/entity/celestial_dust.png"));

    public NailRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override
    public boolean shouldRender(CelestialNail nail, Frustum frustum, double x, double y, double z) {
        double dx = x - nail.getX(), dy = y - nail.target().getY(), dz = z - nail.getZ();
        if (dx * dx + dz * dz > 448 * 448 || Math.abs(dy) > 448) return false;
        double radius = Math.max(105, nail.radius() * 2.6);
        return frustum.isVisible(new AABB(nail.getX() - radius, nail.target().getY() - 45, nail.getZ() - radius,
                nail.getX() + radius, Math.max(nail.getY() + 130, nail.target().getY() + 235), nail.getZ() + radius));
    }

    @Override
    public ResourceLocation getTextureLocation(CelestialNail entity) { return STONE; }

    @Override
    public void render(CelestialNail nail, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light) {
        var phase = nail.phase();
        float age = nail.age() + partial;
        float time = (nail.level().getGameTime() % 36000) + partial;
        float visibility = switch (phase) {
            case SUMMONING -> smooth(age / CelestialNail.MATERIALIZE_TICKS);
            case SOLIDIFYING -> 1F;
            default -> 1F;
        };
        float intensity = CelestiaConfig.GLOW.get().floatValue();
        int quality = CinematicEffects.quality();
        boolean reduced = quality == 0;
        float recession = phase == CelestialNail.Phase.SOLIDIFYING
                ? 1 - smooth(age / CelestialNail.SOLIDIFY_TICKS) : 1;
        double renderOrigin = Mth.lerp(partial, nail.yOld, nail.getY());
        double animatedY = nail.visualY(partial);
        float vertical = (float) (nail.target().getY() + 1.08 - animatedY);
        float modelScale = nail.modelHeight() / 24F;
        float pulse = 0.78F + 0.22F * Mth.sin(time * 0.13F);

        poses.pushPose();
        poses.translate(0, animatedY - renderOrigin, 0);
        poses.pushPose();
        poses.scale(modelScale, modelScale * (0.72F + 0.28F * visibility), modelScale);
        float motionTime = phase == CelestialNail.Phase.SOLIDIFYING
                ? time - age * (1 - recession) : time;
        monument(new NailMesh(poses, buffers.getBuffer(SOLID), false, visibility), motionTime, recession);
        core(new NailMesh(poses, buffers.getBuffer(GLOW), true, visibility * intensity * (0.16F + 0.84F * recession)),
                motionTime, pulse, reduced);
        poses.popPose();

        var distance = entityRenderDispatcher.distanceToSqr(nail);
        if (phase.ordinal() < CelestialNail.Phase.IMPACTING.ordinal()) {
            var sky = new NailMesh(poses, buffers.getBuffer(GLOW), true, intensity);
            groundSeal(nail, sky, vertical, time, reduced);
            if (phase == CelestialNail.Phase.DESCENDING) {
                descent(sky, age, time, pulse, reduced);
            } else {
                float contraction = phase == CelestialNail.Phase.CHARGING ? smooth((age - nail.chargeTicks() + 11) / 11) : 0;
                WeatherMeshes.seal(sky, nail.modelHeight() + 2, age, time, phase == CelestialNail.Phase.SUMMONING, contraction, quality);
                summoning(sky, phase, age, time, pulse, reduced);
            }
        } else {
            float surface = nail.surfaceAge() + partial;
            var glow = new NailMesh(poses, buffers.getBuffer(GLOW), true, visibility * intensity * recession);
            WeatherMeshes.impact(glow, vertical, surface, time, nail.radius(), quality);
            aftermath(glow, vertical, floor(nail, 0, 0, vertical), surface, time, nail.radius(), reduced);
            if (distance < 230 * 230 && recession > 0.04F) {
                fractures(nail, glow, vertical, surface, time, reduced);
                if (!reduced && surface < 36) arcs(glow, vertical, surface, time);
                particles(poses, buffers, vertical, surface, time, visibility, reduced);
            }
        }
        poses.popPose();
    }


    private static void groundSeal(CelestialNail nail, NailMesh mesh, float y, float time, boolean reduced) {
        int sides = reduced ? 32 : 64;
        for (int i = 0; i < sides; i++) {
            double a = Math.PI * 2 * i / sides + time * 0.013;
            double b = a + Math.PI * 2 / sides;
            for (int lane = 0; lane < 2; lane++) {
                if (lane == 0 && i % 5 == 4) continue;
                float radius = nail.radius() - lane * 0.62F;
                Point p = polar(0, 0, 0, radius, a), q = polar(0, 0, 0, radius, b);
                float ay = floor(nail, p.x(), p.z(), y) + 0.045F * lane;
                float by = floor(nail, q.x(), q.z(), y) + 0.045F * lane;
                ribbon(mesh, p.x(), ay, p.z(), q.x(), by, q.z(), lane == 0 ? 0.12F : 0.045F,
                        lane == 0 ? GOLD : CYAN, lane == 0 ? 0.52F : 0.34F);
            }
        }
    }

    private static void arcs(NailMesh mesh, float y, float age, float time) {
        for (int strand = 0; strand < 5; strand++) {
            float lifetime = fract(age * 0.12F + strand * 0.317F);
            double spin = strand * 2.399963 + time * 0.032;
            Point previous = polar(0, y, 0, 1.15F, spin);
            for (int link = 1; link <= 8; link++) {
                double zigzag = Mth.sin(link * 8.1F + strand * 3.4F + Mth.floor(age / 3)) * 0.5;
                Point point = polar(0, y + link * (0.6F + lifetime * 0.9F), 0, 1.2F + link * 0.36F, spin + zigzag);
                float alpha = (1 - lifetime) * (1 - age / 36) * 0.58F;
                ribbon(mesh, previous.x(), previous.y(), previous.z(), point.x(), point.y(), point.z(),
                        0.063F, strand % 3 == 0 ? GOLD : WHITE, alpha);
                previous = point;
            }
        }
    }

    private static void monument(NailMesh mesh, float time, float recession) {
        double angle = Math.PI / 8;
        mesh.section(0, 0, 0, 7.8F, 0, 1.53F, 8, angle, 0x69c8ec, 0x315e91, 1);
        mesh.section(0, 7.8F, 0, 1.05F, 1.53F, 2.27F, 8, angle, SILVER, IVORY, 1);
        mesh.section(0, 8.85F, 0, 0.36F, 2.27F, 2.27F, 8, angle, GOLD, 0x805231, 1);
        mesh.section(0, 9.21F, 0, 1.5F, 2.27F, 1.24F, 8, angle, IVORY, SILVER, 1);
        mesh.section(0, 10.71F, 0, 7.9F, 1.24F, 1.24F, 8, angle, IVORY, SILVER, 1);
        mesh.section(0, 18.61F, 0, 0.64F, 1.24F, 1.91F, 8, angle, SILVER, IVORY, 1);
        mesh.section(0, 19.25F, 0, 0.23F, 1.91F, 1.99F, 8, angle, GOLD, 0xb48042, 1);
        mesh.section(0, 19.48F, 0, 1.2F, 1.99F, 2.18F, 8, angle, IVORY, SILVER, 1);
        mesh.section(0, 20.68F, 0, 0.39F, 2.18F, 1.71F, 8, angle, GOLD, 0xbc8346, 1);
        mesh.section(0, 21.07F, 0, 1.2F, 1.71F, 0.8F, 8, angle, IVORY, SILVER, 1);
        mesh.diamond(0, 22.17F, 0, 0.84F, 1.83F, Math.PI / 4, CYAN, 1);
        for (int i = 0; i < 8; i++) {
            double azimuth = Math.PI / 4 * i;
            Point anchor = polar(0, 10, 0, 1.19F, azimuth);
            float x = anchor.x(), z = anchor.z();
            mesh.section(x, 8.4F, z, 2.1F, 0.62F, 0.27F, 4, azimuth + angle, SILVER, IVORY, 1);
            mesh.section(x, 10.5F, z, 7.95F, 0.27F, 0.27F, 4, azimuth + angle, IVORY, 0x809bb4, 1);
            mesh.section(x, 18.45F, z, 2.05F, 0.27F, 0.63F, 4, azimuth + angle, SILVER, IVORY, 1);
            Point rail = polar(0, 0, 0, 1.47F, azimuth);
            mesh.section(rail.x(), 10.3F, rail.z(), 8.25F, 0.07F, 0.07F, 4, azimuth, GOLD, 0xbc8346, 1);
            for (int mark = 0; mark < 5; mark++) {
                mesh.diamond(rail.x(), 10.62F + mark * 1.55F, rail.z(), 0.19F, 0.86F, azimuth, GOLD, 1);
            }
            if (recession > 0.02F && i % 2 == 0) {
                double orbit = azimuth + time * 0.026;
                float y = 10.2F + (i % 3) * 2.5F + Mth.sin(time * 0.07F + i) * 0.58F;
                Point fragment = polar(0, y, 0, 2 + recession * 2.45F, orbit);
                mesh.diamond(fragment.x(), y, fragment.z(), 0.56F, 3.5F, orbit, IVORY, recession);
                mesh.section(fragment.x(), y + 1.58F, fragment.z(), 0.2F, 0.59F, 0.59F, 4, orbit, GOLD, SILVER, recession);
            }
        }
    }

    private static void core(NailMesh mesh, float time, float pulse, boolean reduced) {
        mesh.section(0, 0.23F, 0, 7.3F, 0, 1.68F, 6, Math.PI / 6, CYAN, BLUE, 0.26F * pulse);
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI / 3 * i;
            float x = (float) Math.cos(angle) * 1.31F, z = (float) Math.sin(angle) * 1.31F;
            mesh.section(x, 9.8F, z, 10, 0.046F, 0.046F, 4, angle, CYAN, WHITE, 0.72F * pulse);
            float y = 10 + ((time * 0.12F + i * 1.72F) % 8.8F);
            mesh.diamond(x, y, z, 0.17F, 0.8F, angle, WHITE, 0.86F);
        }
        mesh.ring(10.6F, 3.35F, 0.11F, time * 0.045, 48, 4, CYAN, pulse * 0.62F);
        mesh.ring(17.7F, 3.66F, 0.10F, -time * 0.035, 56, 5, GOLD, pulse * 0.65F);
        mesh.glyphs(17.72F, 3.4F, -time * 0.035, 12, WHITE, 0.42F);
        if (!reduced) mesh.section(0, 5, 0, 15.5F, 2F, 2.45F, 8, time * 0.025, BLUE, CYAN, 0.036F * pulse);
    }

    private static void summoning(NailMesh mesh, CelestialNail.Phase phase, float age, float time, float pulse, boolean reduced) {
        float y = 23.8F;
        float reveal = phase == CelestialNail.Phase.SUMMONING ? smooth(age / 18) : 1;
        mesh.ring(y, 10 * reveal, 0.17F, time * 0.019, 80, 1, GOLD, 0.86F * reveal);
        mesh.ring(y + 0.02F, 9 * reveal, 0.14F, -time * 0.03, 64, 4, CYAN, 0.63F * reveal);
        mesh.ring(y + 0.04F, 6.8F * reveal, 0.14F, time * 0.047, 56, 7, GOLD, 0.75F * reveal);
        mesh.ring(y + 0.06F, 5.5F * reveal, 0.075F, -time * 0.055, 48, 3, WHITE, 0.68F * reveal);
        mesh.glyphs(y + 0.08F, 8F * reveal, -time * 0.023, 20, GOLD, pulse * reveal);
        mesh.glyphs(y + 0.09F, 5.9F * reveal, time * 0.045, 12, CYAN, pulse * reveal);
        for (int i = 0; i < 7; i++) {
            double turn = time * 0.019 + Math.PI * 2 / 7 * i;
            Point a = polar(0, y + 0.11F, 0, 8.6F * reveal, turn);
            Point b = polar(0, y + 0.11F, 0, 8.6F * reveal, turn + Math.PI * 6 / 7);
            ribbon(mesh, a.x(), a.y(), a.z(), b.x(), b.y(), b.z(), 0.068F, GOLD, 0.55F * reveal);
        }
        if (phase == CelestialNail.Phase.SUMMONING && age > 4 && age < 21) {
            mesh.section(0, -8, 0, 55, 0.48F, 0.7F, 8, 0, CYAN, WHITE, 0.22F * (1 - age / 21));
        }
        int motes = moteCount(reduced ? 14 : 60);
        for (int i = 0; i < motes; i++) {
            double a = i * 2.399963 + time * (0.017 + i % 4 * 0.004);
            float tide = fract(time * 0.027F + i * 0.618F);
            float r = (11 - tide * 10) * reveal;
            float h = y + Mth.sin(i * 2.1F + time * 0.08F) * (2 + 5 * (1 - tide));
            Point p = polar(0, h, 0, r, a);
            mesh.diamond(p.x(), p.y(), p.z(), 0.035F + tide * 0.055F, 0.31F, a,
                    i % 3 == 0 ? GOLD : CYAN, reveal * Mth.sin(tide * Mth.PI));
        }
    }

    private static void descent(NailMesh mesh, float age, float time, float pulse, boolean reduced) {
        float length = Math.min(140, 23 + age * 10.0F);
        mesh.section(0, 2, 0, 20 + length, 2.15F, 0.7F, 8, time * 0.14, BLUE, CYAN, 0.14F * pulse);
        mesh.section(0, 9, 0, 15 + length, 0.9F, 0.02F, 6, -time * 0.1, WHITE, CYAN, 0.68F);
        for (int i = 0; i < (reduced ? 3 : 7); i++) {
            float cycle = fract(i / 7F + age * 0.065F);
            float radius = 3 + cycle * 38;
            mesh.ring(3 + cycle * (18 + length), radius, 0.17F, time * 0.083 + i, 48, 5,
                    i % 2 == 0 ? CYAN : GOLD, (1 - cycle) * 0.7F);
        }
        for (int i = 0; i < moteCount(reduced ? 10 : 44); i++) {
            float travel = fract(age * 0.097F + i * 0.618F);
            double a = i * 2.399963 + time * 0.07;
            Point p = polar(0, 2 + travel * (21 + length), 0, 1.6F + travel * 5, a);
            mesh.diamond(p.x(), p.y(), p.z(), 0.045F, 2 + travel * 3, a, i % 2 == 0 ? GOLD : WHITE, 1 - travel);
        }
    }

    private static void aftermath(NailMesh mesh, float y, float floor, float age, float time, int radius, boolean reduced) {
        float beam = 0.11F + 0.19F * (float) Math.exp(-age * 0.055F);
        float columnHeight = 90 + 140 * (float) Math.exp(-age / 230F);
        mesh.section(0, floor, 0, columnHeight, 2.4F, 0.08F, 8, time * 0.035, CYAN, WHITE, beam);
        if (!reduced) mesh.section(0, floor, 0, columnHeight * 0.89F, 7.2F, 0.21F, 8, -time * 0.02, BLUE, CYAN, beam * 0.42F);
        float flash = CelestiaConfig.FLASH.get().floatValue();
        if (age < 11 && flash > 0) {
            float fade = (1 - age / 11) * flash;
            mesh.section(0, y - 0.6F, 0, 16, 3.2F, 0.4F, 12, 0, WHITE, CYAN, fade * 0.84F);
            mesh.ring(y, age * 1.85F + 1, 1.5F, 0, 72, 1, WHITE, fade * 0.65F);
        }
        for (int i = 0; i < 4; i++) {
            float t = age - 2 - i * 9;
            if (t < 0 || t > 25) continue;
            mesh.ring(y + 0.09F * i, (2 + t * radius * 0.091F), 0.45F, time * 0.01, 72,
                    i == 0 ? 1 : 5, i % 2 == 0 ? CYAN : GOLD, (1 - t / 25) * 0.8F);
        }
        float echo = fract(time / 86);
        mesh.ring(y, 2 + echo * radius, 0.14F, time * 0.01, 56, 7, CYAN, (1 - echo) * 0.31F);
        for (int i = 0; i < 4; i++) {
            float t = age - 13 - 7 * i;
            if (t <= 0 || t > 12) continue;
            double a = i * 2.399963;
            Point p = polar(0, y, 0, 2 + i * 2, a);
            mesh.diamond(p.x(), y, p.z(), 0.7F, t * 0.75F, a, i % 2 == 0 ? CYAN : GOLD, 1 - t / 12);
        }
    }

    private static void fractures(CelestialNail nail, NailMesh mesh, float y, float age, float time, boolean reduced) {
        for (var segment : nail.cracks()) {
            float growth = smooth((age - segment.delay()) / 6);
            if (growth <= 0) break;
            float bx = Mth.lerp(growth, segment.x0(), segment.x1());
            float bz = Mth.lerp(growth, segment.z0(), segment.z1());
            float ax = segment.x0(), az = segment.z0();
            float ay = floor(nail, ax, az, y), by = floor(nail, bx, bz, y);
            float wave = 0.46F + 0.23F * Mth.sin(time * 0.19F - segment.delay() * 0.29F);
            ribbon(mesh, ax, ay, az, bx, by, bz, segment.width(), CYAN, wave);
            if (!reduced) {
                ribbon(mesh, ax, ay + 0.012F, az, bx, by + 0.012F, bz,
                        segment.width() * 0.24F, GOLD, wave * 1.1F);
                if (segment.delay() % 6 == 0) {
                    float drift = fract(time * 0.029F + segment.x0() * 0.37F);
                    mesh.diamond(bx, by + drift * 1.6F, bz, 0.047F, 0.2F, time * 0.02, CYAN, (1 - drift) * wave);
                }
            }
        }
    }

    private static float floor(CelestialNail nail, float x, float z, float y) {
        var origin = nail.target();
        var pos = origin.offset(Mth.floor(x + 0.5F), 0, Mth.floor(z + 0.5F));
        if (!nail.level().hasChunkAt(pos)) return y;
        int height = nail.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        return y + Mth.clamp(height - origin.getY() - 1, -25, 25);
    }

    private static void ribbon(NailMesh mesh, float ax, float ay, float az, float bx, float by, float bz,
                               float width, int color, float alpha) {
        float length = Mth.sqrt((bx - ax) * (bx - ax) + (bz - az) * (bz - az));
        if (length < 0.001F) return;
        float x = (bz - az) * width / length, z = (ax - bx) * width / length;
        mesh.quad(new Point(ax + x, ay, az + z), new Point(bx + x, by, bz + z),
                new Point(bx - x, by, bz - z), new Point(ax - x, ay, az - z), color, alpha);
    }

    private static void particles(PoseStack poses, MultiBufferSource buffers, float y,
                                  float age, float time, float visibility, boolean reduced) {
        var glow = new NailMesh(poses, buffers.getBuffer(GLOW), true, CelestiaConfig.GLOW.get().floatValue());
        int count = moteCount(switch (CinematicEffects.quality()) {
            case 0 -> 26; case 1 -> 92; case 2 -> 230; default -> 420;
        });
        for (int i = 0; i < count; i++) {
            double a = i * 2.399963 + time * 0.019;
            float drift = fract(time * (0.014F + i % 5 * 0.002F) + i * 0.618F);
            float height = 8 + i % 10 + (1 - visibility) * 14;
            Point p = polar(0, y + drift * height, 0, 1.1F + i % 11 * 0.43F + drift, a);
            glow.diamond(p.x(), p.y(), p.z(), 0.052F + i % 5 * 0.019F, 0.34F, a,
                    i % 3 == 0 ? GOLD : CYAN, (1 - drift) * 0.72F * visibility + (1 - visibility) * drift * 0.54F);
        }
        if (age > 160 || count == 0) return;

        var debris = new NailMesh(poses, buffers.getBuffer(SOLID), false, visibility);
        for (int i = 1; i < count; i += 2) {
            float t = age - i % 13;
            if (t < 0 || age > 70) continue;
            float rise = t * (0.52F + i % 8 * 0.11F) - 0.019F * t * t;
            if (rise <= 0) continue;
            Point rock = blastPoint(y, i, t);
            debris.diamond(rock.x(), rock.y() + rise, rock.z(), 0.18F + (i % 6) * 0.075F,
                    0.58F, time * 0.08 + i, i % 3 == 0 ? 0x907360 : 0x87939b, Math.max(0, 1 - age / 70));
        }
        if (reduced) return;

        var dust = new NailMesh(poses, buffers.getBuffer(DUST), false, visibility);
        for (int i = 1; i < count; i += 2) {
            float t = age - i % 13;
            if (t < 0) continue;
            Point p = blastPoint(y + 0.4F + t * 0.023F, i, t);
            float size = 1.9F + t * 0.13F;
            double angle = i * 2.399963 + time * 0.017;
            float dx = (float) Math.sin(angle) * size, dz = (float) Math.cos(angle) * size;
            dust.quad(new Point(p.x() - dx, p.y() - size, p.z() - dz),
                    new Point(p.x() + dx, p.y() - size, p.z() + dz),
                    new Point(p.x() + dx, p.y() + size, p.z() + dz),
                    new Point(p.x() - dx, p.y() + size, p.z() - dz), 0x8394a4, (1 - age / 160) * 0.28F);
        }
    }

    private static Point blastPoint(float y, int index, float time) {
        float radius = 1.7F + time * (0.44F + index % 9 * 0.16F);
        return polar(0, y, 0, radius, index * 2.399963);
    }

    private static int moteCount(int base) { return (int) (base * CelestiaConfig.PARTICLES.get()); }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static float fract(float value) { return value - Mth.floor(value); }
}
