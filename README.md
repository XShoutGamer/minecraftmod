# Celestia: Divine Authority

For **Minecraft Java Edition 1.21.1**, **NeoForge 21.1**, and **Java 21**. Aim at exposed ground and press **B**. Change the binding in **Options → Controls → Key Binds → Celestia: Divine Authority**.

The default Nail is **60 blocks long and 8 blocks wide**. It gathers energy for six seconds at 200 blocks above the target, then descends in seven server ticks. The ordinary ground datum is the top of the targeted block. With destructive terrain enabled and sufficient build space, the finished structure extends 30 blocks above that datum and 30 below it.

With terrain destruction enabled, the falling Nail cuts a shaft about 12 blocks across through any obstructions above the target. At impact it opens a crater about 44 blocks across by default, with irregular shelves up to 50 blocks below the sampled ground and a central pedestal under the Nail. Separate branching cuts excavate a 1–5-block-wide path up to 12 blocks deep and can extend roughly 145 blocks. Bedrock, fluids, unloaded chunks, build limits and region protections can shorten cuts. Visible rings and atmospheric effects cover a wider area than the excavation. The default starburst and shockwave radius is 320 blocks, with a 180-block sky seal and 360-block light column.

After roughly 53 seconds from impact, counting the impact and conversion animations, the Nail becomes collidable world blocks. The permanent block profile shares its cross sections with the animated mesh. The core and fracture residue emit block light and have animated textures. Textures and audio are original.

The impact shockwave travels 100 blocks in one second at normal server speed. It damages and pushes living entities outward once per impact. Both effects fall linearly from full strength at the impact center to zero at 100 blocks, using three-dimensional distance. The caster is immune, and the shockwave respects PvP settings and friendly-fire rules. Configure the peak damage and push independently with `impactDamage` and `impactKnockback` in the server config. Zero disables the corresponding effect.

Use a disposable world for the first cast. Install the same mod version on the server and participating clients.

## Configuration

Use `/celestia cooldown disable` to turn off the Nail's cast cooldown, `/celestia cooldown enable` to restore the configured cooldown, and `/celestia cooldown` to check its status. The setting is saved per world and applies to all players. The commands require cheats or operator permission.

- **Server** — Edit `config/minecraftmod-server.toml`, with optional overrides in the world's `serverconfig` directory. Set `craterRadius` to override the radius. At zero it uses the new 22-block default or a legacy `excavationRadius` customized away from 15. The crater starts cutting on the impact tick and uses its own `craterEditsPerTick` budget. `terrainEditsPerTick` controls the slower branching cuts. Set `visualOnly = true` for an event with no damage, terrain edits, or monument placement. Set `solidificationSeconds = -1` to keep the animated entity. If any part of the monument cannot be safely placed, the entity stays in place and retries when the caster is present.
- **Graphics** — Set `visualQuality` in `config/minecraftmod-client.toml` to `LOW`, `MEDIUM`, `HIGH`, `CINEMATIC`, or `ULTRA_CELESTIAL`. Ultra adds scene-space exposure, radial blur and distortion, and bright-pixel scattering through a bundled GLSL pass. Effects need no shader pack. If post-processing fails, the renderer uses the fog and exposure overlay. Visual distances are configurable and also depend on world view distance and entity tracking.
- **Accessibility** — `glowIntensity`, `atmosphericIntensity`, `flashIntensity`, `exposureIntensity`, `distortionIntensity`, `cameraShakeIntensity`, and `motionStreakIntensity` are independent. Set any to zero to turn it off. `reducedEffects = true` uses the lowest geometry budget. Each impact has a single screen exposure pulse.
- **Protection** — Terrain edits check world bounds, spawn protection, and NeoForge break and placement events. Add blocks to `minecraftmod:impact_immune` to protect them. Claim integration depends on the installed protection mod honoring those events. Placement preflights the whole monument and rolls back canceled placement.

## Continuous currents and the VFX update

The blue core, crystal and fracture blocks sample one animated world-space field. Adjacent faces share their phase and old monuments receive the new material when their chunks render. The golden terrain streams, helical currents and the larger shockwave crests are joined strips. Their bright pulses travel along the length of each path.

The storm uses seeded divine, golden, crimson and residual lightning with different durations, widths and layers. Crimson bolts have an alpha-blended dark center and additive red edges. Lightning is visual and cannot start fires. New positional electrical sounds follow the server's existing cast timeline. `cinematicSoundIntensity` controls those extra layers.

**Ultra Celestial** adds denser branching, extra glow passes, atmospheric loops, descending sparks, and terrain-following ribbons. In `minecraftmod-client.toml`, use `lightningDensity`, `lightningComplexity`, `energyRibbonDensity`, `particleVariety`, and `residualEffectTicks` to tune them. `impactMonochrome = true` enables a short grayscale pulse. `impactMonochromeStrength` and `impactMonochromeTicks` control its strength and duration. It defaults to on. `reducedFlash` suppresses grayscale and exposure, while `reducedMotion` suppresses camera shake, distortion, and motion effects. These settings are independent of the selected preset.

World lighting comes from the existing luminous blocks and temporary impact light. Lightning halos, fog, and the fullscreen pass approximate surrounding illumination. Terrain ribbons follow the client heightmap, which can hide portions behind overhangs or in unloaded chunks.

## Animated lightning and monochrome impact

Crimson bolts cycle through cached seeded shapes. Their endpoints stay fixed while bends and attached branches move. The black center and red glow share the same animated vertices. The sky lightning follows independent cycles of growth, discharge, decay, and reformation. Charge level increases the number of active arcs.

- `alternateCrimsonGeometry` controls whether the main impact bolts switch paths. `lightningGeometryIntervalSeconds` defaults to `0.5` and `lightningGeometryStates` defaults to `2`. Movement, secondary-branch animation, growth speed, residual duration, and glow each have their own client settings.
- `impactMonochrome` defaults to on. It has its own setting, independent of the old inversion toggle. The scene turns grayscale first, then the crimson glow and dark core are drawn over it with the original terrain depth. When enabled, a server impact packet queues the grayscale pulse. Its default 12-tick duration starts on the first rendered frame. `impactMonochromeRecoveryTicks` controls the return to normal scenery. The shader samples the scene before applying exposure and leaves the HUD intact. `reducedFlash = true` disables the grayscale pulse.
- In a development client, **F8** previews a milder grayscale pulse without casting. Launch the client with the JVM property `minecraftmod.cinematicDebug=true` to log packet receipt, shader initialization, drawing, and cleanup.

## Build and run

With JDK 21, run `./gradlew build` (Windows: `gradlew.bat build`). Put the JAR from `build/libs/` into the NeoForge profile's `mods` folder. Use `./gradlew runClient` or `./gradlew runServer` for development. A dedicated server requires acceptance of the Minecraft EULA.

For a quick check, hold **B** briefly and confirm there is one cast. Watch from the crater edge as the rings fade and the monument solidifies. Walk into it to check collision, then reload the world to check persistence. For multiplayer, watch a single cast from two connected clients.

To regenerate the base effect texture and audio, run `python3 tools/generate_assets.py` with Pillow, NumPy, and FFmpeg with libvorbis. Run `python3 tools/generate_materials.py` to regenerate the weathered material atlas and animated block textures.
