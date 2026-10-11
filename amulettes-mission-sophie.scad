// ============================================================
//  Amulettes « Mission Sophie » : 18 symboles (dessins d'une jeune Gardienne), 2 couleurs
//  Amulette en forme de goutte, qui épouse le tag NFC porte-clés
//  (goutte de 27 x 54 mm), avec boucle, logement et couvercle.
//  Unités : millimètres. Compatible OpenSCAD 2021.01 et plus récent.
//
//  IMPRESSION : la face AVANT de l'amulette est posée sur le plateau.
//  Le symbole est déjà retourné dans le modèle pour que, une fois la pièce
//  retournée à la main, il se lise dans le bon sens.
//  Le logement du tag s'ouvre sur le dos : on y glisse le tag, puis on
//  colle (ou clipse en force) le couvercle.
//
//  COULEURS : 2 pièces à exporter par amulette, à charger ensemble dans le
//  logiciel de découpe (elles se superposent exactement) :
//    - PART = "base"   -> couleur 1 (corps, boucle, logement)
//    - PART = "symbol" -> couleur 2 (symbole incrusté, affleurant)
//    - PART = "lid"    -> couvercle du logement, couleur 1 (à imprimer à part)
// ============================================================

// ---------- CE QU'ON AFFICHE OU EXPORTE ----------
SYMBOL = "all";         // un des noms de NAMES (ex. "telepathe"),
                         // ou "all" pour les 18 en grille
SHAPE  = "goutte";       // "goutte" (épouse le tag), "rond" (médaillon rond), "etoile" (le Cœur de Sophie)
HAS_LUG = SHAPE != "etoile";   // pas de boucle pour le Cœur de Sophie (ce n'est pas un pendentif)
BORDER_IN = 1.6;         // liseré de l'étoile : distance au bord (mm)
BORDER_W  = 1.6;         // liseré de l'étoile : largeur (mm)
STAR_N = 4;              // nombre de branches de l'étoile (4 = l'étoile de Sophie, 8 = étoile de lumière)
PART   = "preview";      // preview, base, symbol, lid, ou "flat" (dessin 2D seul, pour export SVG)

// ---------- LE TAG (mesuré sur le scan, à vérifier au pied à coulisse) ----------
TAG_L     = 54;    // longueur totale de la goutte (mm)
TAG_R_BIG = 13.5;  // rayon de la partie ronde (largeur 27 mm)
TAG_R_TIP = 6.0;   // rayon de la pointe (côté trou), un peu large par sécurité
TAG_T     = 1.1;   // épaisseur du tag (1,1 mm, valeur utilisée pour l'impression)

// ---------- RÉGLAGES ----------
CLEAR   = 0.6;   // marge autour du tag, sur chaque bord (mm)
CLEAR_Z = 0.3;   // marge en épaisseur au-dessus du tag (mm)
WALL    = 2.5;   // épaisseur des parois autour du logement (mm)
FRONT_T = 1.8;   // épaisseur de la face avant, sous le tag (mm)
LID_T   = 1.0;   // épaisseur du couvercle (mm)
LID_GAP = 0.2;   // jeu du couvercle dans le logement, par bord (mm)
INLAY   = 1.0;   // profondeur du symbole incrusté dans la face avant (mm)
SYM_SCALE = 0.72; // taille du symbole (1 = 30 mm de large environ)
LUG_T   = 3.0;   // épaisseur de la boucle (mm)
LUG_R   = 5.0;   // rayon extérieur de la boucle (mm)
HOLE_R  = 2.2;   // rayon du trou de la boucle (mm) : 4,4 mm de diamètre

$fn = 72;

// ---------- VALEURS DÉRIVÉES (ne pas modifier) ----------
TIP_Y        = TAG_L - TAG_R_BIG - TAG_R_TIP;   // centre de la pointe (origine = centre de la partie ronde)
POCKET_DEPTH = TAG_T + CLEAR_Z + LID_T;
BASE_T       = FRONT_T + POCKET_DEPTH;
// Médaillon rond : plus petit cercle qui contient la poche (centré sur l'axe du tag)
ROUND_CY     = (TIP_Y + TAG_R_TIP - TAG_R_BIG) / 2;
ROUND_R      = (TIP_Y + TAG_R_TIP + TAG_R_BIG) / 2 + CLEAR + WALL;
STAR_R       = ROUND_R * (STAR_N == 4 ? 1.45 : 1.28);   // pointe des branches (étoile)
SYM_CY       = SHAPE == "rond" || SHAPE == "etoile" ? ROUND_CY : 0;           // centre du symbole
SYM_K        = SHAPE == "rond" ? 1.55 : SHAPE == "etoile" ? 2.15 : 1;               // symbole plus grand sur le rond
TOP_Y        = SHAPE == "etoile" ? ROUND_CY + STAR_R - 3 : SHAPE == "rond" ? ROUND_CY + ROUND_R : TIP_Y + TAG_R_TIP + CLEAR + WALL; // haut du corps
LUG_Y        = TOP_Y + LUG_R - 1.5;              // centre de la boucle
PITCH_X      = SHAPE == "rond" ? 2 * ROUND_R + 8 : 2 * (TAG_R_BIG + CLEAR + WALL) + 8;
PITCH_Y      = SHAPE == "rond" ? 2 * ROUND_R + 2 * LUG_R + 8 : LUG_Y + LUG_R + TAG_R_BIG + CLEAR + WALL + 8;
NAMES = ["telepathe", "empathe", "flasheur", "eclipseur", "hydrokinesiste", "givreur",
         "teleporteur", "instillateur", "polyglotte", "invocateur", "technopathe", "tenebreux",
         "chargeur", "phaseur", "hypnotiseur", "rafaleur", "vociferateur", "fluctuateur"];

// ============================================================
//  SYMBOLES (d'après les dessins d'une jeune Gardienne), en 2D, centrés sur
//  l'origine, dans un rayon d'environ 14 unités.
//  Style : traits épais et arrondis (W), faciles à imprimer.
// ============================================================

W = 2.4;   // épaisseur des traits (avant mise à l'échelle SYM_SCALE)

// --- outils ---
module seg(a, b, w = W) { hull() { translate(a) circle(d = w, $fn = 24); translate(b) circle(d = w, $fn = 24); } }
module pl(pts, w = W) { for (i = [0 : len(pts) - 2]) seg(pts[i], pts[i + 1], w); }
function arc(c, r, a0, a1, n = 24) = [for (i = [0 : n]) let(a = a0 + (a1 - a0) * i / n) [c[0] + r * cos(a), c[1] + r * sin(a)]];
function spiral(c, r0, r1, a0, turns, n = 60) =
  [for (i = [0 : n]) let(t = i / n, a = a0 + 360 * turns * t, r = r0 + (r1 - r0) * t) [c[0] + r * cos(a), c[1] + r * sin(a)]];
module ring(c, r, w = W) { translate(c) difference() { circle(r + w / 2); circle(r - w / 2); } }
module sparkle(c, s = 2.5) {
  translate(c) polygon([[0, s], [s * 0.25, s * 0.25], [s, 0], [s * 0.25, -s * 0.25],
                        [0, -s], [-s * 0.25, -s * 0.25], [-s, 0], [-s * 0.25, s * 0.25]]);
}
// Bonhomme bâton : tête, corps, jambes ; bras "down", "up", "fwd" (vers la droite)
module figure(p, s = 1, arms = "down", head = true) {
  x = p[0]; y = p[1];
  if (head) translate([x, y + 5.6 * s]) circle(2.5 * s);
  pl([[x, y + 2.6 * s], [x, y - 2.6 * s]]);
  pl([[x - 3 * s, y - 8 * s], [x, y - 2.6 * s], [x + 3 * s, y - 8 * s]]);
  if (arms == "down") pl([[x - 3.6 * s, y - 1 * s], [x, y + 1.2 * s], [x + 3.6 * s, y - 1 * s]]);
  if (arms == "up")   pl([[x - 4 * s, y + 4.4 * s], [x, y + 1 * s], [x + 4 * s, y + 4.4 * s]]);
  if (arms == "fwd")  pl([[x - 3.4 * s, y - 1 * s], [x, y + 1.2 * s], [x + 4.6 * s, y + 2.4 * s]]);
}
module heart(c, s = 1) {
  translate(c) scale(s) {
    hull() { translate([-4.6, 3]) circle(5.6); translate([0, -10.5]) circle(0.9); }
    hull() { translate([4.6, 3]) circle(5.6); translate([0, -10.5]) circle(0.9); }
  }
}
module bubble(w, h) { // bulle de dialogue pleine, queue en bas à gauche
  hull() { for (x = [-w / 2 + 4, w / 2 - 4], y = [-h / 2 + 4, h / 2 - 4]) translate([x, y]) circle(4); }
  polygon([[-w / 2 + 3, -h / 2 + 2], [-w / 2 + 10, -h / 2 + 1], [-w / 2 - 1, -h / 2 - 5]]);
}

// --- les 18 talents ---

// Télépathe : un nuage de pensée et ses deux bulles
module sym_telepathe() {
  translate([2, 3]) {
    hull() { translate([-5, -1]) circle(4.5); translate([5, -1]) circle(4.5); }
    translate([-6, 1.5]) circle(4.2); translate([-1.5, 5]) circle(5); translate([4.5, 3.5]) circle(4.4);
  }
  translate([-8, -7]) circle(2.4);
  translate([-11.5, -11]) circle(1.5);
}

// Empathe : un cœur dans un cœur
module sym_empathe() {
  difference() { heart([0, 1.5]); heart([0, 1.2], 0.66); }
  heart([0, 1.4], 0.3);
}

// Flasheur : une ampoule qui rayonne
module sym_flasheur() {
  difference() {
    union() {
      translate([0, 2.5]) circle(7);
      hull() { translate([-3.4, -3]) square([6.8, 1]); translate([-2.6, -8.5]) square([5.2, 1]); }
    }
    pl([[-2.6, 1], [-1.3, 4.2], [0, 1], [1.3, 4.2], [2.6, 1]], 1.3);   // filament
    translate([-4, -5.6]) square([8, 1]);                              // pas de vis
  }
  for (a = [90, 40, 140, -5, 185]) seg([10 * cos(a), 2.5 + 10 * sin(a)], [13 * cos(a), 2.5 + 13 * sin(a)], 2);
}

// Éclipseur : une silhouette qui s'efface à moitié
module sym_eclipseur() {
  module body() {
    translate([0, 6.5]) circle(4.3);
    hull() { translate([0, -1]) circle(4.6); translate([-7, -12]) square([14, 1]); }
  }
  intersection() { body(); translate([-20, -20]) square([20, 40]); }
  intersection() {
    body();
    for (y = [-12 : 4.2 : 12]) translate([1.2, y]) square([20, 2.4]);
  }
}

// Hydrokinésiste : une vague qui s'enroule
module sym_hydrokinesiste() {
  pl(concat([[-13, -6], [-8, -4.5], [-3, -5.5]], spiral([2, 1.5], 7.5, 1.8, 250, 1.05, 50)));
  pl([[-13, -11], [-9, -9.6], [-5, -11], [-1, -9.6], [3, -11], [7, -9.6], [11, -11]]);
}

// Givreur : un flocon à 8 branches
module sym_givreur() {
  for (a = [0 : 45 : 315]) {
    long = (a % 90 == 0);
    L = long ? 13 : 10;
    seg([0, 0], [L * cos(a), L * sin(a)]);
    if (long) for (d = [-40, 40])
      seg([8 * cos(a), 8 * sin(a)], [8 * cos(a) + 3.6 * cos(a + d), 8 * sin(a) + 3.6 * sin(a + d)], 2);
    else translate([L * cos(a), L * sin(a)]) circle(1.8);
  }
  circle(3);
}

// Téléporteur : un bonhomme saute d'un endroit à un autre
module sym_teleporteur() {
  figure([-7, -3], 0.85);
  figure([7, -3], 0.85, arms = "up");
  pl(arc([0, 4], 7, 165, 25, 16), 2);
  translate([6.6, 6.2]) rotate(-35) polygon([[-2.6, 1.2], [2.6, 0], [-2.6, -1.8]]);
  sparkle([11.5, 9], 2.4); sparkle([2.5, -11], 1.8);
}

// Instillateur : une étoile envoie un rayon dans une tête
module sym_instillateur() {
  figure([5, -4], 0.95);
  sparkle([-8, 8], 4.2);
  pl([[-5, 5], [-3.5, 2.5], [-1, 3.5], [0.5, 1.5]], 1.8);
  heart([10.5, 8], 0.28);
}

// Polyglotte : une bulle remplie de lettres de langues différentes
module sym_polyglotte() {
  difference() {
    bubble(26, 18);
    translate([-7.5, -3]) text("A", size = 7, font = "Liberation Sans:style=Bold", halign = "center");
    translate([0, -3]) text("Ж", size = 7, font = "Liberation Sans:style=Bold", halign = "center");
    translate([7.5, -3]) text("Ω", size = 7, font = "Liberation Sans:style=Bold", halign = "center");
  }
}

// Invocateur : une flèche fait apparaître un objet
module sym_invocateur() {
  pl([[-13, -3], [-4.5, -3]]);
  translate([-4, -3]) polygon([[-1.5, 3.8], [3.5, 0], [-1.5, -3.8]]);
  difference() {
    translate([2.5, -9]) square([10, 9]);
    translate([6.7, -9.5]) square([1.6, 10]);   // ruban
  }
  translate([2, 0.6]) square([11, 2.4]);         // couvercle
  pl([[7.5, 3], [5, 6]], 1.6); pl([[7.5, 3], [10, 6]], 1.6);
  sparkle([1, 10], 2.6); sparkle([12.5, 10.5], 2);
}

// Technopathe : une machine qui envoie des étincelles
module sym_technopathe() {
  for (y = [-8.5, -3.5]) { translate([-13, y - 0.8]) square([3.5, 1.6]); translate([9.5, y - 0.8]) square([3.5, 1.6]); }
  difference() {
    translate([-10, -11]) square([20, 12]);
    for (x = [-6.5, -1.5, 3.5], y = [-8.5, -3.5]) translate([x, y]) square([3, 2.6]);
  }
  pl([[-10, 4], [-6, 10], [-2, 4], [2, 10], [6, 4], [10, 10]], 2.2);
}

// Ténébreux : une ombre vivante aux yeux clairs
module sym_tenebreux() {
  difference() {
    union() {
      translate([1, 2]) circle(9);
      translate([-7.4, -9.5]) square([16.8, 11]);
      for (x = [-5, 1, 7]) translate([x, -9.5]) circle(3);
    }
    translate([-2, 3]) scale([1, 1.4]) circle(1.8);
    translate([4, 3]) scale([1, 1.4]) circle(1.8);
  }
  ring([-11, -9], 2.2, 1.6);
}

// Chargeur : un éclair plein d'énergie
module sym_chargeur() {
  polygon([[3, 14], [-8, -1], [-1.5, -1], [-4.5, -14], [8, 3], [1.5, 3]]);
  sparkle([-9, 9], 3); sparkle([10, -9], 2.6);
}

// Phaseur : un bonhomme qui traverse un mur
module sym_phaseur() {
  // Le bonhomme, bras tendus, coupé par le mur : une moitié de chaque côté.
  difference() {
    union() {
      translate([0, 6.5]) circle(3.4);
      pl([[0, 2.5], [0, -3]], 3);
      pl([[-4.5, -12], [0, -3], [4.5, -12]]);
      pl([[-8, 2], [0, 1], [8, 2]]);
    }
    translate([-1.9, -20]) square([3.8, 40]);
  }
  translate([-0.7, -14]) square([1.4, 28]);
}

// Hypnotiseur : l'un fait tourner la tête de l'autre
module sym_hypnotiseur() {
  figure([-7, -3], 0.85, arms = "fwd");
  figure([7, -3], 0.85, head = false);
  ring([7, 3.2], 3.2, 1.4); translate([7, 3.2]) circle(1);
  pl([[-2.5, 0], [-0.5, 1.5], [1.5, 0], [3.5, 1.5]], 1.4);
}

// Rafaleur : trois bourrasques qui tourbillonnent
module sym_rafaleur() {
  pl(concat([[-13, 7], [3, 7]], spiral([3, 10.5], 3.5, 0.5, 270, 0.75, 20)));
  pl(concat([[-13, 0], [7, 0]], spiral([7, 3.5], 3.5, 0.5, 270, 0.75, 20)));
  pl(concat([[-11, -7], [1, -7]], spiral([1, -10.5], 3.5, 0.5, 90, -0.75, 20)));
}

// Vociférateur : une bulle qui crie
module sym_vociferateur() {
  difference() {
    translate([-2, 0]) bubble(22, 17);
    pl([[-9, -2], [-7, 3], [-5, -2], [-3, 3], [-1, -2], [1, 3], [3, -2], [5, 3]], 1.6);
  }
  pl(arc([8, 0], 4.5, -40, 40, 10), 1.8);
  pl(arc([8, 0], 8, -35, 35, 12), 1.8);
}

// Fluctuateur : petit ou grand, c'est lui qui décide
module sym_fluctuateur() {
  figure([-9, -4.5], 0.65);
  translate([-4.5, -3.2]) square([4, 1.4]); translate([-4.5, -0.6]) square([4, 1.4]);
  figure([6, -2], 1.1, arms = "up");
}

// --- Le Cœur de Sophie : un cœur de lumière qui renferme son étoile ---
module sym_coeur_sophie() {
  difference() {
    heart([0, 0.5], 1);
    heart([0, 1.1], 0.72);
  }
  // l'étoile de Sophie au centre du cœur
  translate([0, 0.6]) polygon([for (i = [0 : 7]) let(a = 90 + i * 45, r = i % 2 == 0 ? 5.2 : 1.4) [r * cos(a), r * sin(a)]]);
  // trois rayons de lumière au-dessus
  for (a = [60, 90, 120]) seg([12 * cos(a), 5 + 7 * sin(a)], [14.5 * cos(a), 5 + 9.5 * sin(a)], 1.6);
}

// --- Faux badges des Invisibles : des symboles de talents presque justes ---

// Faux Givreur : un flocon à 7 branches irrégulières (un vrai en a 8), une branche tordue
module sym_faux_givreur() {
  angs = [0, 49, 101, 158, 207, 262, 311];
  lens = [13, 11, 12.5, 9.5, 13, 11.5, 10.5];
  for (i = [0 : 6]) {
    a = angs[i] + 90; L = lens[i];
    if (i == 3) {           // branche tordue
      pl([[0, 0], [6 * cos(a), 6 * sin(a)], [6 * cos(a) + 4 * cos(a + 35), 6 * sin(a) + 4 * sin(a + 35)]]);
    } else {
      seg([0, 0], [L * cos(a), L * sin(a)]);
      if (i % 2 == 0) for (d = [-40, 40])
        seg([7.5 * cos(a), 7.5 * sin(a)], [7.5 * cos(a) + 3.4 * cos(a + d), 7.5 * sin(a) + 3.4 * sin(a + d)], 2);
      else translate([L * cos(a), L * sin(a)]) circle(1.8);
    }
  }
  circle(3);
}

// Faux Empathe : un cœur penché, un peu bossu, fêlé
module sym_faux_empathe() {
  rotate(-9) difference() {
    union() {
      hull() { translate([-4.3, 3]) circle(5.2); translate([0.6, -10.2]) circle(0.9); }
      hull() { translate([4.9, 3.4]) circle(6); translate([0.6, -10.2]) circle(0.9); }
    }
    translate([0.4, 1.2]) scale(0.6) heart([0, 0]);
    // fêlure en zigzag
    pl([[-1.5, 9.5], [0.2, 5.5], [-1, 3.5], [1.2, 0], [-0.4, -2.5]], 1.1);
  }
  rotate(-9) heart([1.3, 1.9], 0.3);   // petit cœur, décentré
}

module symbol2d(name) {
  if      (name == "coeur_sophie")   sym_coeur_sophie();
  else if (name == "faux_givreur")   sym_faux_givreur();
  else if (name == "faux_empathe")   sym_faux_empathe();
  else if (name == "telepathe")      sym_telepathe();
  else if (name == "empathe")        sym_empathe();
  else if (name == "flasheur")       sym_flasheur();
  else if (name == "eclipseur")      sym_eclipseur();
  else if (name == "hydrokinesiste") sym_hydrokinesiste();
  else if (name == "givreur")        sym_givreur();
  else if (name == "teleporteur")    sym_teleporteur();
  else if (name == "instillateur")   sym_instillateur();
  else if (name == "polyglotte")     sym_polyglotte();
  else if (name == "invocateur")     sym_invocateur();
  else if (name == "technopathe")    sym_technopathe();
  else if (name == "tenebreux")      sym_tenebreux();
  else if (name == "chargeur")       sym_chargeur();
  else if (name == "phaseur")        sym_phaseur();
  else if (name == "hypnotiseur")    sym_hypnotiseur();
  else if (name == "rafaleur")       sym_rafaleur();
  else if (name == "vociferateur")   sym_vociferateur();
  else if (name == "fluctuateur")    sym_fluctuateur();
}

// Symbole retourné (miroir en X) car la face avant est posée sur le plateau
module face2d(name) {
  translate([0, SYM_CY]) mirror([1, 0]) scale(SYM_SCALE * SYM_K) symbol2d(name);
  // Étoile : liseré de couleur qui suit le contour (même couleur que le symbole)
  if (SHAPE == "etoile") difference() {
    offset(r = -BORDER_IN) body2d();
    offset(r = -BORDER_IN - BORDER_W) body2d();
  }
}

// ============================================================
//  PIÈCES 3D
// ============================================================

// Forme du tag (goutte), origine au centre de la partie ronde, pointe vers le haut
module tag2d() {
  hull() {
    circle(r = TAG_R_BIG, $fn = 128);
    translate([0, TIP_Y]) circle(r = TAG_R_TIP);
  }
}

module pocket2d() { offset(r = CLEAR) tag2d(); }
module body2d() {
  if (SHAPE == "rond") translate([0, ROUND_CY]) circle(r = ROUND_R, $fn = 160);
  else if (SHAPE == "etoile") translate([0, ROUND_CY]) {
    // cœur rond qui contient le tag, et les branches de l'étoile, aux bouts arrondis
    circle(r = ROUND_R, $fn = 160);
    for (i = [0 : STAR_N - 1]) rotate(90 + i * 360 / STAR_N)
      hull() {
        rotate(-360 / STAR_N / 2) translate([ROUND_R * 0.7, 0]) circle(1);
        rotate(360 / STAR_N / 2) translate([ROUND_R * 0.7, 0]) circle(1);
        translate([STAR_R - 2.5, 0]) circle(2.5);
      }
    // petites pointes entre les grandes (étoile à 4 branches seulement)
    if (STAR_N == 4) for (i = [0 : 3]) rotate(45 + i * 90)
      hull() { translate([ROUND_R - 6, -5]) circle(1); translate([ROUND_R - 6, 5]) circle(1); translate([ROUND_R + 5, 0]) circle(1.5); }
  }
  else offset(r = WALL) pocket2d();
}

module lug2d() {
  translate([0, LUG_Y]) circle(r = LUG_R);
}

// Corps : goutte + boucle - trou - logement du tag - creux du symbole
module base3d(name) {
  difference() {
    union() {
      linear_extrude(BASE_T) body2d();
      if (HAS_LUG) linear_extrude(LUG_T) hull() { lug2d(); translate([0, TOP_Y - 3]) circle(r = 3); }
    }
    // trou de la boucle
    if (HAS_LUG) translate([0, LUG_Y, -1]) cylinder(r = HOLE_R, h = BASE_T + 2);
    // logement du tag, ouvert sur le dos
    translate([0, 0, FRONT_T]) linear_extrude(POCKET_DEPTH + 1) pocket2d();
    // creux du symbole, sur la face avant, dans la partie ronde
    translate([0, 0, -0.01]) linear_extrude(INLAY + 0.01) face2d(name);
  }
}

// Symbole : remplit exactement le creux (couleur 2)
module symbol3d(name) {
  linear_extrude(INLAY) face2d(name);
}

// Couvercle du logement du tag
module lid3d() {
  linear_extrude(LID_T) offset(delta = -LID_GAP) pocket2d();
}

module one(name) {
  if (PART == "flat")        symbol2d(name);
  else if (PART == "base")        base3d(name);
  else if (PART == "symbol") symbol3d(name);
  else if (PART == "lid")    lid3d();
  else {
    // aperçu : on retourne la pièce pour voir la face avant, avec les couleurs
    rotate([0, 180, 0]) translate([0, 0, -BASE_T]) {
      color("#1f2a55") base3d(name);
      color("#efe3b8") symbol3d(name);
      color("#1f2a55")
        translate([0, 0, FRONT_T + TAG_T + CLEAR_Z]) lid3d();
    }
  }
}

if (SYMBOL == "all") {
  for (i = [0 : len(NAMES) - 1])
    translate([(i % 6) * PITCH_X, -floor(i / 6) * PITCH_Y, 0])
      one(NAMES[i]);
} else {
  one(SYMBOL);
}
