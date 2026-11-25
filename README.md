# Minecraft Mod

A starter mod for **Minecraft Java Edition 1.21.1** using **NeoForge**. The mod ID is `minecraftmod`. When NeoForge loads the mod, it writes `Minecraft Mod initialized` to the game log.

## Build and run

Install a **JDK 21** and check that both `java -version` and `javac -version` report version 21. From the project folder, run:

| Goal | macOS or Linux | Windows |
| --- | --- | --- |
| Build a mod JAR | `./gradlew build` | `gradlew.bat build` |
| Launch a development client | `./gradlew runClient` | `gradlew.bat runClient` |
| Launch a headless development server | `./gradlew runServer` | `gradlew.bat runServer` |

The server requires you to read and accept the Minecraft EULA in `run/eula.txt` before it can start. The build writes the mod JAR to `build/libs/`. To play with it outside the development environment, put that JAR into the `mods` folder of a NeoForge 1.21.1 installation.

Start adding mod logic in `src/main/java/io/github/xshoutgamer/minecraftmod/MinecraftMod.java`. Mod metadata lives in `src/main/resources/META-INF/neoforge.mods.toml`.
