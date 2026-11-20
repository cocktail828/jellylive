"""CCTV uses the current dev media grid/detail system, never the main branch UI."""
def additional(g):
    rect,text,center,button,icon,scene=[g[x] for x in ('rect','text','centered_text','centered_button','icon','scene')]
    def grid(category,empty=False,error=False):
        s=g['header']('央视频')+button(96,136,452,'分类：'+category,h=76)
        s+=button(888,136,296,'首字母：全部',h=76)+button(1208,136,296,'筛选：全部',h=76)+button(1528,136,296,'刷新' if category!='收藏'else '最近收藏',h=76)
        s+=text(96,252,category+' · 示例目录',24,g['MUTED'])
        if empty or error:
            s+=icon('media',912,346,96,'#b7a2ff')+center(960,536,'节目加载失败' if error else '暂无收藏',44,weight=650)
            s+=center(960,620,'请检查网络后重试。' if error else '浏览节目后，可在详情页收藏节目。',28,g['MUTED'])
            return s+button(700,718,520,'重试' if error else '浏览电视剧',True,h=76)
        s+='<defs><clipPath id="cctv-grid-clip"><rect x="96" y="262" width="1728" height="764"/></clipPath></defs><g clip-path="url(#cctv-grid-clip)">'
        for row in range(2):
            for col in range(6):
                x=100+col*292;y=270+row*476;i=row*6+col
                s+=scene(x,y,260,390,i)+rect(x,y+326,260,64,'#101629eb',0)+center(x+130,y+368,f'节目示例 {i+1:02}',28,weight=600)+center(x+130,y+424,'2025 · 20集',24,g['MUTED'])
                if i==0:s+=rect(x,y,260,390,'none',14,'#fff',4)
        return s+'</g>'
    def detail():
        s=scene(0,0,1920,1080)+rect(0,0,1920,1080,'#101326',0,fill_opacity=.92)+g['header']('央视频详情')+scene(96,214,280,420)
        s+=text(424,280,'海岸日记',64,weight=700)+text(424,392,'2025 · 旅行纪实 · CCTV-9纪录',28,g['MUTED'])+text(424,449,'收录 60集',26,g['MUTED'])
        s+=text(424,522,'沿着海岸线，记录城市的生活与风景。',30)+text(424,566,'一次次相遇，让平凡日常成为值得珍藏的记忆...',30)
        s+=button(424,622,176,'继续播放',True,h=68)+button(624,622,176,'从头播放',h=68)+button(824,622,176,'取消收藏',h=68)
        s+=button(96,708,360,'选集：1–20',h=56)+center(1776,748,'1 / 5',24,g['MUTED'])
        for i in range(4):
            x=100+i*440;s+=scene(x,780,400,152,i)+text(x+16,987,f'第{i+1}集 · 真实分集标题',26)
        return s
    for slug,title,body in [
        ('26-cctv-series','央视频：电视剧',grid('电视剧')),
        ('26-cctv-animation','央视频：动画片',grid('动画片')),
        ('26-cctv-documentary','央视频：纪录片',grid('纪录片')),
        ('26-cctv-special','央视频：特别节目',grid('特别节目')),
        ('26-cctv-detail','央视频：共用详情与选集',detail()),
        ('26-cctv-favorites-empty','央视频：收藏为空',grid('收藏',empty=True)),
        ('26-cctv-load-error','央视频：来源失败与重试',grid('电视剧',error=True)),
    ]:
        g['emit'](slug,title,body);g['SCREENS'][-1]['note']='沿用dev媒体库的六列海报、工具栏、紧凑文字操作与焦点翻页。节目名称、年份和数量为布局示例；真实内容来自央视接口。'
