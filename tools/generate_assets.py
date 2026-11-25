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
    Image.new("RGBA", (4, 4), (255, 255, 255, 255)).save(TEXTURES / "celestial_energy.png")


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
        swell = np.sin(2 * np.pi * (70 * t + 24 * t * u * u))
        pulses = 0.72 + 0.28 * np.sin(2 * np.pi * (1.4 * t + 7 * t * u * u))
        wave = u ** 0.55 * (0.43 * bass + 0.29 * chord + 0.18 * swell + 0.13 * soft_noise) * pulses
        wave *= np.clip((0.985 - u) * 25, 0, 1)
    elif name == "descent":
        glide = np.sin(2 * np.pi * (218 * t - 75 * t * t))
        wave = np.sin(np.pi * u) ** 0.6 * (0.52 * glide + (0.12 + u) * soft_noise + 0.15 * air)
    elif name == "release":
        chord_sweep = sum(np.sin(2 * np.pi * (f * t - 6.5 * t * t)) for f in (246, 369, 552, 739)) / 4
        wave = (0.45 * chord_sweep + 0.25 * soft_noise) * np.exp(-2.1 * t)
        for delay in (0.18, 0.41, 0.66):
            n = int(delay * RATE)
            wave[n:] += 0.07 * chord_sweep[:-n] * np.exp(-5 * t[:-n])
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
    cues = {"summon": 1.7, "gather": 1.1, "charge": 5.78, "descent": 0.82, "impact": 3.1,
            "shockwave": 1.2, "fracture": 0.9, "ambient": 3.8, "dissolve": 1.8, "debris": 1.75, "rumble": 6.4, "release": 2.25}
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
