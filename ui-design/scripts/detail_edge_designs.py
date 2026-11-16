#!/usr/bin/env python3
"""Detail edge states in the existing native TV design system. All media data is illustrative."""
from pathlib import Path
import json
import library_home_4k as ui
import cctv_designs as cctv

ROOT=Path(__file__).resolve().parents[1]
SCENES=[
 ('13-movie-rich-detail','电影详情：长简介与完整演职人员','movie'),
 ('13-movie-series-rich-detail','剧集详情：多季与大量分集','series'),
 ('cctv-rich-detail','央视频详情：长节目名与大量期数','cctv'),
 ('13-movie-reading-detail','详情展开阅读：长简介','reading'),
]

def shell(kind):
    if kind=='cctv':
        return cctv.begin('央视频详情边界','期名与数量为布局示例；不杜撰演员和季数。')+cctv.shell('特别节目','none')
    section='电视剧' if kind=='series' else '电影'
    s=ui.home(section=section,focus='none').split('<g clip-path="url(#grid-viewport)"')[0]
    s=s.replace(ui.text(800,452,section,80,ui.FG,650),ui.text(800,452,'剧集详情' if kind=='series' else '电影详情',80,ui.FG,650))
    return s.replace(ui.control(3200,350,512,'名称升序',False,'sort',48)+ui.icon('down',3638,402,40,ui.SECONDARY),'')

def title(lines):
    return ''.join(ui.text(1488,650+i*100,line,88,ui.FG,650) for i,line in enumerate(lines))

def people():
    s=ui.text(1488,1470,'演职人员',64,ui.FG,600)
    s+='<defs><clipPath id="cast-viewport"><rect x="1480" y="1520" width="2232" height="400"/></clipPath></defs><g clip-path="url(#cast-viewport)">'
    for i,(name,role) in enumerate([('林川','导演'),('陈远','饰 林野'),('许宁','饰 沈禾'),('周行','饰 青年林野'),('孟青','饰 老年林野')]):
        x=1488+i*736
        s+=ui.box(x,1536,688,320,'#242631',28)
        if i==0:s+=ui.box(x+4,1540,680,312,'none',24,'#f6f4ff',8)
        s+=f'<circle cx="{x+88}" cy="1636" r="52" fill="#373047"/>'+ui.text(x+88,1654,name[0],52,ui.FG,500,'middle')
        s+=ui.text(x+180,1636,name,60,ui.FG,600)+ui.text(x+180,1730,role,48,ui.SECONDARY)
    return s+'</g>'

def scene(kind):
    s=shell(kind)
    if kind=='reading':
        s+=ui.text(800,646,'山河之间：归途、旧日来信与未竟的远方',80,ui.FG,650)
        s+=ui.text(800,778,'剧情简介',56,ui.FG,600)
        s+=ui.box(800,838,2912,1110,'#171924',28,'#f6f4ff',8)
        lines=['一封迟到多年的来信，让离乡已久的林野重新踏上归途。',
               '穿过山川与旧城，他在沿途遇见曾经的伙伴，也逐渐了解家人未曾说出的往事。',
               '故事从一次普通的返乡开始，在不同时代的记忆之间展开。',
               '每一段相遇都改变着他对故乡的认识，也让他重新思考那些没有完成的约定。',
               '当熟悉的街巷和陌生的生活交错，他需要决定如何面对过去，又如何继续前行。',
               '这段旅程既属于一个家庭，也映照着普通人在岁月中的选择。',
               '当山海再次出现在眼前，故乡的意义也在一次次告别与重逢中变得清晰。']
        for i,line in enumerate(lines):s+=ui.text(864,964+i*132,line,56,ui.SECONDARY)
        return s+'</g></svg>'
    is_tv=kind=='cctv'
    name='沿着历史的道路：文化与生活的时代记录' if is_tv else '长夜微光' if kind=='series' else '山河之间：归途、旧日来信与未竟的远方'
    s+=ui.poster(800,552,0,name,'2026' if is_tv else '2025',None if is_tv else '8.6','特别节目' if is_tv else '电视剧' if kind=='series' else '电影',None,focus=False)
    s+=title(['沿着历史的道路：','文化与生活的时代记录'] if is_tv else ['长夜微光'] if kind=='series' else ['山河之间：归途、旧日来信','与未竟的远方'])
    s+=ui.text(1488,838,'2026 · 文化 · CCTV-1' if is_tv else '2025 · 剧情 / 家庭 · 中国大陆',48,ui.SECONDARY)
    s+=ui.text(1488,920,'收录1042期' if is_tv else '当前库可用8季 · 第8季24集' if kind=='series' else '评分8.6 · 01:48:00 · 4K',48,ui.SECONDARY)
    s+=ui.text(1488,1000,'节目简介' if is_tv else '剧情简介',56,ui.FG,600)
    s+=ui.text(1488,1074,'沿着山川与旧城，寻找生活与时代留下的故事。',52,ui.SECONDARY)
    s+=ui.text(1488,1144,'那些未曾说出的往事，逐渐在旅途中展开……',52,ui.SECONDARY)
    if kind=='movie':
        for i,label in enumerate(['继续播放','从头播放','标为已看','收藏']):
            s+=ui.control(1488+i*556,1200,520,label,size=52,center=True)
        s+=people()
    else:
        for x,w,label in [(1488,736,'继续播放 · 第1007期' if is_tv else '继续播放 · 第8季第12集'),(2272,608,'查看分期' if is_tv else '查看分集'),(2928,608,'收藏')]:
            s+=ui.control(x,1200,w,label,size=50,center=True)
        s+=ui.text(1488,1480,'选期' if is_tv else '选季',60,ui.FG,600)
        if not is_tv:
            s+=ui.control(1700,1384,860,'第8季 · 24集',ico='series',size=52,selected=True)
            s+=ui.icon('down',2472,1434,40)
        labels=['991–1000期','1001–1010期','1011–1020期'] if is_tv else ['1–10集','11–20集','21–24集']
        s+='<defs><clipPath id="ranges-viewport"><rect x="1692" y="1516" width="2020" height="172"/></clipPath></defs><g clip-path="url(#ranges-viewport)">'
        for i,label in enumerate(labels):s+=ui.control(1700+i*724,1536,688,label,size=52,selected=i==1)
        s+='</g>'
        for i in range(10):
            x,y=1488+i%5*448,1704+i//5*184
            label=f'{1001+i}期' if is_tv else f'第{11+i}集'
            s+=ui.control(x,y,412,label,i==0,size=52,center=True)
    return s+'</g></svg>'

def merge(screens,output):
    for ident,name,kind in SCENES:
        screen=dict(id=ident,title=name,note='详情边界补充设计，待审查后实施。名称、人数、季数与集数均为布局示例；原生4K，App内部央视频状态与媒体服务器账号数据隔离。',flow='详情简介确认 → 展开阅读；返回恢复简介焦点。演员左右随焦点滚动；选季确认后加载对应分集。',kind='state',nativeResolution=[3840,2160],detailScene=kind)
        screen['cctvScene' if kind=='cctv' else 'libraryScene']='rich-detail-'+kind
        screens.append(screen)
        (output/'svg'/f'{ident}.svg').write_text(scene(kind))
    return screens

if __name__=='__main__':
    out=ROOT/'designs';screens=json.loads((out/'manifest.json').read_text())
    screens=[s for s in screens if not s.get('detailScene')]
    merge(screens,out)
    (out/'manifest.json').write_text(json.dumps(screens,ensure_ascii=False,indent=2)+'\n')
