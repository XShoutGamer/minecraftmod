# Celestia: Divine Authority

For **Minecraft Java Edition 1.21.1**, **NeoForge 21.1**, and **Java 21**. Aim at exposed ground and press **B**. Change the binding in **Options → Controls → Key Binds → Celestia: Divine Authority**.

The default Nail is **60 blocks long and 8 blocks wide**. It gathers energy for six seconds at 200 blocks above the target, then descends in seven server ticks. The ordinary ground datum is the top of the targeted block. With destructive terrain enabled and sufficient build space, the finished structure extends 30 blocks above that datum and 30 below it.

With terrain destruction enabled, the falling Nail cuts a shaft about 12 blocks across through any obstructions above the target. At impact it opens a crater about 44 blocks across by default, with irregular shelves up to 50 blocks below the sampled ground and a central pedestal under the Nail. Separate branching cuts excavate a 1–5-block-wide path up to 12 blocks deep and can extend roughly 145 blocks. Bedrock, fluids, unloaded chunks, build limits and region protections can shorten cuts. Visible rings and atmospheric effects cover a wider area than the excavation. The default starburst and shockwave radius is 320 blocks, with a 180-block sky seal and 360-block light column.

After roughly 53 seconds from impact, counting the impact and conversion animations, the Nail becomes collidable world blocks. The permanent block profile shares its cross sections with the animated mesh. The core and fracture residue emit block light and have animated textures. Textures and audio are original.

The impact shockwave travels 100 blocks in one second at normal server speed. It damages and pushes living entities outward once per impact. Both effects fall linearly from full strength at the impact center to zero at 100 blocks, using three-dimensional distance. The caster is immune, and the shockwave respects PvP settings and friendly-fire rules. Configure the peak damage and push independently with `impactDamage` and `impactKnockback` in the server config. Zero disables the corresponding effect.

Use a disposable world for the first cast. Install the same mod version on the server and participating clients.

## Scarlet Hex

Tap **N** to establish or dismiss the Hex. Hold **N** to open the spell wheel, point at a glyph, and release **N** to select it. **Esc** closes the wheel. **V** casts the selected spell. For a sustained spell, hold **V** and release it to finish. Both bindings are in Minecraft's Controls menu.

**G**, **F**, and **H** default to Chaos Energy Orb, Chaos Barrier, and Absolute Telekinesis. Hold **N**, hover over a spell, and press one of those keys to save the assignment. **R** and **C** are reserved for independent magic by default. If an existing profile has Hex shortcuts bound to either key, rebind those shortcuts in Controls. The three shortcut keys can be changed individually in Minecraft Controls. If a shortcut shares the swap-hands key, the shortcut takes priority while you can cast Hex spells.

| Spell | Controls and behavior |
| --- | --- |
| Absolute Telekinesis | Aim at a mob, item, or falling block and hold **V**. Nearby eligible objects can lift with it. Release to lower them. |
| Reality Rewrite | Aim at exposed ground and hold **V** to increase the size of the patch. Release to send the wave. |
| Chaos Energy Orb | Hold **V** to charge. The orb detonates on terrain or a creature, damaging and pushing multiple targets with distance falloff. |
| Chaos Barrier | Hold **V** to deflect incoming projectiles. Crouch when starting to cycle dome, forward, personal, and stationary modes. |
| Scarlet Ascension | Press **V** to toggle flight and damage resistance, subject to server settings. |
| Reality Fracture | Press **V** to open a rift and travel to a validated landing spot. Crouch and press **V** to cycle between your crosshair and saved Teleportation Runes. |
| Scarlet Runes | Aim and press **V** to place a rune. Crouch and press **V** to cycle Protection, Gravity, Silence, Restoration, and Teleportation. |
| Temporal Authority | Press **V** to slow nearby hostile mobs, falling blocks, items, and projectiles locally. |
| Chaos Gravity Field | Press **V** to lift nearby entities. Crouch when casting to cycle the gravity modes. |
| Astral Echo | Press **V** for a moving crimson projection. Crouch to anchor it in place. It attacks nearby hostile mobs in sight while the domain is active. |
| Genesis | Aim at the altered area and press **V** to restore your recorded block changes. |
| Scarlet World | Press **V** to expand the physical domain in stages to 150, 200, and 300 blocks from the default 100. The casting boost fades, and the expanded radius lasts until dismissal. |

The domain defaults to a 100-block radius, five seconds of expansion, and persistence until dismissal. The **Ultra Scarlet** and **Cinematic Overdrive** presets request the server's separate 150-block default. Operators can set both radii to the same value if they want a single world-wide gameplay radius. The server also controls target mode, spell cooldowns, duration, and permissions in `minecraftmod-hex-server.toml`. The default aim mode uses where you're looking. Stationary, following, and cosmetic aura modes are available. Active domains and spells clear on logout or dimension travel.

Reality Rewrite edits blocks by default for the owner of a local world, even with cheats off. To make it cosmetic locally, set `singlePlayerTerrainRewrite = false`. Dedicated servers require `terrainRewrite = true` and operator permissions. Hold the spell to preview the affected surface. Its real mode changes eligible layers of stone, soil, and sand and records original states in a bounded, persistent per-dimension journal. Set the material with `rewritePreset` or use `CUSTOM` with `rewriteCustomBlock`. Temporary edits are restored when their owner is online in the dimension, the chunk is loaded, and the replacement is still present. Genesis restores your changes where the expected replacement is still present. `recordNailExcavation` separately opts into recording future Nail excavation by operators. Genesis needs those records to restore a crater. Containers, fluids, protected blocks, and inaccessible chunks are excluded.

**Cinematic Overdrive** increases smoke ray-march samples, mesh density, tendril detail, and particles, and extends the Nail effects' rendering range and detail. Set `visualQuality = "CINEMATIC_OVERDRIVE"` in `minecraftmod-client.toml`. The Hex uses procedural 3D-density smoke integrated in the fragment shader, translucent and emissive geometry, crimson fog and color grading, and original positional audio. The edition uses view-facing smoke bounds and screen-space lighting. The client settings in `minecraftmod-hex-client.toml` control smoke, glow, sparks, lightning, and atmosphere. Screen distortion defaults to off.

The `firstPersonMagic` section of `minecraftmod-hex-client.toml` controls the hand aura preset, offsets, spacing, scale, spark density, smoke, glow, camera fade, tendril length, and soft crosshair mask. Third-person hand scale has its own setting.

The Orb defaults to an 8–15 block blast, or 25 blocks during Scarlet World. Player damage and terrain edits from the Orb are off by default. Protection runes reduce damage to their owner and allies. Silence applies to other players' Hex spell casts. Teleportation anchors persist. Other active rune effects expire or clear when their domain closes. Temporal Authority slows or suspends eligible entity ticks locally. Gravity fields steer entities by velocity without permanently changing their gravity or AI flags. Set `abilityDebug = true` on the server to show cast targets and affected counts where available.

## Independent Chaos Magic and Darkhold

- **X** channels Chaos Disintegration at a mob or block under your crosshair. The target must still be reachable when the channel finishes. Crouch while casting to channel a small block area. Block removal uses the Reality Rewrite permissions and is recorded for Genesis.
- **R** opens the independent spell menu and **C** casts its selected power. It contains Chaos Flight, Personal Chaos Shield, Reality Step, Personal Telekinesis, and Chaos Projection. These powers work without a Hex.
- Tap **J** to summon or dismiss the Darkhold. While it is summoned, hold **J** to open the grimoire. Click a spell, then press and release **K** to cast, or hold **K** for extra power. Crouch while casting Memory of Reality to restore a bounded batch of your recorded changes.
- Darkhold spells include an anchored Dreamwalker flight, Abyssal Portal teleportation, orbiting Forbidden Guardians, a local Eternal Eclipse, Mirror Reality illusions, Void Singularity pull, an Astral Library sanctuary, Dimensional Prison for hostile mobs, Memory of Reality, and The Forbidden Scripture range boost.

The default Reality Rewrite shape is a sphere. Set `rewriteVolume` to `CYLINDER`, `TERRAIN`, or `CUBE` for other shapes. Hold the spell to increase its radius from 6 blocks up to 60 blocks within `rewriteChargeTicks` by default. Set `rewriteVolumeMaxRadius` to override it. A customized legacy radius is honored when the new setting is zero. The Scarlet palette blackens logs and planks, removes foliage, and adds animated Scarlet-Fractured Stone. Mob corruption is separately configurable. Reality Rewrite has a maximum three-second cooldown at 20 TPS. Edits run in batches and stop at the per-dimension terrain journal limit. The default is 262,144 saved blocks, and worlds with the previous stock limits pick up the larger capacity automatically. Set `terrainJournalLimit` for a specific limit. Aim Genesis at earlier corrupted terrain to restore it and free its undo records.

Dreamwalker uses the player's existing avatar and returns it to an anchor if safe. The library and Mirror Reality use visual geometry. Memory of Reality previews recorded locations with spectral markers. Guardians are server-controlled projections. Container block entities are protected, and exact reconstruction requires previously recorded edits.

### Darkhold field actions

Keep the spell selected after casting with **K**, then use **U** for its field action. You can rebind **U** in Minecraft Controls.

| Spell | Gameplay |
| --- | --- |
| Eternal Eclipse | Creates a local darkness field with hostile-mob pulses, personal healing and speed, damage reduction, projectile interception, and increased independent spell reach. **U** performs Shadow Passage to a safe point in the field. |
| Mirror Reality | Creates a field in the current dimension. It reflects approaching projectiles near the caster. **U** links the origin to a safe targeted landing spot. Walk into a gate to travel. **Crouch + U** places temporary solid platforms where terrain edits are permitted. |
| Void Singularity | Hold **K** to increase pull radius and damage. Its field pulls mobs, items, and supported projectiles. Hostile mobs take repeated damage near the core. |
| Astral Library | Creates a sanctuary with a temporary placement-permitted platform, healing, and defense. **U** opens the altar for reach, duration, recovery, or restoration attunements. **Crouch + U** restores nearby journaled blocks. |
| Forbidden Scripture | Manifests four formations for Dominion damage and slowing, Protection, Transcendence flight and speed, and Creation platforms with **U**. The default duration is 60 seconds before charge and attunement bonuses. |

Reality Rewrite propagates from the cast center through its selected volume. Configure inner, middle, and edge coverage and initial or periodic mob damage in `minecraftmod-hex-server.toml`. The Scarlet palette uses custom blackened wood, planks, soil, sand, bricks, and stone. It physically removes eligible foliage within the cast and journals the changed positions for Genesis. Canopies are selected by position and block tags. Blocks outside the cast keep their foliage, and inventories and unrecorded changes stay outside Genesis's journal. Temporary platforms use saved block ticks for expiration.

## Configuration

Use `/celestia cooldown disable` to turn off the Nail's cast cooldown, `/celestia cooldown enable` to restore the configured cooldown, and `/celestia cooldown` to check its status. The setting is saved per world and applies to all players. The commands require cheats or operator permission.

- **Server** — Edit `config/minecraftmod-server.toml`, with optional overrides in the world's `serverconfig` directory. Set `craterRadius` to override the radius. At zero it uses the new 22-block default or a legacy `excavationRadius` customized away from 15. The crater starts cutting on the impact tick and uses its own `craterEditsPerTick` budget. `terrainEditsPerTick` controls the slower branching cuts. Set `visualOnly = true` for an event with no damage, terrain edits, or monument placement. Set `solidificationSeconds = -1` to keep the animated entity. If any part of the monument cannot be safely placed, the entity stays in place and retries when the caster is present.
- **Graphics** — Set `visualQuality` in `config/minecraftmod-client.toml` to `LOW`, `MEDIUM`, `HIGH`, `CINEMATIC`, `ULTRA_CELESTIAL`, `ULTRA_SCARLET`, or `CINEMATIC_OVERDRIVE`. The Ultra presets add scene-space exposure, radial blur and distortion, and bright-pixel scattering through a bundled GLSL pass. Effects need no shader pack. If post-processing fails, the renderer uses the fog and exposure overlay. Visual distances are configurable and also depend on world view distance and entity tracking.
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
