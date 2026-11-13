"""Settings refinement: Android TV/D-pad, incumbent JellyLive palette, design-only."""
from html import escape
from design_text import text_width

CATEGORIES=['播放设置','显示与操作','媒体库连接','机顶盒配置','关于']
INK='#f6f5ff';MUTED='#b9bfd0';VALUE='#c5b8ff';SURFACE='#1b2038';FOCUS='url(#purple)'

def install(g):
    rect=g['rect'];text=g['text'];icon=g['icon'];line=g['line']
    def tx(x,y,value,size=30,color=INK,weight=400,anchor=None):
        attrs=f' text-anchor="{anchor}"' if anchor else ''
        return f'<text x="{x}" y="{y}" fill="{color}" font-size="{size}" font-weight="{weight}"{attrs}>{escape(str(value))}</text>'
    def fit(value,size,width):
        value=str(value)
        if text_width(value,size)<=width:return value
        while value and text_width(value+'…',size)>width:value=value[:-1]
        return value+'…'
    def para(x,y,value,width=1200,size=26,color=MUTED,lh=None):
        lines=[];part=''
        for c in value:
            if c=='\n' or text_width(part+c,size)>width:
                lines.append(part);part='' if c=='\n' else c
            else:part+=c
        if part:lines.append(part)
        return ''.join(tx(x,y+i*(lh or size*1.55),v,size,color) for i,v in enumerate(lines))
    def top(title='设置',large=False):
        fs=1.5 if large else 1
        return tx(96,140,title,46*fs,weight=650)+tx(1824,132,'JellyLive',30*fs,VALUE,500,'end')
    def nav(category,focus=False,large=False):
        s='';fs=1.5 if large else 1
        for i,label in enumerate(CATEGORIES):
            active=i==category;white=active and focus;y=226+i*112
            s+=rect(100,y,348,84,FOCUS if white else '#36305c' if active else '#171c32',14,'#fff' if white else '#363e5d',4 if white else 1)
            s+=tx(124,y+54, label,30*fs,INK if white else VALUE if active else INK,650 if active else 500)
        return s
    def frame(category,heading=None,nav_focus=False,large=False):
        fs=1.5 if large else 1
        return top(large=large)+nav(category,nav_focus,large)+tx(544,272,heading or CATEGORIES[category],36*fs,weight=650)
    def action(x,y,w,label,focus=False,h=88,disabled=False):
        color=MUTED if disabled else INK
        return rect(x,y,w,h,'#151a2b' if disabled else FOCUS if focus else '#242a46',14,'#fff' if focus else '#363e5d',4 if focus else 1)+tx(x+w/2,y+h/2+12,label,30,color,weight=600,anchor='middle')
    def switch(x,y,on):
        return rect(x,y,80,42,'#7561ed' if on else '#444b64',21)+f'<circle cx="{x+(58 if on else 22)}" cy="{y+21}" r="15" fill="{INK}"/>'
    def row(y,title,value='',focus=False,kind='choice',checked=False,large=False,disabled=False):
        # One line per action; preserve generous TV focus targets and actual font sizes.
        fs=1.5 if large else 1;h=132 if large else 96
        s=f'<g data-role="settings-row" data-title="{escape(title)}" data-kind="{kind}" data-disabled="{str(disabled).lower()}">'
        s+=rect(548,y,1240,h,'#151a2b' if disabled else FOCUS if focus else SURFACE,14,'#fff' if focus else 'none',4 if focus else 0)
        baseline=y+h/2+12*fs
        s+=tx(574,baseline,fit(title,32*fs,870 if kind=='radio' else 820),32*fs,MUTED if disabled else INK,600)
        if kind=='toggle':
            s+=tx(1650,baseline,'开启' if checked else '关闭',28*fs,INK if focus else VALUE,anchor='end')+switch(1684,y+h/2-21,checked)
        elif kind=='radio':
            if checked:s+=icon('check',1722,y+h/2-18,36,INK)
        elif kind!='read':
            s+=tx(1722,baseline,fit(value,28*fs,320),28*fs,MUTED if disabled else INK if focus else VALUE,anchor='end')
            if not disabled:s+=icon('forward',1742,y+h/2-15,30,INK if focus else MUTED)
        return s+'</g>'
    playback=['启动时自动播放直播','剧集自动播放下集']
    def preview(y=640,h=300):
        # Reuse the established video artwork; compare readable channel text against a real scene.
        s=f'<defs><clipPath id="settings-preview"><rect x="548" y="{y}" width="1240" height="{h}" rx="14"/></clipPath></defs><g clip-path="url(#settings-preview)">'
        s+=g['scene'](548,y,1240,h)+rect(1270,y,518,h,'#111629',0,fill_opacity=.65)
        s+=tx(1310,y+67,'频道列表',28,MUTED,600)+rect(1294,y+91,470,72,'#36305c',10)
        s+=tx(1310,y+139,'01  新闻频道',32,weight=600)+tx(1310,y+207,'02  纪实频道',32)+tx(1310,y+266,'节目名称与频道信息',26,MUTED)
        return (s+'</g>').replace('<text ','<text stroke="#080c18" stroke-width="2.6" stroke-linejoin="round" paint-order="stroke" ')
    def main(selected=0,focus_menu=True,large=False):
        s=frame(selected,nav_focus=focus_menu,large=large)
        if selected==0:
            for i,title in enumerate(playback):s+=row(320+i*(156 if large else 112),title,focus=not focus_menu and i==0,kind='toggle',checked=True,large=large)
        elif selected==1:
            s+=row(320,'字号','跟随系统',focus=not focus_menu)
            s+=row(432,'频道侧栏透明度','35%')
            s+=tx(548,602,'显示预览',28,MUTED,600)+preview()
        return s
    def choice(title,options,current=0,focus=None,large=False):
        focus=current if focus is None else focus;s=frame(1 if title=='字号' else 0,title,large=large)
        for i,name in enumerate(options):s+=row(332+i*(156 if large else 112),name,focus=i==focus,kind='radio',checked=i==current,large=large)
        return s
    def transparency():
        s=frame(1,'频道侧栏透明度')+tx(548,337,'背景透明度',28,MUTED)
        s+=rect(548,368,1240,154,FOCUS,14,'#fff',4)+tx(1168,440,'35%',76,weight=650,anchor='middle')+line(598,440,634,440,INK,3)+icon('plus',1704,422,36,INK)
        s+=line(704,462,1632,462,'#b9bfd0',8)+line(704,462,1110,462,'#e1d7ff',8)+f'<circle cx="1110" cy="462" r="17" fill="{INK}"/>'
        s+=tx(704,565,'0%',26,MUTED)+tx(1632,565,'80%',26,MUTED,anchor='end')
        s+=tx(548,637,'侧栏预览',28,MUTED,600)+preview(670,300)
        return s
    def media_summary(state='connected'):
        s=frame(2)
        status={'connected':'已连接','empty':'未连接','offline':'未连接','checking':'检查中…'}[state]
        name='尚未添加媒体库' if state=='empty' else '客厅媒体库'
        color='#bfe2d2' if state=='connected' else '#f2b4bb' if state=='offline' else MUTED
        has_server=state!='empty';retry_focus=state in ('offline','checking')
        suffix=' · '+status
        label=fit(name,36,(1120 if has_server else 1240)-text_width(suffix,36))
        text_end=548+text_width(label+suffix,36)
        s+=tx(548,400,label,36,weight=650)+tx(548+text_width(label,36),400,suffix,36,color,600)
        if has_server:
            # 48dp target at the TV design scale; keep the same node throughout retry.
            x=text_end+24
            action_name='正在重试连接' if state=='checking' else '重试连接' if state=='offline' else '检查连接'
            s+=f'<g data-role="connection-retry" data-busy="{str(state=="checking").lower()}" aria-label="{action_name}">'
            s+=rect(x,340,96,96,'none',0)
            s+=icon('refresh',x+30,370,36,INK if retry_focus else VALUE)
            if retry_focus:s+=line(x+26,422,x+70,422,INK,3)
            s+='</g>'
        info={'connected':'当前账号：示例账号','empty':'添加后即可浏览电影与剧集。','offline':'暂时无法连接，请重试或切换媒体库。','checking':'正在连接服务器并验证当前账号…'}[state]
        s+=tx(548,482,info,28,MUTED)
        s+=row(554,'新增媒体库',focus=not retry_focus)+row(666,'切换媒体库',disabled=state=='empty')
        return s
    def media_switch(state='ready',large=False):
        s=frame(2,'切换媒体库',large=large);fs=1.5 if large else 1
        for i,name in enumerate(['客厅媒体库 · 示例账号','家庭影音 · 访客账号','书房媒体库 · 示例账号']):
            if large and i==1:name='家庭影音服务器的较长名称 · 访客账号'
            s+=row(332+i*(156 if large else 112),name,focus=i==1,kind='radio',checked=i==0,large=large)
        y=900 if large else 758
        if state=='switching':s+=tx(548,y,'正在连接家庭影音…',28,MUTED)
        elif state=='error':s+=para(548,y,'无法连接家庭影音，请检查网络后重试。\n当前仍使用客厅媒体库。',1200,28,'#f2b4bb')
        return s
    def connection(error=False,loading=False):
        s=top('新增媒体库')+tx(96,202,'Jellyfin 或兼容媒体服务器',28,MUTED)
        for i,(label,value) in enumerate([('服务器地址','http://media.example:8096'),('用户名','示例账号'),('密码','••••••••')]):
            y=240+i*172;s+=tx(100,y+29,label,30,weight=600)+rect(100,y+52,1080,96,'#20263e',14,'#fff' if (i==2 and error) or (i==0 and not error and not loading) else '#363e5d',4 if (i==2 and error) or (i==0 and not error and not loading) else 1)+tx(128,y+112,value,32,MUTED if loading else INK)
        if error:s+=tx(100,786,'账号或密码不正确，请检查后重试。',28,'#ffb4bc')
        elif loading:s+=tx(100,786,'正在验证服务器与账号…',28,MUTED)
        s+=action(100,836,500,'正在连接…' if loading else '检测并连接',loading)+action(628,836,260,'取消连接' if loading else '取消')
        s+=action(1280,292,520,'发现局域网服务器',disabled=loading)+tx(1280,498,'连接提示',34,weight=650)
        for y,value in [(570,'使用服务器上的媒体账号。'),(658,'切换地址或账号后需重新登录。'),(746,'媒体库与电视直播独立使用。')]:s+=para(1280,y,value,520,28)
        return s
    def keyboard(title='新增媒体库',label='密码',value='••••••••'):
        s=top(title)+tx(100,232,label,32,weight=600)+rect(100,268,1260,96,'#20263e',14)+tx(128,330,value,36)+action(1400,268,388,'完成')
        s+=rect(0,470,1920,610,'#1b2033',0)+tx(100,542,'输入'+label,30,weight=600)
        for i,letters in enumerate(['QWERTYUIOP','ASDFGHJKL','ZXCVBNM']):
            x=960-(len(letters)*116-16)/2
            for j,letter in enumerate(letters):s+=action(x+j*116,584+i*100,100,letter,i==0 and j==0,h=76)
        for x,w,label in [(330,260,'中文 / 英文'),(622,430,'空格'),(1084,240,'删除'),(1356,234,'完成')]:s+=action(x,914,w,label,h=82)
        return s
    def box_summary():
        s=frame(3)+tx(548,332,'已保存配置 · 安徽移动百视通',28,MUTED)
        for i,title in enumerate(['从机顶盒导入','导入配置文件','手动修改','导出配置文件']):s+=row(374+i*132,title,focus=i==0)
        s+=tx(548,950,'导出文件包含账号与密钥，请妥善保存。',26,MUTED)
        return s
    def box_import():
        s=top('从机顶盒导入')+tx(100,204,'机顶盒配置',28,MUTED)
        for y,name,value in [(292,'机顶盒 IP','192.0.2.10'),(494,'ADB 端口','5555')]:
            s+=tx(100,y,name,30,weight=600)+rect(100,y+28,1080,96,'#20263e',14,'#fff' if y==292 else '#363e5d',4 if y==292 else 1)+tx(128,y+90,value,34)
        s+=tx(1280,292,'读取前准备',34,weight=650)
        for y,value in [(365,'与电视连接同一局域网。\n机顶盒保持开机。'),(480,'开启网络 ADB，并允许读取配置。'),(595,'仅适配安徽移动百视通。\n需要 root shell 读取权限。')]:s+=para(1280,y,value,520,28)
        s+=action(100,790,500,'读取配置')+action(628,790,260,'取消')
        return s
    def box_edit(imported=False,focus=True):
        s=top('机顶盒配置')+tx(100,212,'读取成功，尚未保存' if imported else '常用信息',32,VALUE if imported else MUTED)
        for y,label,value in [(304,'平台编号（UserID）','示例平台编号'),(524,'原机顶盒 MAC','00:11:22:33:44:55')]:
            s+=tx(100,y,label,30,weight=600)+rect(100,y+32,1080,96,'#20263e',14,'#fff' if y==304 and not imported and focus else '#363e5d',4 if y==304 and not imported and focus else 1)+tx(128,y+94,value,34)
        s+=tx(1280,304,'其他参数',34,weight=650)+para(1280,370,'设备、服务与认证参数按类别查看。',520,28)
        s+=action(1280,532,520,'高级参数')+para(1280,702,'修改暂存为草稿，保存后才会生效。',520,28)
        s+=action(100,836,500,'测试登录与频道',imported)+action(628,836,500,'保存配置')
        return s
    def advanced(group=False):
        s=top('设备标识' if group else '高级参数')+tx(100,202,'设置 / 机顶盒配置 / '+('高级参数' if group else '手动修改'),26,MUTED)
        rows=[('TVID','示例设备标识'),('STBID','示例机顶盒标识'),('原机顶盒 IP','192.0.2.10'),('设备型号','导入的设备型号'),('设备版本描述','导入的版本信息')] if group else [('设备标识','TVID、STBID、IP、型号与版本'),('账号与授权','绑定账号、密码、认证密钥及用户分组'),('服务地址','登录地址、节目单地址'),('渠道参数','客户端渠道与标识')]
        for i,(title,value) in enumerate(rows):
            y=280+i*130
            s+=rect(100,y,1688,112,FOCUS if i==0 else SURFACE,14,'#fff' if i==0 else 'none',4 if i==0 else 0)+tx(128,y+69,title,32,weight=600)
            if group:s+=tx(1688,y+69,fit(value,28,1120),28,INK if i==0 else VALUE,anchor='end')
            s+=icon('forward',1728,y+42,28)
        return s
    def discard():
        s=box_edit(focus=False)+rect(0,0,1920,1080,'#080c18',0,fill_opacity=.84)
        s+=rect(520,340,880,380,'#171c32',16,'#363e5d',1)+tx(568,416,'放弃本次修改？',40,weight=650)+para(568,479,'尚未保存的内容将丢失。\n已生效的配置不会改变。',784,28)
        s+=action(568,594,360,'继续编辑',True)+action(952,594,360,'放弃修改')
        return s
    def discovery():
        s=top('选择媒体服务器')+tx(100,212,'发现的局域网服务',28,MUTED)
        for i,(name,address) in enumerate([('客厅媒体库','media.example:8096'),('家庭影音服务器','cinema.example:8096'),('书房媒体库','library.example:8096')]):
            y=292+i*128
            s+=rect(100,y,1688,104,FOCUS if i==0 else SURFACE,14,'#fff' if i==0 else 'none',4 if i==0 else 0)
            s+=tx(132,y+65,name,34,weight=600)+tx(1728,y+65,address,28,INK if i==0 else VALUE,anchor='end')
        return s
    def about():
        s=frame(4,nav_focus=True)
        s+='<defs><linearGradient id="about-mark" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#AA5CC3"/><stop offset="1" stop-color="#00A4DC"/></linearGradient></defs>'
        s+=f'<svg x="1110" y="462" width="128" height="128" viewBox="-16 -16 105 105"><g fill="url(#about-mark)" fill-rule="evenodd"><path d="{g["LOGO_OUTER"]}"/><path d="{g["LOGO_INNER"]}"/></g></svg>'
        s+=tx(1174,656,'JellyLive',52,weight=650,anchor='middle')+tx(1174,722,'v0.1.3',32,VALUE,anchor='middle')
        s+=tx(1174,800,'基于 Jellyfin Android TV / Media3。',30,anchor='middle')+tx(1174,846,'使用 GPL-2.0 许可证。',30,anchor='middle')
        return s
    def selector_scene(large=False):
        return choice('字号',['跟随系统','加大字号'],large=large)
    def replace(slug,title,body):
        index=next(i for i,s in enumerate(g['SCREENS']) if s['id']==slug)
        g['SCREENS'].pop(index);g['emit'](slug,title,body);entry=g['SCREENS'].pop();g['SCREENS'].insert(index,entry)
    g['settings_page']=main
    g['box_import_design']=box_import
    def additional():
        replace('18-jellyfin','新增媒体库：遥控器表单',connection())
        replace('18-jellyfin-ime','新增媒体库：系统输入法',keyboard())
        cases=[
            ('17-settings-audio','音轨默认设置（已取消）',None),
            ('17-settings-subtitle','字幕默认设置（已取消）',None),
            ('17-settings-aspect','画面比例默认设置（已取消）',None),
            ('17-settings-font','设置子菜单：字号',selector_scene()),
            ('17-settings-transparency','设置子菜单：侧栏透明度',transparency()),
            ('17-settings-media','设置：媒体库连接状态',media_summary()),
            ('17-settings-box','设置：机顶盒配置入口',box_summary()),
            ('17-settings-box-import','机顶盒配置：从设备导入',box_import()),
            ('17-settings-box-edit','机顶盒配置：常用信息',box_edit()),
            ('17-settings-box-advanced','机顶盒配置：高级参数分组',advanced()),
            ('17-settings-box-device','机顶盒配置：设备标识',advanced(True)),
            ('17-settings-box-field','机顶盒配置：单项编辑',keyboard('编辑 TVID','TVID','AHYDFF001122334455')),
            ('17-settings-box-discard','配置：放弃草稿确认',discard()),
            ('17-settings-connection-error','媒体库连接：错误保留输入',connection(error=True)),
            ('17-settings-about','设置：关于',about()),
            ('17-settings-large','设置：150%字号',main(focus_menu=False,large=True)),
            ('17-settings-choice-large','设置子菜单：150%字号',selector_scene(True)),
            ('17-settings-box-ready','机顶盒配置：读取成功待保存',box_edit(True)),
            ('17-settings-discovery','媒体库连接：发现服务器',discovery()),
            ('17-settings-connection-loading','媒体库连接：验证中',connection(loading=True)),
        ]
        for slug,title,body in cases:
            if body is None:
                g['SCREENS'].append(dict(id=slug,title=title,note='已取消设置入口，请在各自播放器调整。',flow='播放器底部信息栏',kind='state',retired=True))
            else:
                g['emit'](slug,title,body);g['SCREENS'][-1]['kind']='state' if any(k in slug for k in ['error','discard','loading','large','ready']) else 'main'
    def connection_states():
        for slug,title,body in [
            ('17-settings-media-empty','媒体库连接：尚未添加',media_summary('empty')),
            ('17-settings-media-offline','媒体库连接：暂时未连接',media_summary('offline')),
            ('17-settings-media-checking','媒体库连接：检查中',media_summary('checking')),
            ('17-settings-media-switch','切换媒体库：已保存的账号',media_switch()),
            ('17-settings-media-switching','切换媒体库：连接中',media_switch('switching')),
            ('17-settings-media-switch-error','切换媒体库：失败保留原会话',media_switch('error')),
            ('17-settings-media-switch-large','切换媒体库：150%字号与长名称',media_switch(large=True)),
        ]:
            g['emit'](slug,title,body);g['SCREENS'][-1]['kind']='state'
    g['SETTINGS_CONNECTION_STATES']=connection_states
    return additional
