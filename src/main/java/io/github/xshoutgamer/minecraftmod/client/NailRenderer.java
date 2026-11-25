package io.github.xshoutgamer.minecraftmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.xshoutgamer.minecraftmod.CelestiaConfig;
import io.github.xshoutgamer.minecraftmod.MinecraftMod;
import io.github.xshoutgamer.minecraftmod.nail.CelestialNail;
import io.github.xshoutgamer.minecraftmod.nail.NailProfile;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;
import java.util.WeakHashMap;

import static io.github.xshoutgamer.minecraftmod.client.NailMesh.*;

public final class NailRenderer extends EntityRenderer<CelestialNail> {
    private static final ResourceLocation STONE = MinecraftMod.id("textures/entity/celestial_materials.png");
    private static final ResourceLocation ENERGY = MinecraftMod.id("textures/entity/celestial_energy.png");
    private static final RenderType SOLID = CelestialRenderTypes.material(STONE);
    private static final RenderType GLOW = CelestialRenderTypes.energy(ENERGY);
    private static final RenderType DUST =
            RenderType.entityTranslucent(MinecraftMod.id("textures/entity/celestial_dust.png"));

    private static final RenderType DARK = RenderType.entityTranslucent(ENERGY);
    private final WeakHashMap<CelestialNail, CelestialStorm> storms = new WeakHashMap<>();

    public NailRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override
    public boolean shouldRender(CelestialNail nail, Frustum frustum, double x, double y, double z) {
        double dx = x - nail.getX(), dy = y - nail.target().getY(), dz = z - nail.getZ();
        int limit = CelestiaConfig.VISUAL_RANGE.get();
        if (dx * dx + dz * dz > (double) limit * limit || Math.abs(dy) > limit) return false;
        double radius = Math.max(CelestiaConfig.BLAST_RADIUS.get() * 1.60, CelestiaConfig.SEAL_DIAMETER.get() * 1.82);
        return frustum.isVisible(new AABB(nail.getX() - radius, nail.target().getY() - 45, nail.getZ() - radius,
                nail.getX() + radius, Math.max(nail.getY() + 255, nail.target().getY() + CelestiaConfig.BEAM_HEIGHT.get() + 10), nail.getZ() + radius));
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
        float charge = phase == CelestialNail.Phase.CHARGING ? smooth(age / Math.max(1, nail.chargeTicks())) : 0;
        float pulse = (0.78F + 0.22F * Mth.sin(time * 0.13F)) * (1 + charge * 0.68F);
        float distance = (float) Math.sqrt(entityRenderDispatcher.distanceToSqr(nail));
        if (distance > 285) quality = Math.min(2, quality);

        if (quality > 0 && intensity > 0 && CelestiaConfig.LIGHTNING.get() > 0) {
            storms.computeIfAbsent(nail, entity -> new CelestialStorm(entity.getUUID().getMostSignificantBits()));
        }
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .subtract(nail.getX(), animatedY, nail.getZ());
        var storm = quality == 0 ? null : storms.get(nail);
        poses.pushPose();
        poses.translate(0, animatedY - renderOrigin, 0);
        poses.pushPose();
        poses.scale(nail.modelWidth() / 8F, modelScale * (0.72F + 0.28F * visibility), nail.modelWidth() / 8F);
        float motionTime = phase == CelestialNail.Phase.SOLIDIFYING
                ? time - age * (1 - recession) : time;
        monument(new NailMesh(poses, buffers.getBuffer(SOLID), false, visibility), motionTime, recession);
        var emissive = new NailMesh(poses, buffers.getBuffer(GLOW), true, visibility * intensity * (0.16F + 0.84F * recession));
        core(emissive, motionTime, pulse, reduced);
        if (quality >= 2) EnergyChoreography.channel(emissive, time, .88F);
        poses.popPose();

        if (phase.ordinal() < CelestialNail.Phase.IMPACTING.ordinal()) {
            var sky = new NailMesh(poses, buffers.getBuffer(GLOW), true, intensity);
            groundSeal(nail, sky, vertical, time, reduced);
            if (phase == CelestialNail.Phase.DESCENDING) {
                descent(sky, age, time, pulse, reduced);
            } else {
                float contraction = phase == CelestialNail.Phase.CHARGING ? smooth((age - nail.chargeTicks() + 11) / 11) : 0;
                WeatherMeshes.seal(sky, nail.modelHeight() + 2, age, time, phase == CelestialNail.Phase.SUMMONING,
                        contraction, charge, quality);
                WeatherMeshes.star(sky, nail.modelHeight() + 2, 74 + 95 * charge, time, 0.18F + 0.53F * charge,
                        quality, true);
                summoning(sky, phase, age, time, pulse, reduced);
                if (storm != null) {
                    float skyAge = switch (phase) {
                        case SUMMONING -> age;
                        case LEVITATING -> CelestialNail.MATERIALIZE_TICKS + age;
                        default -> CelestialNail.MATERIALIZE_TICKS + 16 + age;
                    };
                    storm.frame(true, skyAge, time, nail.modelHeight() + 5, CelestiaConfig.SEAL_DIAMETER.get() * 1.13F,
                            quality, charge, camera).glow(sky);
                }
                if (quality >= 2) EnergyChoreography.sky(sky, camera, nail.modelHeight() + 4, time, intensity, charge, quality);
            }
        } else {
            float surface = nail.surfaceAge() + partial;
            var glow = new NailMesh(poses, buffers.getBuffer(GLOW), true, visibility * intensity * recession);
            WeatherMeshes.impact(glow, vertical, surface, time, nail.radius(), quality);
            var discharge = storm == null ? null : storm.frame(false, surface, time, vertical + 3,
                    CelestiaConfig.BLAST_RADIUS.get() * .9F, quality, 0, camera);
            if (discharge != null) discharge.glow(glow);
            if (quality >= 2) EnergyChoreography.land(nail, glow, camera, vertical, surface, time, quality);
            groundFront(nail, glow, vertical, surface, time, quality);
            aftermath(glow, vertical, floor(nail, 0, 0, vertical), surface, time, nail.radius(), reduced);
            if (distance < 300 && recession > 0.04F) {
                fractures(nail, glow, vertical, surface, time, reduced);
                if (!reduced && surface < 36) arcs(glow, vertical, surface, time);
                particles(poses, buffers, vertical, surface, time, visibility * recession, quality);
            }
            if (discharge != null && surface < 43) {
                discharge.darkCenters(new NailMesh(poses, buffers.getBuffer(DARK), false, intensity * recession));
            }
        }
        poses.popPose();
    }


    private static void groundFront(CelestialNail nail, NailMesh mesh, float y, float age, float time, int quality) {
        if (age > 110 || quality == 0) return;
        int steps = quality > 2 ? 96 : 48;
        for (int wave = 0; wave < 3; wave++) {
            float t = (age - wave * 11) / (65 + 8F * wave);
            if (t < 0 || t >= 1) continue;
            float r = (3 + CelestiaConfig.BLAST_RADIUS.get() * (0.86F + wave * 0.05F)) * (1 - (1 - t) * (1 - t));
            Vec3[] crest = new Vec3[steps + 1];
            for (int i = 0; i < steps; i++) {
                Point next = polar(0, 0, 0, r, Math.PI * 2 * i / steps);
                crest[i] = new Vec3(next.x(), floor(nail, next.x(), next.z(), y) + 0.31F, next.z());
            }
            crest[steps] = crest[0];
            float alpha = (1 - t) * (wave == 0 ? 0.84F : .48F);
            EnergyPath.render(mesh, crest, null, .32F + t * 2.1F, alpha,
                    wave % 2 == 0 ? CYAN : GOLD, time * .16F, 1.05F, false);
        }
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
        double corner = Math.PI / 12;
        for (int i = 1; i < NailProfile.sections(); i++) {
            float y = (float) NailProfile.level(i - 1) * 24;
            float end = (float) NailProfile.level(i) * 24;
            float lower = (float) NailProfile.width(i - 1) * 4, upper = (float) NailProfile.width(i) * 4;
            int tile = i <= 2 || i >= 14 ? 2 : i == 5 || i == 12 ? 1 : i == 8 || i == 11 ? 3 : 0;
            int shadow = tile == 2 ? 0xa6d8e8 : 0xc5c8cb;
            mesh.section(0, y, 0, end - y, lower, upper, 12, corner, 0xffffff, shadow, 1, tile);
        }
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI / 4 * i;
            Point p = polar(0, 10.2F, 0, 2.05F, angle);
            mesh.section(p.x(), 10.3F, p.z(), 1.1F, 0.42F, 0.29F, 5, angle, SILVER, IVORY, 1);
            mesh.section(p.x(), 11.4F, p.z(), 4.6F, 0.29F, 0.29F, 5, angle, IVORY, 0x7996b2, 1);
            mesh.section(p.x(), 16, p.z(), 1.05F, 0.29F, 0.54F, 5, angle, IVORY, SILVER, 1);
            for (int mark = 0; mark < 7; mark++) {
                Point engraving = polar(0, 11 + mark * 0.66F, 0, 2.33F, angle);
                mesh.diamond(engraving.x(), engraving.y(), engraving.z(), 0.075F + 0.05F * (mark % 2), 0.39F, angle, GOLD, 1);
            }
            if (recession > 0.02F) {
                double orbit = angle + time * (i % 2 == 0 ? 0.026 : -0.019);
                Point fragment = polar(0, 10.4F + (i % 4) * 2 + Mth.sin(time * 0.07F + i) * 0.65F, 0, 3 + recession * 2.55F, orbit);
                mesh.diamond(fragment.x(), fragment.y(), fragment.z(), 0.4F + 0.14F * (i % 3), 2.4F, orbit, IVORY, recession);
                mesh.section(fragment.x(), fragment.y() + 1.27F, fragment.z(), 0.15F, 0.52F, 0.45F, 4,
                        orbit, 0xffffff, 0xd9c6ab, recession, 1);
            }
        }
    }

    private static void core(NailMesh mesh, float time, float pulse, boolean reduced) {
        mesh.section(0, 0.10F, 0, 6.9F, 0, 1.87F, 12, Math.PI / 12, CYAN, BLUE, 0.26F * pulse);
        mesh.diamond(0, 22.4F, 0, 0.86F, 1.65F, time * 0.055F, WHITE, 0.65F * pulse);
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI / 3 * i;
            float x = (float) Math.cos(angle) * 2.32F, z = (float) Math.sin(angle) * 2.32F;
            mesh.section(x, 10.65F, z, 6.05F, 0.052F, 0.052F, 4, angle, CYAN, WHITE, 0.72F * pulse);
            float y = 10.65F + ((time * 0.15F + i * 1.16F) % 5.4F);
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
        float length = Math.min(245, 62 + age * 28.0F);
        mesh.beam(2, 20 + length, 4.1F, 0.95F, time * 0.14, CYAN, 0.30F * pulse);
        mesh.beam(9, 15 + length, 1.65F, 0.02F, -time * 0.1, WHITE, 0.67F);
        for (int i = 0; i < (reduced ? 3 : 7); i++) {
            float cycle = fract(i / 7F + age * 0.065F);
            float radius = 6 + cycle * 88;
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
        float columnHeight = CelestiaConfig.BEAM_HEIGHT.get() * (0.45F + 0.55F * (float) Math.exp(-age / 410F));
        mesh.beam(floor, columnHeight, 3.2F, 0.25F, time * 0.035, CYAN, beam);
        if (!reduced) {
            mesh.beam(floor, columnHeight * 0.89F, 13.2F, 0.45F, -time * 0.02, BLUE, beam * 0.42F);
            mesh.beam(floor, columnHeight * 0.96F, 1.45F, 0.21F, time * 0.075, WHITE, beam * 0.7F);
        }
        float flash = CelestiaConfig.REDUCED_FLASH.get() ? 0 : CelestiaConfig.FLASH.get().floatValue();
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
            ribbon(mesh, ax, ay, az, bx, by, bz, segment.width() * 0.61F, CYAN, wave);
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

    static float floor(CelestialNail nail, float x, float z, float y) {
        var origin = nail.target();
        var pos = origin.offset(Mth.floor(x + 0.5F), 0, Mth.floor(z + 0.5F));
        if (!nail.level().hasChunkAt(pos)) return y;
        int height = nail.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        return y + Mth.clamp(height - origin.getY() - 1, -58, 49);
    }

    private static void ribbon(NailMesh mesh, float ax, float ay, float az, float bx, float by, float bz,
                               float width, int color, float alpha) {
        float length = Mth.sqrt((bx - ax) * (bx - ax) + (bz - az) * (bz - az));
        if (length < 0.001F) return;
        float x = (bz - az) * width / length, z = (ax - bx) * width / length;
        mesh.softQuad(new Point(ax + x, ay, az + z), new Point(ax - x, ay, az - z),
                new Point(bx - x, by, bz - z), new Point(bx + x, by, bz + z), color, alpha);
    }

    private static void particles(PoseStack poses, MultiBufferSource buffers, float y,
                                  float age, float time, float visibility, int quality) {
        boolean reduced = quality == 0;
        var glow = new NailMesh(poses, buffers.getBuffer(GLOW), true, CelestiaConfig.GLOW.get().floatValue());
        int count = moteCount(switch (quality) {
            case 0 -> 32; case 1 -> 110; case 2 -> 285; case 3 -> 650; default -> 1060;
        });
        for (int i = 0; i < count; i++) {
            double a = i * 2.399963 + time * 0.019;
            float drift = fract(time * (0.014F + i % 5 * 0.002F) + i * 0.618F);
            float height = 8 + i % 10 + (1 - visibility) * 14;
            Point p = polar(0, y + drift * height, 0, 1.1F + i % 57 * 2.77F + drift * 12, a);
            glow.diamond(p.x(), p.y(), p.z(), 0.052F + i % 5 * 0.019F, 0.34F, a,
                    i % 3 == 0 ? GOLD : CYAN, (1 - drift) * 0.72F * visibility + (1 - visibility) * drift * 0.54F);
        }
        if (age > 320 || count == 0) return;

        var debris = new NailMesh(poses, buffers.getBuffer(SOLID), false, visibility);
        for (int i = 1; i < count; i += 2) {
            float t = age - i % 13;
            if (t < 0 || age > 104) continue;
            float rise = t * (0.78F + i % 8 * 0.19F) - 0.019F * t * t;
            if (rise <= 0) continue;
            Point rock = blastPoint(y, i, t);
            debris.diamond(rock.x(), rock.y() + rise, rock.z(), 0.28F + (i % 6) * 0.14F,
                    0.95F, time * 0.08 + i, i % 3 == 0 ? 0x907360 : 0x87939b, Math.max(0, 1 - age / 104));
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
                    new Point(p.x() - dx, p.y() + size, p.z() - dz), 0x8394a4, (1 - age / 320) * 0.28F);
        }
    }

    private static Point blastPoint(float y, int index, float time) {
        float radius = 1.7F + time * (0.64F + index % 11 * 0.21F);
        return polar(0, y, 0, radius, index * 2.399963);
    }

    private static int moteCount(int base) { return (int) (base * CelestiaConfig.PARTICLES.get()); }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static float fract(float value) { return value - Mth.floor(value); }
}
