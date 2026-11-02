"""
Иконка и знак «Зерно»: зерно-семя с плёночным зерном на тёплом тёмном фоне.
Запуск (нужны numpy и Pillow):  python3 tools/brand/make_icons.py
Пишет слои адаптивной иконки в app/src/main/res/mipmap-*, знак в drawable-nodpi,
монохромный слой (вектор) и превью для магазина в docs/brand/.
"""
import math
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res")
DOCS = os.path.join(ROOT, "docs", "brand")

# Геометрия знака в долях стороны слоя 108 dp.
LENGTH, WIDTH, AXIS_DEG, P, TAPER = 0.45, 0.28, 62.0, 1.55, 0.18
TOP, BOTTOM = (255, 224, 168), (222, 104, 42)
BG_DARK, BG_LIGHT = (20, 14, 11), (52, 33, 22)


def film_grain(shape, seed, fine=0.7, coarse=1.8, mix=0.55, scale=1.0):
    rng = np.random.default_rng(seed)

    def layer(sigma):
        n = rng.standard_normal(shape)
        im = Image.fromarray((n * 30 + 128).clip(0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(sigma * scale))
        g = np.asarray(im).astype(float) - 128
        return g / (g.std() + 1e-9)

    g = layer(fine) * (1 - mix) + layer(coarse) * mix
    return g / g.std()


def grainify(rgb, amount, seed, scale, coarse_mix=0.55):
    g = film_grain(rgb.shape[:2], seed, mix=coarse_mix, scale=scale)
    lum = (rgb @ [0.3, 0.55, 0.15]) / 255
    w = 0.35 + 1.6 * lum * (1 - lum)  # как у плёнки: сильнее всего в полутонах
    out = rgb + g[..., None] * amount * w[..., None]
    c = film_grain(rgb.shape[:2], seed + 11, 0.9, 1.5, scale=scale)
    out[..., 2] += c * amount * 0.25 * w
    out[..., 0] -= c * amount * 0.12 * w
    return np.clip(out, 0, 255)


def lerp(a, b, t):
    a, b = np.array(a, float), np.array(b, float)
    return a + (b - a) * np.clip(t, 0, 1)[..., None]


def seed_field(size, ss=4):
    """Маска зерна с антиалиасингом и координаты u (вдоль оси, -1..1) и v (поперёк)."""
    n = size * ss
    yy, xx = np.mgrid[0:n, 0:n] / ss
    a = math.radians(AXIS_DEG)
    dx, dy = xx - size / 2, yy - size / 2
    u = dx * math.cos(a) + dy * math.sin(a)
    v = -dx * math.sin(a) + dy * math.cos(a)
    y = u / (LENGTH * size / 2)
    w = (WIDTH * size / 2) * (1 + TAPER * y)
    f = np.abs(v / w) ** 2 + np.abs(y) ** P
    mask = (f <= 1).astype(float).reshape(size, ss, size, ss).mean((1, 3))
    return mask, y[::ss, ::ss], (v / (WIDTH * size / 2))[::ss, ::ss]


def seed_body(size, u, v):
    body = lerp(TOP, BOTTOM, u * 0.55 + 0.5)
    light = np.clip(1 - 0.42 * np.clip(v, -1, 1), 0.5, 1.25)
    rim = np.clip(1 - (np.abs(v) ** 6) * 0.35, 0, 1)
    body = body * (light * rim)[..., None]
    cv = 0.18 * np.sin(u * 1.4)  # бороздка, слегка изогнутая
    ends = np.clip(1 - np.abs(u) ** 4, 0, 1)
    groove = np.exp(-((v - cv) / 0.075) ** 2) * ends
    highlight = np.exp(-((v - cv + 0.13) / 0.07) ** 2) * ends
    return body * (1 - 0.55 * groove)[..., None] + np.array([255, 236, 200]) * 0.22 * highlight[..., None]


def background(size):
    yy, xx = np.mgrid[0:size, 0:size] / size
    d = np.sqrt((xx - 0.42) ** 2 + (yy - 0.30) ** 2)
    rgb = lerp(BG_LIGHT, BG_DARK, d / 0.8)
    return grainify(rgb, 13 * size / 432, seed=5, scale=size / 432, coarse_mix=0.25)


def foreground(size, glow=0.28):
    mask, u, v = seed_field(size)
    body = grainify(seed_body(size, u, v), 20 * size / 432, seed=3, scale=size / 432)
    halo = np.asarray(Image.fromarray((mask * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(size * 0.06))).astype(float) / 255
    halo_a = np.clip(halo * glow * (1 - mask), 0, 1)
    alpha = np.clip(mask + halo_a, 0, 1)
    rgb = (body * mask[..., None] + np.array([255, 120, 40]) * halo_a[..., None]) / np.maximum(alpha, 1e-6)[..., None]
    return np.dstack([np.clip(rgb, 0, 255), alpha * 255]).astype(np.uint8)


def monochrome_vector():
    """Силуэт зерна с бороздкой-прорезью (evenOdd) для тематических иконок Android 13+."""
    a = math.radians(AXIS_DEG)

    def to_xy(uu, vv):
        u, v = uu * LENGTH * 54, vv
        return 54 + u * math.cos(a) - v * math.sin(a), 54 + u * math.sin(a) + v * math.cos(a)

    pts = []
    for i in range(96):
        t = 2 * math.pi * i / 96
        y = math.copysign(abs(math.cos(t)) ** (2 / P), math.cos(t))
        w = WIDTH * 54 * (1 + TAPER * y)
        pts.append(to_xy(y, w * math.sin(t)))
    outline = "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in pts) + " Z"
    groove = []
    for side in (1, -1):
        rng = range(-30, 31) if side == 1 else range(30, -31, -1)
        for i in rng:
            y = i / 40
            cv = 0.18 * math.sin(y * 1.4) * WIDTH * 54
            half = 1.1 * (1 - abs(y) ** 4)
            groove.append(to_xy(y, cv + side * half))
    groove_path = "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in groove) + " Z"
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Сгенерировано tools/brand/make_icons.py — не править руками. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FF000000"
        android:fillType="evenOdd"
        android:pathData="{outline} {groove_path}" />
</vector>
"""


def save_webp(arr, path, lossless=True):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if lossless:
        Image.fromarray(arr).save(path, "WEBP", lossless=True, quality=100)
    else:  # фон — сплошное зерно, без потерь он весит в разы больше
        Image.fromarray(arr).save(path, "WEBP", quality=88, method=6)


def main():
    densities = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
    big_bg = background(432).astype(np.uint8)
    big_fg = foreground(432)
    for d, px in densities.items():
        bg = np.asarray(Image.fromarray(big_bg).resize((px, px), Image.LANCZOS))
        fg = np.asarray(Image.fromarray(big_fg, "RGBA").resize((px, px), Image.LANCZOS))
        folder = os.path.join(RES, f"mipmap-{d}")
        for old in ("ic_launcher.webp", "ic_launcher_round.webp"):
            p = os.path.join(folder, old)
            if os.path.exists(p):
                os.remove(p)
        save_webp(bg, os.path.join(folder, "ic_launcher_background.webp"), lossless=False)
        save_webp(fg, os.path.join(folder, "ic_launcher_foreground.webp"))
    with open(os.path.join(RES, "drawable", "ic_launcher_monochrome.xml"), "w") as f:
        f.write(monochrome_vector())

    # Знак для интерфейса: зерно без фона и с мягким свечением.
    mark = foreground(384, glow=0.0)
    m = np.asarray(Image.fromarray(mark, "RGBA").crop((96, 96, 288, 288)))
    os.makedirs(os.path.join(RES, "drawable-nodpi"), exist_ok=True)
    Image.fromarray(m, "RGBA").save(os.path.join(RES, "drawable-nodpi", "zerno_mark.png"), optimize=True)

    # Превью для магазина/документации: 512×512 со скруглением, как в Play.
    os.makedirs(DOCS, exist_ok=True)
    store = Image.fromarray(background(512).astype(np.uint8)).convert("RGBA")
    fg = Image.fromarray(foreground(512), "RGBA")
    store.alpha_composite(fg)
    store.convert("RGB").save(os.path.join(DOCS, "icon-512.png"))


if __name__ == "__main__":
    main()
