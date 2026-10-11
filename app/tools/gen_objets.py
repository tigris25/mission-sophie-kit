"""Generates the laser files that go with the game (same layers as the character cards).

Usage (from app/):  python tools/gen_objets.py
- ../cartes-souvenirs-svg/carte-souvenir-01..10-recto.svg + one common verso (85.6 x 54 mm)
- ../roue-elfique-svg/roue-grand-disque.svg and roue-petit-disque.svg (Polyglotte's cipher wheel)
"""
import math, os

CINZEL = "Cinzel, 'Trajan Pro', 'Times New Roman', serif"
GARA = "'Cormorant Garamond', Garamond, 'Times New Roman', serif"
MOTTO = "NOUS JURONS DE PROTÉGER LA LUMIÈRE"


def head(w, h, title, card=True):
    L = ['<?xml version="1.0" encoding="UTF-8"?>',
         f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:inkscape="http://www.inkscape.org/namespaces/inkscape" width="{w}mm" height="{h}mm" viewBox="0 0 {w} {h}">',
         f'  <title>{title}</title>']
    return L


def layer(id_, label, attrs, items):
    return [f'  <g id="{id_}" inkscape:groupmode="layer" inkscape:label="{label}" {attrs}>'] + ['    ' + i for i in items] + ['  </g>']


CUT = 'fill="none" stroke="#ff0000" stroke-width="0.1"'
LINE = 'fill="none" stroke="#000000" stroke-width="0.3" stroke-linecap="round" stroke-linejoin="round"'
FILL = 'fill="#000000" stroke="none"'
CORNERS = ['<polygon points="5.6,4.4 6.8,5.6 5.6,6.8 4.4,5.6"/>', '<polygon points="5.6,47.2 6.8,48.4 5.6,49.6 4.4,48.4"/>',
           '<polygon points="80,4.4 81.2,5.6 80,6.8 78.8,5.6"/>', '<polygon points="80,47.2 81.2,48.4 80,49.6 78.8,48.4"/>']
FRAME = ['<rect x="2" y="2" width="81.6" height="50" rx="1.6"/>', '<rect x="3.2" y="3.2" width="79.2" height="47.6" rx="0.8"/>']


def star(cx, cy, r):
    pts = []
    for i in range(8):
        a = math.pi / 2 + i * math.pi / 4
        rr = r if i % 2 == 0 else r * 0.28
        pts.append(f'{cx + rr * math.cos(a):.3f},{cy - rr * math.sin(a):.3f}')
    return f'<polygon points="{" ".join(pts)}"/>'


def souvenir_recto(n):
    L = head(85.6, 54.0, f'Carte-souvenir {n} - recto')
    L += layer('contour_carte', 'Contour carte (repere, ne pas graver)', CUT, ['<rect x="0" y="0" width="85.6" height="54.0" rx="3"/>'])
    L += layer('traits', 'Gravure traits', LINE, FRAME + ['<circle cx="21" cy="27" r="14"/>', '<circle cx="21" cy="27" r="12.9"/>',
                                                          '<line x1="40" y1="24.6" x2="80" y2="24.6" stroke-width="0.25"/>'])
    L += layer('pleins', 'Gravure pleine', FILL, CORNERS + ['<polygon points="40,23.7 40.9,24.6 40,25.5 39.1,24.6"/>',
                                                           star(21, 9.5, 2.2), star(9, 45, 1.6), star(33, 45, 1.6)])
    L += layer('textes', 'Textes (convertir en chemins)', FILL, [
        f'<text x="21" y="{33.6 if n < 10 else 32.8}" text-anchor="middle" font-family="{CINZEL}" font-weight="700" font-size="{19 if n < 10 else 15}">{n}</text>',
        f'<text x="40" y="12.5" font-family="{CINZEL}" font-size="2.3" letter-spacing="0.35">MISSION SOPHIE</text>',
        f'<text x="40" y="20" font-family="{CINZEL}" font-weight="700" font-size="3.9">CARTE-SOUVENIR</text>',
        f'<g font-family="{GARA}" font-style="italic" font-weight="600" font-size="3.2">',
        '<text x="40" y="31">Tu as réveillé un pouvoir</text>',
        '<text x="40" y="35">de Sophie. Garde-moi bien :</text>',
        '<text x="40" y="39">je te servirai à la fin !</text>',
        '</g>',
        f'<text x="40" y="47" font-family="{CINZEL}" font-size="2.3" letter-spacing="0.3">N° {n}</text>',
    ])
    return '\n'.join(L + ['</svg>', ''])


def souvenir_verso():
    L = head(85.6, 54.0, 'Carte-souvenir - verso')
    L += layer('contour_carte', 'Contour carte (repere, ne pas graver)', CUT, ['<rect x="0" y="0" width="85.6" height="54.0" rx="3"/>'])
    L += layer('traits', 'Gravure traits', LINE, FRAME + ['<line x1="18" y1="27" x2="36" y2="27" stroke-width="0.25"/>',
                                                          '<line x1="49.6" y1="27" x2="67.6" y2="27" stroke-width="0.25"/>'])
    L += layer('pleins', 'Gravure pleine', FILL, CORNERS + [star(42.8, 27, 4)])
    L += layer('textes', 'Textes (convertir en chemins)', FILL, [
        f'<text x="42.8" y="16" text-anchor="middle" font-family="{CINZEL}" font-weight="700" font-size="4.2" letter-spacing="0.4">GARDIENNES</text>',
        f'<text x="42.8" y="42" text-anchor="middle" font-family="{CINZEL}" font-size="2.1">{MOTTO}</text>',
    ])
    return '\n'.join(L + ['</svg>', ''])


def wheel(big):
    """Cipher wheel disc: outer letters A-Z, center hole for a split pin (attache parisienne)."""
    R = 60 if big else 45          # disc radius (mm)
    rt = R - 8                     # letter radius
    size = 125
    c = size / 2
    L = head(size, size, 'Roue elfique - ' + ('grand disque' if big else 'petit disque'))
    cut = [f'<circle cx="{c}" cy="{c}" r="{R}"/>', f'<circle cx="{c}" cy="{c}" r="1.6"/>']
    if not big:  # a small tab to turn the disc
        cut = [f'<path d="M {c - 6} {c - R + 0.3} A {R} {R} 0 1 0 {c + 6} {c - R + 0.3} L {c + 4} {c - R - 6} L {c - 4} {c - R - 6} Z"/>',
               f'<circle cx="{c}" cy="{c}" r="1.6"/>']
    L += layer('decoupe', 'Découpe', CUT, cut)
    lines = [f'<circle cx="{c}" cy="{c}" r="{R - 2}"/>', f'<circle cx="{c}" cy="{c}" r="{R - 13}"/>']
    for i in range(26):
        a = 2 * math.pi * i / 26 - math.pi / 26
        lines.append(f'<line x1="{c + (R - 13) * math.sin(a):.2f}" y1="{c - (R - 13) * math.cos(a):.2f}" x2="{c + (R - 2) * math.sin(a):.2f}" y2="{c - (R - 2) * math.cos(a):.2f}" stroke-width="0.2"/>')
    L += layer('traits', 'Gravure traits', LINE, lines)
    L += layer('pleins', 'Gravure pleine', FILL, [] if big else [star(c, c, 4)])
    texts = []
    for i, ch in enumerate('ABCDEFGHIJKLMNOPQRSTUVWXYZ'):
        deg = 360 * i / 26
        texts.append(f'<text x="{c}" y="{c - rt + 2.6}" text-anchor="middle" font-family="{CINZEL}" font-weight="700" font-size="7" '
                     f'transform="rotate({deg:.3f} {c} {c})">{ch}</text>')
    L += layer('textes', 'Textes (convertir en chemins)', FILL, texts)
    return '\n'.join(L + ['</svg>', ''])


root = '..'
d = os.path.join(root, 'cartes-souvenirs-svg')
os.makedirs(d, exist_ok=True)
for n in range(1, 11):
    open(os.path.join(d, f'carte-souvenir-{n:02d}-recto.svg'), 'w', encoding='utf8').write(souvenir_recto(n))
open(os.path.join(d, 'carte-souvenir-verso.svg'), 'w', encoding='utf8').write(souvenir_verso())
d = os.path.join(root, 'roue-elfique-svg')
os.makedirs(d, exist_ok=True)
open(os.path.join(d, 'roue-grand-disque.svg'), 'w', encoding='utf8').write(wheel(True))
open(os.path.join(d, 'roue-petit-disque.svg'), 'w', encoding='utf8').write(wheel(False))
print('ok')
