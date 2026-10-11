// ============================================================
//  Roue elfique « Mission Sophie » (épreuve de la Polyglotte)
//  Deux disques : le grand porte un axe central à clipser,
//  le petit s'enfile dessus et tourne.
//  Unités : millimètres. OpenSCAD 2021.01 et plus récent.
//
//  IMPRESSION : les deux disques à plat, lettres vers le haut, sans support.
//  L'axe est fendu en deux : il se pince pour entrer dans le trou du petit
//  disque, puis sa lèvre le retient. Imprimer l'axe en PLA ou PETG,
//  avec 3 ou 4 parois pour qu'il soit solide.
//
//  COULEURS (facultatif) : les lettres sont incrustées, affleurantes.
//    - PART = "grand" / "petit"                 -> couleur 1 (disques)
//    - PART = "grand_lettres" / "petit_lettres" -> couleur 2 (lettres)
//    À charger ensemble dans le logiciel de découpe (elles se superposent).
//    En une seule couleur : imprimer "grand" et "petit" seuls, les lettres
//    sont alors gravées en creux (on peut les repasser au feutre).
// ============================================================

PART = "preview";   // preview, grand, petit, grand_lettres, petit_lettres, test_axe
CHIFFRES = false;   // true : A à Z puis 0 à 9 (36 cases) pour des codes comme K6

// ---------- RÉGLAGES ----------
R_GRAND  = 58;     // rayon du grand disque
T_GRAND  = 3;      // épaisseur du grand disque
R_PETIT  = 42;     // rayon du petit disque
T_PETIT  = 2.4;    // épaisseur du petit disque
INLAY    = 0.8;    // profondeur des lettres
FONT     = "Liberation Sans:style=Bold";   // ou "Cinzel:style=Bold" si installée
SIZE_G   = CHIFFRES ? 6.2 : 7.5;    // hauteur des lettres du grand disque
SIZE_P   = CHIFFRES ? 4.6 : 5.6;    // hauteur des lettres du petit disque

AXE_D    = 6;      // diamètre de l'axe
JEU      = 0.25;   // jeu entre l'axe et le trou (par côté)
JEU_Z    = 0.3;    // jeu en hauteur sous la lèvre
PAROI    = 1.3;    // épaisseur des deux moitiés de l'axe (v1 : 1.6) : plus fin = plus souple
LEVRE    = 0.35;   // débord de la lèvre (validé à l'essai ; v1 : 0.5, trop dur)
FENTE    = 1.8;    // largeur de la fente de l'axe (v1 : 1.4)

$fn = 96;
ABC = CHIFFRES ? "ABCDEFGHIJKLMNOPQRSTUVWXYZØ123456789" : "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
N = len(ABC);

// ---------- VALEURS DÉRIVÉES ----------
RL_G = R_GRAND - 2 - SIZE_G / 2 - 1.5;    // rayon des lettres (grand)
RL_P = R_PETIT - 2 - SIZE_P / 2 - 1.2;    // rayon des lettres (petit)
R_TROU = AXE_D / 2 + JEU;
H_AXE  = T_PETIT + JEU_Z;                  // partie lisse de l'axe, au-dessus du grand disque
H_TETE = 1.8;                              // hauteur de la tête (lèvre + cône d'entrée)

// Lettres disposées en cercle, lisibles depuis l'extérieur, A en haut
module ring_letters(r, size) {
  for (i = [0 : N - 1])
    rotate(-i * 360 / N)
      translate([0, r])
        text(ABC[i], size = size, font = FONT, halign = "center", valign = "center");
}
module ring_ticks(r0, r1, w = 0.6) {
  for (i = [0 : N - 1]) rotate(-(i + 0.5) * 360 / N) translate([-w / 2, r0]) square([w, r1 - r0]);
}

// Décor : une étoile à 4 branches
module star2d(r) {
  polygon([for (i = [0 : 7]) let(a = 90 + i * 45, rr = i % 2 == 0 ? r : r * 0.3) [rr * cos(a), rr * sin(a)]]);
}

// Motif gravé du grand disque (lettres + séparateurs + cercles)
module grand_motif() {
  ring_letters(RL_G, SIZE_G);
  ring_ticks(RL_G - SIZE_G / 2 - 1.5, R_GRAND - 1.5);
  difference() { circle(R_GRAND - 1.5 + 0.35); circle(R_GRAND - 1.5 - 0.35); }
  difference() { circle(RL_G - SIZE_G / 2 - 1.5 + 0.35); circle(RL_G - SIZE_G / 2 - 1.5 - 0.35); }
}
// Motif gravé du petit disque
module petit_motif() {
  ring_letters(RL_P, SIZE_P);
  ring_ticks(RL_P - SIZE_P / 2 - 1.2, R_PETIT - 1.2, 0.5);
  difference() { circle(RL_P - SIZE_P / 2 - 1.2 + 0.3); circle(RL_P - SIZE_P / 2 - 1.2 - 0.3); }
  for (a = [45, 135, 225, 315]) rotate(a) translate([0, (R_TROU + RL_P - SIZE_P) / 2 + 1]) star2d(2.6);
}

// Axe fendu à clipser, posé sur le grand disque
module axe() {
  difference() {
    union() {
      cylinder(r = AXE_D / 2, h = H_AXE);
      // lèvre : s'élargit (pente 45°, imprimable) puis cône d'entrée
      translate([0, 0, H_AXE]) cylinder(r1 = AXE_D / 2, r2 = AXE_D / 2 + LEVRE, h = LEVRE);
      translate([0, 0, H_AXE + LEVRE]) cylinder(r1 = AXE_D / 2 + LEVRE, r2 = AXE_D / 2 - 0.8, h = H_TETE - LEVRE);
    }
    // fente qui permet aux deux moitiés de se rapprocher
    translate([-FENTE / 2, -AXE_D, 0.8]) cube([FENTE, 2 * AXE_D, H_AXE + H_TETE]);
    // creux central pour plus de souplesse
    translate([0, 0, 0.8]) cylinder(r = AXE_D / 2 - PAROI, h = H_AXE + H_TETE);
  }
}

module grand() {
  difference() {
    cylinder(r = R_GRAND, h = T_GRAND, $fn = 180);
    translate([0, 0, T_GRAND - INLAY]) linear_extrude(INLAY + 0.01) grand_motif();
  }
  translate([0, 0, T_GRAND]) axe();
}

module petit2d() {
  union() {
    circle(R_PETIT, $fn = 160);
    // languette pour tourner la roue, en face du A
    hull() { translate([0, R_PETIT - 3]) circle(4); translate([0, R_PETIT + 5]) circle(3); }
  }
}
module petit() {
  difference() {
    linear_extrude(T_PETIT) petit2d();
    translate([0, 0, -1]) cylinder(r = R_TROU, h = T_PETIT + 2);
    // petit chanfrein en dessous pour faciliter l'entrée de l'axe
    translate([0, 0, -0.01]) cylinder(r1 = R_TROU + 0.5, r2 = R_TROU, h = 0.5);
    translate([0, 0, T_PETIT - INLAY]) linear_extrude(INLAY + 0.01) petit_motif();
  }
}

// Pièce d'essai : un bout de grand disque avec l'axe, et une rondelle trouée,
// pour vérifier le clipsage avant d'imprimer les grandes pièces.
module test_axe() {
  cylinder(r = 10, h = 1.5); translate([0, 0, 1.5]) axe();
  translate([25, 0, 0]) difference() { cylinder(r = 8, h = T_PETIT); translate([0, 0, -1]) cylinder(r = R_TROU, h = T_PETIT + 2); }
}

if (PART == "grand") grand();
else if (PART == "petit") petit();
else if (PART == "grand_lettres") translate([0, 0, T_GRAND - INLAY]) linear_extrude(INLAY) grand_motif();
else if (PART == "petit_lettres") translate([0, 0, T_PETIT - INLAY]) linear_extrude(INLAY) petit_motif();
else if (PART == "test_axe") test_axe();
else {
  // aperçu : la roue assemblée, petit disque tourné de 3 crans (A du petit en face du D du grand)
  color("#1f2a55") grand();
  color("#efe3b8") translate([0, 0, T_GRAND - INLAY]) linear_extrude(INLAY) grand_motif();
  translate([0, 0, T_GRAND + 0.05]) rotate(-3 * 360 / N) {
    color("#6a4fb0") petit();
    color("#efe3b8") translate([0, 0, T_PETIT - INLAY]) linear_extrude(INLAY) petit_motif();
  }
}
