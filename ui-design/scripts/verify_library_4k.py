#!/usr/bin/env python3
"""Check exported TV artwork and measured text geometry. Run in a container."""
from pathlib import Path
import json
import re
import struct
import xml.etree.ElementTree as ET
from html.parser import HTMLParser
from design_text import text_width

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT/'designs'
NS = {'s':'http://www.w3.org/2000/svg'}


def transform(value, ox, oy, scale):
    for kind,args in re.findall(r'(translate|scale)\(([^)]+)\)',value):
        nums = [float(v) for v in re.split(r'[ ,]+',args.strip())]
        if kind == 'translate':
            ox += scale*nums[0]
            oy += scale*(nums[1] if len(nums)>1 else 0)
        else:
            assert len(nums)==1 or nums[0]==nums[1], 'Only uniform scale is expected'
            scale *= nums[0]
    return ox,oy,scale


def geometry(node, slug, errors, ox=0, oy=0, scale=1, clipped=False, card_bounds=None):
    tag = node.tag.rsplit('}',1)[-1]
    if tag == 'defs':
        return
    ox,oy,scale = transform(node.get('transform',''),ox,oy,scale)
    clipped = clipped or bool(node.get('clip-path'))
    if node.get('data-role') == 'media-card':
        art = node.find('./s:defs/s:clipPath/s:rect',NS)
        card_left = ox+float(art.get('x'))*scale
        card_bounds = (card_left,card_left+float(art.get('width'))*scale)
    if tag == 'text':
        value = node.text or ''
        size = float(node.get('font-size','48'))
        x = ox+scale*float(node.get('x','0'))
        y = oy+scale*float(node.get('y','0'))
        width = text_width(value,size)*scale
        anchor = node.get('text-anchor','start')
        left = x-width/2 if anchor=='middle' else x-width if anchor=='end' else x
        right = left+width
        # Second-row content below the viewport is intentionally clipped.
        if not (clipped and y>2032):
            if left<127 or right>3713 or y-size*scale<127 or y+size*scale*.2>2033:
                errors.append(f'{slug}: text outside safe area: {value} ({left:.1f},{y:.1f},{right:.1f})')
            if card_bounds and (left<card_bounds[0]-1 or right>card_bounds[1]+1):
                errors.append(f'{slug}: text outside its card: {value}')
    if tag == 'rect' and node.get('stroke')=='#f6f4ff':
        sw = float(node.get('stroke-width','0'))*scale
        x = ox+float(node.get('x','0'))*scale-sw/2
        y = oy+float(node.get('y','0'))*scale-sw/2
        right = x+float(node.get('width','0'))*scale+sw
        bottom = y+float(node.get('height','0'))*scale+sw
        if x<127 or right>3713 or y<127 or bottom>2033:
            errors.append(f'{slug}: focus frame outside safe area')
    for child in node:
        geometry(child,slug,errors,ox,oy,scale,clipped,card_bounds)


class Links(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links = []
        self.ids = set()

    def handle_starttag(self,tag,attrs):
        attrs = dict(attrs)
        if 'id' in attrs:
            self.ids.add(attrs['id'])
        self.links.extend(attrs[key] for key in ('href','src') if key in attrs)


def main():
    manifest=json.loads((OUT/'manifest.json').read_text())
    scenes=[scene for scene in manifest if scene.get('libraryScene') and not scene.get('detailScene') and not scene.get('detailLayout') and not scene.get('retired')]
    errors=[]
    assert len(scenes)==24
    for scene in scenes:
        slug=scene['id'];source=(OUT/'svg'/f'{slug}.svg').read_text();root=ET.fromstring(source)
        assert root.get('viewBox')=='0 0 3840 2160'
        geometry(root,slug,errors)
        assert len(root.findall('.//s:g[@data-focused="true"]',NS))==1,slug
        assert len(root.findall('.//s:g[@data-role="sidebar-entry"]',NS))==5
        ids=[n.get('id') for n in root.iter() if n.get('id')]
        assert len(ids)==len(set(ids)),slug
        for ref in re.findall(r'url\(#([^)]+)\)',source):assert ref in ids,(slug,ref)
        for card in root.findall('.//s:g[@data-role="media-card"]',NS):
            assert not any(n.text in ('未看','在看','已看','进度未知') for n in card.findall('.//s:text',NS)),slug
            assert len(card.findall('./s:g[@data-role="card-caption"]',NS))==1
        for group in root.findall('.//s:g[@data-role="watch-progress"]',NS):assert 0<=float(group.get('data-progress'))<=1
        for folder,dims in [('previews',(1920,1080)),('native-4k',(3840,2160))]:
            assert struct.unpack('>II',(OUT/folder/f'{slug}.png').read_bytes()[16:24])==dims
    metadata=ET.parse(OUT/'svg/12-library-metadata.svg').getroot()
    cards=metadata.findall('.//s:g[@data-role="media-card"]',NS)
    assert all(not cards[i].findall('.//s:g[@data-role="watch-progress"]',NS) for i in (0,2))
    assert '年份未知' in ''.join(cards[0].itertext())
    assert not errors,'\n'.join(errors)
    print(f'PASS: {len(scenes)} main-design library SVGs, 50 PNGs, five sidebar entries, unique focus, measured geometry, progress and metadata.')

if __name__=='__main__':main()
