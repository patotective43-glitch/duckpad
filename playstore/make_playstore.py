"""Genera la carpeta playstore de DuckPad: capturas dentro de los límites de Google Play, icono de 512 px y cabeceras."""
import os
from pathlib import Path

os.environ["PYGAME_HIDE_SUPPORT_PROMPT"] = "1"
import pygame
import pygame.freetype

# Uso: make_playstore.py <carpeta con las capturas en bruto> <RobotoStatic-Regular.ttf>
# Capturas con `adb exec-out screencap -p`; la fuente sale de /system/fonts de cualquier Android.
import sys

RAW = Path(sys.argv[1])
FONT = Path(sys.argv[2])
OUT = Path(__file__).parent

CARAMEL = (0x8F, 0x4C, 0x14)
CREAM = (0xFF, 0xF1, 0xDA)
TRIGGER = (0xF6, 0xC2, 0x7A)
PAD_BG = (0x10, 0x12, 0x14)  # fondo del mando
PAGE_BG = (0xFA, 0xFA, 0xFA)  # fondo de la pantalla principal en tema claro

pygame.init()
pygame.freetype.init()


def save(surface, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    # Google Play pide PNG de 24 bits, sin transparencia.
    pygame.image.save(surface.convert(24) if surface.get_bitsize() != 24 else surface, str(path))
    w, h = surface.get_size()
    print(f"{path.relative_to(OUT)}  {w}x{h}  {max(w, h) / min(w, h):.2f}:1")


def load(name):
    return pygame.image.load(str(RAW / name))


def fill_black(surface, rect, color):
    """Pinta del color de fondo las franjas negras del recorte de la cámara o de la barra de estado."""
    surface.fill(color, pygame.Rect(rect))


def pad_to_ratio(surface, color, max_ratio=2.0):
    w, h = surface.get_size()
    if w >= h and w / h > max_ratio:
        nh = int(w / max_ratio + 0.999)
        out = pygame.Surface((w, nh))
        out.fill(color)
        out.blit(surface, (0, (nh - h) // 2))
        return out
    if h > w and h / w > max_ratio:
        nw = int(h / max_ratio + 0.999)
        out = pygame.Surface((nw, h))
        out.fill(color)
        out.blit(surface, ((nw - w) // 2, 0))
        return out
    return surface


def black_columns(surface, x_from, x_to):
    """Ancho de la franja negra pura a la izquierda (recorte de la cámara en horizontal)."""
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


# --- Capturas del móvil (Realme 7 5G, 2400x1080: más de 2:1, hay que rellenar) ---
for src, dst in (("movil-mando.png", "01-mando.png"), ("movil-editor.png", "03-editor.png")):
    s = load(src)
    strip = black_columns(s, 0, 300)
    fill_black(s, (0, 0, strip, s.get_height()), PAD_BG)
    save(pad_to_ratio(s, PAD_BG), OUT / "movil" / dst)

s = load("movil-mando-vertical.png")
fill_black(s, (0, 0, s.get_width(), black_rows(s)), PAD_BG)
save(pad_to_ratio(s, PAD_BG), OUT / "movil" / "02-mando-vertical.png")

# Pantalla principal: fuera la barra de estado (110 px) y la de navegación (desde y = 2268).
s = load("movil-inicio.png")
save(s.subsurface(pygame.Rect(0, 110, s.get_width(), 2268 - 110)).copy(), OUT / "movil" / "04-inicio.png")

# --- Capturas de la tablet (Redmi Pad SE, 1920x1200: ya caben) ---
for src, dst in (("tablet-mando.png", "01-mando.png"), ("tablet-editor.png", "02-editor.png")):
    s = load(src)
    # El asa de multiventana de HyperOS (los tres puntos de arriba) no es de la app.
    fill_black(s, (900, 0, 120, 40), PAD_BG)
    save(s, OUT / "tablet" / dst)
s = load("tablet-inicio.png")
# Fuera la barra de estado (48 px) y la franja translúcida de navegación de abajo.
save(s.subsurface(pygame.Rect(0, 48, s.get_width(), 1115 - 48)).copy(), OUT / "tablet" / "03-inicio.png")


# --- Icono: el adaptativo de la app (lienzo de 108) con su zona visible de 72 llevada a 512 px ---
def draw_mark(surface, ox, oy, k, bg):
    """Dibuja el móvil-mando del icono: lienzo de 108 unidades, origen (ox, oy), k píxeles por unidad."""
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


SS = 4  # supermuestreo para suavizar bordes
big = pygame.Surface((512 * SS, 512 * SS))
big.fill(CARAMEL)
k = 512 * SS / 72
draw_mark(big, -18 * k, -18 * k, k, CARAMEL)
save(pygame.transform.smoothscale(big, (512, 512)), OUT / "icono-512.png")


# --- Cabecera de 1024x500 ---
def header(title_line, tagline, path):
    W, H = 1024, 500
    big = pygame.Surface((W * SS, H * SS))
    big.fill(CARAMEL)
    k = 300 * SS / 56  # el cuerpo del icono (56 unidades de ancho) mide 300 px
    mark_cx, mark_cy = 250 * SS, H * SS / 2
    draw_mark(big, mark_cx - 54 * k, mark_cy - 54.5 * k, k, CARAMEL)
    font = pygame.freetype.Font(str(FONT))
    x = 470 * SS
    room = W * SS - x - 48 * SS
    tag_size = 40 * SS
    # La frase se parte en líneas que quepan a la derecha del icono.
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
    assert widest <= room or font.get_rect(title_line, size=104 * SS).width <= room, "el texto no cabe"
    save(pygame.transform.smoothscale(big, (W, H)), path)


header("DuckPad", "Tu móvil, mando Bluetooth para tu PC", OUT / "cabecera-es-1024x500.png")
header("DuckPad", "Your phone as a Bluetooth PC controller", OUT / "cabecera-en-1024x500.png")
