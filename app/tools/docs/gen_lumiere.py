"""Draws the dot template of the word LUMIÈRE (Flasheuse trial) used by prep.js -> lumiere.png."""
import os
from PIL import Image, ImageDraw
D = {'L': ['10000', '10000', '10000', '10000', '10000', '10000', '11111'], 'U': ['10001'] * 6 + ['01110'],
     'M': ['10001', '11011', '10101', '10101', '10001', '10001', '10001'], 'I': ['01110'] + ['00100'] * 5 + ['01110'],
     'E': ['11111', '10000', '10000', '11110', '10000', '10000', '11111'], 'R': ['11110', '10001', '10001', '11110', '10100', '10010', '10001']}
step, r, gap = 60, 11, 2
W, H = (4 * 5 + 3 * gap) * step + step, 20 * step + step
im = Image.new('RGB', (W, H), 'white'); d = ImageDraw.Draw(im)
for li, word in enumerate([list('LUMI'), ['È', 'R', 'E']]):
    y0, x = step * (3 + li * 10), step
    for ch in word:
        for ry, row in enumerate(D['E' if ch == 'È' else ch]):
            for cx, v in enumerate(row):
                if v == '1':
                    d.ellipse((x + cx * step - r, y0 + ry * step - r, x + cx * step + r, y0 + ry * step + r), fill='black')
        if ch == 'È':
            for cx, cy in [(1, -2), (2, -1.4)]:
                d.ellipse((x + cx * step - r, y0 + cy * step - r, x + cx * step + r, y0 + cy * step + r), fill='black')
        x += (5 + gap) * step
im.save(os.path.join(os.path.dirname(__file__), 'lumiere.png'))
