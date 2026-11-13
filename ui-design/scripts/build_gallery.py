#!/usr/bin/env python3
"""Generate an offline gallery and a page relationship diagram."""
from pathlib import Path
from html import escape
import json
import hashlib
from build_designs import header, footer, rect, text, line

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'designs'
screens=[dict(item,number=i+1) for i,item in enumerate(json.loads((OUT/'manifest.json').read_text())) if not item.get('retired',False)]
PARTS=[('startup','启动','启动动画、初始化与获取失败'),('live','直播','主页、全屏、频道、收藏、节目单与直播源'),('media','影视','媒体库、筛选排序、电影详情与播放器'),('settings','设置','全局设置、选择子菜单、媒体库连接与机顶盒配置')]
def part_for(screen):
    if screen['id'].startswith('17-settings'): return 'settings'
    number=screen['number']
    if number in (1,2,24): return 'startup'
    if number in (21,22,23) or screen['id']=='18-jellyfin-ime': return 'settings'
    if number in (*range(15,21),27,28,29,39,40,41) or screen['id'].startswith(('15-media-','12-library-','24-library-','13-movie-')): return 'media'
    return 'live'
flows=json.loads((Path(__file__).parent/'gallery_flows.json').read_text())
reading_order=['JellyLive 设计阅读顺序（保留图号，不重绘）']
for key,title,_ in PARTS:
    reading_order.extend(['',title])
    for flow in flows[key]:
        figures=' → '.join(f'图{number:02}' for number in flow['figures'])
        reading_order.append(f'- {flow["title"]}：{figures}')
(OUT/'DESIGN_READING_ORDER.txt').write_text('\n'.join(reading_order)+'\n')
ordered=[n for key,_,_ in PARTS for flow in flows[key] for n in flow['figures']]
assert len(ordered)==len(set(ordered))==len(screens) and set(ordered)=={x['number'] for x in screens}
screens.sort(key=lambda screen:ordered.index(screen['number']))
for screen in screens:
    screen['part']=part_for(screen)
    screen['revision']=hashlib.sha256((OUT/'svg'/f"{screen['id']}.svg").read_bytes()).hexdigest()[:10]
rules=json.loads((Path(__file__).parent/'design_rules.json').read_text())
rule_cards=''.join('<article class="spec"><h3>'+escape(rule['title'])+'</h3>'+''.join('<p>'+escape(p)+'</p>' for p in rule['paragraphs'])+'</article>' for rule in rules)
(OUT/'INTERACTION_RULES.txt').write_text('JellyLive 当前交互规则 · 2026-11-11\n\n'+'\n\n'.join(rule['title']+'\n'+'\n'.join(rule['paragraphs']) for rule in rules)+'\n')
main_count=sum(s['kind']=='main' for s in screens)
state_count=len(screens)-main_count
cards=[]
for i,s in enumerate(screens):
    cards.append(f'''<button class="card" data-index="{i}" data-kind="{s['kind']}" data-part="{s['part']}" aria-label="查看 {escape(s['title'])}">
    <div class="thumbnail"><img src="previews/{s['id']}.png?v={s['revision']}" alt="{escape(s['title'])}界面设计" loading="lazy"><span>{s["number"]:02}</span></div>
    <div class="caption"><h3>{escape(s['title'])}</h3><p>{escape(s['note'])}</p></div></button>''')

def part_sections(only=None,only_numbers=None):
    result=[];by_number={screen['number']:card for card,screen in zip(cards,screens)}
    for key,title,description in PARTS:
        if only and key!=only:continue
        groups=[];count=0;navigation=[]
        for flow in flows[key]:
            numbers=[n for n in flow['figures'] if only_numbers is None or n in only_numbers]
            if not numbers:continue
            anchor=f"flow-{key}-{flow['id']}";count+=len(numbers)
            navigation.append(f'<a class="utility" href="#{anchor}">{escape(flow["title"])}</a>')
            related=f'<p class="intro"><a href="{flow["related"]}">{escape(flow["relatedTitle"])}</a></p>' if flow.get('related') else ''
            groups.append(f'<div class="flow-group" id="{anchor}"><h3>{escape(flow["title"])}</h3>'+related+'<div class="grid">'+''.join(by_number[n] for n in numbers)+'</div></div>')
        if not groups:continue
        result.append(f'<section class="design-part" id="part-{key}" data-part="{key}"><h2>{title} <small>· {count} 张</small></h2><p class="intro">按入口、操作、子菜单和异常状态排列，保留原图号。<a href="{key}.html">单独查看{title}部分</a></p><nav aria-label="{title}流程目录">'+''.join(navigation)+'</nav>'+''.join(groups)+'</section>')
    return ''.join(result)

part_navigation='<nav class="part-nav" aria-label="设计分区"><a class="utility" href="tv-review.html">本轮更新</a><a class="utility" href="adaptation.html">TV适配效果</a><a class="utility" href="index.html">全部分区</a>'+''.join(f'<a class="utility" href="{key}.html">{title} · {sum(s["part"]==key for s in screens)}</a>' for key,title,_ in PARTS)+'</nav>'

template='''<!doctype html>
<html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>JellyLive · 电视界面设计册</title>
<style>
:root{color-scheme:dark;--bg:#090c17;--card:#171c30;--muted:#acb1c7;--purple:#b9a7ff}*{box-sizing:border-box}body{margin:0;background:var(--bg);color:#f6f5ff;font-family:system-ui,"Noto Sans CJK SC","Microsoft YaHei",sans-serif}a{color:var(--purple)}button{font:inherit;color:inherit;cursor:pointer}button:focus-visible,a:focus-visible{outline:3px solid white;outline-offset:5px}header,main,footer{max-width:1600px;margin:auto;padding:40px 5vw}header{padding-top:70px;padding-bottom:20px}.eyebrow{color:var(--purple);letter-spacing:.18em;font-size:13px}h1{font-size:clamp(38px,5vw,72px);margin:15px 0}h1 span{font-weight:350;color:var(--muted)}.intro{max-width:900px;color:var(--muted);line-height:1.9}.tags{display:flex;gap:10px;flex-wrap:wrap}.tags span{padding:8px 14px;border:1px solid #383251;border-radius:30px;font-size:13px;color:#d5c9ff}nav{display:flex;gap:10px;flex-wrap:wrap;margin:28px 0}.filter,.utility{border:1px solid #3a3f58;border-radius:10px;padding:12px 22px;background:#1b2035;text-decoration:none;font-size:14px}.filter[aria-pressed="true"]{background:#5c4bb6;border-color:#b9a7ff}.flow-group{margin:38px 0 58px;scroll-margin-top:24px}.flow-group>h3{font-size:22px;margin-bottom:20px}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:28px}.card{text-align:left;padding:0;border:1px solid #2b3047;border-radius:16px;background:var(--card);overflow:hidden}.card:hover{border-color:#b9a7ff}.thumbnail{position:relative;aspect-ratio:16/9;background:#0d1020}.thumbnail img{width:100%;height:100%;object-fit:contain}.thumbnail span{position:absolute;right:12px;bottom:12px;background:#080b16b8;padding:4px 10px;border-radius:6px;font-size:12px}.caption{padding:22px 26px}.caption h3{margin:0 0 10px;font-size:20px}.caption p{margin:0;font-size:14px;color:var(--muted);line-height:1.8}section{margin-bottom:55px}h2{font-size:26px;margin:0 0 20px}.specs{display:grid;grid-template-columns:1fr 1fr;gap:20px}.spec{padding:26px;background:var(--card);border:1px solid #2b3047;border-radius:14px}.spec h3{margin:0 0 15px}.spec p,.spec li{font-size:15px;color:var(--muted);line-height:1.9}.spec ul{padding-left:20px}.flow{width:100%;border:1px solid #2b3047;border-radius:16px}.swatches{display:flex;gap:18px;flex-wrap:wrap}.swatch i{display:block;width:72px;height:44px;border-radius:7px;margin-bottom:8px;border:1px solid #444}.swatch{font-size:12px;color:var(--muted)}footer{color:var(--muted);font-size:13px;line-height:1.9;border-top:1px solid #2b3047}dialog{width:min(96vw,1700px);max-width:none;max-height:96vh;border:1px solid #55516d;border-radius:16px;background:#101426;color:white;padding:20px}dialog::backdrop{background:#03050cee}.viewer-top{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-bottom:16px}.viewer-top h2{margin:0;font-size:20px}.viewer-actions{display:flex;gap:10px;flex-wrap:wrap}.viewer-actions button{padding:10px 18px;border-radius:8px;border:1px solid #4a4267;background:#292342}#large{display:block;width:100%;max-height:70vh;object-fit:contain;background:#080b16}#note,#route{color:var(--muted);line-height:1.7;font-size:14px}#route{color:#d1c3ff}#downloads{display:flex;gap:24px;font-size:14px}button[hidden],section[hidden]{display:none}.design-part{margin-top:48px}.design-part h2{padding-bottom:16px;border-bottom:1px solid #30364e}.design-part small{font-size:16px;color:var(--muted);font-weight:400}.part-nav a[aria-current="page"]{background:#5c4bb6;border-color:#b9a7ff}.tip{font-size:13px;color:var(--muted)}
@media(max-width:800px){.grid,.specs{grid-template-columns:1fr}header,main,footer{padding-left:20px;padding-right:20px}.viewer-top{align-items:flex-start;flex-direction:column}.caption{padding:18px}}
@media print{body{background:white;color:black}header,main,footer{padding:15px}nav,.tip,.specs,dialog{display:none}.grid{display:block}.card{display:block;width:100%;break-inside:avoid;background:white;color:black;margin-bottom:25px}.caption p{color:#333}.card[hidden]{display:none}.thumbnail{max-height:60vh}.caption{padding:12px}.intro{color:#333}}
</style>
<header><div class="eyebrow">JELLYLIVE / CURRENT DESIGN / 2026.11.10</div><h1>打开电视，<span>回到观看。</span></h1>
<p class="intro">当前设计 · 2026-11-11。按启动、直播、影视、设置四部分查看，保留原图号。直播与媒体使用同高两行信息栏，媒体新增画面比例；加载提示统一为正在缓冲；直播源配置使用电视大行弹窗。按钮只显示当前选项，字幕关闭时显示“字幕”。频道侧栏宽560px，音轨/字幕采用紧凑单行浮层，媒体列表为2:3竖版海报。图片与文字均为设计示意，原生实现与验收另见代码仓库文档。</p>
<div class="tags"><span>__MAIN__ 个主要界面 + __STATE__ 个状态界面</span><span>1920 × 1080</span><span>SVG 可编辑 / PNG 高清预览</span><span>遥控器优先</span><span>离线可浏览</span></div></header>
<main><section aria-labelledby="pages"><h2 id="pages">按功能查看设计</h2>__PART_NAV__<nav aria-label="界面分类"><button class="filter" data-filter="all" aria-pressed="true">全部 __TOTAL__</button><button class="filter" data-filter="main" aria-pressed="false">主要界面 __MAIN__</button><button class="filter" data-filter="state" aria-pressed="false">状态界面 __STATE__</button><a class="utility" href="previews/05-live-playback-settings.png">画面比例浮层</a><a class="utility" href="previews/12-library-filter.png">筛选设计</a><a class="utility" href="previews/12-library-sort.png">排序设计</a><a class="utility" href="previews/15-media-speed.png">倍速浮层</a><a class="utility" href="previews/15-media-next.png">自动下一集</a><a class="utility" href="previews/11-library-empty-history.png">暂无观看历史</a><a class="utility" href="previews/06-channel-drawer.png">透明频道侧栏</a><a class="utility" href="startup-animation.html">播放启动动画</a><a class="utility" href="previews/live-favorite-flow.png">长按收藏反馈</a><a class="utility" href="#behavior">操作与状态规则</a><a class="utility" href="#relationships">页面关系图</a></nav>
<p class="tip">点击图稿放大。放大后使用 ← → 浏览设计图，Esc 关闭；这不是 App 交互原型。</p>__PARTS__</section>
<section id="startup"><h2>启动动画 · 图标旋转，文字固定</h2><p class="intro">左侧 Jellyfin 图标每 2 秒匀速旋转一圈，右侧 JellyLive 固定；背景与系统启动页、应用首帧保持一致。应用就绪即转入主页，不额外等动画结束。</p><a class="utility" href="startup-animation.html">打开动态预览与播放控制</a></section>
<section id="relationships"><h2>页面关系</h2><a href="svg/25-page-map.svg"><img class="flow" src="previews/25-page-map.png" alt="JellyLive 页面导航关系图" loading="lazy"></a></section>
<section id="behavior"><h2>当前交互规则</h2><div class="specs">__RULE_CARDS__</div></section>
<section><h2>已取消页面</h2><p>图05独立频道浏览、图32自动首次收藏说明、图42直播播放列表已取消，当前画册和设计包不含旧图像。原编号占位保留，其他图号不重排。</p></section><section><h2>视觉基准</h2><div class="swatches"><div class="swatch"><i style="background:#101326"></i>背景 #101326</div><div class="swatch"><i style="background:#171c32"></i>面板 #171C32</div><div class="swatch"><i style="background:#7561ed"></i>品牌 #7561ED</div><div class="swatch"><i style="background:#f6f5ff"></i>文字 #F6F5FF</div><div class="swatch"><i style="background:#a9adc6"></i>辅助 #A9ADC6</div></div></section>
</main><footer>当前设计与交互说明更新于2026-11-11；设计规范与原生验收独立记录。图17/19参考 Jellyfin 的结构，以当前 JellyLive 风格重新绘制；场景、频道与媒体信息均为示意。矢量风景为原创素材。<br>原始设计：svg/ · 高清预览：previews/ · 页面清单：manifest.json · 生成方式见 README.txt。</footer>
<dialog id="viewer" aria-labelledby="viewer-title"><div class="viewer-top"><h2 id="viewer-title"></h2><div class="viewer-actions"><button id="prev" aria-label="上一张">← 上一张</button><button id="next" aria-label="下一张">下一张 →</button><button id="close">关闭 Esc</button></div></div><img id="large" alt=""><p id="note"></p><p id="route"></p><div id="downloads"><a id="svg-link">打开 SVG 原稿</a><a id="png-link">打开高清 PNG</a></div></dialog>
<script>
const screens=__MANIFEST__;
const dialog=document.querySelector('#viewer');let active=0,opener=null;
function show(i){active=(i+screens.length)%screens.length;const s=screens[active];document.querySelector('#viewer-title').textContent=`${String(s.number).padStart(2,'0')} · ${s.title}`;const img=document.querySelector('#large');img.src=`previews/${s.id}.png?v=${s.revision}`;img.alt=s.title;document.querySelector('#note').textContent=s.note;document.querySelector('#route').textContent='操作去向：'+s.flow;document.querySelector('#svg-link').href=`svg/${s.id}.svg?v=${s.revision}`;document.querySelector('#png-link').href=`previews/${s.id}.png?v=${s.revision}`;}
document.querySelectorAll('.card').forEach(b=>b.addEventListener('click',()=>{opener=b;show(+b.dataset.index);dialog.showModal();document.querySelector('#close').focus();}));
function step(amount){const indices=[...document.querySelectorAll('.card')].filter(c=>!c.hidden&&c.dataset.part===screens[active].part).map(c=>+c.dataset.index);if(indices.length)show(indices[(indices.indexOf(active)+amount+indices.length)%indices.length]);}
document.querySelector('#prev').onclick=()=>step(-1);document.querySelector('#next').onclick=()=>step(1);document.querySelector('#close').onclick=()=>dialog.close();dialog.addEventListener('close',()=>opener?.focus());dialog.addEventListener('keydown',e=>{if(e.key==='ArrowRight'){e.preventDefault();step(1)}if(e.key==='ArrowLeft'){e.preventDefault();step(-1)}});
document.querySelectorAll('.filter').forEach(b=>b.addEventListener('click',()=>{document.querySelectorAll('.filter').forEach(f=>f.setAttribute('aria-pressed',String(f===b)));document.querySelectorAll('.card').forEach(c=>c.hidden=b.dataset.filter!=='all'&&c.dataset.kind!==b.dataset.filter);document.querySelectorAll('.flow-group,.design-part').forEach(part=>part.hidden=![...part.querySelectorAll('.card')].some(c=>!c.hidden))}));
</script></html>'''
for current_part in [None,*[key for key,_,_ in PARTS],'tv-review']:
    review_numbers={1,13,19,21,22,23,45,46,47,48,50,57,59,61,64,*range(65,101)}
    review=current_part=='tv-review'
    selected=[screen for screen in screens if (screen['number'] in review_numbers if review else current_part is None or screen['part']==current_part)]
    navigation=part_navigation.replace(f'href="{current_part or "index"}.html"',f'href="{current_part or "index"}.html" aria-current="page"')
    page=template.replace('__MAIN__',str(sum(s['kind']=='main' for s in selected))).replace('__STATE__',str(sum(s['kind']=='state' for s in selected))).replace('__TOTAL__',str(len(selected))).replace('__RULE_CARDS__',rule_cards).replace('__PARTS__',part_sections(None,review_numbers) if review else part_sections(current_part)).replace('__PART_NAV__',navigation).replace('__MANIFEST__',json.dumps(screens,ensure_ascii=False))
    if current_part:
        label='本轮TV审查修订' if review else next(title for key,title,_ in PARTS if key==current_part)
        page=page.replace('<title>JellyLive · 电视界面设计册</title>',f'<title>JellyLive · {label}设计</title>')
    (OUT/f'{current_part or "index"}.html').write_text(page)
(OUT/'flow-groups.json').write_text(json.dumps(flows,ensure_ascii=False,indent=2))
(OUT/'sections.json').write_text(json.dumps([dict(id=key,title=title,file=f'{key}.html',figures=[s['number'] for s in screens if s['part']==key]) for key,title,_ in PARTS],ensure_ascii=False,indent=2))

# The map uses its own neutral framing, outside product UI.
body=header('JellyLive 页面关系','主流程与恢复路径 · 频道浏览并入主页和全屏列表')
nodes=[(64,230,380,130,'启动','品牌动画 / 缓存与获取'),(580,230,640,130,'主页','纯视频 / 焦点换台 / 四磁贴'),(1360,230,496,130,'全屏直播与节目','全屏 / 侧栏 / 节目单'),(64,540,380,160,'频道列表与收藏','首页 / 全屏列表 · 收藏卡片'),(510,540,410,160,'直播源','内置更新 / 第三方源'),(986,540,410,160,'媒体库','浏览 / 详情 / 播放'),(1462,540,394,160,'设置与连接','页签联动 / Jellyfin'),(64,810,590,110,'媒体搜索','仅当前媒体库 · 电影 / 剧集详情'),(720,810,1136,110,'状态与恢复','失败重试 / 缓存 / 手动源 / 返回独立直播')]
for x,y,w,h,a,b in nodes:body+=rect(x,y,w,h,'#22233f',18,'#6b5d9e',2)+text(x+24,y+48,a,30,weight=600)+text(x+24,y+92,b,24,'#b9bed0')
for a,b,c,d in [(444,295,580,295),(1220,295,1360,295),(900,360,900,440),(254,440,1658,440),(254,440,254,540),(715,440,715,540),(1191,440,1191,540),(1658,440,1658,540),(1191,700,1191,760),(1191,760,360,760),(360,760,360,810)]:body+=line(a,b,c,d,'#a599d9',3)
body+=footer('返回原则：先关闭弹层，再回原页面；全屏退出回到原焦点。')
svg=f'<svg xmlns="http://www.w3.org/2000/svg" width="1920" height="1080" viewBox="0 0 1920 1080"><title>JellyLive 页面关系图</title><g font-family="Noto Sans CJK SC,Microsoft YaHei,sans-serif"><rect width="1920" height="1080" fill="#101326"/>{body}</g></svg>'
(OUT/'svg/25-page-map.svg').write_text(svg)
print('Built offline gallery and page map.')
