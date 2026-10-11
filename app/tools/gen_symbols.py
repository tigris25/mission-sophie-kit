"""Regenerates android/.../ui/SymbolPaths.kt from ../amulettes-mission-sophie.scad.

Usage (from app/):  python tools/gen_symbols.py [path/to/openscad.com]
Exports each talent symbol as SVG (PART="flat") with OpenSCAD, then writes the path data.
"""
import os, re, shutil, subprocess, sys, tempfile

OPENSCAD = sys.argv[1] if len(sys.argv) > 1 else (shutil.which('openscad') or 'openscad')
SCAD = os.path.join('..', 'amulettes-mission-sophie.scad')
OUT = 'android/src/main/kotlin/fr/mission/sophie/ui/SymbolPaths.kt'
names = re.search(r'NAMES = \[(.*?)\];', open(SCAD, encoding='utf8').read(), re.S).group(1)
names = re.findall(r'"([a-z]+)"', names)

lines = ['package fr.mission.sophie.ui', '', '/**',
         ' * The talent symbols, generated from amulettes-mission-sophie.scad (PART="flat", SVG export).',
         ' * Units: the same as the 3D model (symbols fit in a radius of about 14), y pointing down.',
         ' * Do not edit by hand: run tools/gen_symbols.py.', ' */', 'object SymbolPaths {',
         '    val paths: Map<String, String> = mapOf(']
with tempfile.TemporaryDirectory() as tmp:
    for n in names:
        svg = os.path.join(tmp, n + '.svg')
        subprocess.run([OPENSCAD, '-o', svg, '-D', f'SYMBOL="{n}"', '-D', 'PART="flat"', SCAD], check=True, capture_output=True)
        d = ' '.join(re.findall(r'd="([^"]*)"', open(svg, encoding='utf8').read(), re.S))
        d = re.sub(r'-?\d+(\.\d+)?', lambda m: ('%.2f' % float(m.group())).rstrip('0').rstrip('.'), d)
        lines.append(f'        "{n}" to "{" ".join(d.split())}",')
lines += ['    )', '}', '']
open(OUT, 'w', encoding='utf8').write('\n'.join(lines))
print(f'{len(names)} symbols -> {OUT}')
