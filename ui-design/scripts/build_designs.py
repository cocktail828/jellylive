#!/usr/bin/env python3
"""Build the JellyLive SVG design pack using the Python standard library."""
from pathlib import Path
from html import escape
import json
import math
import unicodedata
import base64
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'designs'
INK, MUTED, PANEL, ACCENT = '#f6f5ff', '#a9adc6', '#171c32', '#7561ed'
SCREENS = []
SCREEN_COPY = json.loads((Path(__file__).parent/'screen_copy.json').read_text())

def text(x,y,s,size=28,color=INK,weight=400):
    return f'<text x="{x}" y="{y}" fill="{color}" font-size="{size}" font-weight="{weight}">{escape(str(s))}</text>'

def centered_text(x,y,s,size=28,color=INK,weight=400):
    return f'<text x="{x}" y="{y}" text-anchor="middle" fill="{color}" font-size="{size}" font-weight="{weight}">{escape(str(s))}</text>'

def rect(x,y,w,h,fill=PANEL,r=18,stroke='none',sw=1,fill_opacity=None):
    opacity='' if fill_opacity is None else f' fill-opacity="{fill_opacity}"'
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{fill}"{opacity} stroke="{stroke}" stroke-width="{sw}"/>'

def line(x,y,x2,y2,color='#30364e',sw=2):
    return f'<path d="M{x} {y} L{x2} {y2}" stroke="{color}" stroke-width="{sw}" fill="none"/>'

def icon(name,x,y,size=36,color=INK):
    paths={
      'play':'M10 5 L29 18 L10 31 Z',
      'star':'M18 3 L23 13 L34 14 L26 22 L28 33 L18 28 L8 33 L10 22 L2 14 L13 13 Z',
      'source':'M18 3 L33 18 L18 33 L3 18 Z M18 10 L26 18 L18 26 L10 18 Z',
      'media':'M5 5 H31 V31 H5 Z M5 12 H31 M12 5 V12 M24 5 V12 M14 17 L24 22 L14 27 Z',
      'gear':'M18 3 L23 5 L25 10 L31 12 L33 18 L30 24 L25 26 L23 31 L17 33 L11 30 L9 25 L4 23 L3 17 L6 11 L11 9 L13 4 Z M24 18 A6 6 0 1 1 12 18 A6 6 0 1 1 24 18',
      'back':'M23 5 L10 18 L23 31',
      'forward':'M13 5 L26 18 L13 31',
      'down':'M6 12 L18 24 L30 12',
      'search':'M25 15 A10 10 0 1 1 5 15 A10 10 0 1 1 25 15 M23 23 L33 33',
      'plus':'M18 5 V31 M5 18 H31',
      'check':'M5 18 L14 27 L31 8',
      'refresh':'M30 13 A13 13 0 1 0 30 24 M30 4 V14 H20',
      'pause':'M12 6 V30 M24 6 V30',
      'rewind':'M17 7 L4 18 L17 29 Z M32 7 L19 18 L32 29 Z',
      'fast-forward':'M4 7 L17 18 L4 29 Z M19 7 L32 18 L19 29 Z',
      'more':'M7 17 V19 M18 17 V19 M29 17 V19',
      'link':'M15 10 L19 6 A8 8 0 0 1 30 17 L25 22 M11 14 L6 19 A8 8 0 0 0 17 30 L22 25 M12 24 L24 12',
      'alert':'M18 3 L34 31 H2 Z M18 12 V21 M18 25 V27',
      'tv':'M4 7 H32 V27 H4 Z M12 32 H24 M18 27 V32',
    }
    return f'<g transform="translate({x} {y}) scale({size/36})"><path d="{paths[name]}" fill="none" stroke="{color}" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></g>'

def button(x,y,w,label,focus=False,ico=None,h=76):
    s=rect(x,y,w,h,'url(#purple)' if focus else '#242a46',14,'#ffffff' if focus else '#363e5d',4 if focus else 1)
    if ico: s+=icon(ico,x+24,y+(h-32)/2,32)
    return s+text(x+(70 if ico else 26),y+h/2+10,label,28,weight=600)

def centered_button(x,y,w,label,focus=False,h=60,font_size=28):
    s=rect(x,y,w,h,'url(#purple)' if focus else '#242a46',14,'#ffffff' if focus else '#363e5d',4 if focus else 1)
    return s+f'<text x="{x+w/2}" y="{y+h/2+10}" text-anchor="middle" fill="{INK}" font-size="{font_size}" font-weight="600">{escape(label)}</text>'

def quiet_action(x,y,w,label,focus=False,selected=False,name=None):
    color=INK if focus else '#cbbaf0' if selected else '#b9bfd0'
    display=label
    if estimated_text_width(label,26)>w-16:
        value=label
        while value and estimated_text_width(f'{value}…',26)>w-16:value=value[:-1]
        display=f'{value}…'
    s=f'<g data-role="player-action" data-option-name="{escape(name or label,quote=True)}" data-current-option="{escape(label,quote=True)}" data-x="{x}" data-y="{y}" data-width="{w}" data-height="60">'
    if name:s+=f'<title>{escape(name)}：{escape(label)}</title>'
    s+=f'<text x="{x+w/2}" y="{y+40}" text-anchor="middle" fill="{color}" font-size="26" font-weight="{600 if focus else 400}">{escape(display)}</text>'
    if focus:s+=line(x+40,y+52,x+w-40,y+52,'#bba9ef',2)
    return s+'</g>'

PLAYER_BAR=(64,872,1792,144)
OVERLAY_OPACITY=.65
SPEEDS=['0.5x','0.75x','1x','1.25x','1.5x','1.75x','2x']

def frosted_backdrop(body,x,y,w,h,r=18):
    """Blur only the background under a translucent panel, never its text."""
    key=f'frost-{x}-{y}-{w}-{h}'
    snapshot='<svg xmlns="http://www.w3.org/2000/svg" width="1920" height="1080" viewBox="0 0 1920 1080"><defs><linearGradient id="bg" x2="1" y2="1"><stop stop-color="#101326"/><stop offset="1" stop-color="#080c18"/></linearGradient><linearGradient id="purple" x2="1" y2="1"><stop stop-color="#6652d5"/><stop offset="1" stop-color="#3a327d"/></linearGradient></defs><rect width="1920" height="1080" fill="url(#bg)"/><g font-family="Noto Sans CJK SC,Microsoft YaHei,sans-serif">'+body+'</g></svg>'
    encoded=base64.b64encode(snapshot.encode()).decode()
    return f'<defs><clipPath id="{key}-clip">{rect(x,y,w,h,"#fff",r)}</clipPath><filter id="{key}-blur" x="0" y="0" width="1920" height="1080" filterUnits="userSpaceOnUse" color-interpolation-filters="sRGB"><feGaussianBlur stdDeviation="12"/></filter></defs><g data-role="frosted-backdrop" clip-path="url(#{key}-clip)"><image x="0" y="0" width="1920" height="1080" href="data:image/svg+xml;base64,{encoded}" filter="url(#{key}-blur)"/></g>'

def option_label(name,value):
    if not value or str(value).lower() in {'关闭','默认','自动','跟随媒体','跟随频道','不可用','暂无','无','off','none','default','auto'}:
        return name
    return str(value)

def estimated_text_width(value,size):
    return sum(size*(1 if unicodedata.east_asian_width(c) in ('W','F') else .32 if c.isspace() else .55) for c in value)

def track_popover(kind,options,selected,anchor_right,background):
    """Content-sized single-line rows: language at left, details at right."""
    width=max(280,min(520,math.ceil(max(estimated_text_width(name,26)+estimated_text_width(detail,24)+118 for name,detail in options)/8)*8))
    height=64+len(options)*58+18
    x,y=anchor_right-width,852-height
    s=frosted_backdrop(background,x,y,width,height)
    s+=f'<g data-role="track-menu" data-kind="{kind}" data-width="{width}">'
    s+=rect(x,y,width,height,'#191f34',18,'#59627d',1,fill_opacity=OVERLAY_OPACITY)+text(x+26,y+46,kind,30,weight=650)
    for i,(name,detail) in enumerate(options):
        ry=y+64+i*58
        s+=f'<g data-role="option-row" data-selected="{str(i==selected).lower()}">'
        s+=rect(x+12,ry,width-24,52,'url(#purple)' if i==selected else 'none',10,'#fff' if i==selected else 'none',3 if i==selected else 1)
        if i==selected:s+=icon('check',x+24,ry+16,22,'#e0d2ff')
        s+=text(x+58,ry+35,name,26,weight=600 if i==selected else 400)
        if detail:s+=f'<text x="{x+width-28}" y="{ry+35}" text-anchor="end" fill="{"#e0d9ef" if i==selected else MUTED}" font-size="24">{escape(detail)}</text>'
        s+='</g>'
    return s+'</g>'

def player_bar():
    return '<g data-role="player-info">'+rect(*PLAYER_BAR,'#101629',20,fill_opacity=OVERLAY_OPACITY)+'</g>'

def pill(x,y,label,color='#b6a8ff',w=150):
    return rect(x,y,w,40,'#2b2850',20)+text(x+18,y+28,label,24,color)

def scene(x,y,w,h,kind=0):
    # Original vector scenery: all SVG elements remain individually editable.
    palettes=[('#152c49','#b18684','#25394b','#121f32'),('#153c42','#c5b390','#2b605e','#112d38'),('#30274b','#d39b86','#554863','#211d3b')]
    sky,sun,mount,front=palettes[kind%3]
    return f'''<svg x="{x}" y="{y}" width="{w}" height="{h}" viewBox="0 0 1100 620" preserveAspectRatio="xMidYMid slice">
      <rect width="1100" height="620" fill="{sky}"/>
      <ellipse cx="760" cy="190" rx="290" ry="170" fill="{sun}" opacity=".15"/>
      <circle cx="760" cy="190" r="62" fill="{sun}"/>
      <path d="M0 365 L160 205 L230 270 L420 90 L680 360 L810 245 L1100 350 V620 H0Z" fill="{mount}"/>
      <path d="M277 244 L420 90 L546 220 L455 186 L423 146 L375 207 L345 190Z" fill="#c4d4dd" opacity=".7"/>
      <path d="M0 450 L220 315 L435 455 L660 275 L885 437 L1100 330 V620 H0Z" fill="{front}"/>
      <path d="M620 401 Q500 455 650 480 Q820 530 500 620 H850 Q970 530 745 477 Q580 448 660 421Z" fill="#93b3bb" opacity=".6"/>
      <path d="M0 570 L180 460 L400 620 H0Z M850 620 L1000 470 L1100 520 V620Z" fill="#091624"/>
      <path d="M125 142 l12 -5 l11 5 M160 125 l9 -4 l9 4" fill="none" stroke="#dae1e7" stroke-width="2" opacity=".6"/>
    </svg>'''

def header(title,subtitle=''):
    s=text(64,107,title,42,weight=650)+text(1628,105,'JellyLive',32,'#b9b0ef',600)
    if subtitle: s+=text(64,155,subtitle,24,MUTED)
    return s

def movie_details():
    """Jellyfin information/action hierarchy with the current JellyLive style."""
    s=scene(0,0,1920,1080)
    s+='<defs><linearGradient id="detail-shade"><stop stop-color="#101326" stop-opacity=".98"/><stop offset=".60" stop-color="#101326" stop-opacity=".92"/><stop offset="1" stop-color="#101326" stop-opacity=".48"/></linearGradient></defs>'
    s+=rect(0,0,1920,1080,'url(#detail-shade)',0)+header('电影详情')
    s+=scene(64,214,300,450)+rect(64,214,300,450,'none',14,'#424862',1)
    s+=text(88,611,'山河之间',40,weight=650)
    s+=text(424,280,'山河之间',64,weight=700)+text(424,332,'一段穿越山河的归途',28,'#c4b7e8')
    s+=icon('star',424,367,28,'#f2cf83')+text(466,392,'8.6',28,'#f2cf83',600)
    s+=text(550,392,'2025  ·  中国大陆  ·  01:48:00',28,MUTED)
    s+=pill(424,419,'4K',w=86)+pill(526,419,'HDR',w=102)+text(660,449,'剧情 / 旅行',26,MUTED)
    for y,t in [(522,'一封迟到的来信，开启了一段穿越山河的旅程。'),(566,'沿途的相遇，让归乡有了新的意义。')]:s+=text(424,y,t,30)
    s+='<g data-role="movie-actions">'+button(424,622,336,'继续播放 00:36:20',True,'play')
    s+=centered_button(784,622,336,'从头播放',h=76)+centered_button(1144,622,336,'标为已看',h=76)+centered_button(1504,622,336,'更多',h=76)+'</g>'
    s+=text(64,718,'电影 · 4K',26,MUTED)+text(64,762,'剧情 / 旅行',26,MUTED)
    s+=text(424,756,'导演  林川',28,MUTED)+text(744,756,'主演  陈远 / 许宁',28,MUTED)
    s+=text(64,838,'演职人员',32,weight=600)
    for i,(name,role) in enumerate([('陈远','饰 林野'),('许宁','饰 沈禾'),('林川','导演')]):
        x=64+i*354
        s+=rect(x,864,330,152,'#171c32',18,'#363e5d',1)
        s+=f'<circle cx="{x+66}" cy="911" r="23" fill="#696186"/><path d="M{x+30} 978 Q{x+30} 938 {x+66} 938 Q{x+102} 938 {x+102} 978" fill="#696186"/>'
        s+=text(x+122,926,name,30,weight=600)+text(x+122,974,role,24,MUTED)
    return s

def media_player(controls=True,paused=False,active='transport',ended=False,seeking=False,speed='1.25x',audio='普通话',subtitles='关闭',aspect='原始'):
    """Shared two-row footer: progress, then transport/time and quiet options."""
    s=scene(0,0,1920,1080,1)
    if not controls:return s
    s+='<defs><linearGradient id="player-top" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#101326" stop-opacity=".82"/><stop offset="1" stop-color="#101326" stop-opacity="0"/></linearGradient></defs>'
    s+=rect(0,0,1920,220,'url(#player-top)',0)
    s+=text(64,105,'海岸日记',40,weight=650)+text(64,152,'第 1 季 · 第 3 集  远方灯塔',26,'#cbd0e0')+text(1696,105,'20:30:00',28,MUTED)
    s+=player_bar()
    progress=1728 if ended else 588
    s+=rect(96,902,1728,6,'#4b5268',3)+rect(96,902,progress,6,'#b9a6ff',3)
    thumb=978 if seeking else 96+progress
    s+=f'<circle cx="{thumb}" cy="905" r="10" fill="#f6f5ff"/>'
    for i,action in enumerate(['play' if paused or ended else 'pause','rewind','fast-forward']):
        x=96+i*64
        focused=i==0 and active=='transport' and not seeking
        s+=icon(action,x+16,950,32,INK if focused else '#bbc2d2')
        if focused:s+=line(x+14,988,x+50,988,'#bba9ef',2)
    s+=text(312,976,'00:48:00 / 00:48:00' if ended else '00:16:20 / 00:48:00',26,MUTED)
    values={'画面比例':aspect,'倍速':speed,'音轨':audio,'字幕':subtitles}
    for label,x,w in MEDIA_ACTIONS:
        s+=quiet_action(x,936,w,option_label(label,values[label]),selected=active==label,name=label)
    if seeking:
        s+=rect(thumb-110,832,220,44,'#24233c',12,'#5a536e',1)
        s+=f'<text x="{thumb}" y="863" text-anchor="middle" fill="{INK}" font-size="26">00:24:30</text>'
    return s

def media_option_popover(kind,speed='1.25x',audio='普通话',subtitles='关闭'):
    s=media_player(active=kind,speed=speed,audio=audio,subtitles=subtitles)
    if kind=='倍速':
        x,w,h=1088,336,514
        options=[(value,'') for value in SPEEDS];selected=SPEEDS.index(speed)
    elif kind=='音轨':
        options=[('普通话','AAC · 双声道'),('原声','AAC · 双声道')]
        return s+track_popover(kind,options,[name for name,_ in options].index(audio),1624,s)
    else:
        options=[('关闭','不显示字幕'),('简体中文','内嵌字幕'),('English','内嵌字幕')]
        return s+track_popover(kind,options,[name for name,_ in options].index(subtitles),1824,s)
    y=852-h
    s+=frosted_backdrop(s,x,y,w,h)
    s+=rect(x,y,w,h,'#191f34',18,'#59627d',1,fill_opacity=OVERLAY_OPACITY)+text(x+26,y+48,kind,30,weight=650)
    step=60 if kind=='倍速' else 86
    for i,(label,sub) in enumerate(options):
        ry=y+68+i*step
        s+=rect(x+14,ry,w-28,54 if kind=='倍速' else 78,'url(#purple)' if i==selected else 'none',12,'#fff' if i==selected else 'none',3 if i==selected else 1)
        s+=text(x+34,ry+(36 if kind=='倍速' or not sub else 31),label,26,weight=600 if i==selected else 400)
        if sub:s+=text(x+34,ry+62,sub,24,MUTED)
        if i==selected:s+=icon('check',x+w-58,ry+16,25,'#e0d2ff')
    return s

def next_episode():
    s=media_player(ended=True,active='next')
    x,y,w,h=1184,624,640,228
    s+=frosted_backdrop(s,x,y,w,h)
    s+=rect(x,y,w,h,'#191f34',18,'#59627d',1,fill_opacity=OVERLAY_OPACITY)
    s+=text(x+32,y+49,'下一集 · 第 1 季 第 4 集',30,weight=600)
    s+=text(x+32,y+94,'海边来信',26,MUTED)+text(x+370,y+94,'00:00:05 后播放',24,'#cbbaf0')
    s+=quiet_action(x+32,y+130,272,'立即播放',True)+quiet_action(x+336,y+130,272,'取消')
    return s

def footer(hint='方向键  移动    OK  确认    返回  上一级'):
    # Product screens have no persistent instruction footer or separator.
    return ''

def row(x,y,w,title,sub='',focus=False,playing=False,num=1):
    s=rect(x,y,w,94,'url(#purple)' if focus else '#1b2038',14,'#fff' if focus else 'none',4)
    s+=rect(x+18,y+19,56,56,['#3b668b','#50578d','#346d70'][num%3],28)+text(x+30,y+55,f'{num:02}',24,weight=650)
    s+=text(x+94,y+38,title,29,weight=600)
    if sub:s+=text(x+94,y+73,sub,24,'#e0d9ff' if focus else MUTED)
    if playing:s+=icon('play',x+w-49,y+30,28,'#b5a6ff' if not focus else INK)
    return s

CHANNELS=[('山野纪实','行走山河 · 高原之境'),('城市新闻','今日视点'),('体育现场','巅峰对决'),('光影剧场','周末电影'),('旅行生活','沿海慢行'),('音乐现场','晚间音乐会')]

def channel_list(x=1200,y=166,w=656,focus=-1,count=6,no_epg=False,playing=0):
    return ''.join(row(x,y+i*102,w,a,'暂无节目信息' if no_epg else b,i==focus,i==playing,i+1) for i,(a,b) in enumerate(CHANNELS[:count]))

def source_tabs(x,y,w,favorites=False,label='全部频道',focus=False,overlay=False):
    """One selector: favourites is a group, not an adjacent toggle."""
    if favorites:label='收藏'
    s=rect(x,y,w,62,'#242a46' if not overlay else '#20273b',14,'#fff' if focus else '#454d68',4 if focus else 1)
    if overlay:s=f'<g fill-opacity=".65">{s}</g>'
    s+=icon('star' if favorites else 'source',x+22,y+17,28,'#c4b7ff')
    s+=text(x+68,y+42,label,28,weight=600)
    return s+icon('down',x+w-50,y+18,28,MUTED)

def source_menu(x,y,w,selected=0,focused=0,translucent=False):
    s=f'<g opacity=".28">{rect(x+5,y+12,w,344,"#030610",18)}</g>'
    s+=rect(x,y,w,344,'#191f34',18,'#5c6480',1,fill_opacity=OVERLAY_OPACITY if translucent else None)
    for i,(label,ico) in enumerate([('全部频道','source'),('收藏','star'),('电视直播源','play'),('其他直播源','link')]):
        ry=y+14+i*80
        s+=rect(x+14,ry,w-28,72,'url(#purple)' if i==focused else '#232a41' if i==selected else 'none',12,'#fff' if i==focused else 'none',4 if i==focused else 1)
        s+=icon(ico,x+34,ry+21,30,INK if i==focused else '#b8aecf')+text(x+86,ry+47,label,28,weight=600)
        if i==selected:s+=icon('check',x+w-62,ry+22,28,'#e7dfff')
    return s

def drawer_scene(menu=False,transparency=35):
    s=scene(0,0,1920,1080)
    alpha=1-transparency/100
    panel_left,panel_width=1360,560
    content_left,content_width=panel_left+32,panel_width-96
    s+=f'<defs><linearGradient id="drawer-fade"><stop stop-color="#101629" stop-opacity="0"/><stop offset="1" stop-color="#101629" stop-opacity="{alpha}"/></linearGradient></defs>'
    s+=rect(panel_left-96,0,96,1080,'url(#drawer-fade)',0)
    s+=f'<g fill-opacity="{alpha}">{rect(panel_left,0,panel_width,1080,"#101629",0)}</g>'
    s+=source_tabs(content_left,60,content_width,overlay=True)
    entries=CHANNELS+[('少儿天地','奇妙世界'),('人文历史','文明的足迹'),('科学探索','探索未知')]
    for i,(name,program) in enumerate(entries):
        x,y,w=content_left,152+i*96,content_width
        focused=i==2 and not menu
        if focused:s+=rect(x,y,w,90,'url(#purple)',14,'#fff',4)
        s+=text(x+20,y+37,f'{i+1:02}',26,'#ddd5fc' if focused else '#b1b7ce',600)
        s+=text(x+92,y+37,name,30,weight=600)+text(x+92,y+73,program,24,'#e1dafa' if focused else '#c0c5d7')
        if i==0:s+=icon('play',x+w-48,y+31,28,'#cebcff')
        if not focused:s+=f'<g opacity=".16">{line(x+92,y+90,x+w-18,y+90,"#d4dbef",1)}</g>'
    if menu:s+=frosted_backdrop(s,content_left,138,content_width,344)+source_menu(content_left,138,content_width,translucent=True)
    return s

LIVE_ACTIONS=[(label,1040+i*200,184) for i,label in enumerate(['画质','画面比例','音轨','字幕'])]
MEDIA_ACTIONS=[(label,1040+i*200,184) for i,label in enumerate(['画面比例','倍速','音轨','字幕'])]

def live_bar(active=None,ratio='原始',audio='普通话',subtitles='关闭',quality='高清',quality_available=True,channel='01  山野纪实'):
    s=scene(0,0,1920,1080)+player_bar()
    s+=rect(96,902,1728,6,'#3c465c',3)+rect(96,902,1037,6,'#afa0ff',3)
    s+=text(96,976,channel,30,weight=600)
    values={'画质':quality,'画面比例':ratio,'音轨':audio,'字幕':subtitles}
    for i,(label,x,w) in enumerate(LIVE_ACTIONS):
        disabled=label=='画质' and not quality_available
        focus=(active==label if active else i==(0 if quality_available else 1)) and not disabled
        s+=('<g opacity=".5" data-disabled="true">' if disabled else '')+quiet_action(x,936,w,option_label(label,values[label]),focus,name=label)+('</g>' if disabled else '')
    return s

def live_option_popover(kind,ratio='原始',audio='普通话',subtitles='关闭'):
    """Compact anchored menus, with no full-screen scrim or modal window."""
    s=live_bar(kind,ratio=ratio,audio=audio,subtitles=subtitles)
    anchor=next(a for a in LIVE_ACTIONS if a[0]==kind)
    if kind=='画面比例':
        w,h=456,334
        options=[('原始比例',''),('适应屏幕',''),('铺满画面','')]
        selected={'原始':0,'适应':1,'铺满':2}[ratio]
    elif kind=='音轨':
        options=[('普通话','AAC · 双声道'),('原声','AAC · 双声道')]
        return s+track_popover(kind,options,[name for name,_ in options].index(audio),anchor[1]+anchor[2],s)
    else:
        options=[('关闭','不显示字幕'),('简体中文','内嵌字幕'),('English','内嵌字幕')]
        return s+track_popover(kind,options,[name for name,_ in options].index(subtitles),anchor[1]+anchor[2],s)
    x=min(max(1008,anchor[1]+anchor[2]-w),1824-w);y=852-h
    s+=f'<g opacity=".3">{rect(x+6,y+12,w,h,"#030610",18)}</g>'
    s+=frosted_backdrop(s,x,y,w,h)
    s+=rect(x,y,w,h,'#191f34',18,'#59627d',1,fill_opacity=OVERLAY_OPACITY)+text(x+28,y+52,kind,30,weight=650)
    for i,(name,sub) in enumerate(options):
        ry=y+76+i*76
        s+=rect(x+16,ry,w-32,68,'url(#purple)' if i==selected else 'none',12,'#fff' if i==selected else 'none',4 if i==selected else 1)
        s+=text(x+36,ry+44,name,28,weight=600)
        if sub:s+=text(x+36,ry+65,sub,24,'#d0c7e3' if i==0 else MUTED)
        if i==selected:s+=icon('check',x+w-58,ry+22,26,'#e6dcff')
    return s

def playback_popover():
    return live_option_popover('画面比例')

def sources_page(populated=False,add_menu=False):
    s=header('直播源','管理频道来源，已启用的频道会显示在分组列表中')
    s+=button(1500,130,356,'添加直播源',False,'plus',h=64)
    if add_menu:
        s+=rect(1500,130,356,64,'#39315e',14,'#a996e5')+icon('plus',1524,146,32)+text(1570,172,'添加直播源',28,weight=600)
    s+=text(64,242,'电视直播源',30,weight=600)
    s+=rect(64,268,1792,180,'#171d32',18,'#343d56')
    s+=rect(96,316,72,72,'#2b2848',16)+icon('tv',114,332,36,'#c7b8ff')
    s+=text(196,334,'内置直播源',34,weight=600)+text(196,390,'145 个频道 · 今天 20:50 更新',26,MUTED)
    s+=pill(1036,334,'已就绪',color='#a5dbbf',w=134)
    s+=button(1230,320,244,'刷新频道',not populated and not add_menu,'refresh')+button(1500,320,320,'机顶盒配置',False,'gear')
    s+=text(64,514,'第三方直播源',30,weight=600)
    if populated:
        s+=text(318,514,'2 个来源',24,MUTED)
        for i,(name,kind,info,ico) in enumerate([('家庭 IPTV','M3U','32 个频道 · 今天 20:40 更新','source'),('客厅 Jellyfin','Jellyfin','18 个频道 · 已连接服务器','tv')]):
            y=548+i*152
            s+=rect(64,y,1792,132,'#171d32',18,'#343d56')
            s+=rect(96,y+30,72,72,'#242d46',16)+icon(ico,114,y+48,36,'#bdafd9')
            s+=text(196,y+52,name,32,weight=600)+text(196,y+98,info,25,MUTED)
            s+=pill(1130,y+45,kind,w=156)+pill(1320,y+45,'已启用',color='#a5dbbf',w=156)
            s+=button(1628,y+28,192,'管理',i==0,h=76)
    else:
        s+=text(318,514,'0 个来源',24,MUTED)
        s+=rect(64,548,1792,286,'#141a2d',18,'#303950')
        s+=icon('source',929,585,62,'#82779c')
        s+='<text x="960" y="704" text-anchor="middle" fill="#f6f5ff" font-size="32" font-weight="600">暂无第三方直播源</text>'
        s+='<text x="960" y="758" text-anchor="middle" fill="#a9adc6" font-size="26">支持 M3U，以及提供直播频道的 Jellyfin 服务器</text>'
    if add_menu:
        # The entry is shared; Jellyfin is an input type rather than a permanent
        # standalone source button. Connection alone never invents channels.
        x,y,w=1256,208,600
        s+=rect(x+6,y+12,w,206,'#070b16',18)
        s+=rect(x,y,w,206,'#1b2238',18,'#606b86')
        for i,(label,ico,sub) in enumerate([('M3U 直播源','link','添加播放列表与可选节目单'),('Jellyfin 服务器','tv','读取服务端已配置的直播频道')]):
            ry=y+14+i*90
            s+=rect(x+14,ry,w-28,82,'url(#purple)' if i==0 else 'none',12,'#fff' if i==0 else 'none',4 if i==0 else 1)
            s+=icon(ico,x+32,ry+24,32)+text(x+86,ry+34,label,28,weight=600)+text(x+86,ry+66,sub,24,'#dbd4ed' if i==0 else MUTED)
    return s

def homepage(focus='list',no_epg=False,selected=0):
    s=rect(64,64,1100,716,'#04070d',20,'#fff' if focus=='preview' else '#414866',4 if focus=='preview' else 1)
    s+=scene(68,112,1092,614,selected%3)
    s+=rect(1192,64,664,716,'#14192e')
    s+=source_tabs(1208,80,632)
    s+=line(1216,145,1832,145)+channel_list(1208,163,632,selected if focus=='list' else -1,no_epg=no_epg,playing=selected)
    for i,(name,ico) in enumerate([('收藏','star'),('直播源','source'),('媒体库','media'),('设置','gear')]):
        x=64+i*456
        active=focus==name
        s+=rect(x,826,424,176,'url(#purple)' if active else '#242a46',18,'#fff' if active else '#363e5d',4 if active else 1)
        start=x+(424-(44+20+len(name)*36))/2
        s+=icon(ico,start,892,44)+text(start+64,928,name,36,weight=600)
    return s

def poster(x,y,w,h,title,sub='',kind=0,focus=False):
    s=rect(x-4,y-4,w+8,h+8,'none',14,'#fff' if focus else 'none',4)+scene(x,y,w,h,kind)
    s+=rect(x,y+h-100,w,100,'#10182b',0)+text(x+20,y+h-48,title,30,weight=600)
    if sub:s+=text(x+20,y+h-14,sub,24,MUTED)
    return s

TITLES=['山河之间','远方来信','海岸日记','星夜漫游','归途','森林之声']

def library_home(empty=False):
    s=header('我的媒体库','客厅 Jellyfin · 已连接')+button(1540,138,316,'搜索媒体',False,'search')
    s+=text(64,231,'继续观看',34,weight=600)
    if empty:
        s+='<text x="960" y="406" text-anchor="middle" fill="#a9adc6" font-size="32">暂无观看历史</text>'
    else:
        for i,t in enumerate(TITLES[:3]):
            x=64+i*602
            s+=poster(x,264,574,260,t,'继续播放 · 已观看 00:36:00',i,i==0)
            s+=rect(x+16,508,542,5,'#414760',2)+rect(x+16,508,205,5,'#ac9aff',2)
    s+=text(64,608,'探索媒体库',34,weight=600)
    for i,(t,count) in enumerate([('电影','86 部电影'),('剧集','12 部剧集'),('最近添加','98 项内容')]):
        s+=poster(64+i*602,642,574,260,t,count,i+1)
    return s

def portrait_media_card(x,y,title,year,score,kind=0,focus=False):
    w,h=274,411  # Restore the original 2:3 portrait image frame.
    s=rect(x-4,y-4,w+8,h+8,'none',14,'#fff' if focus else 'none',4)+scene(x,y,w,h,kind)
    s+=rect(x+14,y+14,82,42,'#101629dd',12)+text(x+31,y+44,score,26,'#f7d68d',600)
    s+=rect(x,y+h-64,w,64,'#101629eb',0)
    # A static PNG shows the centred name; SVG demonstrates single-line scrolling
    # for an overflowing focused title, after a pause, with clipped edges.
    name_width=w-36
    if len(title)*28>name_width:
        clip=f'media-name-{kind}'
        s+=f'<defs><clipPath id="{clip}"><rect x="{x+18}" y="{y+h-58}" width="{name_width}" height="52"/></clipPath></defs>'
        travel=max(0,(len(title)*28-name_width)/2)
        motion=f'<animate attributeName="dx" values="{travel};{travel};{-travel};{-travel};{travel}" keyTimes="0;.16;.65;.8;1" dur="12s" repeatCount="indefinite"/>' if focus else ''
        s+=f'<g clip-path="url(#{clip})"><text x="{x+w/2}" y="{y+h-22}" text-anchor="middle" fill="{INK}" font-size="28" font-weight="600">{escape(title)}{motion}</text></g>'
    else:
        s+=f'<text x="{x+w/2}" y="{y+h-22}" text-anchor="middle" fill="{INK}" font-size="28" font-weight="600">{escape(title)}</text>'
    s+=text(x,y+h+34,year,24,MUTED)
    return s

def library_grid(filtered=False,focused=True):
    s=header('电影','全部电影 · 86 部' if not filtered else '电影 · 18 部')
    s+='<g data-role="library-toolbar">'+button(920,136,296,'搜索媒体',ico='search')+button(1240,136,296,'类型：剧情' if filtered else '类型：全部')+button(1560,136,296,'排序：名称')+'</g>'
    names=TITLES
    for i,t in enumerate(names):
        s+=portrait_media_card(64+i*302,270,t,'2025 · 剧情',['8.6','8.1','7.9','8.3','7.8','8.5'][i],i,i==0 and focused)
    s+=rect(744,848,72,72,'#171c32',14,'#30364e')+icon('back',764,868,32,'#596078')
    s+=f'<text x="960" y="895" text-anchor="middle" fill="#a9adc6" font-size="26">1-6 / {18 if filtered else 86}</text>'
    s+=rect(1104,848,72,72,'#242a46',14,'#596078')+icon('forward',1124,868,32)
    return s

def library_browse_panel(sort=False):
    # Both panels share the same right edge, 680px width and full-height shell.
    x,w=1240,680
    background=library_grid(filtered=not sort,focused=False)
    s=background+f'<rect width="1240" height="1080" fill="#060917" opacity=".18"/>'+frosted_backdrop(background,x,0,w,1080,r=0)
    s+=rect(x,0,w,1080,'#171d32',0,fill_opacity=OVERLAY_OPACITY)+text(x+44,87,'排序' if sort else '筛选',42,weight=650)
    if sort:
        for i,label in enumerate(['名称','最近添加','发布时间','评分','时长']):
            s+=button(x+44,142+i*92,592,label,i==0,h=76)
            if i==0:s+=icon('check',1800,165,30,'#e6dcff')
        s+=text(x+44,682,'排列方向',28,MUTED)
        s+=button(x+44,716,284,'升序',h=76)+button(x+352,716,284,'降序',h=76)
        s+=rect(x+44,716,284,76,'none',14,'#b9a6ff',2)+icon('check',x+260,739,28,'#c5b2ff')
        s+=button(x+44,958,592,'关闭',h=76)
    else:
        sections=[('观看状态',['全部','未看','已看']),('类型',['全部','剧情','纪录片']),('分辨率',['全部','4K','1080p']),('年份',['全部','2025','2024']),('地区',['全部','中国大陆','其他'])]
        for i,(name,options) in enumerate(sections):
            y=150+i*150
            s+=text(x+44,y,name,28,MUTED)
            for j,label in enumerate(options):
                focused=i==1 and j==1
                s+=button(x+44+j*202,y+24,188,label,focused,h=70)
                if (i==1 and j==1) or (i!=1 and j==0):
                    s+=rect(x+44+j*202,y+24,188,70,'none',14,'#b9a6ff',2)
        s+=button(x+44,958,284,'重置',h=76)+button(x+352,958,284,'关闭',h=76)
    return s

# Jellyfin logo geometry from the upstream Android TV app_logo.xml (GPL-2.0).
# See designs/ASSET_NOTICES.txt and LICENSE-JELLYFIN.txt.
LOGO_INNER='M24.71,49.16c-1.55,-3.12 8.63,-21.57 11.79,-21.57 3.17,0 13.32,18.49 11.79,21.57 -1.53,3.08 -22.02,3.12 -23.58,0z'
LOGO_OUTER='M0.98,65C-3.69,55.61 26.98,0 36.5,0c9.53,0 40.15,55.71 35.53,65s-66.37,9.39 -71.04,0m12.26,-8.15c3.07,6.15 43.52,6.08 46.55,0 3.03,-6.09 -17.03,-42.59 -23.27,-42.59S10.17,50.69 13.23,56.85z'

def splash_body(animated=False):
    motion='<animateTransform attributeName="transform" type="rotate" from="0 36.5 36.5" to="360 36.5 36.5" dur="2s" repeatCount="indefinite"/>' if animated else ''
    s=rect(0,0,1920,1080,'#101326',0)+'<defs><linearGradient id="jellyfin-gradient" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#AA5CC3"/><stop offset="1" stop-color="#00A4DC"/></linearGradient></defs>'
    s+=f'<svg x="620" y="398" width="180" height="180" viewBox="-16 -16 105 105"><g fill="url(#jellyfin-gradient)" fill-rule="evenodd">{motion}<path d="{LOGO_OUTER}"/><path d="{LOGO_INNER}"/></g></svg>'
    s+=text(844,522,'JellyLive',80,INK,650)
    s+='<text x="960" y="662" text-anchor="middle" fill="#a9adc6" font-size="28">正在准备直播…</text>'
    return s

FAVORITES=['山野纪实','城市新闻','体育现场','光影剧场','旅行生活','音乐现场','少儿天地','人文历史','科学探索','都市生活','自然世界','经典影视','地方新闻','生活服务','戏曲欣赏','文化视界']

def favorites_grid(focus=0):
    s=header('我的收藏','内置直播源 · 16 个收藏频道')
    for i,name in enumerate(FAVORITES):
        x,y=64+(i%4)*456,214+(i//4)*178
        s+=rect(x,y,424,146,'url(#purple)' if i==focus else '#1b2038',18,'#fff' if i==focus else '#363e5d',4 if i==focus else 1)
        s+=f'<text x="{x+212}" y="{y+86}" text-anchor="middle" fill="{INK}" font-size="36" font-weight="600">{escape(name)}</text>'
    return s+footer('方向键  移动    确认 / 点击  打开操作菜单    返回  主页')

def settings_page(selected=0,focus_menu=True):
    names=['播放设置','显示与操作','Jellyfin 连接','内置源配置','关于 JellyLive']
    s=header('设置','上下切换菜单，右侧标题与内容即时联动')
    for i,name in enumerate(names):
        x,y=64,218+i*112
        s+=button(x,y,420,name,i==selected and focus_menu)
        if i==selected and not focus_menu:s+=rect(x,y,420,76,'#36305c',14)+text(x+26,y+48,name,28,'#c5b8ff',600)
    s+=rect(530,218,1326,682)+text(574,283,names[selected],36,weight=650)
    rows=[('启动行为','主页自动续播'),('画面比例','保持原始比例'),('默认音轨','跟随媒体'),('默认字幕','关闭'),('解码方式','自动'),('侧栏透明度','35%'),('剧集自动播放下一集','开启')] if selected==0 else [('字号','普通（大字号为 3 列收藏）'),('焦点样式','白色描边 · 紫色背景'),('首页频道','上下移动即切换预览'),('收藏操作','点击打开操作菜单')]
    for i,(a,b) in enumerate(rows):
        y=344+i*80 if selected==0 else 354+i*100
        if i==0 and not focus_menu:s+=rect(552,y-42,1280,80,'url(#purple)',14,'#fff',4)
        s+=text(574,y,a,28)+text(1190,y,b,28,'#c5b8ff')+icon('forward',1786,y-26,28,MUTED)+line(574,y+30,1810,y+30)
    return s+footer('上下  切换页签并刷新内容    右键  设置项    左键  当前页签')

def emit(slug,title,body):
    note,flow=SCREEN_COPY[slug]['note'],SCREEN_COPY[slug]['flow']
    svg=f'''<svg xmlns="http://www.w3.org/2000/svg" width="1920" height="1080" viewBox="0 0 1920 1080" role="img" aria-labelledby="title desc">
<title id="title">{escape(title)}</title><desc id="desc">{escape(note)}</desc>
<defs><linearGradient id="bg" x2="1" y2="1"><stop stop-color="#101326"/><stop offset="1" stop-color="#080c18"/></linearGradient>
<linearGradient id="purple" x2="1" y2="1"><stop stop-color="#6652d5"/><stop offset="1" stop-color="#3a327d"/></linearGradient></defs>
<g font-family="Noto Sans CJK SC, Microsoft YaHei, sans-serif"><rect width="1920" height="1080" fill="url(#bg)"/>{body}</g></svg>'''
    (OUT/'svg'/f'{slug}.svg').write_text(svg)
    kind='state' if int(slug[:2])>=19 or slug.endswith('-empty') else 'main'
    SCREENS.append(dict(id=slug,title=title,note=note,flow=flow,kind=kind))

def favorite_flow():
    """Documentation diagram; captions outside frames are not product UI."""
    ET.register_namespace('', 'http://www.w3.org/2000/svg')
    body=text(64,86,'直播全屏 · 长按确认收藏',46,weight=650)
    body+=text(64,134,'仅在长按操作后显示结果，不自动弹说明',26,MUTED)
    states=[
        ('05-live-favorite-feedback','01  收藏成功','已收藏 · 体育现场','长按确认：收藏当前播放频道','保存成功后提示约2秒'),
        ('05-live-favorite-remove','02  取消收藏','已取消收藏 · 体育现场','再次长按确认：取消收藏','状态同步到收藏列表'),
    ]
    for index,(name,title,message,caption1,caption2) in enumerate(states):
        x=248+index*864
        body+=text(x,190,title,32,weight=600)
        child=ET.parse(OUT/'svg'/f'{name}.svg').getroot()
        ids={e.get('id'):f'panel{index}-{e.get("id")}' for e in child.iter() if e.get('id')}
        for element in child.iter():
            for key,value in list(element.attrib.items()):
                if key=='id':element.set(key,ids[value])
                elif key=='aria-labelledby':element.set(key,' '.join(ids.get(token,token) for token in value.split()))
                else:
                    for old,new in ids.items():value=value.replace(f'url(#{old})',f'url(#{new})')
                    element.set(key,value)
        child.set('x',str(x));child.set('y','216');child.set('width','560');child.set('height','315')
        body+=ET.tostring(child,encoding='unicode')
        body+=rect(x,216,560,315,'none',12,'#414a65',2)
        body+=text(x,584,'底部提示放大',24,MUTED)+rect(x,610,560,80,'#1b2038',16,'#414a65')
        body+=f'<text x="{x+280}" y="660" text-anchor="middle" fill="#dce0ef" font-size="26">{escape(message)}</text>'
        body+=text(x,752,caption1,25,MUTED)+text(x,798,caption2,25,MUTED)
    body+=icon('forward',944,632,32,MUTED)
    body+=text(64,916,'短按确定：显示信息条    长按确定：切换收藏    Menu：不提供功能',30,weight=600)
    body+=text(64,980,'按住只触发一次，松开不再触发短按；侧栏或弹层打开时不执行快捷收藏。',26,MUTED)
    svg=f'<svg xmlns="http://www.w3.org/2000/svg" width="1920" height="1080" viewBox="0 0 1920 1080"><title>直播全屏长按收藏与取消收藏</title><rect width="1920" height="1080" fill="#101326"/><g font-family="Noto Sans CJK SC,Microsoft YaHei,sans-serif">{body}</g></svg>'
    (OUT/'svg/live-favorite-flow.svg').write_text(svg)

def make():
    emit('00-splash','启动动画',splash_body())
    s=text(128,162,'JellyLive',64,weight=700)+pill(128,196,'电视，由你掌控',w=240)
    s+=text(128,340,'打开，就是好节目。',58,weight=650)+text(128,405,'正在获取内置直播源，无需先连接媒体库。',28,MUTED)
    s+=rect(128,480,740,12,'#272c44',6)+rect(128,480,510,12,ACCENT,6)+text(128,540,'正在同步频道…  68%',28)
    s+=button(128,655,290,'手动添加直播源',True,'plus')+button(446,655,270,'先进入主页')
    s+=scene(1010,160,780,650)+footer('获取中可先浏览应用，也可手动添加直播源。')
    emit('01-onboarding','首次启动与初始化',s)
    emit('02-home','主页',homepage())
    emit('02-home-switch','主页：随焦点切台',homepage('list',selected=2))
    s=header('频道浏览','内置直播源 · 128 个频道')+button(1174,140,324,'频道操作')+button(1530,140,326,'搜索频道',False,'search')
    for i,t in enumerate(['全部频道','纪录片','新闻资讯','体育赛事','影视剧场','旅行生活']):
        y=250+i*106
        s+=rect(64,y,260,76,'#36305c' if i==0 else '#242a46',14)+text(90,y+48,t,28,weight=600)
    s+=channel_list(354,250,1502,0,playing=-1)+footer('方向键  浏览频道    确认  全屏播放    返回  主页')
    emit('03-channels','频道浏览（已取消独立页）',s)
    SCREENS[-1]['retired']=True
    emit('04-favorites','收藏频道：卡片网格',favorites_grid())
    s=favorites_grid(2)+'<rect width="1920" height="1080" fill="#060917" opacity=".75"/>'
    s+='<g data-role="favorite-actions-panel">'+rect(700,364,520,352,'#1b2038',24)+text(732,418,'体育现场',38,weight=650)
    s+=button(732,446,456,'播放',True,'play',h=70)+button(732,528,456,'节目单',h=70)+button(732,610,456,'移除',h=70)+'</g>'
    emit('04-favorites-actions','收藏频道：操作菜单',s)
    s=header('我的收藏','当前直播来源')+rect(96,214,1728,686)+'<text x="960" y="568" text-anchor="middle" fill="#a9adc6" font-size="40">暂无收藏频道</text>'+footer('返回  主页')
    emit('04-favorites-empty','收藏频道：空状态',s)
    emit('05-live-player','直播全屏与信息条',live_bar())
    emit('06-channel-drawer','全屏频道侧栏',drawer_scene())
    s=header('电视节目单','今天 11 月 04 日 · 内置直播源')
    for i,t in enumerate(['20:00','21:00','22:00']):s+=text(382+i*480,217,t,28,MUTED)
    for i,(a,b) in enumerate(CHANNELS):
        y=252+i*108;s+=rect(96,y,268,96)+text(120,y+58,a,28,weight=600)
        for j in range(3):
            x=382+j*480;s+=rect(x,y,462,96,'url(#purple)' if i==0 and j==0 else PANEL,12,'#fff' if i==0 and j==0 else 'none',4)
            s+=text(x+20,y+39,[b.split(' · ')[0],'晚间精选','精彩继续'][j],26)+text(x+20,y+76,['20:00—21:00','21:00—22:00','22:00—23:00'][j],24,MUTED)
    s+=line(627,235,627,905,'#b5a1ff',3)+footer('方向键  浏览频道与时间    OK  节目详情    返回  上一级')
    emit('07-guide','电视节目单',s)
    s=s+'<rect width="1920" height="1080" fill="#080c18" opacity=".82"/>'
    s+='<g data-role="program-detail-panel">'+rect(600,334,720,412,'#1b2038',28)+text(632,410,'行走山河 · 高原之境',40,weight=650)+pill(1138,370,'正在播出',w=150)
    s+=text(632,458,'山野纪实  ·  今天 20:00—21:00',26,MUTED)
    for y,t in [(524,'穿越群山与河谷，探访高原深处的自然风貌。'),(566,'跟随镜头，感受山河之间缓慢而坚定的生命。')]:s+=text(632,y,t,28,MUTED)
    s+=button(632,622,300,'观看该频道',True,'play')+button(956,622,180,'关闭')+'</g>'
    emit('08-program-detail','节目详情弹层',s)
    emit('09-sources','直播源管理',sources_page())
    s=header('添加第三方直播源','输入来源信息，检测成功后保存到频道列表')
    for y,label,value,focus in [(226,'来源名称','家庭 IPTV',False),(390,'M3U 播放列表地址','https://example.com/live/channels.m3u',True),(554,'XMLTV 节目单地址（可选）','https://example.com/live/guide.xml',False)]:
        s+=text(128,y,label,28,MUTED)+rect(128,y+24,1376,80,'#1c223c',14,'#fff' if focus else '#3a425f',4 if focus else 1)+text(158,y+76,value,30)
    s+=text(128,737,'检测状态：待检测。未填写节目单也可正常观看。',26,MUTED)
    s+=button(128,800,250,'检测来源')+button(408,800,250,'保存来源')+button(688,800,180,'取消')+footer('OK  编辑字段并打开电视键盘    返回  取消编辑')
    emit('10-source-editor','添加或编辑第三方源',s)
    emit('11-library','媒体库首页',library_home())
    emit('12-library-grid','媒体分类与海报列表',library_grid())
    emit('13-movie','电影详情',movie_details())
    s=header('剧集详情')+scene(1218,182,606,320,1)+text(96,260,'海岸日记',60,weight=650)+text(96,323,'2025 · 旅行纪实 · 共 8 集',28,MUTED)+text(96,390,'沿着海岸线，记录八座城市的生活与风景。',28)
    s+=button(100,450,330,'继续播放 第 3 集',True,'play')+button(100,588,260,'第 1 季')+icon('down',310,612,28,MUTED)
    for i in range(4):s+=poster(100+i*440,700,400,226,f'第 {i+1} 集',['初见海岸','潮起潮落','远方灯塔','海边来信'][i],i)
    s+=footer()
    emit('14-series','剧集详情',s)
    emit('15-media-player','媒体播放器',media_player())
    s=header('媒体搜索','仅搜索当前媒体库的电影和剧集')+rect(96,200,700,86,'#20263e',14,'#585078',2)+text(124,254,'海',36)+icon('search',730,222)
    keys=['A B C D E F','G H I J K L','M N O P Q R','S T U V W X','Y Z 0 1 2 3','4 5 6 7 8 9']
    for i,rs in enumerate(keys):
        for j,k in enumerate(rs.split()):s+=centered_button(100+j*116,300+i*84,100,k,i==0 and j==0,h=76,font_size=30)
    s+=centered_button(100,828,244,'中文 / 英文',h=82,font_size=30)+centered_button(360,828,420,'空格',h=82,font_size=30)
    s+=centered_button(100,928,328,'删除',h=82,font_size=30)+centered_button(452,928,328,'清除',h=82,font_size=30)
    s+=text(830,240,'媒体 · 2',32,weight=600)
    for i,(name,meta) in enumerate([('深海来信','电影 · 2024 · 剧情 / 冒险'),('海岸日记','剧集 · 2023 · 共 8 集')]):
        y=276+i*292
        s+=rect(830,y,994,252,'#1b2038',16)+scene(846,y+18,144,216,i)
        s+=text(1020,y+80,name,36,weight=600)+text(1020,y+136,meta,26,MUTED)
    s+=footer('仅搜索媒体    确认结果  查看详情    返回  恢复原列表')
    emit('16-search','媒体搜索',s)
    emit('17-settings','设置：播放页签',settings_page())
    emit('17-settings-display','设置：显示页签联动',settings_page(1))
    s=header('连接 Jellyfin','媒体库为可选功能，不影响直播')+pill(128,213,'尚未连接',w=160)
    for y,a,b in [(340,'服务器地址','http://192.168.1.20:8096'),(510,'用户名','客厅用户'),(680,'密码','••••••••')]:
        s+=text(128,y,a,28,MUTED)+rect(128,y+24,1080,76,'#20263e',14,'#fff' if y==340 else '#3a425f',4 if y==340 else 1)+text(154,y+74,b,30)
    s+=button(128,838,300,'检测并连接')+button(458,838,200,'取消')+text(1310,370,'连接后可以',34,weight=600)
    for i,t in enumerate(['浏览电影和剧集','同步继续观看进度','使用服务器提供的直播']):s+=icon('check',1310,420+i*76,30,'#b9a7ff')+text(1360,447+i*76,t,26,MUTED)
    s+=footer()
    emit('18-jellyfin','Jellyfin 服务器连接',s)

    # Six independent state designs, each with an actionable recovery route.
    s=header('直播源暂时无法更新')+icon('alert',128,258,72,'#c5b3ff')+text(128,420,'连接没有成功，先用上次的频道。',48,weight=650)
    s+=text(128,491,'上次成功更新：今天 18:00 · 128 个缓存频道可用',28,MUTED)+text(128,542,'也可以手动添加其他直播源，继续观看。',28,MUTED)
    s+=button(128,662,260,'重新获取',True,'refresh')+button(416,662,300,'使用缓存频道')+button(744,662,300,'手动添加来源')+footer()
    emit('19-source-error','状态：内置源获取失败',s)
    s=homepage('none')
    for _,program in CHANNELS:s=s.replace(program,'暂时无法播放')
    s+=rect(104,126,1044,588,'#101629',0)+'<g data-role="channel-error-message" data-center-x="626">'+icon('alert',586,256,80,'#bba8ff')
    s+=centered_text(626,408,'当前来源的频道暂不可用',42,weight=650)+centered_text(626,462,'请重试，或切换到其他直播源。',28,MUTED)
    s+=centered_button(326,510,260,'重试',True,h=76)+centered_button(616,510,310,'检查直播源',h=76)+'</g>'
    emit('20-channel-error','状态：全部频道不可用',s)
    emit('21-no-guide','状态：没有节目单',homepage('list',True))
    for slug,title,head,desc,actions in [
      ('22-library-disconnected','状态：未连接媒体库','连接你的 Jellyfin，开启私人影院。','你仍然可以随时返回主页观看直播。',[('连接 Jellyfin','link'),('返回看直播','play')]),
      ('23-library-empty','状态：媒体库为空','连接成功，还没有媒体内容。','请在 Jellyfin 服务器添加媒体库，再回来刷新。',[('刷新媒体库','refresh'),('返回看直播','play')]),
      ('24-jellyfin-offline','状态：Jellyfin 断连','暂时联系不上你的 Jellyfin。','请检查服务器或网络；独立直播仍可使用。',[('重新连接','refresh'),('连接设置','gear'),('返回看直播','play')])]:
        s=header('我的媒体库')+rect(96,205,1728,695)+'<g data-role="library-state-message" data-center-x="960">'+icon('media' if 'offline' not in slug else 'link',912,368,96,'#b4a0ff')
        s+=centered_text(960,542,head,46,weight=650)+centered_text(960,600,desc,28,MUTED)
        total=len(actions)*320+(len(actions)-1)*28
        start=960-total/2
        for i,(a,b) in enumerate(actions):s+=button(start+i*348,662,320,a,i==0,b)
        s+='</g>'
        s+=footer()
        emit(slug,title,s)

    # Append new variants to preserve the original gallery numbers, including 04 and 10.
    s=homepage('none',selected=2)+source_menu(1192,154,616)
    emit('02-home-source-menu','主页：选择频道分组',s)
    s=homepage('none')+rect(1184,154,640,620,'#14192e',0)+source_tabs(1192,80,616,True)
    for i,n in enumerate([0,2,5]):
        title,program=CHANNELS[n]
        s+=row(1192,163+i*102,616,title,program,i==0,n==0,n+1)
    emit('02-home-favorites','主页：收藏频道列表',s)
    s=scene(0,0,1920,1080)+rect(560,952,800,64,'#101629dd',16)
    s+='<text x="960" y="994" text-anchor="middle" fill="#dce0ef" font-size="26">长按确定键，收藏 / 取消收藏当前频道</text>'
    emit('05-live-favorite-hint','首次操作说明（已取消）',s)
    SCREENS[-1]['retired']=True
    s=scene(0,0,1920,1080)+rect(685,952,550,64,'#101629dd',16)
    s+='<text x="960" y="994" text-anchor="middle" fill="#dce0ef" font-size="26">已收藏 · 体育现场</text>'
    emit('05-live-favorite-feedback','直播全屏：收藏结果提示',s)
    s=scene(0,0,1920,1080)+rect(685,952,550,64,'#101629dd',16)
    s+='<text x="960" y="994" text-anchor="middle" fill="#dce0ef" font-size="26">已取消收藏 · 体育现场</text>'
    emit('05-live-favorite-remove','直播全屏：取消收藏提示',s)

from tv_review_designs import install
TV_AUDIT_VARIANTS=install(globals())
from settings_tv_designs import install as install_settings
SETTINGS_TV_VARIANTS=install_settings(globals())
from playback_box_designs import install as install_playback_box
PLAYBACK_BOX_VARIANTS=install_playback_box(globals())
from live_quality_designs import install as install_live_quality
LIVE_QUALITY_VARIANTS=install_live_quality(globals())

if __name__=='__main__':
    (OUT/'svg').mkdir(parents=True,exist_ok=True)
    make()
    emit('05-live-playback-settings','直播：画面比例局部浮层',playback_popover())
    emit('06-channel-drawer-groups','全屏：展开频道分组',drawer_scene(menu=True))
    emit('09-sources-populated','直播源：统一管理第三方来源',sources_page(populated=True))
    emit('09-sources-add','直播源：统一添加入口',sources_page(add_menu=True))
    emit('12-library-filter','媒体库：右侧筛选',library_browse_panel())
    emit('12-library-sort','媒体库：右侧排序',library_browse_panel(sort=True))
    emit('11-library-empty-history','媒体库：首页无观看历史',library_home(empty=True))
    SCREENS.append(dict(id='05-live-playlist',title='直播播放列表（已取消）',note='用户移除直播播放列表按钮与图42；频道浏览继续使用图10侧栏。仅保留编号占位，不生成图像或加入有效画册。',flow='已取消；右键打开图10频道侧栏',kind='state',retired=True))
    emit('05-live-audio','直播：音轨局部浮层',live_option_popover('音轨',audio='原声'))
    emit('05-live-subtitles','直播：字幕局部浮层',live_option_popover('字幕',subtitles='简体中文'))
    emit('15-media-speed','媒体播放器：倍速浮层',media_option_popover('倍速'))
    SCREENS[-1]['kind']='state'
    emit('15-media-next','剧集：自动播放下一集',next_episode())
    SCREENS[-1]['kind']='state'
    emit('15-media-audio','媒体播放器：音轨浮层',media_option_popover('音轨',audio='原声'))
    SCREENS[-1]['kind']='state'
    emit('15-media-subtitles','媒体播放器：字幕浮层',media_option_popover('字幕',subtitles='简体中文'))
    SCREENS[-1]['kind']='state'
    emit('15-media-hidden','媒体播放器：控件隐藏',media_player(controls=False))
    SCREENS[-1]['kind']='state'
    emit('15-media-seek','媒体播放器：进度预览',media_player(active='seek',seeking=True))
    SCREENS[-1]['kind']='state'
    TV_AUDIT_VARIANTS()
    SETTINGS_TV_VARIANTS()
    PLAYBACK_BOX_VARIANTS()
    LIVE_QUALITY_VARIANTS()
    SETTINGS_CONNECTION_STATES()
    from library_connection_designs import additional as library_connection_states
    library_connection_states(globals())
    favorite_flow()
    # Cancelled slots keep their original numbers but no obsolete images ship.
    for screen in SCREENS:
        if screen.get('retired'):
            for folder,suffix in [('svg','svg'),('previews','png')]:
                (OUT/folder/f"{screen['id']}.{suffix}").unlink(missing_ok=True)
    (OUT/'manifest.json').write_text(json.dumps(SCREENS,ensure_ascii=False,indent=2))
    print(f'Built {len(SCREENS)} SVG designs.')
