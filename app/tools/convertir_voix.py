"""Converts the recorded voices (.wav or other) to .mp3 for the app.

Usage (from app/):  python tools/convertir_voix.py [input folder] [output folder]
Defaults: ../voix/wav  ->  ../voix/mp3

For each file:
- trims the silence at the start and at the end,
- evens out the loudness (EBU R128, same level for Sophie and the Invisibles),
- mono, 44.1 kHz, mp3 128 kbit/s,
- the output name is the input name in lower case, with .mp3 (e.g. "Intro_1.wav" -> "intro_1.mp3").
Then it checks the names against voix-a-enregistrer.csv: unknown names and missing lines are listed.
"""
import csv, os, shutil, subprocess, sys

FFMPEG = os.environ.get('FFMPEG') or shutil.which('ffmpeg') or 'ffmpeg'  # ffmpeg in the PATH, or FFMPEG=...
ROOT = os.path.abspath('..')
SRC = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'voix', 'wav')
DST = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, 'voix', 'mp3')
AUDIO = ('.wav', '.mp3', '.m4a', '.ogg', '.flac')

# Silence trimming at both ends (reverse trick), then loudness normalisation.
FILTER = ('silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.05,'
          'areverse,silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.1,areverse,'
          'loudnorm=I=-16:TP=-1.5:LRA=11')

os.makedirs(DST, exist_ok=True)
files = sorted(f for f in os.listdir(SRC) if f.lower().endswith(AUDIO))
done = []
for f in files:
    name = os.path.splitext(f)[0].strip().lower()
    out = os.path.join(DST, name + '.mp3')
    r = subprocess.run([FFMPEG, '-y', '-loglevel', 'error', '-i', os.path.join(SRC, f), '-af', FILTER,
                        '-ac', '1', '-ar', '44100', '-b:a', '128k', out], capture_output=True, text=True)
    if r.returncode != 0:
        print(f'ERREUR {f} : {r.stderr.strip()[:200]}')
    else:
        done.append(name)
print(f'{len(done)} fichiers convertis -> {DST}')

# Check against the list of expected lines.
expected = {}
with open(os.path.join(ROOT, 'voix', 'voix-a-enregistrer.csv'), encoding='utf-8-sig') as fh:
    for row in csv.DictReader(fh, delimiter=';'):
        expected[row['Fichier'][:-4].lower()] = row  # the app ignores case
unknown = [n for n in done if n not in expected]
missing = [k for k in expected if k not in done]
if unknown:
    print('Noms inconnus (à renommer, sinon l’appli ne les jouera pas) :', ', '.join(unknown))
prio = [k for k in missing if expected[k]['Priorité'] == '1']
print(f'Manquants : {len(missing)} dont {len(prio)} prioritaires' + (' : ' + ', '.join(prio) if prio else ''))
