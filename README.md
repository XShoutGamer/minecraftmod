# Celestia: Divine Authority

For **Minecraft Java Edition 1.21.1**, **NeoForge 21.1**, and **Java 21**. Aim at exposed ground and press **B**. Change the binding in **Options → Controls → Key Binds → Celestia: Divine Authority**.

The default Nail is **60 blocks long and 8 blocks wide**. It gathers energy for six seconds at 200 blocks above the target, then descends in seven server ticks. The ordinary ground datum is the top of the targeted block. With destructive terrain enabled and sufficient build space, the finished structure extends 30 blocks above that datum and 30 below it.

The crater spans at most 30 blocks across, with irregular shelves up to 50 blocks below the sampled ground and a central pedestal under the Nail. Separate branching cuts excavate a 1–5-block-wide path up to 12 blocks deep and can extend roughly 145 blocks. Bedrock, fluids, unloaded chunks, build limits and region protections can shorten cuts. Visible rings and atmospheric effects cover a wider area than the excavation. The default starburst and shockwave radius is 320 blocks, with a 180-block sky seal and 360-block light column.

After roughly 53 seconds from impact, counting the impact and conversion animations, the Nail becomes collidable world blocks. The permanent block profile shares its cross sections with the animated mesh. The core and fracture residue emit block light and have animated textures. Textures and audio are original.

Use a disposable world for the first cast. Install the same mod version on the server and participating clients.

## Configuration

- **Server** — Edit `config/minecraftmod-server.toml`, with optional overrides in the world's `serverconfig` directory. Settings include geometry, timing, crater and fracture size, terrain budget per Nail per tick, damage, cooldown, sound, and temporary light. Set `visualOnly = true` for an event with no damage, terrain edits, or monument placement. Set `solidificationSeconds = -1` to keep the animated entity. If any part of the monument cannot be safely placed, the entity stays in place and retries when the caster is present.
- **Graphics** — Set `visualQuality` in `config/minecraftmod-client.toml` to `LOW`, `MEDIUM`, `HIGH`, `CINEMATIC`, or `ULTRA_CELESTIAL`. Ultra adds scene-space exposure, radial blur and distortion, and bright-pixel scattering through a bundled GLSL pass. Effects need no shader pack. If post-processing fails, the renderer uses the fog and exposure overlay. Visual distances are configurable and also depend on world view distance and entity tracking.
- **Accessibility** — `glowIntensity`, `atmosphericIntensity`, `flashIntensity`, `exposureIntensity`, `distortionIntensity`, `cameraShakeIntensity`, and `motionStreakIntensity` are independent. Set any to zero to turn it off. `reducedEffects = true` uses the lowest geometry budget. Each impact has a single screen exposure pulse.
- **Protection** — Terrain edits check world bounds, spawn protection, and NeoForge break and placement events. Add blocks to `minecraftmod:impact_immune` to protect them. Claim integration depends on the installed protection mod honoring those events. Placement preflights the whole monument and rolls back canceled placement.

## Build and run

With JDK 21, run `./gradlew build` (Windows: `gradlew.bat build`). Put the JAR from `build/libs/` into the NeoForge profile's `mods` folder. Use `./gradlew runClient` or `./gradlew runServer` for development. A dedicated server requires acceptance of the Minecraft EULA.

For a quick check, hold **B** briefly and confirm there is one cast. Watch from the crater edge as the rings fade and the monument solidifies. Walk into it to check collision, then reload the world to check persistence. For multiplayer, watch a single cast from two connected clients.

To regenerate the base effect texture and audio, run `python3 tools/generate_assets.py` with Pillow, NumPy, and FFmpeg with libvorbis. Run `python3 tools/generate_materials.py` to regenerate the weathered material atlas and animated block textures.
