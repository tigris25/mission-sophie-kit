// Voice recording list (Sophie, then the Invisibles) from ../voix/voix-a-enregistrer.json
const fs = require('fs');
const path = require('path');
const { Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell, WidthType, ShadingType, HeadingLevel, BorderStyle } = require('docx');

const ROOT = path.resolve(__dirname, '..', '..', '..');
const DOCS = path.join(ROOT, 'documents');
fs.mkdirSync(DOCS, { recursive: true });
const L = JSON.parse(fs.readFileSync(path.join(ROOT, 'voix/voix-a-enregistrer.json'), 'utf8'));
const VIOLET = '4B2E83', HEAD = 'EDE7F6', BLUE = 'E8F1FF', MAUVE = 'F3E6F5', NOTE = 'FFF4CC';
const b = { style: BorderStyle.SINGLE, size: 4, color: 'A0A0A0' };
const borders = { top: b, bottom: b, left: b, right: b };
const t = (text, o = {}) => new TextRun({ text, ...o });
const P = (runs, o = {}) => new Paragraph({ children: (Array.isArray(runs) ? runs : [runs]).map(x => typeof x === 'string' ? t(x) : x), spacing: { after: 70 }, ...o });
const B = s => t(s, { bold: true });
const H1 = s => new Paragraph({ heading: HeadingLevel.HEADING_1, children: [t(s)], spacing: { before: 240, after: 120 } });
const bullet = runs => P([t('• '), ...(Array.isArray(runs) ? runs : [runs]).map(x => typeof x === 'string' ? t(x) : x)], { indent: { left: 300, hanging: 160 } });

function cell(children, width, fill) {
  return new TableCell({ borders, width: { size: width, type: WidthType.DXA },
    shading: fill ? { fill, type: ShadingType.CLEAR, color: 'auto' } : undefined,
    margins: { top: 40, bottom: 40, left: 80, right: 80 }, children });
}
const W = [520, 2350, 1900, 4868];
function table(rows, fill) {
  const head = new TableRow({ tableHeader: true, children: ['N°', 'Fichier', 'Moment', 'Texte à dire'].map((h, i) =>
    cell([P(t(h, { bold: true, size: 17 }), { spacing: { after: 0 } })], W[i], HEAD)) });
  return new Table({ width: { size: W.reduce((a, c) => a + c, 0), type: WidthType.DXA }, columnWidths: W, rows: [head, ...rows.map(l =>
    new TableRow({ cantSplit: true, children: [
      cell([P(t(String(l.n), { size: 17, bold: true }), { spacing: { after: 0 } })], W[0], fill),
      cell([P(t(l.file, { size: 15, font: 'Consolas' }), { spacing: { after: 0 } }),
            ...(l.prio === 1 ? [P(t('★ prioritaire', { size: 14, color: '8A6D00' }), { spacing: { after: 0 } })] : [])], W[1], fill),
      cell([P(t(l.moment, { size: 15, color: '555555' }), { spacing: { after: 0 } })], W[2], fill),
      cell([P(t(l.spoken, { size: 18 }), { spacing: { after: 0 } }),
            ...(l.note ? [P(t(l.note, { size: 14, italics: true, color: '8A6D00' }), { spacing: { after: 0 } })] : [])], W[3], fill),
    ] }))] });
}

const sophie = L.filter(l => l.speaker === 'sophie'), inv = L.filter(l => l.speaker !== 'sophie');
const ch = [
  new Paragraph({ children: [t('Mission Sophie : les voix à enregistrer', { size: 40, bold: true, color: VIOLET })], spacing: { after: 60 } }),
  new Paragraph({ children: [t(`${L.length} répliques · ${sophie.length} pour Sophie, ${inv.length} pour les Invisibles · ${L.filter(l => l.prio === 1).length} prioritaires (★)`, { size: 22, color: '666666' })], spacing: { after: 200 } }),
  H1('Mode d’emploi'),
  bullet([B('Voix : '), 'une voix féminine, chaleureuse et vive pour Sophie ; une voix masculine, plus grave et un peu théâtrale pour les Invisibles (vitesse 0,9).']),
  bullet([B('Nom des fichiers : '), 'exactement celui de la colonne « Fichier » (en minuscules, avec .mp3). C’est ce nom qui permet à l’appli de jouer la bonne voix.']),
  bullet([B('Ordre conseillé : '), 'd’abord les répliques ★ prioritaires (l’histoire), puis le reste (variantes, indices, épreuves bonus). Sans fichier, le texte s’affiche quand même.']),
  bullet([B('Texte à dire : '), 'parfois différent du texte affiché (remarque en jaune) : le lieu qui change avec la météo, le message codé ou le numéro de carte restent seulement écrits à l’écran.']),
  bullet([B('Dans l’appli : '), 'mettre tous les fichiers dans un dossier du téléphone, puis Admin › Contenu et voix › Importer un dossier. « Vérifier les voix » liste ce qui manque.']),
  bullet([B('Format : '), 'mp3 mono, silences de début et de fin coupés si possible. Le fichier voix-a-enregistrer.csv contient la même liste (pour Excel ou un script).']),
  H1(`Sophie (${sophie.length} répliques)`), table(sophie, BLUE),
  H1(`Les Invisibles (${inv.length} répliques)`), table(inv, MAUVE),
];
const doc = new Document({
  styles: { default: { document: { run: { font: 'Calibri', size: 20 } } },
    paragraphStyles: [{ id: 'Heading1', name: 'Heading 1', basedOn: 'Normal', next: 'Normal', quickFormat: true, run: { size: 30, bold: true, color: VIOLET }, paragraph: { outlineLevel: 0 } }] },
  sections: [{ properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 1000, bottom: 1000, left: 1000, right: 1000 } } }, children: ch }],
});
Packer.toBuffer(doc).then(buf => { fs.writeFileSync(path.join(DOCS, 'voix-mission-sophie.docx'), buf); console.log('écrit : voix-mission-sophie.docx'); });
