// One-page game-master cheat sheet (A4, to print). Codes come from the app's game_content.json.
const fs = require('fs');
const path = require('path');
const { Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell, WidthType, ShadingType, BorderStyle, AlignmentType } = require('docx');

const ROOT = path.resolve(__dirname, '..', '..', '..');
const DOCS = path.join(ROOT, 'documents');
fs.mkdirSync(DOCS, { recursive: true });
const RAW = JSON.parse(fs.readFileSync(path.join(ROOT, 'app/android/src/main/assets/game_content.json'), 'utf8'));
// Weather versions of the places: texts are shown with {{key}} replaced by "rainy / sunny".
const VARIANTS = RAW.variants || {};
const VIDS = Object.keys(VARIANTS);
const both = key => (RAW.params && key in RAW.params) ? RAW.params[key] : VIDS.map(v => VARIANTS[v][key]).filter((x, i, a) => a.indexOf(x) === i).join(' / ');
const C = JSON.parse(JSON.stringify(RAW).replace(/\{\{(\w+)\}\}/g, (m, k) => both(k).replace(/"/g, '\\"')));
const S = 17; // body text size (half-points)
const VIOLET = '4B2E83', HEAD = 'EDE7F6', BLUE = 'E8F1FF', MAUVE = 'F3E6F5', YELLOW = 'FFF4CC';
const b = { style: BorderStyle.SINGLE, size: 4, color: 'B0B0B0' };
const none = { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' };

const t = (text, o = {}) => new TextRun({ text, size: S, ...o });
const P = (runs, o = {}) => new Paragraph({ children: (Array.isArray(runs) ? runs : [runs]).map(x => typeof x === 'string' ? t(x) : x), spacing: { after: 30 }, ...o });
const B = s => t(s, { bold: true });
const H = (s, color = VIOLET) => new Paragraph({ children: [t(s, { bold: true, size: 21, color })], spacing: { before: 90, after: 40 } });
const li = runs => P(runs, { indent: { left: 200, hanging: 140 }, spacing: { after: 20 } });
const dot = (...runs) => li([t('• '), ...runs.map(x => typeof x === 'string' ? t(x) : x)]);

function cell(children, width, fill, borders = { top: b, bottom: b, left: b, right: b }) {
  return new TableCell({ width: { size: width, type: WidthType.DXA }, borders,
    shading: fill ? { fill, type: ShadingType.CLEAR, color: 'auto' } : undefined,
    margins: { top: 40, bottom: 40, left: 80, right: 80 }, children });
}
function grid(widths, rows, headFill = HEAD) {
  return new Table({ width: { size: widths.reduce((a, c) => a + c, 0), type: WidthType.DXA }, columnWidths: widths,
    rows: rows.map((r, i) => new TableRow({ children: r.map((v, j) => cell([P(typeof v === 'string' ? t(v, { size: 16, bold: i === 0 }) : v, { spacing: { after: 0 } })], widths[j], i === 0 ? headFill : undefined)) })) });
}
function box(fill, children, width = 5240) {
  return new Table({ width: { size: width, type: WidthType.DXA }, columnWidths: [width],
    rows: [new TableRow({ children: [cell(children, width, fill)] })] });
}

// ---------------------------------------------------------------- left column
const left = [
  box(YELLOW, [
    P([t('CODE : ', { bold: true, size: 26, color: VIOLET }), t('4275', { bold: true, size: 30 })], { spacing: { after: 20 } }),
    P('Le même pour l\'Admin et le menu MJ. À changer dans Admin › Réglages.'),
  ]),
  H('Ouvrir le menu MJ (pendant la partie)'),
  P([B('Appui long 3 secondes sur le petit portrait de Sophie'), t(' (en haut à gauche), puis le code. Aucun code n\'y est affiché : on peut l\'ouvrir même sur la télé.')]),
  H('Dans le menu MJ'),
  dot(B('Passer l\'étape'), ' : épreuve réussie (remettre quand même la carte).'),
  dot(B('Forcer le code / le cri'), ' : code ou cri-mètre validé.'),
  dot(B('Joker'), ' : accepter un autre pendentif pour cette épreuve.'),
  dot(B('Envoyer l\'indice'), ' · ', B('Message de Sophie'), ' · ', B('Moquerie'), ' · ', B('Message libre'), '.'),
  dot(B('Pause des messages d\'attente'), ' (Sophie qui se plaint, Invisibles qui se moquent).'),
  dot(B('Valider / passer le piège'), '.'),
  dot(B('Étape précédente'), ' : annuler une erreur. Au rassemblement : retire le dernier pendentif posé.'),
  dot(B('Redémarrer'), ' · ', B('Quitter vers l\'accueil'), ' · ', B('Volumes'), ' · ', B('Muet'), ' · ', B('Épreuves bonus'), ' (à la fin).'),
  H('Le déroulé'),
  li([B('1. Rassemblement'), t(' : chaque fille pose son pendentif, puis « Toutes les Gardiennes sont là ! ».')]),
  li([B('2. Chaque épreuve'), t(' : Sophie appelle un talent → ')]),
  li([t('   '), B('1er scan'), t(' du pendentif : la consigne s\'affiche → épreuve → '), B('2e scan'), t(' du même pendentif (30 s minimum) : le clavier s\'ouvre → code → level-up → '), B('« carte-souvenir n°X »'), t(' : donner la carte X.')]),
  li([t('   Vocifératrice : pas de code, tout le monde crie (cri-mètre).')]),
  li([B('3. Le piège'), t(' (au milieu) : scanner le '), B('faux badge'), t(', puis l\'écho : reproduire l\'ordre des cristaux avec les pendentifs.')]),
  li([B('4. Écho final'), t(' : reposer les pendentifs dans l\'ordre des épreuves (cartes n°1, n°2…). Puis Sophie révèle la cachette : scanner le '), B('Cœur'), t('.')]),
  li([B('5. Bonus'), t(' : bouton « Épreuves bonus » après la finale (pas de scan, bouton « Entrer le code »).')]),
  H('En cas de souci'),
  dot(B('Plantage, écran verrouillé'), ' : relancer l\'appli › Jeu › ', B('Reprendre la partie'), '.'),
  dot(B('Tag pas lu'), ' : bien à plat au dos du téléphone, 1 seconde, au même endroit.'),
  dot(B('« Déjà ? Je ne te crois pas »'), ' : 2e scan trop tôt, attendre un peu.'),
  dot(B('Cri-mètre trop dur'), ' : menu MJ › Forcer le code / le cri.'),
  dot(B('Test sans tags'), ' : bouton ', B('SIM'), ' en haut à droite (à désactiver le jour J).'),
];

// ---------------------------------------------------------------- right column
const powers = C.sequence.map(id => C.powers.find(p => p.id === id));
const bonus = C.powers.filter(p => p.bonusOnly);
const codeRows = [['Talent', 'Code', 'Talent', 'Code']];
const all = [...powers, ...bonus];
const half = Math.ceil(all.length / 2);
const codeOf = p => p.kind === 'shout' ? '(cri)' : p.answers[0].replace('ETINCELLE', 'ÉTINCELLE').replace('ECLAIR', 'ÉCLAIR').replace('LUMIERE', 'LUMIÈRE').replace('COEUR', 'CŒUR');
const nameOf = p => p.talent + (p.bonusOnly ? ' (bonus)' : '');
for (let i = 0; i < half; i++) {
  const a = all[i], c = all[i + half];
  codeRows.push([nameOf(a), t(codeOf(a), { size: 16, bold: true }), c ? nameOf(c) : '', c ? t(codeOf(c), { size: 16, bold: true }) : '']);
}
const right = [
  H('Les codes (MJ uniquement)'),
  grid([1420, 1200, 1420, 1200], codeRows),
  P(t('Majuscules, accents et espaces sont ignorés.', { size: 15, italics: true, color: '666666' }), { spacing: { before: 30, after: 30 } }),
  H('Cartes-souvenirs et joker'),
  dot('Une seule pile, ', B('numérotée de 1 à 10'), ', rangée dans l\'ordre.'),
  dot('Après chaque épreuve, l\'écran dit « carte-souvenir n°X » : donner la carte X à la spécialiste (même si l\'épreuve a été passée).'),
  dot(B('Écho final'), ' : les cristaux se rallument dans l\'ordre des épreuves ; les filles posent leurs pendentifs dans l\'ordre de leurs numéros (n°1, n°2…). Bouton « Revoir l\'écho ».'),
  dot(B('Joker'), ' : la carte de personnage, à montrer au MJ une fois (indice, 2 min de plus, emprunt d\'un pendentif).'),
  H('Préparations de dernière minute'),
  dot(B('Givreuse'), ' : bloc de glace avec GIVRE (congelé la veille).'),
  dot(B('Fluctuatrice'), ' : PLUME sous l\'œuf, verre d\'eau, sel.'),
  dot(B('Ténébreuse'), ' : Cuillère, Arrosoir, Peigne, Éponge (CAPE).'),
  dot(B('Téléporteuse'), ' : carte scellée CRISTAL à la chaudière (C4).'),
  dot(B('Flasheuse'), ' : carton percé LUMIÈRE, pièce sombre.'),
  dot(B('Technopathe'), ' : feuille des ampoules (PUCE).'),
  dot(B('Polyglotte'), ' : roue + message codé. Réglage : L du petit en face du O du grand (A en face de D).'),
  dot(B('Chargeuse'), ' : ballons + lettres É C L A I R.'),
  H('Lieux selon la météo (Admin › Réglages)'),
  dot(B('Faux badge'), ' : mezzanine ☔ · trampoline ☀'),
  dot(B('Boîte du Cœur'), ' : cave ☔ · étendage à linge ☀'),
  dot(B('Éclipseuse'), ' : cuisine ☔ · allée du garage ☀'),
  H('Avant de commencer'),
  dot('Téléphone chargé, NFC activé, ', B('Muet désactivé'), ', simulateur désactivé, ', B('bonne météo'), ' choisie.'),
  dot('Admin › Partie : partie remise à zéro. Mode « Ne pas déranger ».'),
];

const noB = { top: none, bottom: none, left: none, right: none };
const layout = new Table({
  width: { size: 10886, type: WidthType.DXA }, columnWidths: [5443, 5443],
  rows: [new TableRow({ children: [
    new TableCell({ width: { size: 5443, type: WidthType.DXA }, borders: noB, margins: { right: 120 }, children: left }),
    new TableCell({ width: { size: 5443, type: WidthType.DXA }, borders: noB, margins: { left: 120 }, children: right }),
  ] })],
});

const doc = new Document({
  styles: { default: { document: { run: { font: 'Calibri', size: S } } } },
  sections: [{
    properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 567, bottom: 567, left: 510, right: 510 } } },
    children: [
      new Paragraph({ children: [t('Mission Sophie · Fiche du Maître du jeu', { bold: true, size: 32, color: VIOLET })], spacing: { after: 20 } }),
      new Paragraph({ children: [t('Les Pouvoirs volés · à garder dans la poche, ne pas montrer aux Gardiennes !', { size: 18, color: '666666' })], spacing: { after: 100 } }),
      layout,
    ],
  }],
});
Packer.toBuffer(doc).then(buf => { fs.writeFileSync(path.join(DOCS, 'fiche-mj-mission-sophie.docx'), buf); console.log('ok'); });
