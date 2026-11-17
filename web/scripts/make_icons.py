"""Рисует иконки веб-версии Pickture: apple-touch-icon (180), 192, 512, maskable 512, favicon 32 и SVG.

python scripts/make_icons.py public/icons
Геометрия совпадает с app/src/main/res/drawable/ic_launcher_*.xml (координаты 108x108):
золотой экран-билет с вырезами по бокам, сердце вырезано насквозь, золотая подставка, индиго-градиент.
"""
import os
import sys
from PIL import Image, ImageDraw

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
GOLD = (255, 200, 87, 255)
TOP, BOTTOM = (0x68, 0x75, 0xFF), (0x4A, 0x52, 0xDD)


def bez(p0, p1, p2, p3, n=48):
    pts = []
    for i in range(n + 1):
        t = i / n
        a = (1 - t) ** 3; b = 3 * (1 - t) ** 2 * t; c = 3 * (1 - t) * t ** 2; d = t ** 3
        pts.append((a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0], a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1]))
    return pts


def render(size, visible, rounded):
    """visible — сколько единиц сетки 108x108 помещается в сторону иконки."""
    S = 2048
    K = S / visible

    def P(x, y):
        return ((x - 54) * K + S / 2, (y - 54) * K + S / 2)

    # Градиент по диагонали, как в ic_launcher_background.xml (от (20,8) к (88,100))
    # Градиент считаем на маленькой картинке и растягиваем — он линейный, качество не теряется
    G = 256
    small = Image.new('RGBA', (G, G))
    px = small.load()
    sx, sy = P(20, 8); ex, ey = P(88, 100)
    dx, dy = ex - sx, ey - sy
    ln = dx * dx + dy * dy
    for y in range(G):
        for x in range(G):
            X, Y = (x + 0.5) * S / G, (y + 0.5) * S / G
            t = max(0.0, min(1.0, ((X - sx) * dx + (Y - sy) * dy) / ln))
            px[x, y] = tuple(round(TOP[i] + (BOTTOM[i] - TOP[i]) * t) for i in range(3)) + (255,)
    bg = small.resize((S, S), Image.BICUBIC)

    gold = Image.new('L', (S, S), 0)
    g = ImageDraw.Draw(gold)
    x0, y0 = P(31, 34); x1, y1 = P(77, 66)
    g.rounded_rectangle([x0, y0, x1, y1], radius=6 * K, fill=255)
    for cx in (31, 77):
        cx_, cy_ = P(cx, 50)
        g.ellipse([cx_ - 4 * K, cy_ - 4 * K, cx_ + 4 * K, cy_ + 4 * K], fill=0)
    heart = []
    for seg in [((54, 58.9), (44.4, 51.7), (43.2, 45.7), (48, 43.3)),
                ((48, 43.3), (51, 42.1), (53.4, 43.9), (54, 46.3)),
                ((54, 46.3), (54.6, 43.9), (57, 42.1), (60, 43.3)),
                ((60, 43.3), (64.8, 45.7), (63.6, 51.7), (54, 58.9))]:
        heart += bez(*[P(*p) for p in seg])
    g.polygon(heart, fill=0)
    sx0, sy0 = P(45, 74); sx1, _ = P(63, 74)
    w = 4.5 * K
    g.rounded_rectangle([sx0 - w / 2, sy0 - w / 2, sx1 + w / 2, sy0 + w / 2], radius=w / 2, fill=255)
    bg.paste(Image.new('RGBA', (S, S), GOLD), (0, 0), gold)

    if rounded:
        mask = Image.new('L', (S, S), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.215), fill=255)
        icon = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        icon.paste(bg, (0, 0), mask)
    else:
        icon = bg
    return icon.resize((size, size), Image.LANCZOS)


# iOS сам скругляет углы и не любит прозрачность — квадрат без альфы
render(180, 76, False).convert('RGB').save(f'{out}/apple-touch-icon.png')
render(192, 76, True).save(f'{out}/icon-192.png')
render(512, 76, True).save(f'{out}/icon-512.png')
# maskable: важное должно поместиться в круг 80% — берём сетку крупнее
render(512, 92, False).convert('RGB').save(f'{out}/maskable-512.png')
render(32, 72, True).save(f'{out}/favicon-32.png')

svg = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="16 16 76 76">
<defs><linearGradient id="g" x1="20" y1="8" x2="88" y2="100" gradientUnits="userSpaceOnUse">
<stop offset="0" stop-color="#6875FF"/><stop offset="1" stop-color="#4A52DD"/></linearGradient></defs>
<rect x="16" y="16" width="76" height="76" rx="16" fill="url(#g)"/>
<path fill="#FFC857" fill-rule="evenodd" d="M37,34H71A6,6 0,0 1,77 40V46A4,4 0,0 0,77 54V60A6,6 0,0 1,71 66H37A6,6 0,0 1,31 60V54A4,4 0,0 0,31 46V40A6,6 0,0 1,37 34Z M54,58.9C44.4,51.7 43.2,45.7 48,43.3C51,42.1 53.4,43.9 54,46.3C54.6,43.9 57,42.1 60,43.3C64.8,45.7 63.6,51.7 54,58.9Z"/>
<path d="M45,74H63" stroke="#FFC857" stroke-width="4.5" stroke-linecap="round"/>
</svg>
'''
open(f'{out}/icon.svg', 'w', encoding='utf-8').write(svg)
print('ok')
