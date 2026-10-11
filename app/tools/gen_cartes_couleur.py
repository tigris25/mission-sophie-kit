"""Colour cards for paper printing + lamination (character cards and keepsake cards n°1-10).

Usage (from app/):  python tools/gen_cartes_couleur.py
Output: ../cartes-couleur/cartes.html (A4 sheets, recto then verso mirrored for long-edge duplex),
then converted to ../cartes-a-imprimer.pdf with Edge (headless).
Texts and symbols come from gen_cartes.py and SymbolPaths.kt; colours from game_content.json.
"""
import json, math, os, random, subprocess, sys

sys.path.insert(0, os.path.dirname(__file__))
from gen_cartes import CARDS, ROW, SYMS, MOTTO  # noqa: E402

ROOT = os.path.abspath('..')
OUT = os.path.join(ROOT, 'cartes-couleur')
content = json.load(open('android/src/main/assets/game_content.json', encoding='utf8'))
COLOR = {p['id']: p['color'] for p in content['powers']}

CINZEL = "Cinzel, 'Trajan Pro', 'Times New Roman', serif"
GARA = "'Cormorant Garamond', 'Adobe Garamond Pro', Garamond, 'Times New Roman', serif"
NIGHT, VIOLET, CREAM, GOLD, SOPHIE = '#0B1026', '#2A1650', '#F0E2B6', '#D9BD72', '#6FA8FF'


def lighten(hex_, k):
    h = hex_.lstrip('#')
    r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4))
    return '#%02X%02X%02X' % tuple(int(c + (255 - c) * k) for c in (r, g, b))


def star4(cx, cy, r, inner=0.28):
    pts = []
    for i in range(8):
        a = math.pi / 2 + i * math.pi / 4
        rr = r if i % 2 == 0 else r * inner
        pts.append(f'{cx + rr * math.cos(a):.2f},{cy - rr * math.sin(a):.2f}')
    return ' '.join(pts)


def sparkles(seed, n=14, avoid=None):
    rnd = random.Random(seed)
    out = []
    for _ in range(n):
        x, y = rnd.uniform(5, 80.6), rnd.uniform(5, 49)
        if avoid and avoid(x, y):
            continue
        r = rnd.uniform(0.35, 1.1)
        op = rnd.uniform(0.35, 0.9)
        out.append(f'<polygon points="{star4(x, y, r)}" fill="{CREAM}" opacity="{op:.2f}"/>')
    return '\n'.join(out)


def defs(uid, color=SOPHIE):
    return f'''<defs>
  <linearGradient id="bg{uid}" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="{NIGHT}"/><stop offset="0.55" stop-color="#16204A"/><stop offset="1" stop-color="{VIOLET}"/>
  </linearGradient>
  <radialGradient id="glow{uid}"><stop offset="0" stop-color="{color}" stop-opacity="0.75"/><stop offset="1" stop-color="{color}" stop-opacity="0"/></radialGradient>
  <radialGradient id="gem{uid}" cx="0.35" cy="0.3" r="0.8"><stop offset="0" stop-color="{lighten(color, 0.45)}"/><stop offset="1" stop-color="{color}"/></radialGradient>
</defs>'''


def frame(uid):
    return f'''<rect x="0" y="0" width="85.6" height="54" rx="3" fill="url(#bg{uid})"/>
<rect x="2" y="2" width="81.6" height="50" rx="1.8" fill="none" stroke="{GOLD}" stroke-width="0.45"/>
<rect x="3.1" y="3.1" width="79.4" height="47.8" rx="1" fill="none" stroke="{GOLD}" stroke-width="0.2" opacity="0.7"/>
''' + ''.join(f'<polygon points="{star4(x, y, 1.3)}" fill="{GOLD}"/>' for x, y in [(5, 5), (80.6, 5), (5, 49), (80.6, 49)])


def sym(id_, x, y, k, fill):
    return f'<path fill-rule="evenodd" fill="{fill}" transform="translate({x:.3f} {y:.3f}) scale({k:.4f})" d="{SYMS[id_]}"/>'


def svg(inner):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 85.6 54" width="85.6mm" height="54mm">{inner}</svg>'


def perso_recto(id_):
    talent, name, epithet, power, _ = CARDS[id_]
    c = COLOR[id_]
    u = 'r' + id_
    pitch = 69.2 / len(ROW)
    xs = [8.2 + pitch / 2 + i * pitch for i in range(len(ROW))]
    row = []
    for s, x in zip(ROW, xs):
        if s == id_:
            row.append(f'<circle cx="{x:.2f}" cy="46.2" r="{pitch / 2:.2f}" fill="{COLOR[s]}"/>' + sym(s, x, 46.2, 0.1, NIGHT))
        else:
            row.append(sym(s, x, 46.2, 0.1, CREAM).replace('<path ', '<path opacity="0.45" '))
    return svg(defs(u, c) + frame(u) + sparkles(hash(id_) & 0xffff, 30, avoid=lambda x, y: not (x > 38 and (y < 8.5 or (x > 76 and y < 39)))) + f'''
<circle cx="21" cy="23.5" r="17" fill="url(#glow{u})"/>
<circle cx="21" cy="23.5" r="13.2" fill="url(#gem{u})"/>
<circle cx="21" cy="23.5" r="13.2" fill="none" stroke="#FFFFFF" stroke-opacity="0.55" stroke-width="0.7"/>
<circle cx="21" cy="23.5" r="14.6" fill="none" stroke="{GOLD}" stroke-width="0.35"/>
{sym(id_, 21, 23.5, 0.6, NIGHT)}
<text x="40" y="11" font-family="{CINZEL}" font-size="2.3" letter-spacing="0.35" fill="{lighten(c, 0.25)}">{talent}</text>
<text x="40" y="18.2" font-family="{CINZEL}" font-weight="700" font-size="5.2" fill="{CREAM}">{name}</text>
<text x="40" y="22.6" font-family="{GARA}" font-style="italic" font-size="3.1" fill="{lighten(SOPHIE, 0.3)}">{epithet}</text>
<line x1="40" y1="25" x2="80" y2="25" stroke="{GOLD}" stroke-width="0.25"/>
<polygon points="{star4(40, 25, 1.1)}" fill="{GOLD}"/>
<text x="40" y="29" font-family="{CINZEL}" font-size="2" letter-spacing="0.35" fill="{GOLD}">POUVOIR</text>
<text x="40" y="33" font-family="{GARA}" font-weight="600" font-size="3.2" fill="{CREAM}">{power[0]}</text>
<text x="40" y="36.7" font-family="{GARA}" font-weight="600" font-size="3.2" fill="{CREAM}">{power[1]}</text>
<line x1="6" y1="40.8" x2="79.6" y2="40.8" stroke="{GOLD}" stroke-width="0.2" opacity="0.8"/>
''' + ''.join(row))


def perso_verso(id_):
    _, name, _, _, quote = CARDS[id_]
    c = COLOR[id_]
    u = 'v' + id_
    return svg(defs(u, c) + frame(u) + sparkles(hash(name) & 0xffff, 30, avoid=lambda x, y: not (x < 14.5 or x > 71 or y < 8)) + f'''
<circle cx="42.8" cy="12.5" r="7.5" fill="url(#glow{u})"/>
<circle cx="42.8" cy="12.5" r="5.2" fill="url(#gem{u})"/>
{sym(id_, 42.8, 12.5, 0.24, NIGHT)}
<line x1="16" y1="12.5" x2="34" y2="12.5" stroke="{GOLD}" stroke-width="0.25"/>
<line x1="51.6" y1="12.5" x2="69.6" y2="12.5" stroke="{GOLD}" stroke-width="0.25"/>
<text x="42.8" y="25.5" text-anchor="middle" font-family="{GARA}" font-style="italic" font-weight="600" font-size="4.2" fill="{CREAM}">{quote[0]}</text>
<text x="42.8" y="30.8" text-anchor="middle" font-family="{GARA}" font-style="italic" font-weight="600" font-size="4.2" fill="{CREAM}">{quote[1]}</text>
<text x="42.8" y="37.8" text-anchor="middle" font-family="{CINZEL}" font-size="2.8" letter-spacing="0.3" fill="{lighten(c, 0.25)}">— {name} —</text>
<line x1="6" y1="41" x2="79.6" y2="41" stroke="{GOLD}" stroke-width="0.2" opacity="0.8"/>
<polygon points="{star4(42.8, 41, 1.1)}" fill="{GOLD}"/>
<text x="42.8" y="46.8" text-anchor="middle" font-family="{CINZEL}" font-size="2.1" fill="{GOLD}">{MOTTO}</text>''')


NUMCOL = [COLOR[i] for i in ROW[::2]][:10]


def souvenir_recto(n):
    c = NUMCOL[(n - 1) % len(NUMCOL)]
    u = f's{n}'
    return svg(defs(u, c) + frame(u) + sparkles(n * 97, 30, avoid=lambda x, y: not (x > 38 and (y < 8.5 or (x > 74 and y < 44)))) + f'''
<circle cx="21" cy="27" r="17" fill="url(#glow{u})"/>
<circle cx="21" cy="27" r="13.2" fill="url(#gem{u})"/>
<circle cx="21" cy="27" r="13.2" fill="none" stroke="#FFFFFF" stroke-opacity="0.55" stroke-width="0.7"/>
<circle cx="21" cy="27" r="14.6" fill="none" stroke="{GOLD}" stroke-width="0.35"/>
<text x="21" y="{33.6 if n < 10 else 32.6}" text-anchor="middle" font-family="{CINZEL}" font-weight="700" font-size="{18 if n < 10 else 14}" fill="{NIGHT}">{n}</text>
<polygon points="{star4(21, 8.6, 2.4)}" fill="{GOLD}"/>
<text x="40" y="12.5" font-family="{CINZEL}" font-size="2.3" letter-spacing="0.35" fill="{lighten(SOPHIE, 0.3)}">MISSION SOPHIE</text>
<text x="40" y="19.5" font-family="{CINZEL}" font-weight="700" font-size="3.9" fill="{CREAM}">CARTE-SOUVENIR</text>
<line x1="40" y1="23.5" x2="80" y2="23.5" stroke="{GOLD}" stroke-width="0.25"/>
<polygon points="{star4(40, 23.5, 1.1)}" fill="{GOLD}"/>
<text x="40" y="30" font-family="{GARA}" font-style="italic" font-weight="600" font-size="3.3" fill="{CREAM}">Tu as réveillé un pouvoir</text>
<text x="40" y="34" font-family="{GARA}" font-style="italic" font-weight="600" font-size="3.3" fill="{CREAM}">de Sophie. Garde-moi bien :</text>
<text x="40" y="38" font-family="{GARA}" font-style="italic" font-weight="600" font-size="3.3" fill="{CREAM}">je te servirai à la fin !</text>
<text x="40" y="46.5" font-family="{CINZEL}" font-size="2.6" letter-spacing="0.3" fill="{c}">N° {n}</text>''')


def souvenir_verso(n):
    u = f'sv{n}'
    return svg(defs(u, SOPHIE) + frame(u) + sparkles(1234 + n, 30, avoid=lambda x, y: not (x < 14 or x > 71.6 or (y < 9 and not 30 < x < 56))) + f'''
<text x="42.8" y="15" text-anchor="middle" font-family="{CINZEL}" font-weight="700" font-size="4.3" letter-spacing="0.5" fill="{CREAM}">GARDIENNES</text>
<circle cx="42.8" cy="27.5" r="10" fill="url(#glow{u})"/>
<polygon points="{star4(42.8, 27.5, 7.5, 0.26)}" fill="{GOLD}"/>
<circle cx="42.8" cy="27.5" r="1.2" fill="#FFFFFF"/>
<line x1="14" y1="27.5" x2="31" y2="27.5" stroke="{GOLD}" stroke-width="0.25"/>
<line x1="54.6" y1="27.5" x2="71.6" y2="27.5" stroke="{GOLD}" stroke-width="0.25"/>
<text x="42.8" y="43.5" text-anchor="middle" font-family="{CINZEL}" font-size="2.1" fill="{GOLD}">{MOTTO}</text>''')


def sheet(cards, mirror=False):
    """A4 page, 2 columns x 5 rows. Verso pages swap the columns (long-edge duplex)."""
    cells = []
    for i in range(10):
        r, col = divmod(i, 2)
        if mirror:
            col = 1 - col
        x = 18.4 + col * (85.6 + 2)
        y = 11.5 + r * (54 + 2)
        body = cards[i] if i < len(cards) else ''
        cells.append(f'<div class="card" style="left:{x}mm;top:{y}mm">{body}</div>')
    return '<div class="page">' + ''.join(cells) + '</div>'


def main():
    os.makedirs(OUT, exist_ok=True)
    pages = []
    for chunk in (ROW[:10], ROW[10:]):
        pages.append(sheet([perso_recto(i) for i in chunk]))
        pages.append(sheet([perso_verso(i) for i in chunk], mirror=True))
    pages.append(sheet([souvenir_recto(n) for n in range(1, 11)]))
    pages.append(sheet([souvenir_verso(n) for n in range(1, 11)], mirror=True))
    html = f'''<!doctype html><html><head><meta charset="utf-8"><title>Cartes Mission Sophie</title>
<style>
@page {{ size: A4; margin: 0; }}
html, body {{ margin: 0; padding: 0; }}
.page {{ position: relative; width: 210mm; height: 297mm; page-break-after: always; }}
.card {{ position: absolute; width: 85.6mm; height: 54mm; outline: 0.1mm solid #bbb; }}
.card svg {{ display: block; width: 85.6mm; height: 54mm; }}
* {{ -webkit-print-color-adjust: exact; print-color-adjust: exact; }}
</style></head><body>{''.join(pages)}</body></html>'''
    path = os.path.join(OUT, 'cartes.html')
    open(path, 'w', encoding='utf8').write(html)
    edge = next((p for p in [r'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe',
                             r'C:\Program Files\Microsoft\Edge\Application\msedge.exe'] if os.path.exists(p)), None)
    pdf = os.path.join(ROOT, 'cartes-a-imprimer.pdf')
    if edge:
        subprocess.run([edge, '--headless', '--disable-gpu', '--no-pdf-header-footer', f'--print-to-pdf={pdf}',
                        'file:///' + path.replace('\\', '/')], capture_output=True, timeout=120)
    print('html:', path, '| pdf:', pdf if os.path.exists(pdf) else 'non généré')


if __name__ == '__main__':
    main()
