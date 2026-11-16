#!/usr/bin/env python3
"""Verify the main design pack library figures in a container."""
from pathlib import Path
import hashlib
import json
import struct
import xml.etree.ElementTree as ET
from urllib.parse import urlsplit,unquote
from verify_library_4k import Links,geometry

ROOT=Path(__file__).resolve().parents[1]
D=ROOT/'designs'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    from library_design_integration import entries
    from library_home_4k import scene_svg
    generated={ident:scene_svg(title,params,note) for _,ident,title,params,note,_ in entries()}
    manifest=json.loads((D/'manifest.json').read_text())
    integrated=json.loads((D/'library-integration.json').read_text())['figures']
    assert len(manifest)==151 and len(integrated)==24
    active={n for n,s in enumerate(manifest,1) if not s.get('retired')}
    flows=json.loads((D/'flow-groups.json').read_text())
    ordered=[n for groups in flows.values() for group in groups for n in group['figures']]
    assert len(ordered)==len(set(ordered))==133 and set(ordered)==active
    errors=[]
    for item in integrated:
        number,slug,scene=item['number'],item['id'],item['scene']
        entry=manifest[number-1]
        assert entry['id']==slug and entry['libraryScene']==scene
        source=D/'svg'/f'{slug}.svg'
        assert source.read_text()==generated[slug], f'{slug}: canonical SVG differs from current drawing source'
        root=ET.parse(source).getroot()
        assert root.get('viewBox')=='0 0 3840 2160'
        geometry(root,slug,errors)
        for folder,dimensions in [('previews',(1920,1080)),('native-4k',(3840,2160))]:
            assert struct.unpack('>II',(D/folder/f'{slug}.png').read_bytes()[16:24])==dimensions
    assert not errors, '\n'.join(errors)
    expected={f'previews/{item["id"]}.png' for item in integrated}
    for page in ['index.html','media.html']:
        markup=(D/page).read_text();links=Links();links.feed(markup)
        paths={unquote(urlsplit(link).path) for link in links.links if urlsplit(link).path}
        assert expected<=paths, f'{page}: missing integrated cards'
        for path in paths:
            if not urlsplit(path).scheme:
                assert (D/path).exists(), f'{page}: broken local resource {path}'
        assert "(s.libraryScene||s.cctvScene)?'native-4k':'previews'" in markup
    assert manifest[62]['title']=='媒体库：观看记录'
    assert all(not s['flow'].startswith('面板') for s in manifest if s.get('libraryScene') in ('filtered','metadata'))
    assert '图106电视剧分类' in manifest[105]['note']
    assert not any('最近添加' in s['title'] for s in manifest if not s.get('retired'))
    assert {p.name for p in D.glob('*.html')}=={'index.html','startup.html','live.html','media.html','cctv.html','settings.html'}
    result=dict(integrated=24,updatedLegacy=9,newFigures=list(range(106,121)),slots=len(manifest),
                active=len(active),flowGroups=sum(map(len,flows.values())),
                errors=[],scope='Local container artifact/geometry/integration checks; no physical TV or Android validation')
    (D/'library-integration-verification.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(result,ensure_ascii=False))


if __name__=='__main__':
    main()
