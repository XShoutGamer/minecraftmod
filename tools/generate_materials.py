"""Create the original weathered Celestia materials without external images."""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ASSETS = Path(__file__).resolve().parents[1] / "src/main/resources/assets/minecraftmod/textures"
SIZE = 256
Y, X = np.mgrid[:SIZE, :SIZE] / (SIZE - 1)
RNG = np.random.default_rng(71)


def grain():
    total = np.zeros((SIZE, SIZE))
    for scale, weight in [(5, 15), (16, 8), (42, 4), (105, 2)]:
        sample = RNG.integers(0, 255, (scale, scale), dtype=np.uint8)
        total += (np.asarray(Image.fromarray(sample).resize((SIZE, SIZE), Image.Resampling.BICUBIC)) / 127.5 - 1) * weight
    return total + RNG.normal(0, 1.8, total.shape)


def surface(rgb, lighting):
    return Image.fromarray(np.uint8(np.clip(np.array(rgb)[None, None, :] + lighting[..., None], 0, 255)))


def relief(image, design, color, inset=False):
    edge = np.asarray(design.filter(ImageFilter.GaussianBlur(1.1)), dtype=np.float32) / 255
    relief_map = (np.roll(edge, 2, axis=0) - np.roll(edge, -2, axis=0)) * (-43 if inset else 46)
    image = Image.fromarray(np.uint8(np.clip(np.asarray(image).astype(float) + relief_map[..., None], 0, 255)))
    image.paste(color, (0, 0), design.point(lambda i: int(i * .73)))
    return image


def glyphs(style):
    mask = Image.new("L", (SIZE, SIZE))
    d = ImageDraw.Draw(mask)
    for offset, strength in [(10, 80), (15, 169), (26, 105)]:
        d.rounded_rectangle((offset, offset, 255-offset, 255-offset), 12, outline=strength, width=2)
    for y in (49, 102, 154, 207):
        if style == "panel":
            d.line([(54,y), (85,y), (113,y-15), (143,y-15), (171,y), (202,y)], fill=196, width=3)
            d.polygon([(128,y-11),(142,y),(128,y+11),(114,y)], outline=238, width=2)
            d.ellipse((126,y-2,130,y+2), fill=220)
            for x in (49, 207):
                d.arc((x-10,y-9,x+10,y+9), 42, 317, fill=145, width=2)
        else:
            for x in (52,128,204):
                d.line([(x-18,y),(x-9,y-10),(x,y),(x+9,y-10),(x+18,y)], fill=185, width=2)
                d.ellipse((x-3,y+6,x+3,y+12), outline=215)
    return mask


def rock():
    noise = grain()
    vein = np.exp(-abs(np.sin(16*X+32*Y+np.sin(Y*22)*.67+noise*.016))*30)
    hairline = np.exp(-abs(np.sin(47*X-14*Y+noise*.026))*55)
    rim = np.exp(-np.minimum(X, 1-X)*52) + np.exp(-np.minimum(Y, 1-Y)*55)
    return surface((198, 198, 183), noise - 19*vein - 14*hairline - rim*15 + 11*np.cos(Y*4))


def gold():
    tone = grain() + 25*np.sin(X*9+.5) + 9*np.sin(Y*80 + 3*X)
    patina = np.maximum(0, np.sin(X*14+Y*12)*np.cos(X*17-Y*7)) ** 3
    base = surface((173, 127, 68), tone)
    weathered = Image.fromarray(np.uint8(np.clip(np.asarray(base) * (1-patina[..., None]*.21), 0, 255)))
    return relief(weathered, glyphs("band"), (239, 193, 96))


def crystal(frame=0):
    phase = frame * np.pi / 4
    main = np.sin((X-.5)*14 + np.sin(Y*20)*.11)
    veins = np.exp(-abs(np.sin(X*21 - Y*26 + .13*np.sin(Y*31))) * 16)
    glow = np.exp(-(X-.52-.076*np.sin(Y*9))**2*125) * (0.62 + .32*np.sin(Y*15-phase))
    light = grain()*.36 + 18*main + 25*veins + 86*glow
    rgb = np.stack([34 + light*.7, 81 + light*1.45, 105 + light*1.57], axis=-1)
    return Image.fromarray(np.uint8(np.clip(rgb,0,255)))


def current(frame, kind, frames=12):
    """A common periodic field. Each adjacent block samples only its world-space window."""
    size = 256
    y, x = np.mgrid[:size, :size] / (size - 1)
    time = frame * 2 * np.pi / frames
    twist = 2 * np.pi * (x + 0.17*np.sin(2*np.pi*y - time) + .052*np.sin(6*np.pi*y + time))
    narrow = (0.5 + .5*np.cos(twist - .55))**39
    halo = (0.5 + .5*np.cos(twist - .55))**7
    narrow += .39*(.5 + .5*np.cos(twist + 2.5))**46
    golden = (0.5 + .5*np.cos(twist + .17 + .19*np.sin(4*np.pi*y + time)))**62
    pulse = .72 + .15*np.cos(6*np.pi*y - time) + .13*np.cos(10*np.pi*y + 2*time)
    distortion = .8*np.sin(2*np.pi*x*4 + 2*np.pi*y*9) + 1.2*np.cos(2*np.pi*x*3 - 2*np.pi*y*14)
    base = {'crystal': (19,47,67), 'core': (12,35,51), 'fracture': (26,39,45)}[kind]
    cyan = np.array([86,208,240]) * (0.90 if kind == 'fracture' else 1.)
    light = (narrow*.80 + halo*.29) * pulse
    image = (np.array(base) + distortion[...,None] + light[...,None]*cyan
             + (golden*halo*(.23+.17*np.cos(4*np.pi*y+time)))[...,None]*np.array([156,108,40]))
    return Image.fromarray(np.uint8(np.clip(image,0,255)))


def write():
    entity = ASSETS / "entity"
    blocks = ASSETS / "block"
    atlas = Image.new("RGB", (512, 512))
    stone = rock()
    sigils = relief(stone, glyphs("panel"), (155,109,56), inset=True)
    alloy = gold()
    ice = crystal()
    for i, tile in enumerate((stone, alloy, ice, sigils)):
        atlas.paste(tile, ((i%2)*SIZE, (i//2)*SIZE))
    atlas.save(entity / "celestial_materials.png")
    for name, tile in [
        ("celestial_stone", stone), ("engraved_celestial_stone", sigils),
        ("celestial_frame", alloy),
    ]:
        tile.resize((128,128), Image.Resampling.LANCZOS).save(blocks / f"{name}.png")
    for name, kind in [('luminous_core', 'core'), ('celestial_crystal', 'crystal'), ('celestial_fracture', 'fracture')]:
        strip = Image.new('RGB', (512, 256*12))
        for frame in range(12):
            tile = current(frame, kind)
            strip.paste(tile, (0, 256*frame))
            strip.paste(tile, (256, 256*frame))
        strip.save(blocks / f'{name}.png')
        (blocks / f'{name}.png.mcmeta').write_text('{"animation":{"width":512,"height":256,"frametime":3,"interpolate":true}}\n')
    distance = np.sqrt(((X-.5)*2)**2+((Y-.5)*2)**2)
    cloud = np.maximum(0, 1-distance)**1.9 * np.clip(.95 + grain()*.016, .53, 1)
    dust = Image.new("RGBA", (SIZE,SIZE),(232,244,255,0))
    dust.putalpha(Image.fromarray(np.uint8(255*cloud)))
    dust.save(entity / "celestial_dust.png")


if __name__ == "__main__":
    write()
