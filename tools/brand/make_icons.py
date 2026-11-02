"""
Знак «Зерно»: минималистичная камера из случайно рассыпанных точек разного размера — как зерно плёнки.
Запуск (нужны numpy и Pillow):  python3 tools/brand/make_icons.py

Пишет:
  res/drawable/zerno_mark.xml            — знак для интерфейса (вектор, градиент)
  res/drawable/ic_launcher_foreground.xml — передний слой иконки (вектор, градиент)
  res/drawable/ic_launcher_monochrome.xml — монохромный слой для тематических иконок
  фон иконки — сплошной @color/zerno_ink (чёрный): зерно здесь — сами точки
  docs/brand/icon-512.png                 — превью для магазина
"""
import math
import os
import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res")
DOCS = os.path.join(ROOT, "docs", "brand")

# Фирменный градиент: янтарь → киноварь → маджента.
GRADIENT = ["#FFC454", "#FF5C30", "#EC2A70"]
BG = (0, 0, 0)

# Геометрия в координатах слоя 108×108 dp (видимая зона ~18..90, безопасный круг ⌀66).
CX, CY = 54.0, 57.0
BODY = (CX, CY, 50.0, 34.5, 7.8)          # центр x, y, ширина, высота, радиус скругления
HUMP = (CX - 9.5, CY - 21.2, 19.0, 10.4, 3.0)
LENS_R = 11.2


def rrect_sdf(x, y, c):
    """Знаковое расстояние до скруглённого прямоугольника (отрицательное внутри)."""
    cx, cy, w, h, r = c
    qx, qy = abs(x - cx) - w / 2 + r, abs(y - cy) - h / 2 + r
    return math.hypot(max(qx, 0), max(qy, 0)) + min(max(qx, qy), 0) - r


def dots(seed=5, sigma=1.35, candidates=11000, r_min=0.34, r_max=1.95, dust=0.016, gap=0.22):
    """
    Камера из случайно рассыпанных точек разного размера, как зерно плёнки: точки густеют и крупнеют
    у контура (корпус с «горбом», кольцо объектива, зрачок) и редкой пылью расходятся вокруг.
    Список (x, y, радиус) в координатах 108×108.
    """
    rng = np.random.default_rng(seed)
    lx, ly = CX, CY + 0.6
    pts = []
    for x, y in rng.uniform(16, 92, (candidates, 2)):
        outline = abs(min(rrect_sdf(x, y, BODY), rrect_sdf(x, y, HUMP)))
        ring = abs(math.hypot(x - lx, y - ly) - LENS_R)
        eye = max(0.0, math.hypot(x - lx, y - ly) - 2.6)
        d = min(outline, ring, eye)
        if rng.random() > math.exp(-(d / sigma) ** 2) * 0.9 + dust:
            continue
        core = math.exp(-(d / (sigma * 0.8)) ** 2)
        r = r_min + (r_max - r_min) * core * rng.uniform(0.25, 1.0) ** 1.4
        if d > 2.5 * sigma:
            r = rng.uniform(0.22, 0.45)  # пыль
        if all(math.hypot(x - px, y - py) > r + pr + gap for px, py, pr in pts):
            pts.append((x, y, r))
    return pts


def circle_path(pts):
    return " ".join(
        f"M{x - r:.2f},{y:.2f} a{r:.2f},{r:.2f} 0 1,0 {2 * r:.2f},0 a{r:.2f},{r:.2f} 0 1,0 {-2 * r:.2f},0 Z"
        for x, y, r in pts
    )


def bbox(pts, pad=0.6):
    xs = [x - r for x, _, r in pts] + [x + r for x, _, r in pts]
    ys = [y - r for _, y, r in pts] + [y + r for _, y, r in pts]
    return min(xs) - pad, min(ys) - pad, max(xs) + pad, max(ys) + pad


def vector(pts, viewport, gradient=True, size_dp=None):
    x0, y0, x1, y1 = viewport
    vw, vh = x1 - x0, y1 - y0
    shifted = [(x - x0, y - y0, r) for x, y, r in pts]
    w, h = (size_dp if size_dp else (vw, vh))
    head = f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Сгенерировано tools/brand/make_icons.py — не править руками.
     Длинный путь (сотни точек) осознанно: вектор рисуется один раз и кэшируется. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    xmlns:tools="http://schemas.android.com/tools"
    tools:ignore="VectorPath"
    android:width="{w:.1f}dp"
    android:height="{h:.1f}dp"
    android:viewportWidth="{vw:.2f}"
    android:viewportHeight="{vh:.2f}">
"""
    d = circle_path(shifted)
    if not gradient:
        return head + f'    <path android:fillColor="#FF000000" android:pathData="{d}" />\n</vector>\n'
    bx0, by0, bx1, by1 = bbox(shifted, 0)
    items = "\n".join(
        f'                <item android:offset="{i / (len(GRADIENT) - 1):.2f}" android:color="{c}" />'
        for i, c in enumerate(GRADIENT)
    )
    return head + f"""    <path android:pathData="{d}">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="{bx0:.2f}" android:startY="{by0:.2f}"
                android:endX="{bx1:.2f}" android:endY="{by1:.2f}">
{items}
            </gradient>
        </aapt:attr>
    </path>
</vector>
"""


def raster_dots(pts, size, canvas=BG):
    """Растровая отрисовка точек с градиентом (для превью и PNG)."""
    ss = 4
    img = Image.new("RGB", (size * ss, size * ss), canvas)
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = bbox(pts, 0)
    cols = [tuple(int(c[i:i + 2], 16) for i in (1, 3, 5)) for c in GRADIENT]
    k = size * ss / 108
    for x, y, r in pts:
        t = ((x - x0) * (x1 - x0) + (y - y0) * (y1 - y0)) / ((x1 - x0) ** 2 + (y1 - y0) ** 2)
        t = min(max(t, 0), 1)
        a, b, u = (cols[0], cols[1], t / 0.5) if t < 0.5 else (cols[1], cols[2], (t - 0.5) / 0.5)
        c = tuple(int(a[i] + (b[i] - a[i]) * u) for i in range(3))
        d.ellipse([(x - r) * k, (y - r) * k, (x + r) * k, (y + r) * k], fill=c)
    return img.resize((size, size), Image.LANCZOS)


def main():
    pts = dots()
    drawable = os.path.join(RES, "drawable")
    os.makedirs(drawable, exist_ok=True)
    full = (0, 0, 108, 108)
    with open(os.path.join(drawable, "ic_launcher_foreground.xml"), "w") as f:
        f.write(vector(pts, full, size_dp=(108, 108)))
    with open(os.path.join(drawable, "ic_launcher_monochrome.xml"), "w") as f:
        f.write(vector(pts, full, gradient=False, size_dp=(108, 108)))
    near = [(x, y, r) for x, y, r in pts if BODY[0] - BODY[2] / 2 - 3 < x < BODY[0] + BODY[2] / 2 + 3
            and HUMP[1] - HUMP[3] / 2 - 3 < y < BODY[1] + BODY[3] / 2 + 3]
    box = bbox(near)
    with open(os.path.join(drawable, "zerno_mark.xml"), "w") as f:
        f.write(vector(near, box, size_dp=(24 * (box[2] - box[0]) / (box[3] - box[1]), 24)))

    # Старые растровые слои иконки больше не нужны.
    for d in ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"):
        folder = os.path.join(RES, f"mipmap-{d}")
        for old in ("ic_launcher_background.webp", "ic_launcher_foreground.webp", "ic_launcher.webp", "ic_launcher_round.webp"):
            p = os.path.join(folder, old)
            if os.path.exists(p):
                os.remove(p)
        if os.path.isdir(folder) and not os.listdir(folder):
            os.rmdir(folder)
    for old in ("drawable-nodpi/zerno_mark.png",):
        p = os.path.join(RES, old)
        if os.path.exists(p):
            os.remove(p)
    nodpi = os.path.join(RES, "drawable-nodpi")
    if os.path.isdir(nodpi) and not os.listdir(nodpi):
        os.rmdir(nodpi)

    os.makedirs(DOCS, exist_ok=True)
    raster_dots(pts, 512).save(os.path.join(DOCS, "icon-512.png"))


if __name__ == "__main__":
    main()
