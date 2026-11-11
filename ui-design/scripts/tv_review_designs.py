"""TV audit refinements. Geometry stays native-TV, 1920x1080; no Android code changes."""
from html import escape
import math


def install(g):
    text,rect,line,icon,scene,button,centered,center_button=[g[n] for n in ('text','rect','line','icon','scene','button','centered_text','centered_button')]
    from design_text import text_width
    g['estimated_text_width']=text_width
    INK,MUTED=g['INK'],g['MUTED']; secondary='#d7dcec'

    def header(title,subtitle='',fs=1):
        s=text(96,107 if fs==1 else 121,title,42*fs,weight=650)+text(1628 if fs==1 else 1540,105,'JellyLive',32*fs,'#b9b0ef',600)
        return s+(text(96,155 if fs==1 else 184,subtitle,24*fs,MUTED) if subtitle else '')

    def player_bar():
        # Overall glass remains 35% transparent; a continuous lower scrim protects small labels.
        return '<g data-role="player-info" data-base-opacity="0.65" data-label-protection="0.36">'+rect(64,872,1792,144,'#101629',20,fill_opacity=.65)+rect(80,932,1760,68,'#080d1c',12,fill_opacity=.36)+'</g>'

    def quiet(x,y,w,label,focus=False,selected=False,name=None):
        shown=label
        while g['estimated_text_width'](shown,26)>w-16 and len(shown)>1:shown=shown[:-2]+'…'
        s=f'<g data-role="player-action" data-option-name="{escape(name or label)}" data-current-option="{escape(label)}" data-x="{x}" data-y="{y}" data-width="{w}" data-height="60">'
        s+=f'<title>{escape(name or label)}：{escape(label)}</title>'+centered(x+w/2,y+40,shown,26,INK if focus else secondary,600 if focus else 400)
        if focus:s+=line(x+40,y+52,x+w-40,y+52,'#cbb7ff',2)
        return s+'</g>'

    def homepage(focus='list',no_epg=False,selected=0):
        s=rect(100,64,1052,716,'#04070d',20,'#fff' if focus=='preview' else '#414866',4 if focus=='preview' else 1)
        s+=scene(104,126,1044,588,selected%3)+rect(1184,64,640,716,'#14192e')
        s+=g['source_tabs'](1192,80,616)+g['channel_list'](1192,163,616,selected if focus=='list' else -1,no_epg=no_epg,playing=selected)
        for i,(name,ico) in enumerate([('收藏','star'),('直播源','source'),('媒体库','media'),('设置','gear')]):
            x=100+i*438;w=406;active=focus==name
            s+=rect(x,826,w,176,'url(#purple)' if active else '#242a46',18,'#fff' if active else '#363e5d',4 if active else 1)
            start=x+(w-(64+len(name)*36))/2
            s+=icon(ico,start,892,44)+text(start+64,928,name,36,weight=600)
        return s

    def favorites_grid(focus=0):
        s=header('我的收藏','内置直播源 · 16 个收藏频道')
        for i,name in enumerate(g['FAVORITES']):
            x,y=100+(i%4)*440,214+(i//4)*178
            s+=rect(x,y,400,146,'url(#purple)' if i==focus else '#1b2038',18,'#fff' if i==focus else '#363e5d',4 if i==focus else 1)+centered(x+200,y+86,name,36,weight=600)
        return s

    def drawer_scene(menu=False,transparency=35):
        s=scene(0,0,1920,1080);alpha=1-transparency/100
        # Background can reach the edge; foreground and its focus frame remain in the safe area.
        x,w=1392,428
        s+='<defs><linearGradient id="drawer-fade"><stop stop-color="#101629" stop-opacity="0"/><stop offset="1" stop-color="#101629" stop-opacity="'+str(alpha)+'"/></linearGradient></defs>'
        s+=rect(1264,0,96,1080,'url(#drawer-fade)',0)+rect(1360,0,560,1080,'#101629',0,fill_opacity=alpha)
        s+=g['source_tabs'](x,60,w,overlay=True)
        for i,(name,program) in enumerate(g['CHANNELS']+[('少儿天地','奇妙世界'),('人文历史','文明的足迹'),('科学探索','探索未知')]):
            y=152+i*96;focused=i==2 and not menu
            if focused:s+=rect(x,y,w,90,'url(#purple)',14,'#fff',4)
            s+=text(x+20,y+37,f'{i+1:02}',26,INK,600)+text(x+92,y+37,name,30,INK,600)+text(x+92,y+73,program,24,secondary)
            if i==0:s+=icon('play',x+w-48,y+31,28,'#cebcff')
        if menu:s+=g['frosted_backdrop'](s,x,138,w,344)+g['source_menu'](x,138,w,translucent=True)
        return s.replace('<text ','<text stroke="#080c18" stroke-width="2.6" stroke-linejoin="round" paint-order="stroke" ')

    def library_home(empty=False):
        s=header('我的媒体库','客厅 Jellyfin · 已连接')+button(1508,138,316,'搜索媒体',False,'search')+text(96,231,'继续观看',34,weight=600)
        if empty:s+=centered(960,406,'暂无观看历史',32,MUTED)
        else:
            for i,t in enumerate(g['TITLES'][:3]):
                x=100+i*584;s+=g['poster'](x,264,552,260,t,'继续播放 · 已观看 00:36:00',i,i==0)
                s+=rect(x+16,508,520,5,'#414760',2)+rect(x+16,508,205,5,'#ac9aff',2)
        s+=text(96,608,'探索媒体库',34,weight=600)
        for i,(t,count) in enumerate([('电影','86 部电影'),('剧集','12 部剧集'),('最近添加','98 项 · 按添加时间')]):s+=g['poster'](100+i*584,642,552,260,t,count,i+1)
        return s

    def poster(x,y,w,h,title,sub='',kind=0,focus=False):
        s=scene(x,y,w,h,kind)+rect(x,y+h-100,w,100,'#10182b',0)+text(x+20,y+h-48,title,30,weight=600)
        if sub:s+=text(x+20,y+h-14,sub,24,MUTED)
        if focus:s+=rect(x,y,w,h,'none',14,'#fff',4)
        return s

    def portrait(x,y,title,year,score,kind=0,focus=False,w=260,fs=1):
        h=w*1.5;caption=64*fs
        s=scene(x,y,w,h,kind)
        s+=rect(x+14,y+14,82*fs,42*fs,'#101629dd',12)+text(x+28,y+44*fs,score,26*fs,'#f7d68d',600)
        s+=rect(x,y+h-caption,w,caption,'#101629eb',0)
        full=title;max_width=w-36;shown=title
        while g['estimated_text_width'](shown,28*fs)>max_width:shown=shown[:-2]+'…'
        # The full text is available in SVG metadata; focus scrolls in the native design contract.
        s+=f'<g data-role="poster-title" data-full-text="{escape(full)}">'
        if shown!=full and focus:
            clip=f'poster-name-{kind}-{int(fs*10)}';travel=g['estimated_text_width'](full,28*fs)-max_width
            s+=f'<defs><clipPath id="{clip}"><rect x="{x+18}" y="{y+h-caption}" width="{max_width}" height="{caption}"/></clipPath></defs>'
            s+=f'<g clip-path="url(#{clip})"><g class="static-label">'+centered(x+w/2,y+h-22*fs,shown,28*fs,INK,600)+'<set attributeName="visibility" to="hidden" begin="1.2s"/></g>'
            s+=f'<g class="motion-label" visibility="hidden"><set attributeName="visibility" to="visible" begin="1.2s"/><animateTransform attributeName="transform" type="translate" values="0 0;0 0;{-travel} 0;{-travel} 0;0 0" keyTimes="0;.16;.65;.8;1" begin="1.2s" dur="12s" repeatCount="indefinite"/>'+text(x+18,y+h-22*fs,full,28*fs,INK,600)+'</g></g><style>@media(prefers-reduced-motion:reduce){.motion-label{display:none!important}.static-label{visibility:visible!important}}</style>'
        else:s+=centered(x+w/2,y+h-22*fs,shown,28*fs,INK,600)
        s+='</g>'
        s+=text(x,y+h+34*fs,year,24*fs,MUTED)
        if focus:s+=rect(x,y,w,h,'none',14,'#fff',4)
        return s

    def library_grid(filtered=False,focused=True,large=False,recent=False,empty=False):
        fs=1.5 if large else 1;title='最近添加' if recent else '电影'
        s=header(title,'按添加时间 · 98 项内容' if recent else '电影 · 0 部' if empty else '电影 · 18 部' if filtered else '全部电影 · 86 部',fs)
        s+='<g data-role="library-toolbar">'+button(888,136,296,'搜索媒体',False,'search')+button(1208,136,296,'类型：剧情' if filtered else '类型：全部')+button(1528,136,296,'排序：最新添加' if recent else '排序：名称')+'</g>'
        if large:
            # The tool row keeps equal widths; its targets grow with the system text setting.
            s=s[:s.index('<g data-role="library-toolbar">')]
            for x,label,ico in [(888,'搜索媒体','search'),(1208,'类型：全部',None),(1528,'排序：名称',None)]:
                s+=rect(x,150,296,100,'#242a46',14,'#363e5d')
                if ico:s+=icon(ico,x+18,176,42)
                s+=centered(x+166 if ico else x+148,215,label,42,INK,600)
        if empty:
            s+=icon('search',912,350,96,'#b4a0ff')+centered(960,536,'没有符合条件的媒体',44,INK,650)+centered(960,596,'调整筛选条件，或清除筛选查看全部电影。',28,MUTED)
            s+=center_button(700,660,520,'清除筛选',True,h=84)
            return s
        cols=5 if large else 6;w=318 if large else 260;step=350 if large else 292;y=286 if large else 270
        for i,t in enumerate(g['TITLES'][:cols]):s+=portrait(100+i*step,y,t if not large or i else '山河之间：归途与远方','2025 · 剧情',['8.6','8.1','7.9','8.3','7.8','8.5'][i],i,i==0 and focused,w,fs)
        pager_y=900 if large else 848
        s+=rect(744,pager_y,72,72,'#171c32',14,'#30364e')+icon('back',764,pager_y+20,32,'#596078')+centered(960,pager_y+47,f'1–{cols} / '+('98' if recent else '18' if filtered else '86'),26,MUTED)+rect(1104,pager_y,72,72,'#242a46',14,'#596078')+icon('forward',1124,pager_y+20,32)
        return s

    def movie_details(large=False):
        s=scene(0,0,1920,1080)+rect(0,0,1920,1080,'#101326',0,fill_opacity=.92)+header('电影详情',fs=1.5 if large else 1)
        px,pw=96,280;s+=scene(px,214,pw,pw*1.5)+rect(px,214,pw,pw*1.5,'none',14,'#424862')
        if large:
            s+=text(424,282,'山河之间',96,weight=700)+text(424,346,'一段穿越山河的归途',42,'#c4b7e8')
            s+=text(424,418,'评分 8.6 · 2025 · 中国大陆',42,MUTED)+text(424,480,'01:48:00 · 4K · 剧情 / 旅行',39,MUTED)
            s+=text(424,556,'一封迟到的来信，开启了一段穿越山河的旅程。',45)+text(424,610,'沿途的相遇，让归乡有了新的意义。',45)
            for i,label in enumerate(['继续播放 00:36:20','从头播放','标为已看']):
                x=424+(i%2)*708;y=660+(i//2)*126
                s+=rect(x,y,684,100,'url(#purple)' if i==0 else '#242a46',14,'#fff' if i==0 else '#363e5d',4 if i==0 else 1)+centered(x+342,y+65,label,42,weight=600)
            s+=text(96,966,'演职人员',48,weight=600)
            return s
        s+=text(424,280,'山河之间',64,weight=700)+text(424,332,'一段穿越山河的归途',28,'#c4b7e8')
        s+=icon('star',424,367,28,'#f2cf83')+text(466,392,'8.6',28,'#f2cf83',600)+text(550,392,'2025 · 中国大陆 · 01:48:00',28,MUTED)
        s+=g['pill'](424,419,'4K',w=86)+g['pill'](526,419,'HDR',w=102)+text(660,449,'剧情 / 旅行',26,MUTED)
        s+=text(424,522,'一封迟到的来信，开启了一段穿越山河的旅程。',30)+text(424,566,'沿途的相遇，让归乡有了新的意义。',30)
        for i,label in enumerate(['继续播放 00:36:20','从头播放','标为已看']):s+=center_button(424+i*472,622,448,label,i==0,h=76)
        s+=text(96,718,'电影 · 4K',26,MUTED)+text(96,762,'剧情 / 旅行',26,MUTED)+text(424,756,'导演  林川',28,MUTED)+text(744,756,'主演  陈远 / 许宁',28,MUTED)+text(96,838,'演职人员',32,weight=600)
        for i,(name,role) in enumerate([('陈远','饰 林野'),('许宁','饰 沈禾'),('林川','导演')]):
            x=96+i*354;s+=rect(x,864,330,152,'#171c32',18,'#363e5d')+f'<circle cx="{x+66}" cy="911" r="23" fill="#696186"/><path d="M{x+30} 978 Q{x+30} 938 {x+66} 938 Q{x+102} 938 {x+102} 978" fill="#696186"/>'+text(x+122,926,name,30,weight=600)+text(x+122,974,role,24,MUTED)
        return s

    def media_player(controls=True,paused=False,active='transport',ended=False,seeking=False,speed='1.25x',audio='普通话',subtitles='关闭',bright=False,seekable=True,aspect='原始'):
        s=scene(0,0,1920,1080,1)
        if bright:s=rect(0,0,1920,1080,'#f8f7f3',0)+scene(0,0,1920,1080,0).replace('#152c49','#f8f7f3').replace('#25394b','#e9e8e4').replace('#121f32','#f1f0ed').replace('#091624','#ffffff')
        if not controls:return s
        s+='<defs><linearGradient id="player-top" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#101326" stop-opacity=".94"/><stop offset=".65" stop-color="#101326" stop-opacity=".92"/><stop offset="1" stop-color="#101326" stop-opacity="0"/></linearGradient></defs>'+rect(0,0,1920,270,'url(#player-top)',0)
        s+=text(96,105,'海岸日记',40,weight=650)+text(96,152,'第 1 季 · 第 3 集  远方灯塔',26,secondary)+text(1696,105,'20:30:00',28,secondary)+player_bar()
        progress=1728 if ended else 588;s+=rect(96,902,1728,6,'#646b80',3)+rect(96,902,progress,6,'#b9a6ff',3)
        if seekable:s+=f'<circle cx="{978 if seeking else 96+progress}" cy="905" r="10" fill="{INK}"/>'
        for i,action in enumerate(['play' if paused or ended else 'pause','rewind','fast-forward']):
            x=96+i*64;available=seekable or i==0
            s+=f'<g opacity="{1 if available else .35}">'+icon(action,x+16,950,32,INK if i==0 else secondary)+'</g>'
            if i==0 and active=='transport' and not seeking:s+=line(x+14,988,x+50,988,'#cbb7ff',2)
        s+=text(312,976,'00:48:00 / 00:48:00' if ended else '00:16:20 / 00:48:00' if seekable else '00:16:20 / --:--:--',26,secondary)
        values={'画面比例':aspect,'倍速':speed,'音轨':audio,'字幕':subtitles}
        for label,x,w in g['MEDIA_ACTIONS']:s+=quiet(x,936,w,g['option_label'](label,values[label]),focus=active==label,name=label)
        if seeking:s+=rect(868,822,220,44,'#24233c',12,'#5a536e')+centered(978,853,'00:24:30',26)
        return s

    def track_popover(kind,options,selected,anchor_right,background,large=False):
        fs=1.5 if large else 1;font=26*fs;step=58*fs;heading=64*fs
        width=max(280,min(620 if large else 520,math.ceil(max(g['estimated_text_width'](a,font)+g['estimated_text_width'](b,24*fs)+118*fs for a,b in options)/8)*8))
        height=heading+len(options)*step+18;x=min(anchor_right,1820)-width;y=852-height
        s=g['frosted_backdrop'](background,x,y,width,height)+rect(x,y,width,height,'#191f34',18,'#59627d',1,fill_opacity=.65)
        s+=rect(x+8,y+heading,width-16,height-heading-8,'#080d1c',10,fill_opacity=.22)+text(x+26,y+46*fs,kind,30*fs,INK,650)
        for i,(name,detail) in enumerate(options):
            ry=y+heading+i*step;selected_row=i==selected
            s+=rect(x+12,ry,width-24,step-6,'url(#purple)' if selected_row else 'none',10,'#fff' if selected_row else 'none',3 if selected_row else 1)
            if selected_row:s+=icon('check',x+24,ry+16*fs,22*fs,'#e0d2ff')
            detail_w=g['estimated_text_width'](detail,24*fs);name_inset=58*fs;name_w=width-name_inset-28-detail_w-24*fs;clip=f'track-{kind}-{i}-{int(width)}'
            s+=f'<defs><clipPath id="{clip}"><rect x="{x+name_inset}" y="{ry}" width="{max(60,name_w)}" height="{step}"/></clipPath></defs>'
            overflowing=g['estimated_text_width'](name,font)>name_w
            if overflowing:
                short=name
                while g['estimated_text_width'](short+'…',font)>name_w:short=short[:-1]
                s+=f'<g clip-path="url(#{clip})"><g class="static-label">'+text(x+name_inset,ry+35*fs,short+'…',font,INK,600 if selected_row else 400)
                if selected_row:s+='<set attributeName="visibility" to="hidden" begin="1.2s"/>'
                s+='</g>'
                if selected_row:
                    travel=g['estimated_text_width'](name,font)-name_w
                    s+=f'<g class="motion-label" visibility="hidden"><set attributeName="visibility" to="visible" begin="1.2s"/><animateTransform attributeName="transform" type="translate" values="0 0;0 0;{-travel} 0;{-travel} 0;0 0" keyTimes="0;.16;.65;.8;1" begin="1.2s" dur="12s" repeatCount="indefinite"/>'+text(x+name_inset,ry+35*fs,name,font,INK,600)+'</g>'
                s+='</g><style>@media(prefers-reduced-motion:reduce){.motion-label{display:none!important}.static-label{visibility:visible!important}}</style>'
            else:s+=text(x+name_inset,ry+35*fs,name,font,INK,600 if selected_row else 400)
            s+=f'<text x="{x+width-28}" y="{ry+35*fs}" text-anchor="end" fill="{INK}" font-size="{24*fs}">{escape(detail)}</text>'
        return s

    def sources_page(populated=False,add_menu=False):
        # Reuse the established page; only move unsafe content and its matching backgrounds.
        s=g['_original_sources'](populated,add_menu)
        s=s.replace('x="64"','x="96"').replace('width="1792"','width="1728"').replace('x="1856"','x="1824"')
        s=s.replace('x="1500" y="130"','x="1464" y="130"').replace('translate(1524 146)','translate(1492 146)').replace('x="1570" y="172"','x="1538" y="172"')
        s=s.replace('x="1256"','x="1224"').replace('x="1262"','x="1230"')
        s=s.replace('x="1270"','x="1238"').replace('x="1342"','x="1310"').replace('translate(1288 ','translate(1256 ')
        return s

    def settings_page(selected=0,focus_menu=True):
        names=['播放设置','显示与操作','Jellyfin 连接','内置源配置','关于 JellyLive'];s=header('设置')
        for i,name in enumerate(names):s+=button(100,218+i*112,384,name,i==selected and focus_menu)
        s+=rect(530,218,1294,682)+text(574,283,names[selected],36,weight=650)
        rows=[('启动行为','主页自动续播'),('画面比例','保持原始比例'),('默认音轨','跟随媒体'),('默认字幕','关闭'),('解码方式','自动'),('侧栏透明度','35%'),('剧集自动播放下一集','开启')] if selected==0 else [('字号','跟随系统'),('电视安全区','标准 · 5%'),('焦点样式','白色描边 · 紫色背景'),('首页频道','上下移动即切换预览'),('收藏操作','点击打开操作菜单')]
        for i,(a,b) in enumerate(rows):
            y=344+i*80 if selected==0 else 354+i*100
            s+=text(574,y,a,28)+text(1190,y,b,28,'#c5b8ff')+icon('forward',1764,y-26,28,MUTED)
        return s

    def state(title,head,desc,actions,focus=0):
        s=header(title)+rect(96,205,1728,695)+icon('alert',912,350,96,'#b4a0ff')+centered(960,534,head,46,INK,650)
        for i,t in enumerate(desc):s+=centered(960,596+i*42,t,28,MUTED)
        w=320;gap=28;start=960-(len(actions)*w+(len(actions)-1)*gap)/2
        for i,label in enumerate(actions):s+=center_button(start+i*(w+gap),708,w,label,i==focus,h=76)
        return s

    def playback_failure():
        # Deliberately no dormant bottom-control focus: retry owns initial focus.
        s=media_player(controls=False)+rect(0,0,1920,1080,'#080c18',0,fill_opacity=.88)
        s+=text(96,105,'海岸日记',40,weight=650)+icon('alert',912,310,96,'#b4a0ff')+centered(960,490,'暂时无法播放',48,INK,650)+centered(960,554,'请检查网络后重试，或返回影片详情。',28,MUTED)
        return s+center_button(626,638,320,'重试播放',True,h=84)+center_button(974,638,320,'返回详情',h=84)

    def ime():
        s=header('连接 Jellyfin')+text(96,180,'客厅 Jellyfin · 客厅用户',28,MUTED)+text(96,244,'密码',32)
        s+=rect(100,270,1196,86,'#20263e',14,'#fff',4)+text(128,327,'••••••••',36)+button(100,382,320,'检测并连接')
        s+=rect(0,470,1920,610,'#1b2033',0)+text(96,536,'输入密码',32,weight=600)
        keys=['QWERTYUIOP','ASDFGHJKL','ZXCVBNM']
        for i,row in enumerate(keys):
            start=960-(len(row)*116-16)/2
            for j,k in enumerate(row):s+=center_button(start+j*116,578+i*102,100,k,i==0 and j==0,h=78)
        for x,w,label in [(330,260,'中文 / 英文'),(622,430,'空格'),(1084,240,'删除'),(1356,234,'完成')]:s+=center_button(x,908,w,label,h=84)
        return s

    def loading():
        s=header('电影','正在准备媒体库')+text(96,238,'正在读取媒体目录…',34,weight=600)+text(96,292,'首次打开大型媒体库可能需要一点时间。',28,MUTED)
        for i in range(6):
            x=100+i*292;s+=rect(x,342,260,390,'#171d32',12)+rect(x+22,670,150,16,'#30364e',4)
        return s+center_button(700,838,520,'返回媒体库',True,h=84)

    def buffering():
        return g['media_wait'](opening=False)

    def next_episode():
        s=media_player(ended=True,active='next');x,y,w,h=1184,624,640,228
        s+=g['frosted_backdrop'](s,x,y,w,h)+rect(x,y,w,h,'#191f34',18,'#59627d',1,fill_opacity=.65)
        s+=rect(x+8,y+66,w-16,h-74,'#080d1c',10,fill_opacity=.22)
        s+=text(x+32,y+49,'下一集 · 第 1 季 第 4 集',30,INK,650)+text(x+32,y+94,'海边来信',26,secondary)+text(x+370,y+94,'00:00:05 后播放',24,secondary)
        return s+quiet(x+32,y+130,272,'立即播放',True)+quiet(x+336,y+130,272,'取消')

    def library_browse_panel(sort=False):
        x=1240;w=680;left=1284;inner=536
        background=library_grid(filtered=not sort,focused=False)
        s=background+rect(0,0,x,1080,'#060917',0,fill_opacity=.18)+g['frosted_backdrop'](background,x,0,w,1080,r=0)
        s+=rect(x,0,w,1080,'#171d32',0,fill_opacity=.65)+text(left,91,'排序' if sort else '筛选',42,weight=650)
        if sort:
            for i,label in enumerate(['名称','添加时间','发布时间','评分','时长']):
                s+=button(left,142+i*92,inner,label,i==0,h=76)
                if i==0:s+=icon('check',1776,165,30,'#e6dcff')
            s+=text(left,682,'排列方向',28,MUTED)+center_button(left,716,256,'升序',h=76)+center_button(1564,716,256,'降序',h=76)
            s+=rect(left,716,256,76,'none',14,'#b9a6ff',2)+icon('check',1492,739,28,'#c5b2ff')+button(left,954,inner,'关闭',h=68)
        else:
            sections=[('观看状态',['全部','未看','已看']),('类型',['全部','剧情','纪录片']),('分辨率',['全部','4K','1080p']),('年份',['全部','2025','2024']),('地区',['全部','中国大陆','其他'])]
            for i,(name,options) in enumerate(sections):
                y=150+i*150;s+=text(left,y,name,28,MUTED)
                for j,label in enumerate(options):
                    bx=left+j*184;focused=i==1 and j==1
                    s+=center_button(bx,y+24,168,label,focused,h=70)
                    if (i==1 and j==1) or (i!=1 and j==0):s+=rect(bx,y+24,168,70,'none',14,'#b9a6ff',2)
            s+=center_button(left,954,256,'重置',h=68)+center_button(1564,954,256,'关闭',h=68)
        return s

    def bright_live():
        s=g['live_bar']()
        for old,new in [('#152c49','#f8f7f3'),('#b18684','#dbd8d3'),('#25394b','#e9e8e4'),('#121f32','#f1f0ed'),('#091624','#ffffff')]:s=s.replace(old,new)
        return s

    overrides=dict(header=header,player_bar=player_bar,quiet_action=quiet,homepage=homepage,favorites_grid=favorites_grid,drawer_scene=drawer_scene,library_home=library_home,portrait_media_card=portrait,poster=poster,library_grid=library_grid,movie_details=movie_details,media_player=media_player,track_popover=track_popover,settings_page=settings_page,library_browse_panel=library_browse_panel,next_episode=next_episode)
    g['_original_sources']=g['sources_page'];overrides['sources_page']=sources_page;g.update(overrides)

    def additional():
        cases=[
          ('15-media-error','媒体播放：失败后遥控重试',playback_failure()),
          ('12-library-no-results','媒体筛选：无结果与清除',library_grid(filtered=True,empty=True)),
          ('24-library-session-expired','媒体库：登录已过期',state('我的媒体库','登录已过期',['重新登录后即可继续访问媒体库。'],['重新登录','返回看直播'])),
          ('24-library-permission','媒体库：暂无访问权限',state('我的媒体库','当前账号没有访问权限',['请联系服务器管理员，或切换有权限的账号。'],['切换账号','返回媒体库'])),
          ('24-library-item-missing','媒体详情：内容已移除',state('媒体详情','这部媒体已不在库中',['服务器中的文件可能已移除或重新整理。'],['刷新列表','返回媒体库'])),
          ('12-library-loading','媒体目录：加载与取消',loading()),
          ('15-media-buffering','媒体播放：缓冲恢复',buffering()),
          ('12-library-large-text','媒体列表：系统大字号 1.5×',library_grid(large=True)),
          ('18-jellyfin-ime','连接媒体库：电视输入法',ime()),
          ('05-live-bright','亮色视频：信息栏可读性',bright_live()),
          ('15-media-unknown-duration','媒体播放：时长未知与不可拖动',media_player(seekable=False)),
          ('13-movie-large-text','电影详情：系统大字号 1.5×',movie_details(large=True)),
          ('12-library-recent','媒体库：最近添加',library_grid(recent=True)),
          ('15-media-long-tracks','媒体音轨：长名称与大字号',media_player(active='音轨')+track_popover('音轨',[('普通话 · 视听辅助解说','AAC · 5.1'),('English','AAC · 2.0')],0,1624,media_player(active='音轨'),large=True)),
        ]
        for slug,title,body in cases:g['emit'](slug,title,body);g['SCREENS'][-1]['kind']='state'
    return additional
