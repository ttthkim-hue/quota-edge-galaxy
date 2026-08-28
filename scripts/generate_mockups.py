"""Generate Quota Edge mockups — one line per provider."""
from PIL import Image, ImageDraw, ImageFont
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "assets"
ASSETS.mkdir(exist_ok=True)

CLAUDE = (217, 119, 87)
CODEX = (16, 163, 127)
BG = (1, 1, 1)
TEXT = (255, 255, 255)
MUTED = (142, 142, 147)
CANVAS = (10, 10, 18)


def font(size, bold=False):
    names = ["C:/Windows/Fonts/consola.ttf", "C:/Windows/Fonts/segoeui.ttf"]
    if bold:
        names = ["C:/Windows/Fonts/consolab.ttf", "C:/Windows/Fonts/segoeuib.ttf"] + names
    for n in names:
        try:
            return ImageFont.truetype(n, size)
        except OSError:
            pass
    return ImageFont.load_default()


def draw_phone(draw, ox, oy, pw, ph, home=True):
    draw.rounded_rectangle((ox, oy, ox + pw, oy + ph), 36, BG, outline=(44, 44, 46))
    draw.ellipse((ox + pw // 2 - 6, oy + 18, ox + pw // 2 + 6, oy + 30), fill=(0, 0, 0))
    draw.text((ox + 18, oy + 14), "9:41", fill=TEXT, font=font(15, True))
    draw.text((ox + pw - 50, oy + 16), "87%", fill=TEXT, font=font(12))
    f = font(10, True)
    draw.text((ox + 18, oy + 34), "● Claude 75%/86%  142m/3.2d", fill=CLAUDE, font=f)
    draw.text((ox + 18, oy + 50), "● Codex  45%/62%  089m/2.1d", fill=CODEX, font=f)
    if home:
        draw.rounded_rectangle((ox + 14, oy + 76, ox + pw - 14, oy + ph - 16), 16, (20, 20, 24))
        draw.text((ox + pw // 2 - 36, oy + ph // 2), "Your app", fill=(72, 72, 74), font=font(12))
    else:
        draw.text((ox + 70, oy + 220), "9:41", fill=TEXT, font=font(48))
        draw.text((ox + 82, oy + 278), "Fri, Aug 28", fill=MUTED, font=font(13))
        draw.rounded_rectangle((ox + 28, oy + 310, ox + pw - 28, oy + 362), 18, (30, 30, 34))
        draw.text((ox + 40, oy + 322), "● Claude 75%/86%  142m/3.2d", fill=CLAUDE, font=f)
        draw.text((ox + 40, oy + 340), "● Codex  45%/62%  089m/2.1d", fill=CODEX, font=f)


def main():
    W, H = 760, 780
    img = Image.new("RGB", (W, H), CANVAS)
    d = ImageDraw.Draw(img)
    d.text((W // 2 - 70, 30), "Quota Edge", fill=TEXT, font=font(32, True))
    d.text((W // 2 - 200, 68), "Claude 75%/86%  142m/3.2d  ·  one line each", fill=MUTED, font=font(13))
    draw_phone(d, 60, 120, 280, 580, home=True)
    d.text((60, 96), "01 Home", fill=MUTED, font=font(12))
    draw_phone(d, 380, 120, 280, 580, home=False)
    d.text((380, 96), "02 Lock screen", fill=MUTED, font=font(12))
    out = ASSETS / "mockup-hero.png"
    img.save(out)
    print(out)


if __name__ == "__main__":
    main()
