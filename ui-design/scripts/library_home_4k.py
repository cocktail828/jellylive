#!/usr/bin/env python3
"""Shared native-4K library drawing functions for the main design pack."""
from pathlib import Path
from html import escape
import ctypes as C
import json
import struct
import xml.etree.ElementTree as ET
import unicodedata

ROOT = Path(__file__).resolve().parents[1]
FG, SECONDARY = '#f3f1f8', '#b5b3c5'
ENTRIES = [
    ('白昼来信','2025','8.6','电影',.37),('长夜微光','2025','9.1','电视剧',.6),
    ('风的方向','2024','8.3','电影',0),('海岸日记','2025','8.8','电视剧',1),
    ('花间时序','2024','8.5','电视剧',0),('旧城回声','2023','8.2','电影',1),
    ('群星之间','2025','8.9','电视剧',.25),('日落以后','2024','7.8','电影',0),
    ('山河漫游','2023','8.7','电视剧',1),('远方的诗','2025','8.1','电影',.7)]


def shorten(value, size, width):
    def measure(s):
        return sum(size * (1 if unicodedata.east_asian_width(c) in ('W', 'F') else .56) for c in s)
    if measure(value) <= width:
        return value
    while value and measure(value+'…') > width:
        value = value[:-1]
    return value+'…'


def text(x, y, value, size=48, color=FG, weight=400, anchor='start'):
    return f'<text x="{x}" y="{y}" font-size="{size}" fill="{color}" font-weight="{weight}" text-anchor="{anchor}">{escape(value)}</text>'


def box(x, y, w, h, color, radius=28, stroke='none', sw=2):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}" fill="{color}" stroke="{stroke}" stroke-width="{sw}"/>'


def icon(name, x, y, size=48, color=SECONDARY):
    paths = {
        'search': 'M27 15a12 12 0 1 1-24 0a12 12 0 1 1 24 0M24 24l10 10',
        'down': 'M8 14l10 10l10-10',
        'grid': 'M4 4h11v11H4zM22 4h11v11H22zM4 22h11v11H4zM22 22h11v11H22z',
        'movie': 'M4 7h28v25H4zM4 15h28M13 7l5 8M24 7l5 8',
        'series': 'M4 7h28v21H4zM12 33h12M18 28v5',
        'history': 'M6 11a14 14 0 1 1-1 14M5 3v10h10M18 9v10l7 4',
        'sort': 'M4 7h19M4 18h13M4 29h7M28 5v26M23 26l5 5l5-5',
        'star': 'M18 3l4.6 9.6L33 14l-7.5 7.3L27.3 32L18 27l-9.3 5l1.8-10.7L3 14l10.4-1.4z',
        'check': 'M5 18l9 9L31 8',
        'alert': 'M18 3L34 32H2zM18 12v9M18 26v1',
        'refresh': 'M30 13A13 13 0 1 0 30 25M30 4v10H20',
    }
    return f'<g transform="translate({x} {y}) scale({size/36})"><path d="{paths[name]}" fill="none" stroke="{color}" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></g>'


def poster(x, y, index, title, year, score, kind, progress, focus=False, fs=1,watch_state=None,focus_sw=8,quality=None):
    palettes = [('#2e3e43', '#152126', '#a4bdb4'), ('#39354c', '#1c1b2a', '#b4a5ce'),
                ('#454034', '#24221c', '#c8b792'), ('#35414d', '#18232d', '#99b4c8'),
                ('#4b3539', '#281b23', '#d2a3a9')]
    top, bottom, accent = palettes[index % 5]
    key = f'art-{index}'
    s = f'<g data-role="media-card" data-focused="{str(focus).lower()}"><title>{escape(title)}</title>'
    s += f'<defs><linearGradient id="{key}" x2="0" y2="1"><stop stop-color="{top}"/><stop offset="1" stop-color="{bottom}"/></linearGradient><clipPath id="clip-{index}">{box(x,y,544,816,"white",32)}</clipPath></defs>'
    s += f'<g filter="url(#card-shadow)">{box(x,y,544,816,f"url(#{key})",32)}</g>'
    s += f'<g clip-path="url(#clip-{index})">'
    # Deliberately abstract, shared poster system: no people or photographic scenes.
    s += f'<circle cx="{x+272}" cy="{y+330}" r="178" fill="none" stroke="{accent}" stroke-width="2" opacity=".3"/>'
    s += f'<circle cx="{x+272}" cy="{y+330}" r="132" fill="none" stroke="{accent}" stroke-width="2" opacity=".22"/>'
    if index % 3 == 0:
        s += f'<path d="M{x+164} {y+475}V{y+295}a108 108 0 0 1 216 0v180" fill="none" stroke="{accent}" stroke-width="34" opacity=".58"/>'
        s += f'<path d="M{x+272} {y+235}v280" stroke="{accent}" stroke-width="2" opacity=".5"/>'
    elif index % 3 == 1:
        s += f'<circle cx="{x+272}" cy="{y+330}" r="99" fill="{accent}" opacity=".5"/>'
        s += f'<path d="M{x+96} {y+350}h352M{x+96} {y+382}h352M{x+96} {y+414}h352" stroke="{bottom}" stroke-width="14"/>'
    else:
        s += f'<path d="M{x+272} {y+170}l128 160l-128 160l-128-160z" fill="{accent}" opacity=".36"/>'
        s += f'<path d="M{x+272} {y+220}l88 110l-88 110l-88-110z" fill="none" stroke="{accent}" stroke-width="3"/>'
    s += '</g>'
    if score is not None:
        s += box(x+28,y+28,144,76,'#11151e',18)+icon('star',x+44,y+47,34,'#e0c790')+text(x+90,y+82,score,44,'#e9d4a9',600)
    if quality:
        w=176 if len(quality)>3 else 104
        s += '<g data-role="quality-badge">'+box(x+516-w,y+680,w,76,'#11151e',18)+text(x+516-w/2,y+733,quality,44,FG,500,'middle')+'</g>'
    status = watch_state or ('进度未知' if progress is None else '已看' if progress == 1 else '未看' if progress == 0 else '在看')
    if progress is not None:
        s += f'<g data-role="watch-progress" data-progress="{progress}"><title>{status} · {round(progress*100)}%</title>'
        s += box(x+28,y+780,488,12,'#5b5968',6)
        if progress > 0:
            s += box(x+28,y+780,488*progress,12,'#c3b0e7',6)
        s += '</g>'
    if focus:
        margin = 10 if fs == 1 else 8
        s += box(x-margin,y-margin,544+2*margin,816+2*margin,'none',42,'#f6f4ff',focus_sw)
    s += '<g data-role="card-caption">'
    s += text(x+272,y+900,shorten(title,60*fs,504),60*fs,FG,600,'middle')
    s += text(x+272,y+972,shorten(f'{year or "年份未知"} · {kind}',48*fs,504),48*fs,SECONDARY,400,'middle')
    s += '</g>'
    # Full title is retained for the focused marquee below the artwork.
    s += f'<desc data-role="focus-title" data-delay-ms="800" data-speed-4k-px-s="60">{escape(title)}</desc>'
    return s+'</g>'


def control(x,y,w,label,focus=False,ico=None,size=48,h=144,selected=False,disabled=False,center=False):
    s = f'<g data-role="control" data-focused="{str(focus).lower()}" data-disabled="{str(disabled).lower()}" data-label="{escape(label)}" opacity="{.45 if disabled else 1}">'
    s += box(x,y,w,h,'#373047' if selected else '#242631',28)
    if focus:
        s += box(x+4,y+4,w-8,h-8,'none',24,'#f6f4ff',8)
    if ico:
        s += icon(ico,x+32,y+(h-44)/2,44)
    s += text(x+w/2 if center else x+(100 if ico else 32),y+h/2+size*.34,label,size,'#dfdce8',500,'middle' if center else 'start')
    return s+'</g>'


def dropdown(which,history=False):
    options = {
        '类型': ['全部','剧情','喜剧','悬疑','科幻','动作','纪录片','动画'],
        '年份': ['全部','2025','2024','2023','未知年份'],
        '评分': ['不限','6分及以上','7分及以上','8分及以上','9分及以上'],
        '进度': ['全部','未看','在看','已看'],
        '排序': ['名称升序','名称降序','添加时间：从新到旧','添加时间：从旧到新','发布时间：从新到旧','发布时间：从旧到新','评分：从高到低','评分：从低到高'],
    }[which]
    if history and which == '排序':
        options = ['最近观看','最早观看',*options]
    x = {'类型':1736,'年份':2238,'评分':2740,'进度':2812,'排序':2664}[which]
    y,w = (520,1048) if which == '排序' else (304,900)
    if history and which == '排序':
        y = 304
    height = 144+len(options)*144+32
    s = f'<g data-role="option-panel" data-option="{which}">'
    s += box(x,y,w,height,'#252633',32,'#565264',2)+text(x+48,y+90,which,56,FG,600)
    for i,label in enumerate(options):
        ry = y+128+i*144
        if i == 0:
            s += box(x+24,ry,w-48,144,'#3b334b',20)
        if i == 1:
            s += f'<g data-role="menu-option" data-focused="true">'+box(x+24,ry,w-48,144,'#302f3f',20,'#f6f4ff',8)+'</g>'
        s += text(x+56,ry+92,label,56,FG,500)
        if i == 0:
            s += icon('check',x+w-104,ry+48,48,'#d8c3f4')
    return s+'</g>'


def home(section='全部媒体',focus='card-0',filters=None,sort='名称升序',state='ready',menu=None,edge=False,large=False,append_error=False):
    filters = filters or {}
    unavailable = state in ('loading','offline','empty-library')
    s = '''<svg xmlns="http://www.w3.org/2000/svg" width="3840" height="2160" viewBox="0 0 3840 2160" role="img" aria-labelledby="title desc">
<title id="title">媒体库首页 · 4K 遥控器设计</title>
<desc id="desc">左上当前媒体库名称，左侧全部媒体、电影、电视剧、观看记录、收藏。顶部搜索和四项筛选，内容区右上排序，五列占位海报。首张卡片白框为遥控焦点。媒体信息均为示例。</desc>
<defs>
<linearGradient id="background" x2="1" y2="1"><stop stop-color="#1b1c29"/><stop offset=".5" stop-color="#10131c"/><stop offset="1" stop-color="#0c1017"/></linearGradient>
<linearGradient id="sidebar" x2="1" y2="0"><stop stop-color="#171924"/><stop offset="1" stop-color="#171924" stop-opacity=".2"/></linearGradient>
<linearGradient id="selection"><stop stop-color="#373047"/><stop offset="1" stop-color="#302c40"/></linearGradient>
<linearGradient id="bottom-fade" x2="0" y2="1"><stop stop-color="#0d1119" stop-opacity="0"/><stop offset="1" stop-color="#0d1119"/></linearGradient>
<filter id="card-shadow" x="-15%" y="-10%" width="130%" height="130%" color-interpolation-filters="sRGB"><feGaussianBlur in="SourceAlpha" stdDeviation="22"/><feOffset dx="0" dy="20"/><feComponentTransfer><feFuncA type="linear" slope=".24"/></feComponentTransfer><feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge></filter>
<clipPath id="grid-viewport"><rect x="774" y="526" width="2964" height="1506"/></clipPath>
</defs>
<rect width="3840" height="2160" fill="url(#background)"/>
<rect width="704" height="2160" fill="url(#sidebar)"/>
<g font-family="Noto Sans CJK SC, sans-serif">
'''
    s += text(128,226,'客厅媒体库',72 if large else 64,FG,600)
    for i,(label,ico) in enumerate([('全部媒体','grid'),('电影','movie'),('电视剧','series'),('观看记录','history'),('收藏','star')]):
        s += f'<g data-role="sidebar-entry" data-focused="{str(focus=="sidebar" and label==section).lower()}">'
        y = 376+i*184
        if label == section:
            s += box(128,y,512,144,'url(#selection)',28)
        if focus == 'sidebar' and label == section:
            s += box(132,y+4,504,136,'none',24,'#f6f4ff',8)
        s += icon(ico,168,y+48,48,'#e3daef' if label == section else SECONDARY)
        s += text(252,y+94,label,72 if large else 56,FG if label == section else SECONDARY,600 if label == section else 400)
        s += '</g>'
    query = '搜索电视剧' if section == '电视剧' else '搜索电影' if section == '电影' else '搜索电影、电视剧'
    s += control(800,128,2912 if large else 904,query,focus=='search','search',72 if large else 52,h=176 if large else 144,disabled=unavailable)
    for i,name in enumerate(['类型','年份','评分','进度']):
        x = 800+i*736 if large else 1736+i*502
        y,w = (344,704) if large else (128,470)
        value = filters.get(name,'不限' if name=='评分' else '全部')
        label = f'{name}：{value.replace("分及以上","分+")}'
        size = 64 if large else 48
        s += control(x,y,w,shorten(label,size,w-112),focus==name,size=size,h=160 if large else 144,selected=name in filters,disabled=unavailable)
        s += icon('down',x+w-80,y+56,40,'#656375' if unavailable else SECONDARY)
    title_y,sort_y,grid_y = (654,544,752) if large else (452,350,552)
    s += text(800,title_y,section,88 if large else 80,FG,650)
    if filters and state != 'no-results':
        s += control(2560,sort_y,592,'清除筛选',focus=='clear',size=48)
    sort_x,sort_w = (2960,752) if large else (3200,512)
    s += control(sort_x,sort_y,sort_w,sort,focus=='sort','sort',64 if large else 48,disabled=unavailable)
    s += icon('down',sort_x+sort_w-74,sort_y+52,40,'#656375' if unavailable else SECONDARY)
    entries = list(ENTRIES)
    if section in ('电影','电视剧'):
        entries = [e for e in entries if e[3] == section]
    if section == '收藏':
        entries = [ENTRIES[i] for i in [1,3,7,0]]
    if section == '观看记录':
        entries = [ENTRIES[i] for i in [6,0,9,3,1,5,8]]
    if filters.get('进度') == '在看':
        entries = [e for e in entries if e[4] is not None and 0<e[4]<1]
    if edge:
        entries = [('山河之间：穿越漫长岁月的归途与远方',None,None,'电影',None),
                   ('没有评分的电影','2024',None,'电影',0),('续播但比例未知','2025','8.3','电视剧',None),
                   ('已看完整示例','2023','8.8','电影',1),('在看片段示例','2025','8.5','电影',.37)]
    if state != 'ready':
        entries = []
    if append_error:
        entries = entries[:5]
    s += '<g clip-path="url(#grid-viewport)" data-role="focus-following-grid">'
    for i,entry in enumerate(entries[:4] if large else entries):
        active = focus == f'card-{i}' and not menu
        quality=None if edge and i==0 else ('4K' if i%3==0 else '1080p' if i%3==1 else '720p')
        if large:
            scale = 688/544
            s += f'<g transform="translate({800+i*736} {grid_y}) scale({scale})">'+poster(0,0,i,*entry,focus=active,fs=1.5/scale,focus_sw=8/scale,quality=quality)+'</g>'
        else:
            s += poster(800+i%5*588,grid_y+i//5*1088,i,*entry,focus=active,watch_state='在看' if edge and i==2 else None,quality=quality)
    if state == 'loading':
        for i in range(5):
            x = 800+i*588
            s += box(x,552,544,816,'#222631',32)+box(x+72,1430,400,40,'#2d303d',16)+box(x+128,1512,288,28,'#272c38',12)
        s += text(2256,1770,'正在加载媒体库…',56,SECONDARY,400,'middle')
    elif state != 'ready':
        title,detail,action = {
            'no-results': ('没有符合条件的媒体','试试调整类型、年份、评分或进度。','清除筛选'),
            'empty-history': ('暂无观看记录','当前媒体服务器账号还没有观看记录。','浏览全部媒体'),
            'empty-favorites': ('暂无收藏','在详情中收藏电影或剧集后，会在这里显示。','浏览全部媒体'),
            'unsupported': ('服务器暂不支持'+section,'此列表依赖当前媒体服务器的账号数据。','浏览全部媒体'),
            'empty-library': ('媒体库暂无内容','在媒体服务中添加电影或电视剧后，重新加载。','重新加载'),
            'offline': ('暂时无法连接媒体库','请检查媒体服务和网络连接，再试一次。','重试连接'),
        }[state]
        s += icon('alert' if state=='offline' else 'history' if state=='empty-history' else 'grid',2192,800,128,SECONDARY)
        s += text(2256,1090,title,80,FG,600,'middle')+text(2256,1206,detail,52,SECONDARY,400,'middle')
        s += control(1952,1328,608,action,True,size=56,center=True)
    s += '</g>'+box(774,1920,2964,112,'url(#bottom-fade)',0)
    if append_error:
        s += text(2256,1760,'后续内容加载失败，已加载的影片仍可浏览。',52,SECONDARY,400,'middle')
        s += control(1952,1840,608,'重试加载',True,size=56,center=True)
    if menu:
        s += dropdown(menu,section=='观看记录')
    return s+'</g></svg>'


def render(svg, target, w, h):
    from render_designs import new, render as draw, surface, context, write, destroy, finish, unref, bind, cairo
    scale = bind(cairo,'cairo_scale',[C.c_void_p,C.c_double,C.c_double],None)
    handle = new(str(svg).encode(),None)
    if not handle:
        raise RuntimeError('SVG load failed')
    image = surface(0,w,h)
    cr = context(image)
    scale(cr,w/3840,h/2160)
    try:
        if not draw(handle,cr) or write(image,str(target).encode()):
            raise RuntimeError('SVG render failed')
    finally:
        destroy(cr)
        finish(image)
        unref(handle)


SCENES = [
    ('home','媒体库首页',{},'图15新版；五列网格，首卡为遥控焦点。'),
    ('movies','电影分类',{'section':'电影'},'图16电影分类；搜索仅查电影，筛选沿用当前分类。'),
    ('series','电视剧分类',{'section':'电视剧'},'图16电视剧分类；观看进度表示当前库内追看进度。'),
    ('history','观看记录',{'section':'观看记录','sort':'最近观看'},'按最近观看时间排序，包含在看和已看内容。'),
    ('history-sort','观看记录排序',{'section':'观看记录','sort':'最近观看','menu':'排序','focus':'none'},'观看记录额外提供最近观看、最早观看；长菜单向上避让安全区。'),
    ('filter-type','类型筛选展开',{'menu':'类型','focus':'none'},'紫底与勾选为已选值；白框为待确认焦点，返回取消。'),
    ('filter-year','年份筛选展开',{'menu':'年份','focus':'none'},'按可用年份从新到旧列出，缺失值单列未知年份。'),
    ('filter-rating','评分筛选展开',{'menu':'评分','focus':'none'},'按最低评分过滤；不限包含未评分条目。'),
    ('filter-progress','进度筛选展开',{'menu':'进度','focus':'none'},'固定顺序：全部、未看、在看、已看。'),
    ('sort','排序展开',{'menu':'排序','focus':'none'},'四个排序字段各有两个方向；勾选与焦点分开显示。'),
    ('filtered','筛选已生效',{'filters':{'进度':'在看'},'focus':'进度'},'当前值和紫底保留筛选状态；清除筛选单独可聚焦。'),
    ('no-results','筛选无结果',{'filters':{'类型':'悬疑','年份':'2025','评分':'9分及以上','进度':'在看'},'state':'no-results','focus':'none'},'保留筛选；确认清除全部条件，返回不修改条件。'),
    ('empty-history','暂无观看记录',{'section':'观看记录','sort':'最近观看','state':'empty-history','focus':'none'},'图41语义更新；无记录与连接故障分开，提供浏览全部媒体。'),
    ('empty-library','媒体库空内容',{'state':'empty-library','focus':'none'},'已成功连接但没有媒体，提供重新加载。'),
    ('loading','首次加载',{'state':'loading','focus':'sidebar'},'骨架不展示虚构片名、评分和数量，导航仍可操作。'),
    ('offline','连接失败',{'state':'offline','focus':'none'},'连接失败不伪装成空库；提供明确重试入口。'),
    ('append-error','追加加载失败',{'append_error':True,'focus':'none'},'保留已加载的卡片，末尾提供重试；图示为用户主动移到重试后的焦点。'),
    ('metadata','长标题与缺失元数据',{'edge':True},'长标题保持字号并截断；缺评分不显示星标，未知进度不画假进度条。'),
    ('large-text','大字号适配',{'large':True},'四列网格，片名90px、元信息72px；顶部搜索与筛选改为两行。'),
    ('focus-sidebar','左侧导航焦点',{'focus':'sidebar'},'白框与紫色分类底色叠加，海报不再保留第二个焦点。'),
    ('focus-search','搜索入口焦点',{'focus':'search'},'确认进入搜索输入；搜索词、条件与列表位置可恢复。'),
    ('focus-sort','排序入口焦点',{'focus':'sort'},'首行最右列上键到排序，再上键到进度；左侧各列上键到就近顶部控件。'),
    ('favorites','收藏',{'section':'收藏'},'当前媒体服务器、当前账号的收藏；与央视频和直播频道收藏完全隔离。'),
    ('empty-favorites','收藏为空',{'section':'收藏','state':'empty-favorites','focus':'none'},'仅服务器成功返回空收藏时展示；详情收藏由服务器确认后更新。'),
    ('unsupported','服务器能力不可用',{'section':'收藏','state':'unsupported','focus':'none'},'服务器不支持收藏时明确说明；观看记录同规则，不用App本地数据替代。'),
]


def scene_svg(title,params,note):
    source = home(**params)
    source = source.replace('媒体库首页 · 4K 遥控器设计',escape(title+' · 4K 遥控器设计'))
    desc_start = source.index('<desc id="desc">')+len('<desc id="desc">')
    desc_end = source.index('</desc>')
    return source[:desc_start]+escape(note+' 媒体信息均为设计示例。')+source[desc_end:]
