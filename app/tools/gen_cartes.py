"""Generates the character cards (laser engraving SVG, 85.6 x 54 mm, recto + verso) for the 18 talents.

Usage (from app/):  python tools/gen_cartes.py
Symbols come from android/.../ui/SymbolPaths.kt (themselves generated from the 3D model, i.e. a child's drawings).
Output: ../cartes-personnages-svg/carte-<name>-recto.svg / -verso.svg
Layers: "contour_carte" (red outline, not engraved), "traits" (line engraving), "pleins" (filled engraving),
"textes" (to convert to paths in the laser software).
"""
import os, re, unicodedata

OUT = os.path.join('..', 'cartes-personnages-svg')
SYMS = dict(re.findall(r'"([a-z]+)" to "([^"]*)"', open('android/src/main/kotlin/fr/mission/sophie/ui/SymbolPaths.kt', encoding='utf8').read()))

# Order of the bottom row = order of the talents in the game content (sequence).
ROW = ["telepathe", "rafaleur", "polyglotte", "phaseur", "empathe", "hydrokinesiste", "technopathe", "eclipseur",
       "fluctuateur", "chargeur", "flasheur", "hypnotiseur", "givreur", "invocateur", "instillateur", "teleporteur",
       "tenebreux", "vociferateur"]

# id: (talent, name, epithet, [power line 1, line 2], [quote line 1, line 2])
CARDS = {
    "telepathe": ("TÉLÉPATHE", "NYRIEL", "l'Oreille des esprits", ["Entendre les pensées, parler", "sans un mot"], ["« Écouter, c'est déjà", "comprendre. »"]),
    "empathe": ("EMPATHE", "LISÉA", "le Cœur qui écoute", ["Ressentir les émotions", "de ceux qui l'entourent"], ["« Je sens quand tu es triste,", "alors je reste là. »"]),
    "flasheur": ("FLASHEUSE", "LUMIEL", "la Main de lumière", ["Faire naître la lumière", "et la plier à sa volonté"], ["« Même la plus petite lueur", "chasse l'obscurité. »"]),
    "eclipseur": ("ÉCLIPSEUSE", "VESPÉRA", "la Silhouette transparente", ["Laisser passer la lumière", "et devenir invisible"], ["« On ne me voit pas,", "mais je suis toujours là. »"]),
    "hydrokinesiste": ("HYDROKINÉSISTE", "MARISEL", "la Voix des vagues", ["Commander à l'eau", "des rivières et des pluies"], ["« Comme l'eau, je trouve", "toujours un passage. »"]),
    "givreur": ("GIVREUSE", "HIVÉANE", "la Fille du givre", ["Geler l'eau et l'air", "en un seul souffle"], ["« Garder la tête froide,", "c'est aussi un pouvoir. »"]),
    "teleporteur": ("TÉLÉPORTEUSE", "ZAËLYS", "l'Éclair vivant", ["Se déplacer en un éclair", "d'un point à un autre"], ["« Pas de temps à perdre, juste des", "éclairs de génie ! »"]),
    "instillateur": ("INSTILLATRICE", "AURÉLITH", "la Semeuse d'émotions", ["Faire naître une émotion", "dans le cœur des autres"], ["« Un sourire, ça se partage", "comme une étincelle. »"]),
    "polyglotte": ("POLYGLOTTE", "MYRENNE", "la Voix des mille langues", ["Comprendre toutes les", "langues, même les secrètes"], ["« Un mot bien choisi ouvre toutes", "les portes. »"]),
    "invocateur": ("INVOCATRICE", "FAELORA", "l'Artisane des mirages", ["Faire apparaître des objets", "venus d'ailleurs"], ["« Une bonne idée vaut mieux qu'un", "grand sortilège. »"]),
    "technopathe": ("TECHNOPATHE", "CYRANE", "l'Esprit des machines", ["Comprendre et commander", "toutes les machines"], ["« Chaque machine a un cœur :", "il suffit de l'écouter. »"]),
    "tenebreux": ("TÉNÉBREUSE", "SOMBRELLE", "la Gardienne des ombres", ["Faire naître les ténèbres", "et les façonner"], ["« L'ombre n'est pas méchante :", "elle protège. »"]),
    "chargeur": ("CHARGEUSE", "VOLTÉA", "l'Étincelle vivante", ["Rassembler l'énergie et la", "relâcher en éclairs"], ["« J'ai toujours un peu", "d'énergie en réserve ! »"]),
    "phaseur": ("PHASEUSE", "PHAELIS", "la Passe-murailles", ["Traverser les murs", "et la matière"], ["« Aucun mur ne m'arrête quand", "mes amies m'attendent. »"]),
    "hypnotiseur": ("HYPNOTISEUSE", "SÉLÉNIA", "le Regard qui envoûte", ["Guider les esprits par", "la voix et le regard"], ["« Regarde-moi bien…", "et souris ! »"]),
    "rafaleur": ("RAFALEUSE", "ÉOLWEN", "le Souffle du vent", ["Faire naître rafales", "et tourbillons"], ["« Je vais où le vent m'emmène, et", "j'emmène mes amies. »"]),
    "vociferateur": ("VOCIFÉRATRICE", "THALYRA", "la Voix qui tonne", ["Faire porter sa voix", "jusqu'au bout du monde"], ["« Quand je parle,", "même le tonnerre écoute. »"]),
    "fluctuateur": ("FLUCTUATRICE", "ÉLYNE", "la Plume et la Pierre", ["Rendre la matière légère", "ou lourde comme la pierre"], ["« Légère comme une plume,", "solide comme un roc. »"]),
}

CINZEL = "Cinzel, 'Trajan Pro', 'Times New Roman', serif"
GARA = "'Cormorant Garamond', Garamond, 'Times New Roman', serif"
MOTTO = "NOUS JURONS DE PROTÉGER LA LUMIÈRE"


def slug(s):
    return ''.join(c for c in unicodedata.normalize('NFD', s.lower()) if unicodedata.category(c) != 'Mn')


def sym(id_, x, y, k):
    # Symbol units are about -14..14 (y down): scale k maps them to millimetres.
    return f'<path fill-rule="evenodd" transform="translate({x:.3f} {y:.3f}) scale({k:.4f})" d="{SYMS[id_]}"/>'


def frame(title):
    return [
        '<?xml version="1.0" encoding="UTF-8"?>',
        '<svg xmlns="http://www.w3.org/2000/svg" xmlns:inkscape="http://www.inkscape.org/namespaces/inkscape" width="85.6mm" height="54.0mm" viewBox="0 0 85.6 54.0">',
        f'  <title>{title}</title>',
        '  <g id="contour_carte" inkscape:groupmode="layer" inkscape:label="Contour carte (repere, ne pas graver)" fill="none" stroke="#ff0000" stroke-width="0.1">',
        '    <rect x="0" y="0" width="85.6" height="54.0" rx="3"/>',
        '  </g>',
    ]


CORNERS = ['<polygon points="5.6,4.4 6.8,5.6 5.6,6.8 4.4,5.6"/>', '<polygon points="5.6,47.2 6.8,48.4 5.6,49.6 4.4,48.4"/>',
           '<polygon points="80,4.4 81.2,5.6 80,6.8 78.8,5.6"/>', '<polygon points="80,47.2 81.2,48.4 80,49.6 78.8,48.4"/>']


def recto(id_):
    talent, name, epithet, power, _ = CARDS[id_]
    # Row between the corner diamonds (x 8.2 to 77.4).
    pitch = 69.2 / len(ROW)
    xs = [8.2 + pitch / 2 + i * pitch for i in range(len(ROW))]
    own = xs[ROW.index(id_)]
    L = frame(f'{name.title()} - recto')
    L += ['  <g id="traits" inkscape:groupmode="layer" inkscape:label="Gravure traits" fill="none" stroke="#000000" stroke-width="0.3" stroke-linecap="round" stroke-linejoin="round">',
          '    <rect x="2" y="2" width="81.6" height="50" rx="1.6"/>',
          '    <rect x="3.2" y="3.2" width="79.2" height="47.6" rx="0.8"/>',
          '    <circle cx="21" cy="23" r="14"/>',
          '    <circle cx="21" cy="23" r="12.9"/>',
          '    <line x1="40" y1="24.6" x2="80" y2="24.6" stroke-width="0.25"/>',
          '    <line x1="6" y1="40.6" x2="79.6" y2="40.6" stroke-width="0.25"/>',
          f'    <circle cx="{own:.3f}" cy="46.2" r="{pitch / 2 - 0.1:.3f}" stroke-width="0.25"/>',
          '  </g>',
          '  <g id="pleins" inkscape:groupmode="layer" inkscape:label="Gravure pleine" fill="#000000" stroke="none">']
    L += ['    ' + c for c in CORNERS]
    L += ['    ' + sym(id_, 21, 23, 0.64),
          '    <polygon points="40,23.7 40.9,24.6 40,25.5 39.1,24.6"/>',
          '    <polygon points="42.8,39.6 43.8,40.6 42.8,41.6 41.8,40.6"/>']
    L += ['    ' + sym(s, x, 46.2, 0.1) for s, x in zip(ROW, xs)]
    L += ['  </g>',
          '  <g id="textes" inkscape:groupmode="layer" inkscape:label="Textes (convertir en chemins)" fill="#000000" stroke="none">',
          f'    <text x="40" y="11.2" font-family="{CINZEL}" font-size="2.3" letter-spacing="0.35">{talent}</text>',
          f'    <text x="40" y="18.2" font-family="{CINZEL}" font-weight="700" font-size="5.2">{name}</text>',
          f'    <text x="40" y="22.6" font-family="{GARA}" font-style="italic" font-weight="600" font-size="3.0">{epithet}</text>',
          f'    <text x="40" y="28.2" font-family="{CINZEL}" font-size="2.0" letter-spacing="0.35">POUVOIR</text>',
          f'    <g font-family="{GARA}" font-weight="600" font-size="3.1">',
          f'    <text x="40" y="32">{power[0]}</text>',
          f'    <text x="40" y="35.6">{power[1]}</text>',
          '    </g>', '  </g>', '</svg>', '']
    return '\n'.join(L)


def verso(id_):
    _, name, _, _, quote = CARDS[id_]
    L = frame(f'{name.title()} - verso')
    L += ['  <g id="traits" inkscape:groupmode="layer" inkscape:label="Gravure traits" fill="none" stroke="#000000" stroke-width="0.3" stroke-linecap="round" stroke-linejoin="round">',
          '    <rect x="2" y="2" width="81.6" height="50" rx="1.6"/>',
          '    <rect x="3.2" y="3.2" width="79.2" height="47.6" rx="0.8"/>',
          '    <line x1="18" y1="13.5" x2="36.3" y2="13.5" stroke-width="0.25"/>',
          '    <line x1="49.3" y1="13.5" x2="67.6" y2="13.5" stroke-width="0.25"/>',
          '    <line x1="6" y1="40.6" x2="79.6" y2="40.6" stroke-width="0.25"/>',
          '  </g>',
          '  <g id="pleins" inkscape:groupmode="layer" inkscape:label="Gravure pleine" fill="#000000" stroke="none">']
    L += ['    ' + c for c in CORNERS]
    L += ['    ' + sym(id_, 42.8, 13.5, 0.2),
          '    <polygon points="42.8,39.6 43.8,40.6 42.8,41.6 41.8,40.6"/>',
          '  </g>',
          '  <g id="textes" inkscape:groupmode="layer" inkscape:label="Textes (convertir en chemins)" fill="#000000" stroke="none">',
          f'    <g font-family="{GARA}" font-style="italic" font-weight="600" font-size="4.2">',
          f'    <text x="42.8" y="25" text-anchor="middle">{quote[0]}</text>',
          f'    <text x="42.8" y="30.4" text-anchor="middle">{quote[1]}</text>',
          '    </g>',
          f'    <text x="42.8" y="37.6" text-anchor="middle" font-family="{CINZEL}" font-size="2.8" letter-spacing="0.3">— {name} —</text>',
          f'    <text x="42.8" y="46.6" text-anchor="middle" font-family="{CINZEL}" font-size="2.1">{MOTTO}</text>',
          '  </g>', '</svg>', '']
    return '\n'.join(L)


if __name__ == '__main__':
  for id_ in ROW:
    n = slug(CARDS[id_][1])
    open(os.path.join(OUT, f'carte-{n}-recto.svg'), 'w', encoding='utf8').write(recto(id_))
    open(os.path.join(OUT, f'carte-{n}-verso.svg'), 'w', encoding='utf8').write(verso(id_))
  print(f'{len(ROW)} cartes -> {OUT}')
