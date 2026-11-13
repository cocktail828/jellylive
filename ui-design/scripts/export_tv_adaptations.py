#!/usr/bin/env python3
"""Vector-derived TV resolution previews; no Android emulation or installed packages."""
from pathlib import Path
from render_designs import new,render,surface,context,write,destroy,finish,unref,bind,cairo
ROOT=Path(__file__).resolve().parents[1];D=ROOT/'designs';OUT=D/'adaptations';OUT.mkdir(exist_ok=True)
scale=bind(cairo,'cairo_scale',[__import__('ctypes').c_void_p,__import__('ctypes').c_double,__import__('ctypes').c_double],None)
targets=[('12-library-grid','普通字号 · 六张海报'),('12-library-large-text','系统1.5倍 · 五张海报'),('05-live-bright','亮色视频 · 文字保护'),('13-movie-large-text','系统1.5倍 · 等宽两行操作'),('17-settings','设置 · 普通字号'),('17-settings-large','设置 · 系统1.5倍字号'),('17-settings-display','显示 · 带背景预览'),('17-settings-media','媒体库 · 当前连接'),('17-settings-media-switch-large','切换媒体库 · 系统1.5倍长名称'),('18-jellyfin','媒体库连接 · TV表单'),('17-settings-box-edit','机顶盒配置 · 常用信息'),('15-media-player','媒体播放器 · 四项功能'),('15-media-aspect','媒体播放器 · 比例选项'),('09-box-config','直播源 · 机顶盒配置'),('09-box-config-large','机顶盒配置 · 系统1.5倍字号'),('05-live-quality','直播 · 真实画质菜单'),('05-live-quality-large','直播 · 画质菜单大字号'),('24-library-restoring','媒体库 · 居中恢复连接'),('24-jellyfin-offline','媒体库 · 失败与重试'),('24-library-switch','媒体库 · 切换列表'),('24-library-switching','媒体库 · 切换中'),('24-library-switch-error','媒体库 · 切换失败')]
sections=[]
for slug,title in targets:
    images=[]
    for w,h in [(1280,720),(3840,2160)]:
        handle=new(str(D/'svg'/f'{slug}.svg').encode(),None);image=surface(0,w,h);cr=context(image);scale(cr,w/1920,h/1080)
        assert render(handle,cr);name=f'{slug}-{w}x{h}.png';assert not write(image,str(OUT/name).encode());destroy(cr);finish(image);unref(handle)
        images.append(f'<a href="adaptations/{name}">{w} × {h} 原图</a>')
    sections.append(f'<section><h2>{title}</h2><p>'+ ' · '.join(images)+f'</p><img src="adaptations/{slug}-1280x720.png" loading="lazy" alt="{title}"></section>')
html='''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>JellyLive · TV适配效果</title><style>:root{color-scheme:dark}body{margin:0;background:#090c17;color:#f6f5ff;font:16px/1.8 system-ui,sans-serif}main{max-width:1500px;margin:auto;padding:32px}h1{font-size:28px}h2{font-size:22px}p{color:#a9adc6}a{color:#b9a7ff}img{display:block;width:100%;height:auto;border:1px solid #363e5d}section{margin:40px 0}a:focus-visible{outline:3px solid white;outline-offset:4px}</style><main><a href="tv-review.html">返回本轮更新</a><h1>Android TV · 分辨率与大字号预览</h1><p>由当前SVG按720p与4K导出；1080p见主画册。保持16:9与相同逻辑构图，重点比较文字、卡片和安全边距。这些是设计预览，不是Android运行截图。</p>__SECTIONS__</main></html>'''
(D/'adaptation.html').write_text(html.replace('__SECTIONS__',''.join(sections)))
print(f'Exported {len(targets)*2} TV resolution previews.')
