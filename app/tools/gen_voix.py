"""Builds the list of voice lines to record (same voice ids as the app, see Lines.kt).

Usage (from app/):  python tools/gen_voix.py
Output: ../voix/voix-a-enregistrer.csv and ../voix/voix-a-enregistrer.json (Sophie first, then the Invisibles).
Lines whose screen text changes with the weather, contains a code or a card number get a "spoken" version.
"""
import csv, json, os, re

ROOT = os.path.abspath('..')
OUT = os.path.join(ROOT, 'voix')
raw = json.load(open('android/src/main/assets/game_content.json', encoding='utf8'))
VARIANTS = raw.get('variants', {})
SOPHIE_DEFAULT = {'heartTooEarly', 'tooFastSecondScan', 'codeOk', 'idleSophie', 'wrongCodeSophie'}

# Spoken versions: one audio file must suit both weathers, and some texts should not be read as written.
SPOKEN = {
    'trap_announce': "Attendez… je capte un signal ! On dirait un de mes pouvoirs perdus. Allez vite le chercher et posez-le sur le Transmetteur !",
    'final_ok_sophie': "C'est ça ! L'équipe est unie et la magie éclate ! Je vous révèle la cachette de mon Cœur. Vite, avant que les Invisibles ne l'attrapent !",
    'consigne_eclipseur': "Les gardes des Invisibles surveillent le passage. Traversez sans qu'ils vous voient bouger : dès qu'un garde se retourne, immobiles ! Si vous êtes surprises, on recommence. De l'autre côté, le Maître du jeu vous confiera le code.",
    'consigne_polyglotte': "Un message codé vous attend sur l'écran. Je ne peux pas vous dire comment régler la roue, les Invisibles nous écoutent… Mais voici mon secret : mes messages commencent toujours par LE. Trouvez le bon réglage, déchiffrez, et le dernier mot sera votre code.",
    'card_secret': "Ta carte-souvenir t'attend auprès du Maître du jeu. Garde-la précieusement, elle servira à la fin !",
}
NOTES = {
    'trap_announce': 'Sans le lieu (il change avec la météo) : il reste écrit à l’écran.',
    'final_ok_sophie': 'Sans le lieu (il change avec la météo) : il reste écrit à l’écran.',
    'consigne_eclipseur': 'Sans le lieu (cuisine / allée du garage).',
    'consigne_polyglotte': 'Le message codé n’est pas lu à voix haute.',
    'card_secret': 'Sans le numéro de la carte (il est écrit à l’écran).',
}


def resolve(text):
    # Placeholders: the first variant (only for display in the list; spoken versions avoid them).
    first = next(iter(VARIANTS.values()), {})
    return re.sub(r'\{\{(\w+)\}\}', lambda m: first.get(m.group(1), m.group(0)), text)


def text_of(l):
    return l if isinstance(l, str) else l['text']


def speaker_of(l, default):
    return default if isinstance(l, str) else l.get('speaker', default)


lines = []


def add(vid, speaker, text, moment):
    text = resolve(text)
    lines.append({'id': vid, 'speaker': speaker, 'moment': moment, 'screen': text,
                  'spoken': SPOKEN.get(vid, text), 'note': NOTES.get(vid, '')})


C = raw
powers = {p['id']: p for p in C['powers']}
for i, l in enumerate(C['intro']):
    add(f'intro_{i + 1}', speaker_of(l, 'sophie'), text_of(l), f'Introduction, écran {i + 1}')
g = C.get('gathering', {})
add('gather_intro', 'sophie', g['introText'], 'Rassemblement : consigne')
for p in C['powers']:
    if p.get('bonusOnly'):
        continue
    t = p['talent']
    la = "L'" if re.match(r'[AEIOUYHÉÈÊ]', t, re.I) else 'La '
    add(f"gather_{p['id']}", 'sophie', g['template'].replace('{la}', la).replace('{talent}', t), f'Rassemblement : {t}')
add('gather_done', 'sophie', g['doneText'], 'Rassemblement : départ de la mission')
order = C['sequence'] + [p['id'] for p in C['powers'] if p['id'] not in C['sequence']]
for pid in order:
    p = powers[pid]
    tag = p['talent'] + (' (bonus)' if p.get('bonusOnly') else '')
    add(f'call_{pid}', 'sophie', p['callText'], f'{tag} : appel')
    add(f'consigne_{pid}', 'sophie', p['consigne'], f'{tag} : consigne')
    add(f'hint_{pid}', 'sophie', p['hint'], f'{tag} : indice')
    add(f'success_{pid}', 'sophie', p['successText'], f'{tag} : réussite')
    if p.get('reactText'):
        add(f'react_{pid}', 'invisibles', p['reactText'], f'{tag} : réaction')
add('card_secret', 'sophie', C['cardText'].replace(' n°{n}', ''), 'Après chaque réussite : la carte')
M = C['messages']
LABELS = {'wrongPendant': 'Mauvais pendentif', 'unknownTag': 'Tag inconnu', 'fakeBadgeTooEarly': 'Faux badge trop tôt',
          'heartTooEarly': 'Cœur trop tôt', 'tooFastSecondScan': 'Second scan trop rapide', 'wrongCode': 'Code faux',
          'codeOk': 'Code juste', 'wrongCodeSophie': 'Code faux (encouragement)', 'idleSophie': 'Attente : Sophie',
          'idleInvisibles': 'Attente : moquerie', 'invisiblesAfterSuccess': 'Réaction générique'}
for key, label in LABELS.items():
    for i, l in enumerate(M.get(key, [])):
        add(f'{key}_{i + 1}', speaker_of(l, 'sophie' if key in SOPHIE_DEFAULT else 'invisibles'), text_of(l), f'{label} {i + 1}')
T = C['trap']
add('trap_announce', 'sophie', T['announceText'], 'Piège : annonce')
if T.get('teaserText'):
    add('trap_teaser', 'invisibles', T['teaserText'], 'Piège : teaser')
add('trap_corrupt_inv', 'invisibles', T['corruptText'], 'Piège : corruption')
if T.get('corruptSophieText'):
    add('trap_corrupt_sophie', 'sophie', T['corruptSophieText'], 'Piège : corruption')
add('trap_repair_intro', 'sophie', T['repairIntroText'], 'Écho : explication')
for i, x in enumerate(T.get('roundIntroTexts', [])):
    add(f'trap_round_{i + 1}', 'sophie', x, f'Écho : manche {i + 1}')
for i, x in enumerate(T.get('wrongStepTexts', [])):
    add(f'trap_wrong_{i + 1}', 'invisibles', x, f'Écho : erreur {i + 1}')
if T.get('helpText'):
    add('trap_help', 'sophie', T['helpText'], 'Écho : coup de pouce')
for i, x in enumerate(T.get('roundSuccessTexts', [])):
    add(f'trap_round_ok_{i + 1}', 'sophie', x, f'Écho : manche {i + 1} réussie')
add('trap_restored_sophie', 'sophie', T['restoredText'], 'Pouvoir réparé')
if T.get('restoredInvText'):
    add('trap_restored_inv', 'invisibles', T['restoredInvText'], 'Pouvoir réparé')
F = C['final']
for key, vid, label in [('announceText', 'final_announce', 'Écho final : annonce'), ('consigneText', 'final_consigne', 'Écho final : consigne')]:
    if F.get(key):
        add(vid, 'sophie', F[key], label)
for i, x in enumerate(F.get('wrongOrderTexts', [])):
    add(f'final_wrong_{i + 1}', 'invisibles', x, f'Écho final : mauvais ordre {i + 1}')
if F.get('lockoutText'):
    add('final_lockout', 'sophie', F['lockoutText'], 'Écho final : blocage')
add('final_hint', 'sophie', F['hint'], 'Écho final : indice')
add('final_ok_sophie', 'sophie', F['heartLocationText'], 'Bon ordre : la cachette du Cœur')
if F.get('heartInvText'):
    add('final_ok_inv', 'invisibles', F['heartInvText'], 'Bon ordre')
for i, l in enumerate(F.get('finaleTexts', [])):
    add(f'finale_{i + 1}', speaker_of(l, 'sophie'), text_of(l), f'Finale, écran {i + 1}')
add('bonus_intro', 'sophie', C['bonus']['introText'], 'Épreuves bonus')

# Sophie first, then the Invisibles; numbered.
ordered = [l for l in lines if l['speaker'] == 'sophie'] + [l for l in lines if l['speaker'] != 'sophie']
# Priority 1 = the story (intro, calls, instructions, successes, trap, final); 2 = variety (lists, hints, greetings, bonus).
BONUS = {p['id'] for p in C['powers'] if p.get('bonusOnly')}
def priority(l):
    vid = l['id']
    if any(vid.endswith('_' + b) for b in BONUS) or vid == 'bonus_intro':
        return 2
    if re.match(r'(intro_|call_|consigne_|success_|react_|card_secret|gather_intro|gather_done|trap_|final_|finale_)', vid):
        return 1
    return 2
for n, l in enumerate(ordered, 1):
    l['n'] = n
    l['file'] = l['id'] + '.mp3'
    l['prio'] = priority(l)
    if any(vid in l['id'] for vid in BONUS):
        l['note'] = (l['note'] + ' ' if l['note'] else '') + 'Épreuve bonus : facultatif.'
os.makedirs(OUT, exist_ok=True)
json.dump(ordered, open(os.path.join(OUT, 'voix-a-enregistrer.json'), 'w', encoding='utf8'), ensure_ascii=False, indent=1)
with open(os.path.join(OUT, 'voix-a-enregistrer.csv'), 'w', encoding='utf-8-sig', newline='') as f:
    w = csv.writer(f, delimiter=';')
    w.writerow(['N°', 'Fichier', 'Personnage', 'Priorité', 'Moment', 'Texte à dire', 'Remarque'])
    for l in ordered:
        w.writerow([l['n'], l['file'], 'Sophie' if l['speaker'] == 'sophie' else 'Invisibles', l['prio'], l['moment'], l['spoken'], l['note']])
ns = sum(1 for l in ordered if l['speaker'] == 'sophie')
print(f'{len(ordered)} répliques : {ns} Sophie, {len(ordered) - ns} Invisibles, {sum(l["prio"] == 1 for l in ordered)} prioritaires -> {OUT}')
