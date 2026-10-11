// Preparation guide + printables for Mission Sophie.
const fs = require('fs');
const path = require('path');
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell, WidthType, ShadingType, HeadingLevel,
  AlignmentType, BorderStyle, PageBreak, VerticalAlign, ImageRun,
} = require('docx');

const ROOT = path.resolve(__dirname, '..', '..', '..');
const DOCS = path.join(ROOT, 'documents');
fs.mkdirSync(DOCS, { recursive: true });
const RAW = JSON.parse(fs.readFileSync(path.join(ROOT, 'app/android/src/main/assets/game_content.json'), 'utf8'));
const V = RAW.variants || {};
const rain = V.pluie || {}, sun = V.beau || {};
const C = JSON.parse(JSON.stringify(RAW).replace(/\{\{(\w+)\}\}/g, (m, k) => (rain[k] || k).replace(/"/g, '\\"')));
const powers = C.sequence.map(id => C.powers.find(p => p.id === id));
const bonus = C.powers.filter(p => p.bonusOnly);

const VIOLET = '4B2E83', HEAD = 'EDE7F6', NOTE = 'FFF4CC';
const W = 9638;
const b = { style: BorderStyle.SINGLE, size: 4, color: 'A0A0A0' };
const borders = { top: b, bottom: b, left: b, right: b };
const t = (text, o = {}) => new TextRun({ text, ...o });
const P = (runs, o = {}) => new Paragraph({ children: (Array.isArray(runs) ? runs : [runs]).map(x => typeof x === 'string' ? t(x) : x), spacing: { after: 80 }, ...o });
const B = s => t(s, { bold: true });
const H1 = s => new Paragraph({ heading: HeadingLevel.HEADING_1, children: [t(s)], spacing: { before: 260, after: 120 } });
const H2 = s => new Paragraph({ heading: HeadingLevel.HEADING_2, children: [t(s)], spacing: { before: 180, after: 80 } });
const box = (runs) => P([t('☐  ', { size: 22 }), ...(Array.isArray(runs) ? runs : [runs]).map(x => typeof x === 'string' ? t(x) : x)],
  { indent: { left: 360, hanging: 360 }, spacing: { after: 50 } });
const pageBreak = () => new Paragraph({ children: [new PageBreak()] });

function cell(v, width, fill, o = {}) {
  const kids = (Array.isArray(v) ? v : [v]).map(x => x instanceof Paragraph ? x
    : new Paragraph({ alignment: o.align, children: [typeof x === 'string' ? t(x, { size: o.size || 18, bold: o.bold }) : x], spacing: { after: 0 } }));
  return new TableCell({ borders, width: { size: width, type: WidthType.DXA }, verticalAlign: o.valign,
    shading: fill ? { fill, type: ShadingType.CLEAR, color: 'auto' } : undefined,
    margins: { top: o.pad ?? 50, bottom: o.pad ?? 50, left: 90, right: 90 }, children: kids });
}
function table(widths, header, rows, o = {}) {
  const trs = [];
  if (header) trs.push(new TableRow({ tableHeader: true, children: header.map((h, i) => cell(h, widths[i], HEAD, { bold: true })) }));
  rows.forEach(r => trs.push(new TableRow({ height: o.height ? { value: o.height, rule: 'atLeast' } : undefined,
    children: r.map((v, i) => cell(v, widths[i], undefined, o)) })));
  return new Table({ width: { size: widths.reduce((a, c) => a + c, 0), type: WidthType.DXA }, columnWidths: widths, rows: trs });
}
function doc(children) {
  return new Document({
    styles: {
      default: { document: { run: { font: 'Calibri', size: 20 } } },
      paragraphStyles: [
        { id: 'Heading1', name: 'Heading 1', basedOn: 'Normal', next: 'Normal', quickFormat: true, run: { size: 30, bold: true, color: VIOLET }, paragraph: { outlineLevel: 0 } },
        { id: 'Heading2', name: 'Heading 2', basedOn: 'Normal', next: 'Normal', quickFormat: true, run: { size: 24, bold: true, color: '5A3E9B' }, paragraph: { outlineLevel: 1 } },
      ],
    },
    sections: [{ properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 1134, bottom: 1134, left: 1134, right: 1134 } } }, children }],
  });
}
const title = (a, s) => [new Paragraph({ children: [t(a, { size: 44, bold: true, color: VIOLET })], spacing: { after: 60 } }),
  new Paragraph({ children: [t(s, { size: 22, color: '666666' })], spacing: { after: 200 } })];

// Equipment per trial
const MAT = {
  telepathe: 'Rien (le MJ chuchote un des messages imprimés)',
  rafaleur: 'Le ballon, éventails ou cartons, ruban pour la ligne d\'arrivée',
  polyglotte: 'Roue elfique imprimée en 3D, carte du message codé',
  phaseur: 'Pelote de laine, scotch, 6 à 10 clochettes, un couloir',
  empathe: '8 cartes émotions (imprimables)',
  hydrokinesiste: 'Sous la pergola : seau d\'eau, verre marqué d\'un trait au feutre, 3-4 éponges, cuillères, serviettes',
  technopathe: 'Feuille des ampoules (imprimables)',
  eclipseur: 'Rien (le MJ joue le garde) : cuisine ☔ ou allée du garage ☀',
  fluctuateur: '1 œuf cru (PLUME écrit dessous au feutre indélébile), grand verre ou bocal d\'eau, paquet de sel, cuillère',
  chargeur: '3-4 ballons de baudruche gonflés, papier de soie, lettres É C L A I R découpées (gabarit imprimable)',
  flasheur: 'Lampe torche, carton fin percé du mot LUMIÈRE (gabarit imprimable), aiguille ou pique, pièce sombre',
  hypnotiseur: 'Rien',
  givreur: 'Boîte congelée la veille avec le code GIVRE en sachet étanche, bol d\'eau tiède, sel',
  invocateur: 'Boîte à récup (carton, papier doré, ciseaux, scotch, gommettes…) pour fabriquer une couronne, minuteur',
  instillateur: 'Rien',
  teleporteur: 'Plan quadrillé de la maison (chaudière en C4), carte scellée CRISTAL (imprimables) posée à la chaudière',
  tenebreux: 'Drap tendu, lampe, et dans l\'ordre : cuillère, arrosoir, peigne, éponge',
  vociferateur: 'Rien (micro autorisé dans l\'appli)',
  telekinesie: '[objet], ficelles, poulie ou bandeau',
  guerison: '[objet à réparer] (puzzle, guirlande…)',
};

// ================================================================ PREPARATION GUIDE
function buildPrep() {
  const ch = [...title('Mission Sophie : la préparation', 'Tout ce qu\'il faut fabriquer, imprimer, rassembler et installer')];
  ch.push(P([t('Cochez au fur et à mesure. Les épreuves jouées dépendent des talents distribués : préparez en priorité celles des invitées confirmées (réponses avant le 5/10).', { italics: true, color: '666666' })]));

  ch.push(H1('1. Le calendrier'));
  ch.push(table([2200, 7438], ['Quand', 'Quoi'], [
    ['Dès maintenant', 'Fabriquer : pendentifs 3D en goutte (tag 1,1 mm), cartes gravées, roue elfique 3D, badges. Commander les tags NFC s\'il en manque.'],
    ['Quand les invitées sont connues (5/10)', 'Attribuer un talent à chacune. Graver sa carte de personnage. Lier les tags dans l\'appli. Préparer le matériel des épreuves de ces talents.'],
    ['La semaine d\'avant', 'Répétition avec l\'appli et les vrais tags (voir section 6). Tester le cri-mètre, la lecture NFC, la recopie sur la télé.'],
    ['La veille', 'Congeler le bloc de glace (Givreuse). Imprimer la fiche MJ. Charger le téléphone. Choisir la météo dans l\'appli.'],
    ['Le jour J, 1 h avant', 'Installer les pièges et les énigmes (section 5). Ranger les cartes-souvenirs dans l\'ordre. Téléphone en « Ne pas déranger ».'],
  ]));

  ch.push(H1('2. À fabriquer'));
  ch.push(table([2600, 1300, 3100, 2638], ['Objet', 'Quantité', 'Fichiers', 'Remarques'], [
    ['Pendentifs 3D', '1 par invitée (7 à 10)', 'stl-amulettes/ (corps + symbole + couvercle)', 'Forme goutte (choisie). 2 couleurs. Tag de 1,1 mm dans le dos.'],
    ['Tags NFC', '1 par pendentif + 2', '—', 'Porte-clés goutte 27 × 54 mm. Les 2 en plus : faux badge et Cœur.'],
    ['Faux badge des Invisibles', '1', 'stl-amulettes/faux_givreur-*', 'Une fausse amulette : flocon à 7 branches, dont une tordue. Couleur légèrement différente.'],
    ['Badge du Cœur de Sophie', '1', '—', 'Un tag décoré d\'un cœur, dans la boîte du Cœur.'],
    ['Boîte du Cœur', '1', '—', 'Une jolie boîte : le badge + le souvenir de chacune (surprise).'],
    ['Cartes de personnage', '1 par invitée', 'cartes-personnages-svg/ (recto + verso)', 'Uniquement les talents distribués. Elles servent aussi de joker.'],
    ['Cartes-souvenirs n°1 à 10', '10', 'cartes-souvenirs-svg/ (10 rectos + 1 verso commun)', 'Une pile rangée dans l\'ordre. Gravure laser comme les cartes de personnage.'],
    ['Roue elfique (Polyglotte)', '1', 'stl-roue-elfique/ (roue-elfique.scad)', 'Impression 3D : le petit disque se clipse sur l’axe du grand (réglages validés à l’essai). Lettres en 2e couleur (fichiers _lettres) ou gravées en creux.'],
  ]));
  ch.push(P([t('Laser : les fichiers ont un calque rouge (découpe ou repère), des calques de gravure, et un calque de textes à convertir en tracés.', { italics: true, color: '666666', size: 18 })]));

  ch.push(H1('3. À imprimer'));
  ch.push(box([B('fiche-mj-mission-sophie.pdf'), ' : la fiche du Maître du jeu (1 page, à garder dans la poche).']));
  ch.push(box([B('imprimables-mission-sophie.docx'), ' : plan quadrillé, feuille des ampoules, message codé, carte scellée CRISTAL, gabarit LUMIÈRE, lettres ÉCLAIR, cartes émotions, messages chuchotés, étiquette GIVRE, panneaux « Barrière des Invisibles ».']));
  ch.push(box([B('deroule-mission-sophie.docx'), ' : le déroulé complet (pour relire avant).']));

  ch.push(H1('4. À rassembler, épreuve par épreuve'));
  ch.push(P('Seulement pour les talents distribués (et les bonus si vous comptez les jouer).'));
  ch.push(table([500, 2000, 7138], ['✓', 'Talent', 'Matériel'],
    [...powers, ...bonus].map(p => ['☐', p.talent + (p.bonusOnly ? ' (bonus)' : ''), MAT[p.id] || ''])));
  ch.push(H2('Pour toute la partie'));
  ['Le téléphone NFC chargé + chargeur, et la télé pour la recopie d\'écran.',
    'Ruban de signalisation pour les zones interdites (« barrière des Invisibles ») + panneaux imprimés.',
    'Un sac pour le MJ : fiche MJ, cartes-souvenirs dans l\'ordre, codes, stylo.',
    'Le souvenir de chacune (dans la boîte du Cœur).',
  ].forEach(x => ch.push(box(x)));

  ch.push(H1('5. Les énigmes et les pièges à installer'));
  ch.push(P('Le jour J, avant l\'arrivée des invitées. Tout doit être visible sans rien ouvrir ni déplacer.'));
  ch.push(table([2400, 3000, 2600, 1638], ['Quoi', '☔ S\'il pleut', '☀ S\'il fait beau', 'Quand'], [
    ['Faux badge des Invisibles (piège)', 'Sur la mezzanine', 'Sur le trampoline', '1 h avant'],
    ['Boîte du Cœur (badge + souvenirs)', 'Dans la cave', 'Près de l\'étendage à linge', '1 h avant'],
    ['Carte scellée CRISTAL (Téléporteuse)', 'À la chaudière (case C4 du plan)', 'À la chaudière (case C4)', '1 h avant'],
    ['Zone des gardes (Éclipseuse)', 'La cuisine', 'L\'allée du garage', 'Au moment'],
    ['Bassine, verre, éponges (Hydrokinésiste)', 'Sous la pergola', 'Sous la pergola', '1 h avant'],
    ['Mur de fils et clochettes (Phaseuse)', 'Un couloir', 'Un couloir', '1 h avant'],
    ['Drap et lampe (Ténébreuse)', 'Une pièce assombrie', 'Une pièce assombrie', '1 h avant'],
    ['Pièce sombre + lampe (Flasheuse)', 'À choisir', 'À choisir', '1 h avant'],
    ['Ligne d\'arrivée (Rafaleuse)', 'À choisir', 'À choisir', 'Au moment'],
    ['Bloc de glace (Givreuse)', 'Congélateur', 'Congélateur', 'La veille'],
    ['Œuf marqué PLUME (Fluctuatrice)', 'Frigo', 'Frigo', 'La veille'],
    ['Rubans « barrière des Invisibles »', 'Zones interdites', 'Zones interdites', '1 h avant'],
  ]));

  ch.push(H1('6. Le téléphone et l\'appli'));
  ['Installer la dernière version de l\'appli.',
    'Admin › Liaison des tags : un tag par pendentif distribué, plus le faux badge et le Cœur. Admin › Test de lecture pour chacun.',
    'Admin › Sauvegarde : exporter les liaisons (copie de secours).',
    'Admin › Réglages : choisir la météo (☔ / ☀), désactiver « Muet » et le simulateur.',
    'Admin › Prévisualisation : tester le cri-mètre en criant à plusieurs.',
    'Admin › Contenu : importer les voix, les images et la vidéo si elles sont prêtes.',
    'Recopie de l\'écran sur la télé testée, son compris.',
    'Admin › Partie : réinitialiser juste avant de commencer.',
  ].forEach(x => ch.push(box(x)));

  ch.push(H1('7. La répétition (dès que le téléphone est là)'));
  ch.push(P('Dans cet ordre, environ 45 minutes. Chaque étape vérifie quelque chose qui n\'a été testé qu\'au simulateur.'));
  const steps = [
    ['Installer la dernière version', 'L\'accueil affiche « Les Pouvoirs volés », le portrait de Sophie, « NFC prêt ».'],
    ['Admin › Réglages', 'Désactiver « Muet ». Choisir la météo. Laisser le simulateur actif pour l\'instant.'],
    ['Admin › Liaison des tags', 'Lier 3 pendentifs (dont la Vocifératrice), le faux badge et le Cœur. Test de lecture de chacun.'],
    ['Admin › Prévisualisation', 'Tester le cri-mètre à plusieurs : la jauge doit monter, 3 s au-dessus du trait valident. Sinon, noter pour baisser le seuil.'],
    ['Recopie sur la télé', 'Lancer Smart View / Chromecast : image et son sur la télé ?'],
    ['Jeu › Nouvelle partie', 'Introduction, puis rassemblement : poser les 3 pendentifs, « Toutes les Gardiennes sont là ! ».'],
    ['Épreuve 1 en vrai', '1er scan → consigne ; 2e scan trop tôt → « Déjà ? » ; après 30 s → clavier ; un code faux, puis le bon → level-up → « carte-souvenir n°1 ». Vérifier que l\'appel suivant attend la fin des dialogues.'],
    ['Le piège', 'Scanner le faux badge → corruption → écho des pouvoirs (3 manches, avec les vrais pendentifs) → pouvoir restauré.'],
    ['Épreuve de la Vocifératrice', 'Cri-mètre en situation réelle.'],
    ['L\'écho final', 'Regarder l\'écho, poser les pendentifs dans le désordre (blocage 20 s), puis dans le bon ordre → lieu du Cœur → scan du Cœur → finale.'],
    ['Les bonus', 'Bouton « Épreuves bonus », en ouvrir une, « Entrer le code ».'],
    ['Le menu MJ', 'Appui 3 s sur le portrait : passer une étape, envoyer un indice, étape précédente.'],
    ['Reprise', 'Fermer l\'appli en pleine épreuve, la rouvrir : « Reprendre la partie » repart au bon endroit.'],
    ['Pour finir', 'Admin › Sauvegarde : exporter les liaisons. Réinitialiser la partie. Désactiver le simulateur (ou le jour J).'],
  ];
  ch.push(table([500, 2600, 6538], ['✓', 'Étape', 'À vérifier'], steps.map(([a, b]) => ['☐', a, b])));
  ch.push(P([t('Noter tout ce qui gêne (textes trop longs, délais, volume) : c\'est réglable sans recompiler dans le contenu ou dans Admin › Réglages.', { italics: true, color: '666666' })]));
  return doc(ch);
}

// ================================================================ PRINTABLES
// 5 x 7 dot font for the LUMIÈRE template (1 = hole to pierce).
const DOTS = {
  L: ['10000', '10000', '10000', '10000', '10000', '10000', '11111'],
  U: ['10001', '10001', '10001', '10001', '10001', '10001', '01110'],
  M: ['10001', '11011', '10101', '10101', '10001', '10001', '10001'],
  I: ['01110', '00100', '00100', '00100', '00100', '00100', '01110'],
  E: ['11111', '10000', '10000', '11110', '10000', '10000', '11111'],
  R: ['11110', '10001', '10001', '11110', '10100', '10010', '10001'],
};

function buildPrint() {
  const ch = [];
  const pageTitle = (a, s) => { ch.push(new Paragraph({ children: [t(a, { size: 40, bold: true, color: VIOLET })], spacing: { after: 60 } }));
    if (s) ch.push(new Paragraph({ children: [t(s, { size: 20, color: '666666', italics: true })], spacing: { after: 200 } })); };
  const big = (s, size, o = {}) => new Paragraph({ alignment: AlignmentType.CENTER, children: [t(s, { size, bold: true, ...o })], spacing: { after: 120 } });

  // 1. Grid plan
  pageTitle('Téléporteuse : le plan quadrillé', 'Dessiner le plan de la maison dans la grille. La chaudière doit se trouver dans la case C4 (c\'est là qu\'on pose la carte scellée CRISTAL).');
  const cols = ['', 'A', 'B', 'C', 'D', 'E', 'F'];
  ch.push(table([640, ...Array(6).fill(1500)], cols, [1, 2, 3, 4, 5].map(r => [String(r), '', '', '', '', '', '']), { height: 2200, size: 28, bold: true }));
  ch.push(pageBreak());

  // 2. Technopathe
  pageTitle('Technopathe : la machine des Invisibles', 'Additionnez les valeurs des ampoules allumées (●) sur chaque rangée. Le total donne une lettre : A = 1, B = 2…');
  const bulbs = { P: '10000', U: '10101', C: '00011', E: '00101' };
  ch.push(table([1600, ...Array(5).fill(1400), 1038], ['Rangée', '16', '8', '4', '2', '1', 'Lettre'],
    Object.values(bulbs).map((code, i) => [`${i + 1}`, ...code.split('').map(c => t(c === '1' ? '●' : '○', { size: 56 })), '']),
    { height: 1200, align: AlignmentType.CENTER, valign: VerticalAlign.CENTER, size: 28 }));
  ch.push(P(''));
  const abc = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('');
  ch.push(table(Array(13).fill(741), null, [abc.slice(0, 13).map((c, i) => `${c} = ${i + 1}`), abc.slice(13).map((c, i) => `${c} = ${i + 14}`)], { align: AlignmentType.CENTER, size: 20 }));
  ch.push(pageBreak());

  // 3. Polyglotte message
  pageTitle('Polyglotte : le message en langue secrète', 'À poser près de la roue elfique.');
  ch.push(P(''));
  ch.push(big('OH PRW HVW RUDJH', 96, { font: 'Cinzel' }));
  ch.push(P(''));
  ch.push(big('Un secret de Sophie : mes messages commencent toujours par LE…', 32));
  ch.push(pageBreak());

  // 4. Sealed card
  pageTitle('Téléporteuse : la carte scellée', 'Découper, plier en deux, fermer avec un sceau (autocollant, cire…) et poser à la chaudière.');
  ch.push(table([W], null, [[[
    big('Sophie s\'est téléportée ici !', 40), big('✦', 60), big('Le mot secret est :', 32), big('CRISTAL', 96, { color: VIOLET })]]], { pad: 400 }));
  ch.push(pageBreak());

  // 5. LUMIÈRE template
  pageTitle('Flasheuse : le gabarit LUMIÈRE', 'Scotcher la feuille sur un carton fin, percer chaque point avec une aiguille. Dans le noir, la lampe placée côté imprimé projette le mot au mur. Tester avant !');
  const img = fs.readFileSync(path.join(__dirname, 'lumiere.png'));
  ch.push(new Paragraph({ alignment: AlignmentType.CENTER, children: [new ImageRun({ type: 'png', data: img, transformation: { width: 600, height: Math.round(600 * 1260 / 1620) } })] }));
  ch.push(pageBreak());

  // 6. ÉCLAIR letters
  pageTitle('Chargeuse : les lettres ÉCLAIR', 'Gabarit à décalquer sur du papier de soie. Découper chaque lettre en petits morceaux légers (les ballons chargés doivent pouvoir les soulever).');
  ch.push(table(Array(3).fill(Math.floor(W / 3)), null, [['É', 'C', 'L'], ['A', 'I', 'R']].map(r => r.map(c => t(c, { size: 220, bold: true }))),
    { align: AlignmentType.CENTER, valign: VerticalAlign.CENTER, height: 4200 }));
  ch.push(pageBreak());

  // 7. Emotion cards
  pageTitle('Empathe : les cartes émotions', 'Découper les 8 cartes. Chaque amie en tire une et la mime sans parler.');
  const emo = [['😊', 'Joie'], ['😨', 'Peur'], ['😠', 'Colère'], ['😢', 'Tristesse'], ['😲', 'Surprise'], ['🤢', 'Dégoût'], ['😎', 'Fierté'], ['😳', 'Timidité']];
  const emoRows = [];
  for (let i = 0; i < 8; i += 2) emoRows.push(emo.slice(i, i + 2).map(([e, n]) => [big(e, 90), big(n, 48)]));
  ch.push(table([W / 2, W / 2], null, emoRows, { valign: VerticalAlign.CENTER, height: 2900 }));
  ch.push(pageBreak());

  // 8. Whispered messages + labels
  pageTitle('Télépathe : les messages à chuchoter', 'Le MJ en choisit un (le plus court si c\'est difficile).');
  ['« Le mot secret de Sophie est ÉTINCELLE. »',
    '« Les Invisibles ont volé la clé : murmurez ÉTINCELLE au Transmetteur. »',
    '« Sophie vous confie son mot magique : ÉTINCELLE. »',
  ].forEach(m => { ch.push(table([W], null, [[t(m, { size: 32 })]], { pad: 300 })); ch.push(P('')); });
  ch.push(H2('Givreuse : l\'étiquette à glisser dans le sachet étanche'));
  ch.push(table([4000], null, [[t('GIVRE', { size: 72, bold: true })]], { pad: 200, align: AlignmentType.CENTER }));
  ch.push(pageBreak());

  // 9. Barrier signs
  for (let i = 0; i < 2; i++) {
    ch.push(P(''));
    ch.push(table([W], null, [[[big('⛔', 110), big('BARRIÈRE DES INVISIBLES', 64, { color: VIOLET }), big('Zone interdite aux Gardiennes', 36)]]], { pad: 500 }));
    ch.push(P(''));
  }
  return doc(ch);
}

(async () => {
  for (const [f, build] of [['preparation-mission-sophie.docx', buildPrep], ['imprimables-mission-sophie.docx', buildPrint]]) {
    try { fs.writeFileSync(path.join(DOCS, f), await Packer.toBuffer(build())); console.log('écrit : ' + f); }
    catch (e) { console.log('IMPOSSIBLE : ' + f + ' ' + e.message); }
  }
})();
