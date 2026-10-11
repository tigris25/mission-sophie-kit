// Generates textes-mission-sophie.docx (from the app's game_content.json) and deroule-mission-sophie.docx.
const fs = require('fs');
const path = require('path');
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell, WidthType, ShadingType,
  HeadingLevel, AlignmentType, LevelFormat, BorderStyle,
} = require('docx');

const ROOT = path.resolve(__dirname, '..', '..', '..');
const DOCS = path.join(ROOT, 'documents');
fs.mkdirSync(DOCS, { recursive: true });
const RAW = JSON.parse(fs.readFileSync(path.join(ROOT, 'app/android/src/main/assets/game_content.json'), 'utf8'));
// Weather versions of the places: texts are shown with {{key}} replaced by "rainy / sunny".
const VARIANTS = RAW.variants || {};
const VIDS = Object.keys(VARIANTS);
const both = key => VIDS.map(v => VARIANTS[v][key]).filter((x, i, a) => a.indexOf(x) === i).join(' / ');
const C = JSON.parse(JSON.stringify(RAW).replace(/\{\{(\w+)\}\}/g, (m, k) => both(k).replace(/"/g, '\\"')));

const W = 9638; // content width (A4, 2 cm margins)
const SOPHIE = 'E8F1FF', INVIS = 'F3E6F5', HEAD = 'EDE7F6', SCREEN = 'F5F5F5', NOTE = 'FFF8E1';
const border = { style: BorderStyle.SINGLE, size: 4, color: 'A0A0A0' };
const borders = { top: border, bottom: border, left: border, right: border };

// ---------------------------------------------------------------- helpers
const t = (text, o = {}) => new TextRun({ text, ...o });
function P(content, o = {}) {
  const runs = (Array.isArray(content) ? content : [content]).map(x => typeof x === 'string' ? t(x) : x);
  return new Paragraph({ children: runs, spacing: { after: 80 }, ...o });
}
const H1 = s => new Paragraph({ heading: HeadingLevel.HEADING_1, children: [t(s)], spacing: { before: 280, after: 120 } });
const H2 = s => new Paragraph({ heading: HeadingLevel.HEADING_2, children: [t(s)], spacing: { before: 200, after: 80 } });
const bullet = (content) => P(content, { numbering: { reference: 'bul', level: 0 }, spacing: { after: 40 } });
const num = (content, ref = 'num') => P(content, { numbering: { reference: ref, level: 0 }, spacing: { after: 40 } });
const bold = s => t(s, { bold: true });

function cell(text, width, fill, o = {}) {
  const paras = (Array.isArray(text) ? text : [text]).map(x =>
    x instanceof Paragraph ? x : new Paragraph({ children: typeof x === 'string' ? [t(x, { size: o.size || 18, bold: o.bold, color: o.color })] : [x], spacing: { after: 0 } }));
  return new TableCell({
    borders, width: { size: width, type: WidthType.DXA },
    shading: fill ? { fill, type: ShadingType.CLEAR, color: 'auto' } : undefined,
    margins: { top: 50, bottom: 50, left: 90, right: 90 },
    children: paras,
  });
}
function table(widths, header, rows, fills = []) {
  const trs = [];
  if (header) trs.push(new TableRow({ tableHeader: true, children: header.map((h, i) => cell(h, widths[i], HEAD, { bold: true })) }));
  rows.forEach((r, ri) => trs.push(new TableRow({ children: r.map((v, i) => cell(v, widths[i], fills[ri])) })));
  return new Table({ width: { size: widths.reduce((a, b) => a + b, 0), type: WidthType.DXA }, columnWidths: widths, rows: trs });
}
const spacer = () => new Paragraph({ children: [], spacing: { after: 60 } });

function doc(children) {
  return new Document({
    styles: {
      default: { document: { run: { font: 'Calibri', size: 20 } } },
      paragraphStyles: [
        { id: 'Heading1', name: 'Heading 1', basedOn: 'Normal', next: 'Normal', quickFormat: true,
          run: { size: 30, bold: true, color: '4B2E83', font: 'Calibri' }, paragraph: { outlineLevel: 0 } },
        { id: 'Heading2', name: 'Heading 2', basedOn: 'Normal', next: 'Normal', quickFormat: true,
          run: { size: 24, bold: true, color: '5A3E9B', font: 'Calibri' }, paragraph: { outlineLevel: 1 } },
      ],
    },
    numbering: { config: [
      { reference: 'bul', levels: [{ level: 0, format: LevelFormat.BULLET, text: '•', alignment: AlignmentType.LEFT,
        style: { paragraph: { indent: { left: 540, hanging: 270 } } } }] },
      ...['num', 'num2', 'num3', 'num4'].map(r => ({ reference: r, levels: [{ level: 0, format: LevelFormat.DECIMAL, text: '%1.',
        alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 540, hanging: 300 } } } }] })),
    ] },
    sections: [{ properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 1134, bottom: 1134, left: 1134, right: 1134 } } }, children }],
  });
}
const title = (a, b) => [
  new Paragraph({ children: [t(a, { size: 44, bold: true, color: '4B2E83' })], spacing: { after: 60 } }),
  new Paragraph({ children: [t(b, { size: 22, color: '666666' })], spacing: { after: 200 } }),
];

// ---------------------------------------------------------------- voice ids (same rules as the app, Lines.kt)
const lineText = l => typeof l === 'string' ? l : l.text;
const lineSpeaker = (l, def) => typeof l === 'string' ? def : (l.speaker || def);
const defaultSpeaker = key => ['heartTooEarly', 'tooFastSecondScan', 'codeOk', 'idleSophie'].includes(key) ? 'sophie' : 'invisibles';
const article = w => /^[AEIOUYHÉÈÊÂÎÔ]/i.test(w) ? "L'" : 'La ';
const who = s => s === 'sophie' ? 'Sophie' : s === 'invisibles' ? 'Invisibles' : 'Écran';
const fillOf = s => s === 'sophie' ? SOPHIE : s === 'invisibles' ? INVIS : SCREEN;

// Rows: [moment, speaker, text, voiceId]
function textTable(rows) {
  const widths = [2100, 1100, 5038, 1400];
  return table(widths, ['Moment', 'Qui', 'Texte', 'Voix (fichier)'],
    rows.map(r => [r[0], who(r[1]), r[2], r[3] ? new TextRun({ text: r[3], size: 14, color: '777777' }) : '']),
    rows.map(r => fillOf(r[1])));
}

// ================================================================ TEXTES
function buildTextes() {
  const powers = Object.fromEntries(C.powers.map(p => [p.id, p]));
  const main = C.sequence.map(id => powers[id]);
  const bonus = C.powers.filter(p => p.bonusOnly);
  const ch = [];
  ch.push(...title('Mission Sophie : les textes', `Sophie et les Invisibles · version 2 (modifiable) · ${C.powers.length - bonus.length} talents + ${bonus.length} bonus · identique au contenu de l'appli (game_content.json v${C.version})`));

  ch.push(H1('Comment lire ce document'));
  ch.push(bullet([bold('Sophie'), ' (fond bleu) : chaleureuse, courageuse, un peu drôle. Elle dit « vous » à tout le groupe (les Gardiennes) et « tu » à la spécialiste.']));
  ch.push(bullet([bold('Les Invisibles'), ' (fond mauve) : un groupe théâtral, prétentieux et moqueur, jamais effrayant. Ils parlent en « nous » et « on », se croient très malins et ratent leurs menaces.']));
  ch.push(bullet([bold('Écran'), ' (fond gris) : texte affiché sans être dit.']));
  ch.push(bullet([bold('Voix (fichier)'), ' : nom du fichier audio à produire pour ce texte (ex. call_telepathe.mp3). Voir la section 7.']));
  ch.push(bullet([bold('Crochets'), ' : les passages entre [crochets] sont à remplacer (lieu, objet, prénom…).']));
  ch.push(bullet([bold('Listes'), ' : quand une situation a plusieurs textes numérotés, l\'appli en tire un au hasard, sans répéter le même deux fois de suite.']));
  ch.push(bullet('Les textes sont originaux : ils n\'utilisent aucune réplique des romans, seulement l\'univers.'));

  // 1. Intro + gathering
  ch.push(H1('1. Acte 0 : l\'appel de Sophie et le rassemblement'));
  const intro = C.intro.map((l, i) => [`Intro, écran ${i + 1}`, lineSpeaker(l, 'sophie'), lineText(l), `intro_${i + 1}`]);
  intro.push(['Bouton de départ', 'screen', C.ui.startButton || 'Lancer la mission', '']);
  intro.push(['Rassemblement', 'sophie', C.gathering.introText, 'gather_intro']);
  intro.push(['Chaque pendentif posé', 'sophie', C.gathering.template.replace('{la}', 'La ').replace('{talent}', '[Talent]') + ' (ex. « L\'Invocatrice a répondu à l\'appel ! »)', 'gather_<talent>']);
  intro.push(['Bouton de fin', 'screen', 'Toutes les Gardiennes sont là !', '']);
  intro.push(['Départ de la mission', 'sophie', C.gathering.doneText, 'gather_done']);
  ch.push(textTable(intro));

  // 2. Trials
  ch.push(H1(`2. Les ${main.length} épreuves des talents`));
  ch.push(P('Seules les épreuves des talents présents au rassemblement sont jouées, dans l\'ordre ci-dessous. Pour chaque épreuve : l\'appel (avant le 1er scan), le titre et la consigne (après le 1er scan), l\'indice (après 3 erreurs de code), la réussite et la réaction des Invisibles.'));
  const trialRows = p => {
    const r = [['Appel', 'sophie', p.callText, `call_${p.id}`], ['Titre', 'screen', p.title, ''],
      ['Consigne', 'sophie', p.consigne, `consigne_${p.id}`], ['Indice', 'sophie', p.hint, `hint_${p.id}`],
      ['Réussite', 'sophie', p.successText, `success_${p.id}`]];
    if (p.reactText) r.push(['Réaction', 'invisibles', p.reactText, `react_${p.id}`]);
    return r;
  };
  main.forEach((p, i) => {
    const code = p.kind === 'shout' ? 'cri-mètre (pas de code)' : `code ${p.answers.join(' / ')}`;
    ch.push(H2(`Épreuve ${i + 1} : ${p.name} (${p.talent}) · ${code}`));
    ch.push(textTable(trialRows(p)));
  });

  ch.push(H1('3. Épreuves bonus (après la finale)'));
  ch.push(P('Ces épreuves n\'ont pas de pendentif. Elles se jouent après la finale, avec les épreuves des talents qui n\'étaient pas présents.'));
  bonus.forEach(p => { ch.push(H2(`Bonus : ${p.name} (${p.talent}) · code ${p.answers.join(' / ')}`)); ch.push(textTable(trialRows(p))); });
  ch.push(textTable([['Ouverture des bonus', 'sophie', C.bonus.introText, 'bonus_intro']]));

  // 4. Game messages
  ch.push(H1('4. Messages de jeu'));
  const list = (key, label, arr) => arr.map((l, i) => [`${label} ${i + 1}`, lineSpeaker(l, defaultSpeaker(key)), lineText(l), `${key}_${i + 1}`]);
  const M = C.messages;
  ch.push(H2('Carte-souvenir (après chaque réussite)'));
  ch.push(textTable([['Après le level-up', 'sophie', C.cardText.replace('{n}', 'X') + ' (X = numéro de la carte)', 'card_secret']]));
  ch.push(H2('Réactions génériques des Invisibles après une réussite'));
  ch.push(P('Utilisées si une épreuve n\'a pas de réaction propre.'));
  ch.push(textTable(list('invisiblesAfterSuccess', 'Réaction', M.invisiblesAfterSuccess)));
  ch.push(H2('Attente : Sophie se plaint (après plusieurs minutes)'));
  ch.push(textTable(list('idleSophie', 'Plainte', M.idleSophie)));
  ch.push(H2('Attente : les Invisibles se moquent'));
  ch.push(textTable(list('idleInvisibles', 'Moquerie', M.idleInvisibles)));
  ch.push(H2('Scans inattendus'));
  ch.push(textTable([...list('wrongPendant', 'Mauvais pendentif', M.wrongPendant), ...list('unknownTag', 'Tag inconnu', M.unknownTag),
    ...list('fakeBadgeTooEarly', 'Faux badge trop tôt', M.fakeBadgeTooEarly), ...list('heartTooEarly', 'Cœur trop tôt', M.heartTooEarly)]));
  ch.push(H2('Saisie du code'));
  ch.push(P("Après un code faux, l'appli réagit au plus une fois toutes les 20 secondes, en alternant une moquerie des Invisibles et un encouragement de Sophie."));
  ch.push(textTable([...list('tooFastSecondScan', 'Second scan trop rapide', M.tooFastSecondScan), ...list('wrongCode', 'Code faux', M.wrongCode),
    ...list('wrongCodeSophie', 'Code faux, encouragement', M.wrongCodeSophie || []),
    ...list('codeOk', 'Code juste', M.codeOk)]));

  // 5. Trap
  const T = C.trap;
  ch.push(H1('5. Le piège et l\'écho des pouvoirs'));
  const trap = [['Annonce', 'sophie', T.announceText, 'trap_announce']];
  if (T.teaserText) trap.push(['Annonce, teaser', 'invisibles', T.teaserText, 'trap_teaser']);
  trap.push(['Corruption', 'invisibles', T.corruptText, 'trap_corrupt_inv']);
  if (T.corruptSophieText) trap.push(['Corruption', 'sophie', T.corruptSophieText, 'trap_corrupt_sophie']);
  trap.push(['Explication de l\'écho', 'sophie', T.repairIntroText, 'trap_repair_intro']);
  T.roundIntroTexts.forEach((x, i) => trap.push([`Manche ${i + 1}`, 'sophie', x, `trap_round_${i + 1}`]));
  trap.push(['Bouton de relecture', 'screen', C.ui.replayButton || 'Écouter à nouveau', '']);
  T.wrongStepTexts.forEach((x, i) => trap.push([`Erreur ${i + 1}`, 'invisibles', x, `trap_wrong_${i + 1}`]));
  if (T.helpText) trap.push(['Coup de pouce', 'sophie', T.helpText, 'trap_help']);
  T.roundSuccessTexts.forEach((x, i) => trap.push([`Manche ${i + 1} réussie`, 'sophie', x, `trap_round_ok_${i + 1}`]));
  trap.push(['Pouvoir réparé', 'sophie', T.restoredText, 'trap_restored_sophie']);
  if (T.restoredInvText) trap.push(['Pouvoir réparé', 'invisibles', T.restoredInvText, 'trap_restored_inv']);
  ch.push(textTable(trap));

  // 6. Final
  const F = C.final;
  ch.push(H1('6. L\'énigme finale et le triomphe'));
  const fin = [];
  if (F.announceText) fin.push(['Annonce', 'sophie', F.announceText, 'final_announce']);
  if (F.consigneText) fin.push(['Consigne', 'sophie', F.consigneText, 'final_consigne']);
  fin.push(['Pastille à chaque pose', 'screen', '(pas de texte, un son neutre et une pastille qui s\'allume)', '']);
  F.wrongOrderTexts.forEach((x, i) => fin.push([`Mauvais ordre ${i + 1}`, 'invisibles', x, `final_wrong_${i + 1}`]));
  if (F.lockoutText) fin.push(['Blocage', 'sophie', F.lockoutText, 'final_lockout']);
  fin.push(['Écho des pouvoirs', 'screen', '(les cristaux se rallument dans l\'ordre où les pouvoirs ont été réveillés ; bouton « Revoir l\'écho »)', '']);
  fin.push(['Indice (après 3 échecs)', 'sophie', F.hint, 'final_hint']);
  if (F.mode === 'words') (F.words || []).forEach(w => fin.push([`Indice, mot ${w.word}`, 'sophie', w.hint || F.hint, 'final_hint']));
  fin.push(['Bon ordre', 'sophie', F.heartLocationText, 'final_ok_sophie']);
  if (F.heartInvText) fin.push(['Bon ordre', 'invisibles', F.heartInvText, 'final_ok_inv']);
  ch.push(textTable(fin));
  ch.push(H2('Finale (après le scan du Cœur)'));
  const finale = F.finaleTexts.map((l, i) => [`Écran ${i + 1}`, lineSpeaker(l, 'sophie'), lineText(l), `finale_${i + 1}`]);
  finale.push(['Bouton', 'screen', 'Épreuves bonus (s\'il reste des talents)', '']);
  ch.push(textTable(finale));

  // Weather table
  if (VIDS.length > 1) {
    ch.push(H1('Les lieux selon la météo'));
    ch.push(P('Les passages « A / B » des textes changent selon la météo : Admin › Réglages › Météo. L\'appli n\'affiche que la bonne version.'));
    const keys = Object.keys(VARIANTS[VIDS[0]]).filter(k => k !== '_label');
    ch.push(table([3000, ...VIDS.map(() => Math.floor(6638 / VIDS.length))], ['Lieu', ...VIDS.map(v => VARIANTS[v]._label || v)],
      keys.map(k => [({ piege_de: 'Faux badge (piège)', coeur_lieu: 'Boîte du Cœur', eclipse_zone: 'Zone de l\'Éclipseuse' })[k] || k, ...VIDS.map(v => VARIANTS[v][k])])));
  }

  // 7. Voices + to decide
  ch.push(H1('7. Voix et points à décider'));
  ch.push(bullet('Chaque texte a un nom de fichier audio (colonne Voix). Déposer les fichiers .mp3 dans un dossier, puis Admin › Contenu › Importer un dossier. Admin › Vérifier les voix liste ce qui manque.'));
  ch.push(bullet('Pour « gather_<talent> », un fichier par talent : gather_telepathe, gather_empathe, etc. (identifiants : ' + C.powers.filter(p => !p.bonusOnly).map(p => p.id).join(', ') + ').'));
  ch.push(bullet('La voix peut dire autre chose que le texte affiché (par exemple le message codé de la Polyglotte n\'est pas lu).'));
  ch.push(bullet('Lieux choisis (voir le tableau « Les lieux selon la météo »). Reste à remplacer, pour les bonus seulement : [objet], [endroit] (Télékinésiste), [objet à réparer] (Guérisseuse).'));
  return doc(ch);
}

// ================================================================ DÉROULÉ
function buildDeroule() {
  const powers = Object.fromEntries(C.powers.map(p => [p.id, p]));
  const main = C.sequence.map(id => powers[id]);
  const bonus = C.powers.filter(p => p.bonusOnly);
  const ch = [];
  ch.push(...title('Mission Sophie : les pouvoirs volés', 'Déroulé de l\'animation, version 6 (modifiable)'));
  ch.push(P([t('Les passages entre [crochets] sont à compléter ou à modifier selon vos choix (lieux, prénoms, matériel).', { italics: true, color: '666666' })]));

  ch.push(H1('Partie 1 : le déroulé'));
  ch.push(H2('Vue d\'ensemble'));
  ch.push(table([2200, 7438], ['Élément', 'Choix retenu'], [
    ['Joueuses', '7 à 10 filles de 9-10 ans, toutes ensemble. Chacune porte le pendentif d\'un talent et joue un personnage fictif. Le jeu s\'adapte au nombre de pendentifs posés au départ.'],
    ['Durée', '1 h 30 environ, plus des épreuves bonus si le temps le permet. On ne raccourcit pas : chaque talent présent a son épreuve.'],
    ['Lieu', 'Maison, et jardin si le temps le permet. Les épreuves se font aux endroits habituels, le MJ y guide les filles, sans fouille.'],
    ['Matériel principal', 'Un téléphone Android NFC (testé sur OnePlus 7 Pro) : le Transmetteur de Sophie, recopié sur la télé. Un tag NFC par pendentif utilisé, plus 1 faux badge (le piège) et 1 badge final (le Cœur de Sophie). Pendentifs imprimés en 3D (symboles dessinés par une enfant). Cartes de personnage (qui servent de joker) et cartes-souvenirs numérotées de 1 à 10.'],
    ['Encadrement', 'Au moins 1 adulte, maître du jeu (MJ) : joue la garde des Invisibles, valide les épreuves, remet les codes et les cartes, gère le rythme avec son menu caché.'],
    ['Règle de Sophie', 'Rien ne se cache dans un tiroir ou un placard fermé : tout est visible sans rien déplacer. Zones interdites marquées par un ruban « barrière des Invisibles ».'],
  ]));

  ch.push(H2('L\'histoire'));
  ch.push(P('Les Invisibles ont brisé les pouvoirs de Sophie. Très affaiblie, elle ne peut plus contacter les invitées que par son Transmetteur (le téléphone). Elle les recrute comme Gardiennes en formation : chaque Gardienne porte un pendentif qui est la clé d\'un talent. Pour le réveiller, la Gardienne doit réussir l\'épreuve de son talent.'));
  ch.push(P('À chaque talent réveillé, Sophie fait un « level up » à l\'écran. Les Invisibles réagissent par un message moqueur, puis Sophie appelle le talent suivant. Ce va-et-vient entre Sophie et ses ennemis rythme toute la partie.'));

  ch.push(H2('Les talents, les pendentifs et les cartes'));
  ch.push(P(`Il y a ${main.length} talents officiels de l'univers, chacun avec son épreuve : ${main.map(p => p.talent).join(', ')}. Chaque invitée reçoit un talent (et son pendentif). Les talents qui ne sont pas distribués deviennent des épreuves bonus.`));
  ch.push(bullet([bold('Le pendentif'), ' : imprimé en 3D avec le symbole du talent, un tag NFC dans le dos. C\'est la clé qui déverrouille la saisie du code.']));
  ch.push(bullet([bold('La carte de personnage'), ' : un prénom elfique inventé, le talent et une citation. Elle sert aussi de joker.']));
  ch.push(bullet([bold('Spécialiste'), ' : à l\'épreuve de son talent, c\'est elle qui fait l\'action clé, les autres l\'aident. Elle seule pose son pendentif pour valider.']));
  ch.push(bullet([bold('Joker'), ' : une fois dans la partie, elle montre sa carte de personnage au MJ pour un bonus (indice, 2 minutes de plus, emprunt du pendentif d\'une autre).']));
  ch.push(bullet([bold('Carte-souvenir'), ' : après son épreuve, chacune reçoit une carte numérotée qui l\'aidera pour l\'énigme finale.']));

  ch.push(H2('Le déroulé d\'une partie'));
  const steps = [
    [bold('Introduction'), ' : Sophie se présente sur le Transmetteur, serment des Gardiennes.'],
    [bold('Rassemblement'), ' : chaque fille pose son pendentif, son talent s\'allume et Sophie l\'accueille. Le MJ termine avec « Toutes les Gardiennes sont là ! ». Le jeu est construit avec les talents présents.'],
    [bold('Les épreuves'), ' des talents présents, dans un ordre qui alterne calme et actif. Le piège des Invisibles tombe au milieu.'],
    [bold('L\'énigme finale'), ' : Sophie rejoue l\'écho des pouvoirs réveillés, les filles reposent leurs pendentifs dans le même ordre (aidées par leurs cartes numérotées).'],
    [bold('Le Cœur de Sophie'), ' : Sophie révèle la cachette de la boîte ; le scan du badge du Cœur lance la finale.'],
    [bold('Les bonus'), ' : s\'il reste du temps, les épreuves des talents non distribués.'],
  ];
  steps.forEach(s => ch.push(num(s)));

  ch.push(H2('Comment se déroule une épreuve'));
  [
    [bold('Appel'), ' : Sophie annonce le talent dont elle a besoin (« Que la Télépathe s\'avance ! »), sans dire l\'épreuve.'],
    [bold('Premier scan'), ' : la spécialiste pose son pendentif. Le titre et la consigne s\'affichent. Ensuite Sophie attend (et se plaint, ou les Invisibles se moquent, si c\'est long).'],
    [bold('Épreuve'), ' : toutes ensemble, la spécialiste au premier plan. Le MJ les guide vers l\'endroit habituel.'],
    [bold('Second scan'), ' : épreuve réussie, la spécialiste repose son pendentif, ce qui ouvre le clavier. Le code vient du MJ (épreuves physiques) ou de l\'épreuve elle-même (messages). 3 essais, puis un indice. Un second scan en moins de 30 s fait dire à Sophie : « Déjà ? Je ne te crois pas… »'],
    [bold('Récompense'), ' : level-up de Sophie, message des Invisibles, puis « Va chercher ta carte-souvenir n°X » : le MJ remet la carte X.'],
  ].forEach(s => ch.push(num(s, 'num2')));
  ch.push(P([bold('Exception : la Vocifératrice.'), ' Pas de code : après le 1er scan, une jauge s\'affiche et tout le groupe crie. Si la jauge reste en haut 3 secondes, le talent est réveillé.']));
  ch.push(P([bold('Mauvais pendentif'), ' : l\'appli refuse avec une moquerie des Invisibles (le joker permet d\'emprunter un autre pendentif). ', bold('Épreuve ratée'), ' : le MJ propose une seconde chance plus simple, ou passe l\'épreuve depuis son menu (il remet quand même la carte).']));

  ch.push(H2('Le minutage'));
  ch.push(table([1500, 2600, 5538], ['Temps', 'Étape', 'Détail'], [
    ['0-10 min', 'Acte 0 : l\'appel de Sophie', 'Introduction, distribution des pendentifs et des cartes de personnage, serment, rassemblement.'],
    ['10-70 min', 'Acte 1 : les épreuves', 'Environ 5 à 6 min par épreuve, plus les déplacements (7 à 10 épreuves). Le piège arrive au milieu (environ 10 min).'],
    ['70-85 min', 'Acte 2 : l\'énigme finale', 'Écho final : les filles reposent leurs pendentifs dans l\'ordre où les pouvoirs ont été réveillés, puis Sophie révèle où se trouve le Cœur.'],
    ['85-95 min', 'Acte 3 : le triomphe', 'Scan du Cœur de Sophie, animation finale, discours de Sophie, remise d\'un souvenir.'],
    ['Ensuite', 'Bonus', 'Épreuves des talents non distribués, si le temps et l\'envie le permettent.'],
  ]));

  ch.push(H2(`Les ${main.length} épreuves des talents`));
  ch.push(P('Dans l\'ordre où l\'appli les joue (seuls les talents présents sont joués). Le titre est affiché au 1er scan.'));
  const kindOf = {
    telepathe: 'Message chuchoté en chaîne', rafaleur: 'Faire avancer un objet en soufflant', polyglotte: 'Message codé, roue elfique',
    phaseur: 'Traverser un mur de fils à clochettes', empathe: 'Deviner des émotions mimées', hydrokinesiste: 'Transporter de l\'eau (éponges, cuillères)',
    technopathe: 'Décoder des ampoules (binaire)', eclipseur: 'Traverser sans être vue (1, 2, 3 soleil)', fluctuateur: 'Faire flotter un œuf (eau salée)',
    chargeur: 'Attraper des lettres par électricité statique', flasheur: 'Faire apparaître un mot avec une lampe', hypnotiseur: '« L\'Hypnotiseuse a dit »',
    givreur: 'Libérer un code pris dans la glace', invocateur: 'Fabriquer un objet avec de la récup', instillateur: 'Faire rire ses amies',
    teleporteur: 'Trouver une case sur un plan quadrillé', tenebreux: 'Deviner des objets à leur ombre', vociferateur: 'Crier toutes ensemble (cri-mètre)',
    telekinesie: 'Déplacer un objet sans le toucher', guerison: 'Réparer un objet cassé',
  };
  const trow = (p, i) => [String(i), `${p.talent}`, p.title, kindOf[p.id] || '', p.kind === 'shout' ? 'Cri-mètre' : p.answers.join(' / ')];
  ch.push(table([500, 1700, 2400, 3438, 1600], ['#', 'Talent', 'Titre affiché', 'Épreuve', 'Code'],
    [...main.map((p, i) => trow(p, i + 1)), ...bonus.map(p => trow(p, 'Bonus'))]));

  ch.push(H2('Fiche MJ : matériel et code de chaque épreuve'));
  ch.push(P('L\'appli ignore les majuscules, les accents et les espaces. Le menu MJ n\'affiche jamais les codes.'));
  const mat = {
    telepathe: ['La dernière fille transmet le message sans erreur', 'Le mot est dans le message chuchoté', 'Rien (message au choix, voir Partie 2)'],
    rafaleur: ['L\'objet passe la ligne sans être touché', 'Le MJ le chuchote', 'Le ballon, éventails, ruban de ligne'],
    polyglotte: ['Le message déchiffré est lu à voix haute', 'Dernier mot du message déchiffré', 'Roue elfique (2 disques)'],
    phaseur: ['Toutes passent sans faire tinter une clochette', 'Le MJ le chuchote', 'Laine, scotch, clochettes, un couloir'],
    empathe: ['L\'Empathe devine 5 émotions', 'Le MJ le chuchote', '8 cartes émotions (joie, peur, colère, tristesse, surprise, dégoût, fierté, timidité)'],
    hydrokinesiste: ['L\'eau atteint le trait du verre', 'Le MJ le chuchote', 'Sous la pergola : seau d\'eau, verre marqué d\'un trait, éponges, cuillères'],
    technopathe: ['Les lettres sont décodées', 'Dans l\'épreuve (feuille des ampoules)', 'Feuille imprimée : 4 rangées de 5 ampoules (16-8-4-2-1)'],
    eclipseur: ['La traversée se fait sans être vue', 'Le MJ le chuchote', 'La cuisine (pluie) ou l\'allée du garage (beau temps)'],
    fluctuateur: ['L\'œuf remonte et montre le code', 'Écrit sous l\'œuf', 'Un œuf (code écrit au feutre dessous), grand verre d\'eau, sel, cuillère'],
    chargeur: ['Les lettres sont attrapées et remises dans l\'ordre', 'Dans l\'épreuve (lettres)', 'Ballons, petits papiers de soie avec les lettres E C L A I R'],
    flasheur: ['Le mot apparaît au mur', 'Dans l\'épreuve (projection)', 'Pièce sombre, lampe torche, carton percé formant le mot'],
    hypnotiseur: ['3 Gardiennes au moins restent après 10 ordres', 'Le MJ le chuchote', 'Rien'],
    givreur: ['Le code est libéré de la glace', 'Dans l\'épreuve (sachet dans la glace)', 'Boîte d\'eau congelée la veille avec le code en sachet étanche, sel, eau tiède'],
    invocateur: ['L\'objet demandé est fabriqué à temps', 'Le MJ le chuchote', 'Boîte à récup pour fabriquer une couronne, minuteur'],
    instillateur: ['Toutes les amies ont ri', 'Le MJ le chuchote', 'Rien (2 minutes)'],
    teleporteur: ['La bonne case du plan est trouvée', 'Écrit sur la carte scellée', 'Plan quadrillé (A-F, 1-5), carte scellée à la chaudière (case C4)'],
    tenebreux: ['Les 4 ombres sont reconnues', 'Initiales des objets', 'Drap, lampe, et dans l\'ordre : Cuillère, Arrosoir, Peigne, Éponge'],
    vociferateur: ['La jauge reste en haut 3 secondes', 'Pas de code (cri-mètre)', 'Rien (micro autorisé dans l\'appli)'],
    telekinesie: ['L\'objet arrive à destination', 'Le MJ le chuchote', '[objet], ficelles, poulie ou bandeau'],
    guerison: ['L\'objet est réparé', 'Le MJ le chuchote', '[objet à réparer] (puzzle, guirlande…)'],
  };
  ch.push(table([1500, 2300, 1300, 2638, 1900], ['Talent', 'Réussie quand…', 'Code', 'Matériel', 'Comment on l\'obtient'],
    [...main, ...bonus].map(p => { const m = mat[p.id] || ['', '', '']; return [p.talent + (p.bonusOnly ? ' (bonus)' : ''), m[0], p.kind === 'shout' ? '—' : p.answers.join(' / '), m[2], m[1]]; })));

  ch.push(H2('Les deux seules cachettes'));
  ch.push(bullet('Le faux badge des Invisibles (le piège), à un endroit précis et visible désigné par le Transmetteur : la mezzanine s\'il pleut, le trampoline s\'il fait beau.'));
  ch.push(bullet('La boîte du Cœur de Sophie (badge final), révélée à la fin de l\'énigme finale : la cave s\'il pleut, près de l\'étendage à linge s\'il fait beau.'));
  ch.push(P('Toutes les autres étapes se passent aux endroits habituels, sans rien chercher.'));

  // Part 2
  ch.push(H1('Partie 2 : préparation des épreuves'));
  ch.push(P('Principes : une clé toujours donnée quelque part (jamais d\'épreuve impossible sans indice), et un corrigé que le MJ garde avec lui.'));
  const sub = (h, lines) => { ch.push(H2(h)); lines.forEach(l => ch.push(bullet(l))); };
  sub('Télépathe : la chaîne de chuchotements', [
    'Les filles se placent en ligne. Le MJ chuchote le message à la première, qui le chuchote à la suivante, une seule fois. La dernière (la Télépathe) le retranscrit et tape le mot secret.',
    'Messages au choix : « Le mot secret de Sophie est ÉTINCELLE. » · « Les Invisibles ont volé la clé : murmurez ÉTINCELLE au Transmetteur. » · « Sophie vous confie son mot magique : ÉTINCELLE. »',
    'Si le message arrive déformé : pas de sanction, second essai avec un message plus court.',
  ]);
  sub('Polyglotte : la roue elfique', [
    'Deux disques concentriques : le grand avec l\'alphabet A-Z, le petit (mobile) avec un second alphabet A-Z.',
    'Sophie ne donne pas le réglage, seulement un secret : ses messages commencent toujours par LE. Les filles tournent le petit disque jusqu’à ce que son L soit en face du O du grand (réglage : A du petit en face du D du grand). « OH PRW HVW RUDJH » se lit alors « LE MOT EST ORAGE ».',
    'Si elles bloquent : menu MJ › Envoyer l’indice (« trouvez le O sur le grand disque, mettez le L du petit en face »).',
    'Roue imprimée en 3D (roue-elfique.scad), version lettres ou lettres + chiffres (zéro barré Ø).',
  ]);
  sub('Technopathe : les ampoules de la machine', [
    'Une feuille avec 4 rangées de 5 ampoules, de valeurs 16, 8, 4, 2, 1. Ampoules coloriées = allumées. Le total d\'une rangée donne une lettre (A = 1).',
    'Pour PUCE : P = 16 (●○○○○), U = 21 = 16 + 4 + 1 (●○●○●), C = 3 = 2 + 1 (○○○●●), E = 5 = 4 + 1 (○○●○●).',
  ]);
  sub('Flasheuse : le rayon révélateur', [
    'Percer à l\'aiguille un carton pour dessiner les lettres LUMIERE en petits points. Dans une pièce sombre, la lampe derrière le carton projette le mot au mur.',
  ]);
  sub('Chargeuse : l\'étincelle vivante', [
    'Découper les lettres E, C, L, A, I, R dans du papier de soie (petits morceaux légers), mélangées sur une table. Frotter les ballons sur les cheveux pour les attraper.',
  ]);
  sub('Fluctuatrice : le poids de l\'eau', [
    'Écrire PLUME au feutre indélébile sous un œuf cru. Le poser au fond d\'un grand verre d\'eau : il coule. En ajoutant du sel (plusieurs cuillères) et en mélangeant, l\'œuf remonte et montre le code. Tester avant !',
  ]);
  sub('Givreuse : le cœur de glace', [
    'La veille : mettre le mot GIVRE dans un petit sachet étanche au milieu d\'une boîte d\'eau, au congélateur. Le jour J : eau tiède et sel, sans outil.',
  ]);
  sub('Ténébreuse : le théâtre d\'ombres', [
    'Un drap tendu, une lampe derrière. Le MJ fait passer dans l\'ordre : Cuillère, Arrosoir, Peigne, Éponge. Initiales : CAPE.',
  ]);
  sub('Téléporteuse : le plan quadrillé', [
    'Un plan simple de la pièce ou du jardin, colonnes A à F, lignes 1 à 5. La case C4 correspond à la chaudière : une carte scellée (visible) y porte le mot CRISTAL.',
    'Version calcul : « Colonne : 6 − 3 = 3, donc C. Ligne : 2 × 2 = 4. »',
  ]);
  sub('Empathe, Instillatrice, Hypnotiseuse, Phaseuse, Hydrokinésiste, Rafaleuse, Éclipseuse, Invocatrice', [
    'Épreuves physiques ou de groupe : le MJ juge la réussite et chuchote le code à la spécialiste (voir la fiche MJ).',
  ]);
  sub('Vocifératrice : le cri-mètre', [
    'Pas de préparation. Autoriser le micro dans l\'appli une fois. Tester le seuil la veille (Admin › Prévisualisation › Tester le cri-mètre). En cas de souci : menu MJ › « Forcer le code / le cri ».',
  ]);

  ch.push(H2('Le piège : le faux badge et l\'écho des pouvoirs'));
  ch.push(bullet('Au milieu de la partie, le Transmetteur annonce un signal étrange et désigne un endroit précis. Le faux badge des Invisibles y est posé, bien visible.'));
  ch.push(bullet('Au scan, Sophie perd le dernier talent réveillé (animation à l\'envers), les Invisibles se moquent.'));
  ch.push(bullet('Réparation, l\'écho des pouvoirs (jeu de mémoire façon Simon) : 3 manches de 3, 4 puis 5 talents, tirés parmi les pendentifs présents. Les filles regardent, écoutent, puis posent leurs pendentifs dans le même ordre. Erreur : la même séquence est rejouée. « Écouter à nouveau » 2 fois par manche. Après 6 erreurs, l\'écho ralentit. Le MJ peut valider la réparation depuis son menu.'));

  ch.push(H2('L\'énigme finale : l\'écho des pouvoirs'));
  ch.push(P('Après chaque épreuve, le MJ remet à la spécialiste la carte-souvenir suivante de la pile (n°1 après la 1re épreuve, n°2 après la 2e…), même si l\'épreuve a été passée. Une seule pile de 10 cartes numérotées, quel que soit le nombre d\'invitées.'));
  ch.push(bullet('À la fin, Sophie rejoue sur l\'écran l\'écho de tous les pouvoirs réveillés, dans l\'ordre où ils l\'ont été : chaque cristal s\'allume avec sa couleur et son son.'));
  ch.push(bullet('Les filles reposent leurs pendentifs dans le même ordre. Les cartes numérotées les aident : la n°1 commence, puis la n°2…'));
  ch.push(bullet('Aucun retour pendant les poses (juste une pastille). Mauvais ordre : moquerie, blocage de 20 secondes, puis l\'écho est rejoué. Après 3 échecs, Sophie donne l\'indice. Bouton « Revoir l\'écho » à tout moment.'));
  ch.push(bullet('Bon ordre : Sophie révèle la cachette de la boîte du Cœur. Le scan du badge du Cœur lance la finale.'));
  ch.push(bullet('La carte de personnage sert de joker (pas de jeton en plus) : la montrer au MJ une fois dans la partie pour un indice, 2 minutes de plus ou l\'emprunt d\'un pendentif.'));

  ch.push(H2('Idées en réserve'));
  ch.push(bullet('Rébus en images, avec la voix des Invisibles.'));
  ch.push(bullet('Message écrit à l\'envers, lisible avec un miroir.'));
  ch.push(bullet('Les mots des cartes (COURAGE, GARDIENS…) : ancienne version de l\'énigme finale, encore disponible dans l\'appli.'));

  ch.push(H2('À décider ensuite'));
  ch.push(bullet('Les endroits habituels des épreuves et les deux cachettes (faux badge, boîte du Cœur).'));
  ch.push(bullet('Pour les bonus seulement : [objet], [endroit] (Télékinésiste), [objet à réparer] (Guérisseuse).'));
  ch.push(bullet('La forme des pendentifs : goutte (33 × 60 mm) ou ronde (60 mm), à choisir.'));
  ch.push(bullet('Les voix de Sophie et des Invisibles (voir le document des textes).'));
  return doc(ch);
}

(async () => {
  for (const [f, build] of [['textes-mission-sophie.docx', buildTextes], ['deroule-mission-sophie.docx', buildDeroule]]) {
    try { fs.writeFileSync(path.join(DOCS, f), await Packer.toBuffer(build())); console.log('écrit : ' + f); }
    catch (e) { console.log('IMPOSSIBLE (fichier ouvert ?) : ' + f); }
  }
  console.log('ok');
})();
