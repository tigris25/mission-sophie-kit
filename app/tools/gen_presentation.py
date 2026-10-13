"""Builds the presentation sheets for 3D model sites (French and English), with the previews.

Usage (from app/):  python tools/gen_presentation.py
Output: ../presentation/mission-sophie-fr.pdf and mission-sophie-en.pdf (HTML converted with Microsoft Edge).
"""
import os, subprocess, tempfile
from PIL import Image

ROOT = os.path.abspath('..')
OUT = os.path.join(ROOT, 'presentation')
REPO = 'https://github.com/tigris25/mission-sophie-kit'
TMP = tempfile.mkdtemp()


def crop(name, pad=30):
    """Previews have wide margins: crop them to the drawing (temporary copy)."""
    im = Image.open(os.path.join(ROOT, 'apercus', name)).convert('RGB')
    bg = Image.new('RGB', im.size, im.getpixel((0, 0)))
    from PIL import ImageChops
    box = ImageChops.difference(im, bg).convert('L').point(lambda v: 255 if v > 12 else 0).getbbox()
    if box:
        im = im.crop((max(box[0] - pad, 0), max(box[1] - pad, 0), min(box[2] + pad, im.width), min(box[3] + pad, im.height)))
    path = os.path.join(TMP, name)
    im.save(path)
    return 'file:///' + path.replace('\\', '/')


def cards_page():
    """First page of the colour cards PDF, as an image."""
    import pymupdf
    d = pymupdf.open(os.path.join(ROOT, 'cartes-a-imprimer.pdf'))
    path = os.path.join(TMP, 'cartes-couleur.png')
    d[0].get_pixmap(dpi=110).save(path)
    return 'file:///' + path.replace('\\', '/')


IMG = {k: crop(f'apercu-{k}.png') for k in ['amulettes-goutte', 'amulettes-rondes', 'coeur-de-sophie', 'faux-badges',
                                            'roue-elfique', 'roue-elfique-chiffres']}
IMG['cartes'] = cards_page()

T = {
'fr': dict(
    lang='fr', title='Mission Sophie : les Pouvoirs volés',
    tagline='Un jeu d’anniversaire avec des pendentifs NFC imprimés en 3D',
    intro='Pour 7 à 10 enfants de 9-10 ans, environ 1 h 30. Inspiré de la série <i>Gardiens des cités perdues</i> : '
          'les Invisibles ont volé les talents des Gardiennes. Chaque enfant reçoit un <b>pendentif imprimé en 3D avec un tag NFC caché dedans</b>. '
          'Posé sur un téléphone Android, « le Transmetteur de Sophie », il réveille son pouvoir et lance une épreuve : '
          'code à trouver, roue de chiffrement, cri à pousser, piège des Invisibles… jusqu’au Cœur de Sophie.',
    tested='Testé en vrai : la partie a été un succès !',
    incl_t='Ce qui est fourni (gratuit)',
    incl=['<b>18 pendentifs</b> « goutte » à symboles, en 2 couleurs, pour tags NFC porte-clés',
          '<b>2 faux badges</b> (le piège) et le <b>Cœur de Sophie</b> (étoile de la finale)',
          'Une <b>roue de chiffrement elfique</b> à disques clipsés (lettres, ou lettres + chiffres)',
          'Les fichiers <b>OpenSCAD</b> paramétrables et tous les <b>STL</b>',
          'L’<b>appli Android</b> (hors ligne, voix, musique, vidéo), la <b>notice</b> en PDF et les <b>cartes</b> à imprimer'],
    p2='Les pièces à imprimer',
    pend_t='Pendentifs', pend=[
          'Taille : 33 × 69 × 4,2 mm, boucle percée Ø 4,4 mm pour un cordon ou un anneau.',
          'Pour <b>tags NFC porte-clés en goutte</b> 27 × 54 mm, épaisseur 1,1 mm (NTAG213/215 : tous conviennent, l’appli ne lit que l’identifiant). '
          'Mesurez les vôtres et ajustez <code>TAG_L</code>, <code>TAG_R_BIG</code>, <code>TAG_T</code> dans le fichier .scad.',
          '<b>2 couleurs</b> : <code>&lt;talent&gt;-base.stl</code> (couleur 1) et <code>&lt;talent&gt;-symbol.stl</code> (couleur 2), à charger ensemble : elles se superposent exactement.',
          '<b>Face avant sur le plateau</b>, sans support : le symbole est déjà retourné pour se lire dans le bon sens.',
          'Imprimante mono-couleur : n’imprimez que la base, le symbole apparaît en creux (et peut être peint).',
          'Glissez le tag dans le logement au dos, puis collez ou clipsez <code>couvercle.stl</code>.'],
    heart_t='Cœur de Sophie et faux badges',
    heart='Le Cœur (87 mm, sans boucle, avec liseré) est le badge final. Les faux badges imitent un vrai pendentif avec un symbole déformé : c’est le piège.',
    wheel_t='Roue elfique',
    wheel=['Grand disque Ø 116 mm, petit disque Ø 84 mm ; lettres incrustées en 2<sup>e</sup> couleur (<code>*_lettres.stl</code>).',
           'Les disques tournent sur un axe clipsé. Imprimez d’abord <code>roue-test_axe.stl</code> : si le clip est trop dur, baissez <code>LEVRE</code> (0,35 mm validé).',
           'Version <code>chiffres/</code> : 36 cases (A–Z, Ø, 1–9) pour des codes comme K6.'],
    settings_t='Réglages conseillés', settings='PLA, couches de 0,2 mm, 2 à 3 périmètres, remplissage 15-20 %, sans support.',
    p3='Pour jouer',
    play=['Téléchargez le kit et l’APK sur GitHub (lien ci-dessous), installez l’appli sur un téléphone Android avec NFC.',
          'Réglez le <b>prénom</b>, l’<b>âge</b> et les <b>cachettes</b> dans l’appli (Admin › Réglages › Personnaliser).',
          'Associez chaque tag à son pendentif (Admin › Liaison), imprimez les cartes, préparez les épreuves avec la notice.',
          'Le jour J, recopiez l’écran du téléphone sur la télé et laissez Sophie guider les enfants !'],
    lang_note='L’appli et la notice sont en français ; tous les textes sont modifiables dans un seul fichier.',
    cards_t='Cartes à imprimer (recto)',
    round_t='Variante : médaillons ronds', round='Dans le .scad, <code>SHAPE = "rond"</code> donne des médaillons ronds, avec un symbole plus grand.',
    link_t='Tout est ici', link_sub='Fichiers 3D, appli, notice, cartes et sources',
    lic_t='Licence', lic='Modèles, textes et documents : <b>CC BY-NC 4.0</b> (partage et modification libres en citant la source, pas d’usage commercial). Code : MIT.',
    fan='Projet de fans, non officiel et gratuit, sans lien avec Shannon Messenger ni ses éditeurs. Voix, portrait et vidéo de Sophie générés par IA. '
        'Musique : « Fantascape », Eric Matyas, www.soundimage.org. Symboles d’après des dessins d’enfant.'),
'en': dict(
    lang='en', title='Mission Sophie: The Stolen Powers',
    tagline='A birthday party game with 3D-printed NFC pendants',
    intro='For 7 to 10 kids aged 9-10, about 1.5 hours. Inspired by the <i>Keeper of the Lost Cities</i> series: '
          'the Neverseen have stolen the girls’ talents. Each child gets a <b>3D-printed pendant with an NFC tag hidden inside</b>. '
          'Placed on an Android phone, “Sophie’s Transmitter”, it awakens her power and starts a challenge: '
          'a code to find, a cipher wheel, a shout to let out, a Neverseen trap… all the way to Sophie’s Heart.',
    tested='Tested for real: the party was a hit!',
    incl_t='What you get (free)',
    incl=['<b>18 drop-shaped pendants</b> with talent symbols, 2 colours, for NFC key-fob tags',
          '<b>2 fake badges</b> (the trap) and <b>Sophie’s Heart</b> (the final star)',
          'An <b>elvish cipher wheel</b> with snap-fit discs (letters, or letters + digits)',
          'Parametric <b>OpenSCAD</b> files and all the <b>STLs</b>',
          'The <b>Android app</b> (offline, voices, music, video), the <b>game guide</b> (PDF) and printable <b>cards</b>'],
    p2='The printed parts',
    pend_t='Pendants', pend=[
          'Size: 33 × 69 × 4.2 mm, loop with a 4.4 mm hole for a cord or a ring.',
          'Made for <b>drop-shaped NFC key-fob tags</b>, 27 × 54 mm, 1.1 mm thick (any NTAG213/215: the app only reads the tag ID). '
          'Measure yours and adjust <code>TAG_L</code>, <code>TAG_R_BIG</code>, <code>TAG_T</code> in the .scad file.',
          '<b>2 colours</b>: <code>&lt;talent&gt;-base.stl</code> (colour 1) and <code>&lt;talent&gt;-symbol.stl</code> (colour 2), loaded together: they overlap exactly.',
          '<b>Front face on the bed</b>, no supports: the symbol is already mirrored so it reads the right way.',
          'Single-colour printer: print the base only, the symbol shows engraved (and can be painted).',
          'Slide the tag into the pocket at the back, then glue or press-fit <code>couvercle.stl</code> (the lid).'],
    heart_t='Sophie’s Heart and fake badges',
    heart='The Heart (87 mm, no loop, with a border) is the final badge. The fake badges mimic a real pendant with a distorted symbol: that’s the trap.',
    wheel_t='Elvish cipher wheel',
    wheel=['Large disc Ø 116 mm, small disc Ø 84 mm; inlaid letters in a 2nd colour (<code>*_lettres.stl</code>).',
           'The discs turn on a snap-fit axle. Print <code>roue-test_axe.stl</code> first: if the snap is too stiff, lower <code>LEVRE</code> (0.35 mm tested).',
           '<code>chiffres/</code> version: 36 slots (A–Z, Ø, 1–9) for codes like K6.'],
    settings_t='Suggested settings', settings='PLA, 0.2 mm layers, 2-3 walls, 15-20 % infill, no supports.',
    p3='How to play',
    play=['Download the kit and the APK from GitHub (link below), install the app on an Android phone with NFC.',
          'Set the <b>name</b>, <b>age</b> and <b>hiding places</b> in the app (Admin › Réglages › Personnaliser).',
          'Pair each tag with its pendant (Admin › Liaison), print the cards, set up the challenges with the guide.',
          'On the day, mirror the phone to the TV and let Sophie lead the kids!'],
    lang_note='The app and the guide are in <b>French</b>; all the texts live in a single file and can be translated. The 3D files work for any game.',
    cards_t='Printable cards (front)',
    round_t='Variant: round medallions', round='In the .scad file, <code>SHAPE = "rond"</code> gives round medallions with a larger symbol.',
    link_t='Everything is here', link_sub='3D files, app, guide, cards and sources',
    lic_t='License', lic='Models, texts and documents: <b>CC BY-NC 4.0</b> (share and remix with credit, no commercial use). Code: MIT.',
    fan='Unofficial, free fan project, not affiliated with Shannon Messenger or her publishers. Sophie’s voices, portrait and video are AI-generated. '
        'Music: “Fantascape”, Eric Matyas, www.soundimage.org. Symbols based on a child’s drawings.'),
}

CSS = '''
@page { size: A4; margin: 0; }
* { box-sizing: border-box; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
body { margin: 0; font-family: "Segoe UI", Calibri, sans-serif; color: #1f1d2b; font-size: 10.5pt; line-height: 1.42; }
.page { width: 210mm; height: 297mm; padding: 15mm 16mm 12mm; position: relative; page-break-after: always; overflow: hidden; }
.band { position: absolute; left: 0; right: 0; top: 0; height: 8mm; background: #1b2142; }
.band::after { content: ""; position: absolute; left: 0; right: 0; bottom: -1.2mm; height: 1.2mm; background: #b8ab88; }
h1 { font-size: 27pt; color: #1b2142; margin: 4mm 0 1mm; letter-spacing: -0.3pt; }
.tag { font-size: 14pt; color: #6a4fa0; margin: 0 0 4mm; }
h2 { font-size: 16pt; color: #1b2142; margin: 5mm 0 2.5mm; border-bottom: 0.5mm solid #b8ab88; padding-bottom: 1mm; }
h3 { font-size: 12pt; color: #6a4fa0; margin: 3.5mm 0 1.5mm; }
p { margin: 0 0 2mm; }
ul, ol { margin: 0 0 2mm; padding-left: 5.5mm; }
li { margin-bottom: 1.2mm; }
code { font-family: Consolas, monospace; font-size: 9pt; background: #eeeaf6; padding: 0 1mm; border-radius: 1mm; }
.hero { width: 100%; height: 100mm; object-fit: contain; background: #f7f7f7; border-radius: 3mm; margin: 2mm 0 3mm; }
.badge { display: inline-block; background: #e9f6ec; color: #23673a; border-radius: 2mm; padding: 1mm 3mm; font-weight: 600; margin-bottom: 3mm; }
.box { background: #f4f1fa; border-left: 1.2mm solid #6a4fa0; border-radius: 1.5mm; padding: 3mm 4mm; margin: 3mm 0; }
.row { display: flex; gap: 6mm; align-items: flex-start; }
.row > div { flex: 1; }
.img { width: 100%; background: #f7f7f7; border-radius: 2mm; display: block; }
.link { background: #1b2142; color: #fff; border-radius: 3mm; padding: 5mm 6mm; margin: 5mm 0 4mm; }
.link .t { color: #b8ab88; font-size: 10pt; text-transform: uppercase; letter-spacing: 1pt; }
.link .u { font-size: 15pt; font-weight: 700; margin: 1mm 0; }
.link .s { font-size: 9.5pt; color: #cfd2e6; }
.small { font-size: 8.5pt; color: #666; }
.foot { position: absolute; left: 16mm; right: 16mm; bottom: 8mm; font-size: 8pt; color: #999; display: flex; justify-content: space-between; }
'''


def page(body, n, t):
    return f'<div class="page"><div class="band"></div>{body}<div class="foot"><span>{t["title"]}</span><span>{n} / 3</span></div></div>'


def build(t):
    li = lambda xs: ''.join(f'<li>{x}</li>' for x in xs)
    p1 = f'''<h1>{t["title"]}</h1><p class="tag">{t["tagline"]}</p>
<img class="hero" src="{IMG["amulettes-goutte"]}">
<span class="badge">✓ {t["tested"]}</span>
<p>{t["intro"]}</p>
<h2>{t["incl_t"]}</h2><ul>{li(t["incl"])}</ul>'''
    p2 = f'''<h2 style="margin-top:4mm">{t["p2"]}</h2>
<h3>{t["pend_t"]}</h3><ul>{li(t["pend"])}</ul>
<div class="row"><div><h3>{t["heart_t"]}</h3><p>{t["heart"]}</p>
<img class="img" src="{IMG["faux-badges"]}" style="margin-top:2mm"></div>
<div style="flex:0 0 52mm"><img class="img" src="{IMG["coeur-de-sophie"]}"></div></div>
<div class="row"><div><h3>{t["wheel_t"]}</h3><ul>{li(t["wheel"])}</ul></div>
<div style="flex:0 0 62mm"><img class="img" src="{IMG["roue-elfique"]}" style="margin-top:4mm"></div></div>
<div class="box"><b>{t["settings_t"]} :</b> {t["settings"]}</div>'''.replace(' :</b>', ':</b>' if t['lang'] == 'en' else ' :</b>')
    p3 = f'''<h2 style="margin-top:4mm">{t["p3"]}</h2>
<div class="row"><div><ol>{li(t["play"])}</ol><div class="box">{t["lang_note"]}</div>
<h3>{t["round_t"]}</h3><p>{t["round"]}</p><img class="img" src="{IMG["amulettes-rondes"]}" style="max-height:48mm;object-fit:contain"></div>
<div style="flex:0 0 66mm"><h3 style="margin-top:0">{t["cards_t"]}</h3><img class="img" src="{IMG["cartes"]}" style="border:0.3mm solid #ddd"></div></div>
<div class="link"><div class="t">{t["link_t"]}</div><div class="u">{REPO.replace("https://", "")}</div><div class="s">{t["link_sub"]}</div></div>
<h3>{t["lic_t"]}</h3><p>{t["lic"]}</p>
<p class="small">{t["fan"]}</p>'''
    return (f'<!doctype html><html lang="{t["lang"]}"><head><meta charset="utf-8"><title>{t["title"]}</title><style>{CSS}</style></head><body>'
            + page(p1, 1, t) + page(p2, 2, t) + page(p3, 3, t) + '</body></html>')


def main():
    os.makedirs(OUT, exist_ok=True)
    edge = next((p for p in [r'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe',
                             r'C:\Program Files\Microsoft\Edge\Application\msedge.exe'] if os.path.exists(p)), None)
    for lang, t in T.items():
        html = os.path.join(TMP, f'mission-sophie-{lang}.html')
        open(html, 'w', encoding='utf8').write(build(t))
        pdf = os.path.join(OUT, f'mission-sophie-{lang}.pdf')
        if edge:
            subprocess.run([edge, '--headless', '--disable-gpu', '--no-pdf-header-footer', f'--print-to-pdf={pdf}',
                            'file:///' + html.replace('\\', '/')], capture_output=True, timeout=120)
        print(lang, ':', pdf if os.path.exists(pdf) else 'non généré (Edge introuvable), HTML : ' + html)


if __name__ == '__main__':
    main()
