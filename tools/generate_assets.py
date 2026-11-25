"""Regenerate the original Celestia textures and positional audio."""
from pathlib import Path
import subprocess

import numpy as np
from PIL import Image

ASSETS = Path(__file__).resolve().parents[1] / "src/main/resources/assets/minecraftmod"
TEXTURES = ASSETS / "textures/entity"
SOUNDS = ASSETS / "sounds"
RATE = 44100


def write_textures():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    rng = np.random.default_rng(840122)
    y, x = np.mgrid[:64, :64]
    stratum = 4 * np.sin(x * 0.23 + y * 0.41) + 5 * np.sin(y * 0.48 - x * 0.08)
    grain = rng.normal(0, 2.8, x.shape)
    borders = np.minimum(x, 63 - x)
    veins = np.maximum(0, np.cos(x * 0.25 + y * 0.68 + np.sin(y * 0.21)) - 0.92) * 88
    base = np.clip(241 + stratum + grain + veins - np.maximum(0, 3 - borders) * 3, 208, 255)
    Image.fromarray(base.astype(np.uint8)).convert("RGBA").save(TEXTURES / "celestial_stone.png")
    Image.new("RGBA", (4, 4), (255, 255, 255, 255)).save(TEXTURES / "celestial_energy.png")
    d = ((x - 31.5) ** 2 + (y - 31.5) ** 2) / 31.5 ** 2
    dust = np.full((64, 64, 4), 255, dtype=np.uint8)
    dust[:, :, 3] = (np.maximum(0, 1 - d) ** 2 * 255).astype(np.uint8)
    Image.fromarray(dust).save(TEXTURES / "celestial_dust.png")


def signal(name, duration, seed):
    rng = np.random.default_rng(seed)
    t = np.arange(int(RATE * duration)) / RATE
    u = t / duration
    noise = rng.uniform(-1, 1, len(t))
    soft_noise = np.convolve(noise, np.ones(36) / 36, "same")
    bass = np.sin(2 * np.pi * (43 * t + 5 * np.sin(t * 1.3)))
    chord = sum(np.sin(2 * np.pi * f * t + 0.25 * np.sin(t * 9)) for f in (150, 225, 337.5)) / 3
    air = np.sin(2 * np.pi * (614 * t + 17 * t * t)) * (0.65 + 0.35 * np.sin(t * 14))
    ramp = np.sin(np.pi * u) ** 0.8

    if name == "summon":
        wave = ramp * (0.38 * chord + 0.16 * air + 0.33 * soft_noise)
    elif name == "gather":
        wave = ramp * (0.23 * chord + 0.21 * air * (0.6 + 0.4 * np.sin(t * 42)))
    elif name == "charge":
        wave = ramp * (0.55 * bass + 0.32 * chord + 0.23 * soft_noise) * (0.75 + 0.25 * np.sin(t * 12))
        wave *= np.clip((0.96 - u) * 14, 0, 1)
    elif name == "descent":
        glide = np.sin(2 * np.pi * (218 * t - 75 * t * t))
        wave = np.sin(np.pi * u) ** 0.6 * (0.52 * glide + (0.12 + u) * soft_noise + 0.15 * air)
    elif name == "impact":
        wave = np.exp(-2.5 * t) * (0.58 * bass + 1.1 * soft_noise + 0.24 * chord)
        wave += 0.16 * noise * np.exp(-35 * t)
        for delay in (0.16, 0.41, 0.73):
            n = int(delay * RATE)
            wave[n:] += 0.08 * bass[:-n] * np.exp(-5 * t[:-n])
    elif name == "shockwave":
        wave = u ** 0.15 * np.exp(-4 * u) * (0.68 * bass + 0.42 * soft_noise)
    elif name == "fracture":
        wave = 0.1 * soft_noise * ramp
        for start in (0.03, 0.13, 0.28, 0.39):
            age = np.maximum(0, t - start)
            wave += (t >= start) * np.exp(-29 * age) * (0.4 * noise + 0.61 * soft_noise)
    elif name == "rumble":
        waves = np.sin(2 * np.pi * (34 * t + np.sin(5 * t) * 0.4)) + 0.45 * bass
        wave = ramp * np.exp(-1.7 * u) * (0.31 * waves + 1.6 * soft_noise)
    elif name == "debris":
        wave = 0.07 * soft_noise * ramp
        for start in (0.02, 0.18, 0.33, 0.60, 0.73, 0.92, 1.35):
            age = np.maximum(0, t - start)
            wave += (t >= start) * np.exp(-24 * age) * (0.16 * noise + 0.6 * soft_noise + 0.18 * bass)
    elif name == "ambient":
        wave = ramp * (0.4 * chord + 0.25 * bass + 0.1 * air)
    elif name == "dissolve":
        wave = ramp * (0.15 * chord + 0.26 * air + 0.2 * soft_noise)
    else:
        raise ValueError(name)
    fade = np.minimum(1, t * 160) * np.minimum(1, (duration - t) * 65)
    return np.clip(wave * fade, -0.98, 0.98).astype("<f4")


def write_audio():
    SOUNDS.mkdir(parents=True, exist_ok=True)
    cues = {"summon": 1.7, "gather": 1.1, "charge": 3.0, "descent": 1.3, "impact": 3.1,
            "shockwave": 1.2, "fracture": 0.9, "ambient": 3.8, "dissolve": 1.8, "debris": 1.75, "rumble": 4.7}
    for i, (name, duration) in enumerate(cues.items()):
        samples = signal(name, duration, 3910 + i).tobytes()
        subprocess.run(
            ["ffmpeg", "-loglevel", "error", "-y", "-f", "f32le", "-ar", str(RATE), "-ac", "1",
             "-i", "pipe:0", "-c:a", "libvorbis", "-q:a", "3", str(SOUNDS / f"{name}.ogg")],
            input=samples, check=True,
        )


if __name__ == "__main__":
    write_textures()
    write_audio()
