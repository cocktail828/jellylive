#!/usr/bin/env python3
"""Canonical native-4K detail layouts and their content edge states. Data is illustrative."""
from pathlib import Path
from html import escape
import json,base64
import library_home_4k as ui
import cctv_designs as cctv

ROOT=Path(__file__).resolve().parents[1]
SCENES=[
 ('13-movie-rich-detail','电影详情：长简介与完整演职人员','movie'),
 ('13-movie-series-rich-detail','剧集详情：多季与大量分集','series'),
 ('cctv-rich-detail','央视频详情：长节目名与大量期数','cctv'),
 ('13-movie-reading-detail','详情展开阅读：长简介','reading'),
 ('13-movie-series-cast-detail','剧集详情：演职人员翻页','series-cast'),
]
NORMAL=[('13-movie','movie',False),('14-series','series',False),
        ('24-library-item-missing','missing',False)]


def shell(kind,large=False):
    if kind=='cctv':
        return cctv.begin('央视频详情','只展示来源提供的节目资料。')+cctv.shell('电视剧','none')
    section='电视剧' if kind=='series' else '电影'
    source=ui.home(section=section,focus='none',large=large).split('<g clip-path="url(#grid-viewport)"')[0]
    heading_y,heading_size=(654,88) if large else (452,80)
    source=source.replace(ui.text(800,heading_y,section,heading_size,ui.FG,650),ui.text(800,heading_y,'剧集详情' if kind=='series' else '媒体详情' if kind=='missing' else '电影详情',heading_size,ui.FG,650))
    x,y,w=(2960,544,752) if large else (3200,350,512)
    source=source.replace(ui.control(x,y,w,'名称升序',False,'sort',64 if large else 48)+ui.icon('down',x+w-74,y+52,40,ui.SECONDARY),'')
    return source


def body_start(large=False):
    top=726 if large else 526
    return f'<defs><clipPath id="detail-viewport"><rect x="774" y="{top}" width="2964" height="{2032-top}"/></clipPath></defs><g clip-path="url(#detail-viewport)" data-role="detail-body">'


def actions(labels,focus=True,y=1200,large=False):
    gap=32;w=(2224-gap*(len(labels)-1))/len(labels)
    s='<g data-role="detail-actions">'
    for i,label in enumerate(labels):
        s+=ui.control(1488+i*(w+gap),y,w,label,focus and i==0,size=64 if large else 52,h=192 if large else 144,center=True)
    return s+'</g>'


def people(y=1512,focus=False,many=False,page=0):
    rows=[('byron','拜伦·霍华德','导演'),('jared','杰拉德·布什','导演'),
          ('ginnifer','金妮弗·古德温','朱迪'),('jason','杰森·贝特曼','尼克'),
          ('quan','关继威','盖瑞'),('idris','伊德瑞斯·艾尔巴','牛局长'),
          ('jenny','珍妮·斯蕾特','羊副市长'),('shakira','夏奇拉','夏奇羊')]
    if not many:rows=rows[:3]
    count=6;pages=(len(rows)+count-1)//count;start=page*count
    s=ui.text(800,y-56,'演职人员',64,ui.FG,600)
    if pages>1:
        s+='<g data-role="cast-pager">'
        for x,previous,enabled in [(800,True,page>0),(3568,False,page<pages-1)]:
            s+=ui.control(x,y-40,144,'',h=144,disabled=not enabled)
            path='M24 8L14 18l10 10' if previous else 'M12 8l10 10l-10 10'
            color=ui.SECONDARY if enabled else '#676b79'
            s+=f'<g transform="translate({x+48} {y+8}) scale(1.333333)"><path d="{path}" fill="none" stroke="{color}" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></g>'
        s+=ui.text(2256,y+56,f'{page+1} / {pages}',48,ui.SECONDARY,400,'middle')+'</g>'
    top=y+(120 if pages>1 else 0)
    for i,(key,name,role) in enumerate(rows[start:start+count]):
        x=800+i*((2912-256)/5 if pages>1 else 416);cx=x+128
        raw=(ROOT/'assets/portraits'/f'{key}.jpg').read_bytes()
        uri='data:image/jpeg;base64,'+base64.b64encode(raw).decode()
        s+=f'<g data-role="person-card" data-focused="{str(focus and i==0).lower()}"><title>{escape(name+" · "+role)}</title>'
        s+=f'<defs><clipPath id="person-{i}"><circle cx="{cx}" cy="{top+128}" r="128"/></clipPath></defs>'
        s+=f'<image data-role="person-avatar" x="{x}" y="{top}" width="256" height="256" preserveAspectRatio="xMidYMin slice" clip-path="url(#person-{i})" xmlns:xlink="http://www.w3.org/1999/xlink" xlink:href="{uri}"/>'
        if focus and i==0:s+=f'<circle data-role="person-focus" cx="{cx}" cy="{top+128}" r="134" fill="none" stroke="#f6f4ff" stroke-width="8"/>'
        s+='<g data-role="person-caption">'+ui.text(cx,top+320,ui.shorten(name,48,256),48,ui.FG,600,'middle')+ui.text(cx,top+388,ui.shorten(role,44,256),44,ui.SECONDARY,400,'middle')+'</g></g>'
    return '<g data-role="detail-people">'+s+'</g>'


def episodes(series=True,rich=False,focus=False,start=1):
    s='<g data-role="detail-episodes">'
    if series:
        s+=ui.control(1488,1388,632,'第8季 · 24集' if rich else '第1季 · 12集',ico='series',size=52,selected=True)
        s+=ui.icon('down',2032,1440,40)
        for i,label in enumerate(['1–10集','11–20集' if rich else '11–12集']):
            s+=ui.control(2160+i*776,1388,736,label,size=52,selected=i==(1 if rich else 0))
    else:
        s+=ui.text(1488,1482,'选期' if rich else '选集',64,ui.FG,600)
        for i,label in enumerate(['991–1000期','1001–1010期'] if rich else ['1–10集','11–20集']):
            s+=ui.control(1900+i*736,1388,688,label,size=52,selected=i==(1 if rich or start==11 else 0))
    for i in range(10):
        x,y=1488+i%5*448,1572+i//5*184
        label=f'{1001+i}期' if rich and not series else f'第{(11 if rich else start)+i}集'
        s+=ui.control(x,y,416,label,focus and i==(6 if not rich and start==1 and not series else 0),size=52,center=True)
    return s+'</g>'


def reading():
    s=shell('movie')+body_start()
    s+=ui.text(800,646,'山河之间：归途、旧日来信与未竟的远方',80,ui.FG,650)
    s+=ui.text(800,778,'剧情简介',56,ui.FG,600)
    s+='<g data-role="detail-reader" data-focused="true">'+ui.box(800,838,2912,1110,'#191d2b',28,'#f6f4ff',8)
    lines=['一封迟到多年的来信，让离乡已久的林野重新踏上归途。',
           '穿过山川与旧城，他在沿途遇见曾经的伙伴，也逐渐了解家人未曾说出的往事。',
           '故事从一次普通的返乡开始，在不同时代的记忆之间展开。',
           '每一段相遇都改变着他对故乡的认识，也让他重新思考那些没有完成的约定。',
           '当熟悉的街巷和陌生的生活交错，他需要决定如何面对过去，又如何继续前行。',
           '这段旅程既属于一个家庭，也映照着普通人在岁月中的选择。',
           '当山海再次出现在眼前，故乡的意义也在一次次告别与重逢中变得清晰。']
    for i,line in enumerate(lines):s+=ui.text(864,964+i*132,line,56,ui.SECONDARY)
    return s+'</g></g></g></svg>'


def media_detail(kind='movie',rich=False,large=False):
    if kind=='reading':return reading()
    if kind=='series-cast':
        s=shell('series')+body_start()
        s+=ui.text(800,646,'长夜微光 · 第8季',64,ui.FG,600)
        s+='<g transform="translate(0 -688)">'+episodes(True,True,False)+'</g>'
        s+=people(focus=True,many=True,page=1)
        return s+'</g></g></svg>'
    s=shell(kind,large)+body_start(large)
    if kind=='missing':
        s+=ui.icon('alert',2192,850,128)
        s+=ui.text(2256,1110,'媒体内容已移除',80,ui.FG,600,'middle')
        s+=ui.text(2256,1230,'服务器中已找不到这项内容，请返回原列表。',52,ui.SECONDARY,400,'middle')
        s+=ui.control(1952,1350,608,'返回列表',True,size=56,center=True)
        return s+'</g></g></svg>'
    name='长夜微光' if rich and kind=='series' else '海岸日记' if kind=='series' else '山河之间：归途、旧日来信与未竟的远方' if rich else '山河之间'
    py=752 if large else 552
    s+=ui.poster(800,py,0,name,'2025','8.6','电视剧' if kind=='series' else '电影',.37,caption=False)
    lines=['山河之间：归途、旧日来信','与未竟的远方'] if rich and kind=='movie' else [name]
    for i,line in enumerate(lines):s+=ui.text(1488,(854 if large else 650)+i*(144 if large else 100),line,120 if large else 88,ui.FG,650)
    s+=ui.text(1488,1000 if large else 838,'2025 · 剧情 / 家庭 · 中国大陆',64 if large else 48,ui.SECONDARY)
    s+=ui.text(1488,1100 if large else 920,'当前库可用8季 · 第8季24集' if rich and kind=='series' else '当前库可用2季 · 第1季12集' if kind=='series' else '评分8.6 · 01:48:00 · 4K',64 if large else 48,ui.SECONDARY)
    s+=ui.text(1488,1210 if large else 1000,'剧情简介',72 if large else 56,ui.FG,600)
    s+=ui.text(1488,1310 if large else 1074,'一封迟到的来信，开启了一段穿越山河的旅程。',64 if large else 52,ui.SECONDARY)
    if not large:s+=ui.text(1488,1144,'沿途的相遇，让归乡有了新的意义……',52,ui.SECONDARY)
    labels=['继续播放','查看分集','收藏'] if kind=='series' else ['继续播放','从头播放','标为已看','收藏']
    s+=actions(labels,focus=not rich,y=1400 if large else 1200,large=large)
    if kind=='series':
        s+=episodes(True,rich,focus=rich)
        s+=people(y=2320,many=rich)
    else:s+=people(y=1780 if large else 1512,focus=rich,many=rich)
    return s+'</g></g></svg>'


def cctv_detail(data=None,episode_focus=False,error=None,episode_start=1,rich=False):
    item=data['categories']['电视剧']['items'][0] if data else None
    name=cctv.clean_title(item['title']) if item else '沿着历史的道路：文化与生活的时代记录'
    cat='电视剧' if item else '特别节目'
    s=cctv.begin('央视频节目详情','App内部记录独立；节目名称、数量按来源展示，压力示例不代表真实节目。')+cctv.shell(cat,'none')+body_start()
    s+=ui.poster(800,552,0,name,'2026',None,cat,.32 if item else None,caption=False)
    count=f"{item.get('count','')}集" if item else '1042期'
    s+=ui.box(828,580,208,76,'#11151e',18)+ui.text(932,633,count,44,ui.FG,500,'middle')
    for i,line in enumerate([name] if item else ['沿着历史的道路：','文化与生活的时代记录']):s+=ui.text(1488,650+i*100,ui.shorten(line,88,2224),88,ui.FG,650)
    meta=' · '.join(str(v) for v in [item.get('year'),item.get('sc'),item.get('channel')] if v) if item else '2026 · 文化 · CCTV-1'
    s+=ui.text(1488,838,ui.shorten(meta,48,2224),48,ui.SECONDARY)+ui.text(1488,920,'收录'+count,48,ui.SECONDARY)
    if error:
        s+=ui.text(2600,1130,'暂时无法加载分集' if error=='episode' else '暂时无法播放本集',80,ui.FG,600,'middle')
        s+=ui.text(2600,1250,'节目资料已保留，可重试或返回。',52,ui.SECONDARY,400,'middle')
        s+=actions(['重试分集' if error=='episode' else '重试播放','返回节目' if error=='episode' else '返回选集'],y=1388)
    else:
        s+=ui.text(1488,1000,'节目简介',56,ui.FG,600)+ui.text(1488,1074,'节目介绍按来源资料展示，长内容可展开阅读。',52,ui.SECONDARY)
        s+=actions(['继续播放','查看分期' if rich else '查看分集','收藏'],focus=not episode_focus and not rich)
        s+=ui.text(1488,1144,'当前观看：第1007期' if rich else '当前观看：第7集',48,ui.SECONDARY)
        s+=episodes(False,rich,focus=episode_focus or rich,start=episode_start)
    return s+'</g></g></svg>'


def scene(kind):
    return cctv_detail(rich=True) if kind=='cctv' else media_detail(kind,rich=True)


def merge(screens,output):
    by_id={s['id']:s for s in screens}
    for ident,kind,large in NORMAL:
        entry=by_id[ident]
        if entry.get('retired'):continue
        entry.update(nativeResolution=[3840,2160],libraryScene='detail-'+kind,detailLayout=True,
                     note='统一原生4K详情：主操作等宽一行，收藏/继续播放简写；详情海报下无重复文字，演职人员圆形头像及图下姓名/角色两行。大字版保持单行操作，正文随焦点滚动。示例数据，设计待实施。')
        (output/'svg'/f'{ident}.svg').write_text(media_detail(kind,large=large))
    for ident,name,kind in SCENES:
        screen=dict(id=ident,title=name,note='统一详情边界设计，待审查后实施。4K操作一行；海报下无文字；圆形人物头像下分姓名/导演或角色两行。头像与角色仅作排版示例，不对应示例片名；素材许可见assets/portraits/CREDITS.json。名称、人数、季数与集数为布局示例；App内部央视频状态与媒体账号隔离。',flow='详情简介确认 → 展开阅读；返回恢复简介焦点。演职人员与头像左边对齐海报；多页使用左右箭头翻页；选季确认加载对应分集。',kind='state',nativeResolution=[3840,2160],detailScene=kind,detailLayout=True)
        screen['cctvScene' if kind=='cctv' else 'libraryScene']='rich-detail-'+kind
        screens.append(screen)
        (output/'svg'/f'{ident}.svg').write_text(scene(kind))
    return screens

if __name__=='__main__':
    out=ROOT/'designs';screens=json.loads((out/'manifest.json').read_text())
    screens=[s for s in screens if not s.get('detailScene')]
    merge(screens,out)
    from retire_font_designs import retire
    retire(screens,out)
    (out/'manifest.json').write_text(json.dumps(screens,ensure_ascii=False,indent=2)+'\n')
