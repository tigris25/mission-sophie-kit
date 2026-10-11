# Les Pouvoirs volés : le Transmetteur de Sophie (Mission Sophie)

Application Android du jeu d'anniversaire « Mission Sophie » (7 à 10 enfants de 9-10 ans).
Documents de jeu (déroulé, textes, fiche MJ, préparation, imprimables) : `../documents/` (régénérables avec `tools/docs`).

Appareil : téléphone Android 8+ avec **NFC** (testé sur OnePlus 7 Pro, Android 12), en paysage. 100 % hors ligne : aucune permission Internet.

## Structure

| Dossier | Contenu |
|---|---|
| `engine/` | Moteur de jeu en Kotlin pur (sans Android) : contenu JSON et versions météo, rassemblement, séquence adaptée aux talents présents, piège et écho des pouvoirs, écho final, bonus, file des messages. Tests unitaires dans `engine/src/test`. |
| `android/` | Application : NFC, persistance, audio, écrans Compose (accueil, Admin, Jeu, menu MJ, simulateur, cri-mètre). |
| `android/src/main/assets/game_content.json` | Tous les textes et réglages du jeu (voir `textes-mission-sophie.docx`, généré depuis ce fichier). |
| `android/src/main/assets/audio/`, `images/`, `video/` | Médias intégrés à l'APK (vides dans ce dépôt : voir le README principal). |
| `tools/` | Générateurs : `gen_symbols.py` (symboles de l'appli depuis le modèle 3D), `gen_cartes.py` (cartes de personnage), `gen_objets.py` (cartes-souvenirs, roue elfique). |

## Compiler

Prérequis : JDK 17 et le SDK Android (plateforme 35). Indiquer le JDK avec `JAVA_HOME`.

```bash
export JAVA_HOME=/chemin/vers/jdk-17
./gradlew :engine:test            # tests du moteur
./gradlew :android:assembleDebug  # APK : android/build/outputs/apk/debug/android-debug.apk
```

L'APK est signé avec `debug-shared.keystore` (clé de debug partagée, dans le dépôt) : une nouvelle version s'installe par-dessus l'ancienne sans perdre les liaisons des tags.

## Installer sur le téléphone

1. Sur le téléphone : Paramètres › À propos › appuyer 7 fois sur « Numéro de build », puis Paramètres › Système › Options pour les développeurs › **Débogage USB**.
2. Brancher en USB, accepter l'empreinte sur le téléphone, puis :

```bash
adb install -r android/build/outputs/apk/debug/android-debug.apk
```

Sans câble : copier l'APK sur le téléphone et l'ouvrir (autoriser « sources inconnues »).

Activer le **NFC** dans les réglages du téléphone. L'antenne NFC est au dos : repérer le bon endroit avec Admin › Test de lecture. Autoriser le **micro** (cri-mètre de la Vocifératrice).

## Utilisation

- **Accueil** : Admin (PIN par défaut `4275`) ou Jeu. La ligne du bas indique le NFC, les pendentifs liés, les badges et la météo.
- **Admin** : liaison des tags (un par pendentif + faux badge + Cœur), test de lecture, prévisualisation (avatars, animations, cri-mètre, sons), réglages (**météo**, délais, volumes, **muet**, PIN, simulateur, voix de secours), import du contenu et des médias, vérification des voix, sauvegarde des liaisons, journal, réinitialisation de la partie.
- **Jeu** : introduction → **rassemblement** (chaque fille pose son pendentif) → épreuves des talents présents, piège au milieu → **écho final** → Cœur → finale → épreuves bonus.
- **Menu MJ** : appui long de **3 secondes** sur le portrait de Sophie (en haut à gauche), puis PIN. Il n'affiche jamais de code : passer l'étape, forcer le code ou le cri, joker, indice, messages, pause des messages d'attente, valider le piège, étape précédente, redémarrer, volumes, muet, bonus.
- La partie est **sauvegardée à chaque changement**. En relançant « Jeu », on choisit « Reprendre la partie » ou « Nouvelle partie ».

## Simulateur de scans

Actif par défaut dans l'APK de debug ; se désactive dans Admin › Réglages › Tests (à faire le jour J).

- En jeu : bouton **SIM** en haut à droite, avec un bouton par talent, le faux badge, le Cœur et un tag inconnu. Si le rôle est lié à un vrai tag, le simulateur utilise son UID ; sinon il injecte directement le rôle. On peut donc tester tout le jeu sans aucun tag.
- En Admin › Liaison : bouton « Tag simulé » dans la fenêtre de scan.

## Contenu (`game_content.json`)

Format de l'annexe A du cahier des charges, **étendu**. Principaux ajouts :

| Champ | Rôle |
|---|---|
| `variants`, `defaultVariant` | Versions des lieux (`pluie`, `beau`) : chaque `{{clé}}` des textes est remplacée par la valeur de la version choisie dans Admin › Réglages › Météo. |
| `sequence` | Ordre préféré des épreuves ; seules celles des talents posés au rassemblement sont jouées. |
| `gathering` | Textes du rassemblement (`{la}{talent}` → « L'Invocatrice »). |
| `powers[].kind` | `code` (défaut) ou `shout` (cri-mètre, réglé par `shout.threshold` et `shout.holdSec`). |
| `powers[].bonusOnly` | Épreuve sans pendentif, jouable seulement en bonus après la finale. |
| `powers[].reactText` | Réaction des Invisibles propre à l'épreuve. |
| `trap.afterPower` | `middle` (défaut) : le piège tombe au milieu des épreuves jouées. |
| `final.mode` | `echo` (défaut) : l'appli rejoue les pouvoirs dans l'ordre des épreuves, les filles reposent leurs pendentifs dans cet ordre (cartes-souvenirs n°1…n°10). `words` : ancienne énigme des mots (`final.words`). |
| `cardText`, `ui.cardReminder` | « Ta carte-souvenir n°{n}… » |
| `messages.wrongCodeSophie` | Encouragements de Sophie, en alternance avec les moqueries après un code faux (au plus une réaction toutes les `settings.wrongCodeMsgGapSec`). |
| `settings.rewardMinSec`, `rewardScreenSec` | L'écran de récompense attend la fin des dialogues (au moins 4 s, au plus 45 s). |
| `ui.homeTitle`, `ui.homeSubtitle` | Titre de l'écran d'accueil. |

À l'import (Admin › Contenu), le fichier est validé. S'il est invalide, un message précis s'affiche et l'ancien contenu reste actif. Un champ manquant prend sa valeur par défaut, et l'anomalie est notée dans le journal.

## Voix, images, sons

Admin › Contenu › **Importer un dossier** copie dans l'application tous les fichiers audio, image et vidéo du dossier choisi. Les fichiers importés ont priorité sur ceux intégrés dans `assets/`. Les noms ne tiennent pas compte des majuscules.

| Fichier | Usage |
|---|---|
| `<identifiant>.mp3` (ou `.ogg`, `.m4a`, `.wav`) | Voix d'un message. À placer dans `assets/audio/` (préparées avec `tools/convertir_voix.py`). Les identifiants sont dans la colonne « Voix » de `textes-mission-sophie.docx`, et dans Admin › Contenu › **Vérifier les voix** (✓/✗). |
| `sophie.png` / `.jpg` | Portrait de Sophie (avatar rond, recadré sur le visage). À placer dans `assets/images/` ou à importer. |
| `invisibles.png` (ou `.gif`, `.webp` animé) | Remplace l'œil des Invisibles dessiné dans le code. |
| `sophie.mp4` | Vidéo muette de Sophie, dans un panneau à gauche, affichée seulement quand elle parle. À placer dans `assets/video/` ou à importer. |
| `music.mp3` / `music.ogg` | Musique d'ambiance en boucle, baissée pendant les voix. Conseillée : « Fantascape » (version boucle), Eric Matyas, www.soundimage.org, à télécharger soi-même (crédit obligatoire, affiché dans Admin › Contenu et voix). |
| `sfx_scan_ok`, `sfx_neutral`, `sfx_step_ok`, `sfx_error`, `sfx_level_up`, `sfx_corruption`, `sfx_finale`, `sfx_power_0` … `sfx_power_19` | Remplacent les sons générés. |

## Choix d'implémentation à connaître

- **Symboles** : ils viennent des dessins d'une jeune Gardienne, modélisés dans `../amulettes-mission-sophie.scad` ; `tools/gen_symbols.py` les exporte vers `SymbolPaths.kt`. Appli, pendentifs et cartes utilisent donc les mêmes dessins.
- **File des messages** : pas de limite de 2 messages en attente, pour ne pas couper les enchaînements. Les messages d'attente sont abandonnés dès qu'une voix est en cours ou en file.
- **Panneau de gauche** : vidéo de Sophie ou œil des Invisibles, seulement pendant qu'ils parlent ; la bulle n'a alors pas d'avatar. Un texte déjà écrit à l'écran (consigne, indice, carte) n'est pas répété en bulle.
- **Écho du piège** : bouton « Commencer l'écho » ; manches de 3, 4, 5 cristaux (au plus le nombre de pendentifs présents) ; les pendentifs posés pendant la lecture sont ignorés.
- **Écho final** : il démarre à la fin des annonces de Sophie ; pas de retour pendant les poses ; après un mauvais ordre, blocage puis nouvel écho ; bouton « Revoir l'écho ».
- **Joker** : autorisé par le MJ, il permet de valider l'épreuve en cours avec n'importe quel pendentif. Côté joueuses, le joker est la carte de personnage.
- **Retour arrière (MJ)** : pendant une épreuve, il ramène au début de cette épreuve ; depuis un appel, à l'épreuve précédente ; au rassemblement, il retire le dernier pendentif posé.
