#!/usr/bin/env python3
"""Builds the ASHFALL UI art of the bottom panel and the top-screen menu (design phase 11) into app/src/main/assets/ui/.

Sources (assets/ashfall-ai, the owner's own FLUX / Meshy art):
  flux/regions/{cinder_flats,deep_mines}  -> ui/region_flats.webp, ui/region_mines.webp   (640x360, lossy)
  flux/regions/{hydroponic_vaults,refinery_district} -> ui/region_hydro.webp, ui/region_refinery.webp
  region_wreck.webp: no painting of the Meridian Wreck exists yet, so it is the refinery district desaturated and tinted blue
                     (a stand-in; replace by a flux/regions image and one line in main() when there is one)
  flux/menu/title                         -> ui/title.webp                               (960x540, top screen)
  flux/menu/{bastion,spectre,xenomant}    -> ui/hero_<key>.webp                          (640x360, hero cards)
  meshy/icons/portrait_<hero>             -> ui/portrait_<key>.webp                      (256x256)
  flux/stats/*                            -> ui/stats.webp  (one 7x2 sheet of 64 px icons, order = UiArt.STAT_KEYS)

Run: python3 tools/build_ui_assets.py   (needs Pillow). The panel decodes these lazily, at display size, when a screen opens.
"""
import os
from PIL import Image, ImageDraw, ImageOps

HERE = os.path.dirname(os.path.abspath(__file__))
# shared asset library: felixarvid2/test/assets (set ASSET_LIBRARY, default: a sibling clone ../test/assets)
LIBRARY = os.environ.get("ASSET_LIBRARY") or os.path.join(HERE, "..", "..", "test", "assets")
AI = os.path.normpath(os.path.join(LIBRARY, "ashfall-ai"))
OUT = os.path.normpath(os.path.join(HERE, "..", "app", "src", "main", "assets", "ui"))

# keep in step with UiArt.STAT_KEYS
STAT_KEYS = ["damage", "maxLife", "armor", "attackSpeed", "cooldownReduction", "critChance", "critDamage",
             "damageReduction", "damageVsElite", "damageVsVulnerable", "moveSpeed", "potionHealing", "resourceGen", "generic"]
STAT_PX = 64


def save(im, name, q):
    os.makedirs(OUT, exist_ok=True)
    p = os.path.join(OUT, name)
    im.save(p, "WEBP", quality=q, method=6)
    print("%-22s %6d B  %dx%d" % (name, os.path.getsize(p), im.width, im.height))


def rounded(im, frac):
    m = Image.new("L", (im.width * 4, im.height * 4), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, m.width - 1, m.height - 1), radius=int(m.width * frac), fill=255)
    im = im.convert("RGBA")
    im.putalpha(m.resize(im.size, Image.LANCZOS))
    return im


def wreck_stand_in(im):
    """The refinery painting with most of its colour taken out and a cold blue-black cast (the Wreck has no painting of its own yet)."""
    grey = ImageOps.grayscale(im).convert("RGB")
    cast = ImageOps.colorize(ImageOps.grayscale(im), black=(4, 8, 18), white=(150, 182, 230))
    return Image.blend(Image.blend(im, grey, 0.8), cast, 0.55)


def regions():
    for src, dst in (("cinder_flats", "region_flats"), ("deep_mines", "region_mines"),
                     ("hydroponic_vaults", "region_hydro"), ("refinery_district", "region_refinery")):
        save(Image.open(os.path.join(AI, "flux", "regions", src + ".webp")).convert("RGB").resize((640, 360), Image.LANCZOS), dst + ".webp", 78)
    refinery = Image.open(os.path.join(AI, "flux", "regions", "refinery_district.webp")).convert("RGB").resize((640, 360), Image.LANCZOS)
    save(wreck_stand_in(refinery), "region_wreck.webp", 78)


def main():
    regions()
    save(Image.open(os.path.join(AI, "flux", "menu", "title.webp")).convert("RGB").resize((960, 540), Image.LANCZOS), "title.webp", 80)
    for k in ("bastion", "spectre", "xenomant"):
        save(Image.open(os.path.join(AI, "flux", "menu", k + ".webp")).convert("RGB").resize((640, 360), Image.LANCZOS), "hero_" + k + ".webp", 78)
        # lossy WEBP with alpha: the portrait keeps its rounded corners
        save(rounded(Image.open(os.path.join(AI, "meshy", "icons", "portrait_" + k + ".webp")).convert("RGBA").resize((256, 256), Image.LANCZOS), 0.06),
             "portrait_" + k + ".webp", 86)
    sheet = Image.new("RGBA", (STAT_PX * 7, STAT_PX * 2), (0, 0, 0, 0))
    for i, k in enumerate(STAT_KEYS):
        im = rounded(Image.open(os.path.join(AI, "flux", "stats", k + ".webp")).convert("RGBA").resize((STAT_PX, STAT_PX), Image.LANCZOS), 0.17)
        sheet.paste(im, ((i % 7) * STAT_PX, (i // 7) * STAT_PX))
    save(sheet, "stats.webp", 90)


if __name__ == "__main__":
    main()
