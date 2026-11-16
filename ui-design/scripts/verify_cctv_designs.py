#!/usr/bin/env python3
"""Container-only checks for canonical CCTV design assets and data boundaries."""
from pathlib import Path
from urllib.parse import urlsplit,parse_qs
import json,struct,xml.etree.ElementTree as ET
from verify_library_4k import geometry,Links,NS

ROOT=Path(__file__).resolve().parents[1]
D=ROOT/'designs'

def main():
    manifest=json.loads((D/'manifest.json').read_text())
    mapping=json.loads((D/'cctv-integration.json').read_text())
    scenes=[(i,s) for i,s in enumerate(manifest,1) if s.get('cctvScene')]
    assert len(scenes)==27 and mapping['merged'] is True
    assert [i for i,s in scenes]==list(range(121,147))+[149]
    errors=[]
    for number,scene in scenes:
        ident=scene['id'];root=ET.parse(D/'svg'/f'{ident}.svg').getroot()
        assert root.get('viewBox')=='0 0 3840 2160'
        geometry(root,ident,errors)
        assert all(n.text!='来源顺序' for n in root.findall('.//s:text',NS)),ident
        assert len(root.findall('.//s:g[@data-focused="true"]',NS))==1,ident
        assert [n.get('data-category') for n in root.findall('.//s:g[@data-role="sidebar-entry"]',NS)]==['电视剧','动画片','纪录片','特别节目','观看记录','收藏']
        for card in root.findall('.//s:g[@data-role="media-card"]',NS):
            assert not any(n.text in ('未看','在看','已看','进度未知') for n in card.findall('.//s:text',NS)),ident
            assert len(card.findall('./s:g[@data-role="card-caption"]',NS))==1
        for folder,dims in [('previews',(1920,1080)),('native-4k',(3840,2160))]:
            assert struct.unpack('>II',(D/folder/f'{ident}.png').read_bytes()[16:24])==dims
        assert 'App内部' in scene['note']
    for slug in ('detail','episodes','episodes-later','episode-error','play-error'):
        root=ET.parse(D/'svg'/f'cctv-{slug}.svg')
        assert root.find('.//s:g[@data-role="watch-progress"]',NS).get('data-progress')=='0.32'
    source=json.loads((ROOT/'scripts/data/cctv-source-samples.json').read_text())
    assert list(source['categories'])==['电视剧','动画片','纪录片','特别节目']
    for category,value in source['categories'].items():
        query=parse_qs(urlsplit(value['request']).query)
        assert query['fc']==[category] and 'channelid' not in query and value['httpStatus']==200
        assert all(item['fc']==category for item in value['items'])
    for page in ('index.html','cctv.html'):
        markup=(D/page).read_text();links=Links();links.feed(markup)
        assert {f'previews/{s["id"]}.png' for _,s in scenes}<={urlsplit(link).path for link in links.links}
        assert 'proposals/' not in markup
        for link in links.links:
            path=urlsplit(link).path
            if path and not urlsplit(link).scheme:assert (D/path).exists(),(page,path)
    sections=json.loads((D/'sections.json').read_text())
    assert next(s for s in sections if s['id']=='cctv')['figures']==list(range(121,147))+[149]
    assert not set(range(121,147)) & set(next(s for s in sections if s['id']=='media')['figures'])
    for number in (3,4,25,30,31,105):
        item=manifest[number-1]
        text=''.join(n.text or '' for n in ET.parse(D/'svg'/f'{item["id"]}.svg').findall('.//s:text',NS))
        assert '央视频' in text and '纪录片' not in text,number
    assert not (ROOT/'proposals/cctv').exists(), 'Temporary proposal directory must be removed'
    assert not (ROOT/'TV_LIBRARY_PLAN.md').exists(), 'Temporary process plan must be removed'
    assert not errors,'\n'.join(errors)
    result=dict(scenes=len(scenes),merged=True,errors=errors,
                storageScopes=['live-source-channel','media-server-account-item','app-internal-cctv-album'],
                scope='Local container static design checks; no Android, actual server writes or CCTV playback validation.')
    (D/'cctv-verification.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(result,ensure_ascii=False))

if __name__=='__main__':main()
