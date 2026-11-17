"""Рисует иконку Pickture «Афиша» для ПК: pickture.png (1024), ресурс 256 px и .ico.

python icon.py OUT_DIR_ICONS OUT_RESOURCES
Геометрия совпадает с app/src/main/res/drawable/ic_launcher_foreground.xml (координаты 108x108).
"""
import sys
from PIL import Image, ImageDraw

icons_dir, res_dir = sys.argv[1], sys.argv[2]
S = 4096                      # рисуем с запасом и уменьшаем — так края гладкие
K = S / 1024 * 14.05          # пикселей на единицу векторной сетки 108x108
GOLD = (255, 200, 87, 255)


def P(x, y):
    return ((x - 54) * K + S / 2, (y - 54) * K + S / 2)


def bez(p0, p1, p2, p3, n=48):
    out = []
    for i in range(n + 1):
        t = i / n
        a = (1 - t) ** 3; b = 3 * (1 - t) ** 2 * t; c = 3 * (1 - t) * t ** 2; d = t ** 3
        out.append((a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0], a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1]))
    return out


# Фон: скруглённый квадрат с вертикальным градиентом индиго
bg = Image.new('RGBA', (S, S))
top, bottom = (0x68, 0x75, 0xFF), (0x4A, 0x52, 0xDD)
grad = ImageDraw.Draw(bg)
for y in range(S):
    t = y / (S - 1)
    grad.line([(0, y), (S, y)], fill=tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,))
mask = Image.new('L', (S, S), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.215), fill=255)
icon = Image.new('RGBA', (S, S), (0, 0, 0, 0))
icon.paste(bg, (0, 0), mask)

# Золотой экран-билет: прямоугольник минус вырезы по бокам минус сердце
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
# Подставка
sx0, sy = P(45, 74); sx1, _ = P(63, 74)
w = 4.5 * K
g.rounded_rectangle([sx0 - w / 2, sy - w / 2, sx1 + w / 2, sy + w / 2], radius=w / 2, fill=255)

icon.paste(Image.new('RGBA', (S, S), GOLD), (0, 0), gold)

big = icon.resize((1024, 1024), Image.LANCZOS)
big.save(f'{icons_dir}/pickture.png')
big.resize((256, 256), Image.LANCZOS).save(f'{res_dir}/pickture.png')
big.save(f'{icons_dir}/pickture.ico', sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
print('ok')
