"""Builds DuckPad's playstore folder: screenshots within Google Play's limits, the 512 px icon and the feature graphics."""
import os
from pathlib import Path

os.environ["PYGAME_HIDE_SUPPORT_PROMPT"] = "1"
import pygame
import pygame.freetype

# Usage: make_playstore.py <folder with the raw shots from capture.py> <RobotoStatic-Regular.ttf>
# The font comes from /system/fonts on any Android device.
import sys

RAW = Path(sys.argv[1])
FONT = Path(sys.argv[2])
OUT = Path(__file__).parent

CARAMEL = (0x8F, 0x4C, 0x14)
CREAM = (0xFF, 0xF1, 0xDA)
TRIGGER = (0xF6, 0xC2, 0x7A)
PAD_BG = (0x10, 0x12, 0x14)  # controller background

pygame.init()
pygame.freetype.init()


def save(surface, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    # Google Play wants 24-bit PNG, no transparency.
    pygame.image.save(surface.convert(24) if surface.get_bitsize() != 24 else surface, str(path))
    w, h = surface.get_size()
    print(f"{path.relative_to(OUT)}  {w}x{h}  {max(w, h) / min(w, h):.2f}:1")


def load(name):
    return pygame.image.load(str(RAW / name))


def fill_black(surface, rect, color):
    """Paints the black strips left by the camera cutout or the status bar in the background colour."""
    surface.fill(color, pygame.Rect(rect))


def black_columns(surface, x_from, x_to):
    """Width of the pure black strip on the left (camera cutout in landscape)."""
    w, h = surface.get_size()
    x = 0
    while x < w and all(surface.get_at((x, y))[:3] == (0, 0, 0) for y in range(0, h, 40)):
        x += 1
    return x


def black_rows(surface):
    w, h = surface.get_size()
    y = 0
    while y < h and all(surface.get_at((x, y))[:3] == (0, 0, 0) for x in range(0, w, 40)):
        y += 1
    return y


def rounded(surface, radius):
    """Copy with rounded corners; the mask is drawn at twice the size for smooth edges."""
    w, h = surface.get_size()
    mask = pygame.Surface((w * 2, h * 2), pygame.SRCALPHA)
    pygame.draw.rect(mask, (255, 255, 255, 255), mask.get_rect(), border_radius=radius * 2)
    mask = pygame.transform.smoothscale(mask, (w, h))
    out = pygame.Surface((w, h), pygame.SRCALPHA)
    out.blit(surface, (0, 0))
    out.blit(mask, (0, 0), special_flags=pygame.BLEND_RGBA_MULT)
    return out


def wrap(font, text, size, room):
    lines, line = [], ""
    for word in text.split():
        trial = (line + " " + word).strip()
        if font.get_rect(trial, size=size).width > room and line:
            lines.append(line)
            line = word
        else:
            line = trial
    return lines + [line]


def compose(shot, size, headline, path):
    """Store screenshot: caramel background, headline on top, the app's screen below with rounded corners."""
    W, H = size
    canvas = pygame.Surface(size)
    canvas.fill(CARAMEL)
    font = pygame.freetype.Font(str(FONT))
    margin = round(min(W, H) * 0.07)
    title = round(min(W, H) * 0.06)
    lines = wrap(font, headline, title, W - 2 * margin)
    line_h = round(title * 1.25)
    for i, text in enumerate(lines):
        r = font.get_rect(text, size=title)
        font.render_to(canvas, ((W - r.width) // 2, margin + i * line_h), text, CREAM, size=title)
    top = margin + len(lines) * line_h + margin // 2
    room_w, room_h = W - 2 * margin, H - top - margin
    k = min(room_w / shot.get_width(), room_h / shot.get_height())
    scaled = pygame.transform.smoothscale(shot, (round(shot.get_width() * k), round(shot.get_height() * k)))
    canvas.blit(rounded(scaled, round(min(scaled.get_size()) * 0.045)), ((W - scaled.get_width()) // 2, top))
    save(canvas, path)


def have(*names):
    return all((RAW / n).exists() for n in names)


LANDSCAPE, PORTRAIT, TABLET = (1920, 1080), (1080, 1920), (1920, 1200)

# --- Phone (Realme 7 5G, 2400x1080) ---
if have("phone-controller.png", "phone-editor.png", "phone-controller-portrait.png", "phone-home.png"):
    for src, dst, headline in (
        ("phone-controller.png", "01-controller.png", "Use your phone as a PC controller"),
        ("phone-editor.png", "02-editor.png", "Move and resize every control"),
    ):
        s = load(src)
        # The camera cutout leaves a black strip on the left.
        fill_black(s, (0, 0, black_columns(s, 0, 300), s.get_height()), PAD_BG)
        compose(s, LANDSCAPE, headline, OUT / "phone" / dst)
    s = load("phone-controller-portrait.png")
    fill_black(s, (0, 0, s.get_width(), black_rows(s)), PAD_BG)
    compose(s, PORTRAIT, "Play in landscape or portrait", OUT / "phone" / "03-controller-portrait.png")
    # Main screen without the status bar (110 px) and the navigation bar (from y = 2268).
    s = load("phone-home.png")
    s = s.subsurface(pygame.Rect(0, 110, s.get_width(), 2268 - 110)).copy()
    compose(s, PORTRAIT, "No PC software needed", OUT / "phone" / "04-home.png")

# --- Tablet (Redmi Pad SE, 1920x1200) ---
if have("tablet-controller.png", "tablet-editor.png", "tablet-home.png"):
    for src, dst, headline in (
        ("tablet-controller.png", "01-controller.png", "Use your tablet as a PC controller"),
        ("tablet-editor.png", "02-editor.png", "Move and resize every control"),
    ):
        s = load(src)
        # The HyperOS multi-window handle (three dots at the top) isn't part of the app.
        fill_black(s, (900, 0, 120, 40), PAD_BG)
        compose(s, TABLET, headline, OUT / "tablet" / dst)
    s = load("tablet-home.png")
    # Without the status bar (48 px) and the navigation strip at the bottom.
    s = s.subsurface(pygame.Rect(0, 48, s.get_width(), 1115 - 48)).copy()
    compose(s, TABLET, "No PC software needed", OUT / "tablet" / "03-home.png")


# --- Icon: the app's adaptive icon (108 canvas) with its visible 72 area scaled to 512 px ---
def draw_mark(surface, ox, oy, k, bg):
    """Draws the icon's phone-controller: 108-unit canvas, origin (ox, oy), k pixels per unit."""
    def P(x, y):
        return ox + x * k, oy + y * k

    def capsule(x1, x2, y1, y2):
        r = (y2 - y1) / 2
        rect = pygame.Rect(*P(x1, y1), (x2 - x1) * k, (y2 - y1) * k)
        pygame.draw.rect(surface, TRIGGER, rect, border_radius=int(r * k))

    capsule(34, 46, 35, 40)
    capsule(62, 74, 35, 40)
    body = pygame.Rect(*P(26, 42), 56 * k, 32 * k)
    pygame.draw.rect(surface, CREAM, body, border_radius=int(10 * k))
    for cx, cy, r in ((40, 58, 7), (68, 52.5, 2.8), (73.5, 58, 2.8), (68, 63.5, 2.8), (62.5, 58, 2.8)):
        pygame.draw.circle(surface, bg, P(cx, cy), r * k)


SS = 4  # supersampling for smooth edges
big = pygame.Surface((512 * SS, 512 * SS))
big.fill(CARAMEL)
k = 512 * SS / 72
draw_mark(big, -18 * k, -18 * k, k, CARAMEL)
save(pygame.transform.smoothscale(big, (512, 512)), OUT / "icon-512.png")


# --- 1024x500 feature graphic ---
def header(title_line, tagline, path):
    W, H = 1024, 500
    big = pygame.Surface((W * SS, H * SS))
    big.fill(CARAMEL)
    k = 300 * SS / 56  # the icon body (56 units wide) is 300 px
    mark_cx, mark_cy = 250 * SS, H * SS / 2
    draw_mark(big, mark_cx - 54 * k, mark_cy - 54.5 * k, k, CARAMEL)
    font = pygame.freetype.Font(str(FONT))
    x = 470 * SS
    room = W * SS - x - 48 * SS
    tag_size = 40 * SS
    # The tagline wraps to fit right of the icon.
    lines, line = [], ""
    for word in tagline.split():
        trial = (line + " " + word).strip()
        if font.get_rect(trial, size=tag_size).width > room and line:
            lines.append(line)
            line = word
        else:
            line = trial
    lines.append(line)
    title_rect = font.get_rect(title_line, size=104 * SS)
    line_h = int(tag_size * 1.3)
    block = title_rect.height + 40 * SS + line_h * len(lines)
    top = (H * SS - block) / 2
    font.render_to(big, (x, top), title_line, CREAM, size=104 * SS)
    for i, text in enumerate(lines):
        font.render_to(big, (x, top + title_rect.height + 40 * SS + i * line_h), text, TRIGGER, size=tag_size)
    widest = max(font.get_rect(t, size=tag_size).width for t in lines + [title_line])
    assert widest <= room or font.get_rect(title_line, size=104 * SS).width <= room, "text does not fit"
    save(pygame.transform.smoothscale(big, (W, H)), path)


header("DuckPad", "Your phone as a Bluetooth PC controller", OUT / "feature-graphic-en-1024x500.png")
header("DuckPad", "Tu móvil, mando Bluetooth para tu PC", OUT / "feature-graphic-es-1024x500.png")
