# Celestia: Divine Authority

For **Minecraft Java Edition 1.21.1**, **NeoForge 21.1**, and **Java 21**. Aim at exposed terrain and press **B**. Change the binding under **Options → Controls → Key Binds → Celestia: Divine Authority**.

A 40-block monument materializes 112 blocks above the target beneath a 92-block sky seal. At the default altitude and speed it falls in roughly half a second. The impact creates a layered energy dome and propagating shockwaves, carves a roughly 40-block-radius crater up to 15 blocks deep, and sends fractures up to roughly 90 blocks outward. After about 45 seconds from impact, the monument solidifies into collidable, harvestable celestial blocks. The permanent core and fracture residue emit block light and have animated textures.

Use a disposable world for your first cast. The enabled-by-default terrain edits, fractured blocks, and monument are permanent. Install the same mod version on the server and participating clients. Existing 0.1.0 config files retain their previous values. Back them up and regenerate them if you want the new defaults.

## Configuration

- **Server:** `minecraftmod-server.toml` in `config/`, with optional overrides in the world's `serverconfig` directory. Tune altitude, height, timing, damage, sound, terrain edits and temporary lighting. Set `persistenceSeconds = -1` to keep the animated entity. A monument that cannot be safely placed remains an entity and retries placement when its caster is present.
- **Client:** `config/minecraftmod-client.toml`. Select `LOW`, `MEDIUM`, `HIGH` or `CINEMATIC` via `visualQuality`. Set `flashIntensity`, `cameraShakeIntensity` or `motionStreakIntensity` to zero to disable each effect. `reducedEffects = true` selects the lowest geometry budget.
- **Terrain and lighting:** Edits are batched and skip unloaded chunks. They check build height, world border, spawn protection and NeoForge break/place events. Additional blocks can be protected through the `minecraftmod:impact_immune` block tag. Region protection depends on the protection mod honoring these events. Set both `terrainDestruction = false` and `environmentLighting = false` to suppress impact carving and temporary light. The permanent monument may still form in clear space.
- **Rendering:** The flash, fog tint, motion streaks, emissive geometry and additive glow are shader-pack-free. Motion streaks approximate the impression of blur without sampling or warping the rendered scene. Visibility from a distance also depends on server tracking and client render distance.

## Build and play

With JDK 21, run `./gradlew build` (Windows: `gradlew.bat build`). Put the JAR from `build/libs/` in the profile's `mods` folder. For development, use `./gradlew runClient` or `./gradlew runServer`. A dedicated server requires acceptance of the Minecraft EULA.

In a test world, hold **B** briefly and check that it casts once. Watch the monument after impact until the rings fade and it solidifies. Walk into the monument to verify collision, then reopen the world to check persistence. To check multiplayer timing, watch the same cast from two clients.

Original textures and audio ship in the mod. `python3 tools/generate_assets.py` regenerates the entity textures and sound effects using Pillow, NumPy and FFmpeg with libvorbis.
