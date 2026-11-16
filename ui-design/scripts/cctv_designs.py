#!/usr/bin/env python3
"""Canonical CCTV designs, integrated into the main design pack."""
from pathlib import Path
import sys,json,re,struct
from html import escape
import library_home_4k as ui

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'designs'
CATS=['电视剧','动画片','纪录片','特别节目']
NAV=CATS+['观看记录','收藏']
FILTERS={'电视剧':['类型','地区','年份'],'动画片':['类型','地区'],
         '纪录片':['类型','频道','年份'],'特别节目':['类型','频道','节日'],'观看记录':['分类','进度'],'收藏':['分类','进度']}
OPTIONS={
    '类型':['全部','谍战','悬疑','刑侦','历史','古装','武侠','军旅'],
    '年份':['全部','2026','2025','2024','2023','2022','2021','2020'],
    '频道':['全部','CCTV-1 综合','CCTV-2 财经','CCTV-3 综艺','CCTV-4 中文国际','CCTV-9 纪录','CCTV-10 科教','CCTV-14 少儿'],
    '节日':['全部','春节','元宵','清明','劳动节','青年节','六一','端午'],
}


def begin(title,note):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="3840" height="2160" viewBox="0 0 3840 2160" role="img" aria-labelledby="title desc">
<title id="title">{escape(title)}</title><desc id="desc">{escape(note)}</desc><defs>
<linearGradient id="background" x2="1" y2="1"><stop stop-color="#1b1c29"/><stop offset=".5" stop-color="#10131c"/><stop offset="1" stop-color="#0c1017"/></linearGradient>
<linearGradient id="sidebar" x2="1" y2="0"><stop stop-color="#171924"/><stop offset="1" stop-color="#171924" stop-opacity=".2"/></linearGradient>
<linearGradient id="purple"><stop stop-color="#6652d5"/><stop offset="1" stop-color="#3a327d"/></linearGradient>
<filter id="card-shadow" x="-15%" y="-10%" width="130%" height="130%" color-interpolation-filters="sRGB"><feGaussianBlur in="SourceAlpha" stdDeviation="22"/><feOffset dy="20"/><feComponentTransfer><feFuncA type="linear" slope=".24"/></feComponentTransfer><feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge></filter>
<linearGradient id="fade" x2="0" y2="1"><stop stop-color="#0d1119" stop-opacity="0"/><stop offset="1" stop-color="#0d1119"/></linearGradient>
<clipPath id="viewport"><rect x="774" y="526" width="2964" height="1506"/></clipPath>
</defs><rect width="3840" height="2160" fill="url(#background)"/><rect width="704" height="2160" fill="url(#sidebar)"/>
<g font-family="Noto Sans CJK SC, sans-serif">'''


def clean_title(value):
    value=re.sub(r'^纪录片《(.*)》$',r'\1',value)
    return value[1:-1] if value.startswith('《') and value.endswith('》') else value


def shell(cat,focus='card-0',chosen=None,large=False,disabled=False):
    chosen=chosen or {}
    s=ui.text(128,226,'央视频',72,ui.FG,600)
    for i,label in enumerate(NAV):
        y=376+i*184
        s+=f'<g data-role="sidebar-entry" data-category="{label}" data-focused="{str(focus=="sidebar" and label==cat).lower()}">'
        s+=ui.box(128,y,512,144,'#373047' if label==cat else 'none',28)
        if focus=='sidebar' and label==cat:s+=ui.box(132,y+4,504,136,'none',24,'#f6f4ff',8)
        s+=ui.icon('history' if i==4 else 'star' if i==5 else 'series' if i<2 else 'movie',168,y+48,48)
        s+=ui.text(252,y+94,label,72 if large else 56,ui.FG if label==cat else ui.SECONDARY,600 if label==cat else 400)+'</g>'
    s+=ui.control(800,128,2912 if large else 904,'首字母：'+chosen.get('首字母','全部'),focus=='首字母','search',64 if large else 52,h=176 if large else 144,disabled=disabled,selected='首字母' in chosen)
    s+=ui.icon('down',3600 if large else 1610,192,40,ui.SECONDARY if not disabled else '#656375')
    names=FILTERS[cat]
    for i,name in enumerate(names):
        if large:
            x=800+i*992;w=928 if len(names)==3 else 1424;x=800+i*(w+64);y=344
        elif len(names)==2:
            x=1736+i*1004;w=972;y=128
        else:
            x=1736+i*668;w=640 if i==2 else 636;y=128
        label=name+'：'+chosen.get(name,'全部')
        s+=ui.control(x,y,w,ui.shorten(label,64 if large else 48,w-112),focus==name,size=64 if large else 48,h=160 if large else 144,disabled=disabled,selected=name in chosen)
        s+=ui.icon('down',x+w-80,y+56,40,ui.SECONDARY if not disabled else '#656375')
    ty,by=(654,544) if large else (452,350)
    s+=ui.text(800,ty,cat,88 if large else 80,ui.FG,650)
    if cat in CATS:
        s+=ui.control(3408,by,304,'刷新',focus=='refresh','refresh',48,disabled=disabled)
    else:
        s+=ui.control(3056,by,656,'最近观看' if cat=='观看记录' else '最近收藏',focus=='sort','sort',48)
    return s


def card(item,x,y,index,focus=False,large=False,progress=0,missing=False):
    title=clean_title(item['title'])
    count=item.get('count')
    count_label=f'{count}{"期" if item["fc"]=="特别节目" else "集"}' if str(count).isdigit() else None
    year=item.get('year')
    a,b=(year,count_label or item['fc']) if year else (count_label or item['fc'],item.get('sc') or item['fc'])
    scale=688/544 if large else 1
    # A known local viewing history fixture; no viewing state is read from CCTV.
    content=ui.poster(0,0,index,title,a,None,b,None if missing else progress,focus=focus,
                      fs=1.5/scale if large else 1,focus_sw=8/scale)
    if count_label:
        content+=ui.box(28,28,208,76,'#11151e',18)+ui.text(132,81,count_label,44,'#dfdce8',500,'middle')
    return f'<g transform="translate({x} {y}) scale({scale})">{content}</g>'


def panel(which,cat):
    if which=='首字母':
        s=ui.box(800,304,1120,1056,'#252633',32,'#565264',2)+ui.text(848,394,'首字母',56,ui.FG,600)
        for i,label in enumerate(['全部',*'ABCDEFGHIJKLMNOPQRSTUVWXYZ']):
            x,y=848+i%6*168,448+i//6*168
            s+=ui.control(x,y,144,label,i==1,size=56,selected=i==0,center=True)
        return s
    labels=OPTIONS[which]
    x=1736 if which=='类型' else 2404 if which=='频道' else 2812
    s=ui.box(x,304,900,144+len(labels)*144+48,'#252633',32,'#565264',2)+ui.text(x+48,394,which,56,ui.FG,600)
    for i,label in enumerate(labels):
        y=432+i*144
        s+=ui.control(x+24,y,852,label,i==1,size=56,selected=i==0)
        if i==0:s+=ui.icon('check',x+796,y+48,48,'#d8c3f4')
    s+=ui.icon('down',x+428,304+144+len(labels)*144+8,32)
    return s


def browse(data,cat='电视剧',focus='card-0',menu=None,state='ready',chosen=None,large=False,missing=False):
    chosen=chosen or {}
    disabled=state in ('loading','offline')
    s=shell(cat,focus if not menu else 'none',chosen,large,disabled)
    rows=list(data['categories'][cat]['items']) if cat in CATS else [item for category in CATS for item in data['categories'][category]['items'][:1 if cat=='观看记录' else 2]]
    if chosen.get('类型'):rows=[r for r in rows if r.get('sc')==chosen['类型']]
    if state in ('loading','offline','empty','local-empty','episode-error','play-error'):rows=[]
    if state=='append-error':rows=rows[:5]
    s+='<g clip-path="url(#viewport)">'
    for i,item in enumerate(rows[:4] if large else rows):
        x=800+i*736 if large else 800+i%5*588
        y=752 if large else 552+i//5*1088
        progress=.32 if cat=='观看记录' or cat=='收藏' and i%2==0 else 0 if cat=='收藏' else [.32,0,0,1,0,.6,0,0,0,0][i]
        s+=card(item,x,y,i,focus==f'card-{i}' and not menu,large,progress,missing and i==0)
    if state=='loading':
        for i in range(5):
            x=800+i*588;s+=ui.box(x,552,544,816,'#222631',32)+ui.box(x+72,1430,400,40,'#2d303d',16)
        s+=ui.text(2256,1760,'正在加载节目…',56,ui.SECONDARY,400,'middle')
    elif state in ('offline','empty','local-empty','episode-error','play-error'):
        title,detail,action={
            'local-empty':('还没有观看记录' if cat=='观看记录' else '还没有收藏节目','浏览四类节目，找到想看的内容。','浏览电视剧'),
            'offline':('暂时无法加载节目','请检查网络连接，再试一次。','重新加载'),
            'empty':('没有符合条件的节目','调整筛选条件，查看其他节目。','清除筛选'),
            'episode-error':('暂时无法加载分集','节目资料仍保留，可以稍后重试。','重试分集'),
            'play-error':('暂时无法播放本集','可以重试，或返回选集选择其他内容。','重试播放'),
        }[state]
        s+=ui.icon('alert' if state!='empty' else 'grid',2192,800,128)
        s+=ui.text(2256,1090,title,80,ui.FG,600,'middle')+ui.text(2256,1206,detail,52,ui.SECONDARY,400,'middle')
        s+=ui.control(1952,1328,608,action,True,size=56,center=True)
        if state in ('episode-error','play-error'):
            s+=ui.control(1952,1520,608,'返回选集' if state=='play-error' else '返回节目',size=56,center=True)
    s+='</g>'+ui.box(774,1920,2964,112,'url(#fade)',0)
    if state=='append-error':
        s+=ui.text(2256,1760,'后续节目加载失败，已加载内容仍可浏览。',52,ui.SECONDARY,400,'middle')
        s+=ui.control(1952,1840,608,'重试加载',True,size=56,center=True)
    if menu:s+=panel(menu,cat)
    return s


def details(data,episode_focus=False,error=None,episode_start=1):
    s=shell('电视剧','none')
    item=data['categories']['电视剧']['items'][0]
    title=clean_title(item['title'])
    s+=card(item,800,552,0,False,progress=.32)
    s+=ui.text(1488,650,ui.shorten(title,96,2150),96,ui.FG,650)
    meta=' · '.join(str(v) for v in [item.get('year'),item.get('sc'),item.get('channel')] if v)
    s+=ui.text(1488,758,meta,48,ui.SECONDARY)
    s+=ui.text(1488,854,'收录'+str(item.get('count',''))+'集',48,ui.SECONDARY)
    if error:
        heading='暂时无法加载分集' if error=='episode' else '暂时无法播放本集'
        message='节目资料已保留，请重试加载分集。' if error=='episode' else '节目与选集位置已保留，可以重试或重新选集。'
        s+=ui.icon('alert',2536,928,128)
        s+=ui.text(2600,1190,heading,80,ui.FG,600,'middle')+ui.text(2600,1310,message,48,ui.SECONDARY,400,'middle')
        s+=ui.control(1952,1456,608,'重试分集' if error=='episode' else '重试播放',True,size=56,center=True)
        s+=ui.control(2608,1456,608,'返回节目' if error=='episode' else '返回选集',size=56,center=True)
        return s
    s+=ui.text(1488,992,'节目简介',56,ui.FG,600)
    s+=ui.text(1488,1084,'节目介绍按来源资料展示，长内容可展开阅读。',52,ui.SECONDARY)
    s+=ui.control(1488,1180,736,'继续播放 · 第7集',not episode_focus,size=56,center=True)
    s+=ui.control(2272,1180,608,'查看分集',size=56,center=True)
    s+=ui.control(2928,1180,608,'收藏',size=56,center=True)
    s+=ui.text(1488,1460,'选集',64,ui.FG,600)
    s+=ui.control(1900,1364,688,'1–10集',size=56,center=True,selected=episode_start==1)
    s+=ui.control(2636,1364,688,'11–20集',size=56,center=True,selected=episode_start==11)
    for i in range(10):
        x,y=1488+i%5*452,1536+i//5*184
        s+=ui.control(x,y,416,f'第{i+episode_start}集',episode_focus and i==(6 if episode_start==1 else 0),size=56,center=True)
    return s


SCENES=[
 ('drama','电视剧',{},'默认分类，按fc请求；元信息来自少量真实字段样本，本机进度为布局示例。'),
 ('animation','动画片',{'cat':'动画片'},'两个有效筛选扩宽；缺年份显示数量与题材，不摆放虚假年份按钮。'),
 ('documentary','纪录片',{'cat':'纪录片'},'类型、频道、年份；不显示未提供的评分。'),
 ('special','特别节目',{'cat':'特别节目'},'类型、频道、节日；保留原期名，数量不解释为已完结。'),
 ('history','观看记录',{'cat':'观看记录'},'四类节目的本机记录合并，按最近观看；数据为布局示例。'),
 ('favorites','收藏',{'cat':'收藏'},'四类节目的本机收藏合并，详情可收藏/取消收藏；数据为布局示例。'),
 ('history-empty','观看记录为空',{'cat':'观看记录','state':'local-empty','focus':'none'},'仅本机记录确认读完且为空时展示，不混同加载失败。'),
 ('favorites-empty','收藏为空',{'cat':'收藏','state':'local-empty','focus':'none'},'无收藏时可进入电视剧浏览；本机读取失败另示重试，已有记录不清空。'),
 ('alphabet','首字母查找',{'menu':'首字母'},'源站支持的全部和A–Z；这不是全库任意关键词搜索。'),
 ('genre','类型筛选',{'menu':'类型'},'紫底勾选表示生效条件，白框表示下移后的待确认项；还有更多题材可滚动。'),
 ('year','年份筛选',{'menu':'年份'},'按实际源站年份选择，列表向下可继续浏览更早年份。'),
 ('channel','频道筛选',{'cat':'纪录片','menu':'频道'},'频道仅在纪录片和特别节目中提供。'),
 ('festival','节日筛选',{'cat':'特别节目','menu':'节日'},'选项来自源站节日分类，更多项以滚动方式展开。'),
 ('filtered','类型筛选生效',{'chosen':{'类型':'农村'},'focus':'类型'},'只展示真实样本中题材为农村的节目，保留当前条件。'),
 ('detail','节目详情',{'detail':True},'播放按钮与选集布局提案；完整分集和媒体直链尚未验证。'),
 ('episodes','遥控选集',{'detail':True,'episode_focus':True},'分集数字仅为布局示例；详情返回恢复来源卡片。'),
 ('episodes-later','后半分集区间',{'detail':True,'episode_focus':True,'episode_start':11},'区间按钮可聚焦；切换至11–20集后恢复区间内焦点，数字为布局示例。'),
 ('loading','首屏加载',{'state':'loading','focus':'sidebar'},'保留六项侧栏可切换，工具暂不可用，骨架不虚构节目。'),
 ('empty','筛选无结果',{'state':'empty','focus':'none','chosen':{'类型':'悬疑','年份':'2020'}},'仅完整范围确认零项时使用；清除条件保留当前分类。'),
 ('offline','首屏加载失败',{'state':'offline','focus':'none'},'失败和空分类分开处理，提供明确重试。'),
 ('append-error','后续加载失败',{'state':'append-error','focus':'none'},'保留已加载卡片；图示为用户主动移到重试后的焦点。'),
 ('episode-error','分集加载失败',{'detail':True,'error':'episode'},'操作提案；保留节目封面、名称和资料，重试或回节目详情。'),
 ('play-error','本集播放失败',{'detail':True,'error':'play'},'保留节目身份并提供重试/返回选集；不宣称节目直链已打通。'),
 ('large','大字号四列',{'large':True},'片名90px、元信息72px，查找与筛选分两行。'),
 ('metadata','缺少本机进度',{'missing':True},'本机记录未读出时不冒充未看，也不画虚构比例。'),
 ('sidebar','侧栏遥控焦点',{'cat':'纪录片','focus':'sidebar'},'确认切换分类；白框焦点与当前分类底色分开。'),
]



def scene_svg(title,params,note):
    data=json.loads((ROOT/'scripts/data/cctv-source-samples.json').read_text())
    params=dict(params)
    if params.pop('detail',False):body=details(data,**params)
    else:body=browse(data,**params)
    return begin(title,note)+body+'</g></svg>'


def merge(screens,output):
    assert len(screens)==120, 'Keep existing figure numbers stable before adding CCTV'
    figures=[]
    for slug,title,params,note in SCENES:
        ident='cctv-'+slug
        screen=dict(id=ident,title='央视频：'+title,note=note+' 原生4K；观看记录与收藏由App内部按节目ID保存，与媒体服务器和直播频道收藏隔离。',
                    flow='首页央视频 → 图121；四分类 → 图121–124；观看记录 → 图125；收藏 → 图126；详情/选集 → 图135–137；返回恢复来源位置。',
                    kind='main' if slug in ('drama','animation','documentary','special','history','favorites','detail') else 'state',
                    nativeResolution=[3840,2160],cctvScene=slug)
        screens.append(screen)
        (output/'svg'/f'{ident}.svg').write_text(scene_svg(title,params,note))
        figures.append(dict(number=len(screens),id=ident,scene=slug))
    (output/'cctv-integration.json').write_text(json.dumps(dict(figures=figures,sourceSamples='scripts/data/cctv-source-samples.json',storage='app-internal-cctv',merged=True),ensure_ascii=False,indent=2)+'\n')


if __name__=='__main__':
    screens=json.loads((OUT/'manifest.json').read_text())
    screens=[s for s in screens if not s.get('cctvScene')]
    merge(screens,OUT)
    (OUT/'manifest.json').write_text(json.dumps(screens,ensure_ascii=False,indent=2)+'\n')
    print('Merged',len(SCENES),'CCTV scenes; previous figure numbers preserved.')
