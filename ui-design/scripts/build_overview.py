#!/usr/bin/env python3
"""Contact sheet from existing PNG renders using existing ImageMagick."""
from pathlib import Path
import json,subprocess
ROOT=Path(__file__).resolve().parents[1]
D=ROOT/'designs'
pages=[D/'previews'/f"{s['id']}.png" for s in json.loads((D/'manifest.json').read_text()) if not s.get('retired',False)]
pages.append(D/'previews/25-page-map.png')
subprocess.run(['montage',*map(str,pages),'-thumbnail','480x270','-tile','4x','-geometry','480x270+8+8','-background','#2b3047',str(D/'overview.png')],check=True)
manifest=json.loads((D/'manifest.json').read_text())
for part in json.loads((D/'sections.json').read_text()):
    selected=[D/'previews'/f"{manifest[number-1]['id']}.png" for number in part['figures']]
    subprocess.run(['montage',*map(str,selected),'-thumbnail','480x270','-tile',f'{min(4,len(selected))}x','-geometry','480x270+8+8','-background','#2b3047',str(D/f"overview-{part['id']}.png")],check=True)
print('Updated overview for all designs.')

# Keep shared summaries derived from current frames, including the latest scoped update.
for name, slugs in [
 ('tv-final-effects', ['15-media-player','15-media-aspect','15-media-opening','09-box-config','09-box-config-large','09-box-config-import-error']),
 ('overview-tv-review', [m['id'] for m in manifest[50:64]]),
]:
 subprocess.run(['montage',*[str(D/'previews'/f'{slug}.png') for slug in slugs],'-thumbnail','640x360','-tile','2x','-geometry','640x360+8+8','-background','#2b3047',str(D/f'{name}.png')],check=True)
