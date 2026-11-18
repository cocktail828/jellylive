#!/usr/bin/env python3
"""Vector-derived TV resolution previews; no Android emulation or installed packages."""
from pathlib import Path
from render_designs import new,render,surface,context,write,destroy,finish,unref,bind,cairo
ROOT=Path(__file__).resolve().parents[1];D=ROOT/'designs';OUT=D/'adaptations';OUT.mkdir(exist_ok=True)
scale=bind(cairo,'cairo_scale',[__import__('ctypes').c_void_p,__import__('ctypes').c_double,__import__('ctypes').c_double],None)
targets=[('13-movie-cast-next','电影详情 · 演职人员下一页'),('12-library-favorites','媒体库 · 收藏'),('13-movie','电影详情 · 等宽同行操作'),('15-media-long-tracks','媒体音轨 · 长名称与固定布局'),('09-sources', '设置直播源 · 未配置与禁用刷新'), ('09-sources-populated', '设置直播源 · 来源直接操作'), ('11-library', '影视首页 · 当前卡片与比例进度'), ('25-home-unconfigured', '未配置直播源 · 完整主页空状态'), ('12-library-grid', '固定布局 · 六张海报'), ('05-live-bright', '亮色视频 · 文字保护'), ('17-settings', '设置 · 固定布局'), ('17-settings-display', '显示 · 带背景预览'), ('17-settings-media', '媒体库 · 当前连接'), ('18-jellyfin', '媒体库连接 · TV表单'), ('17-settings-box-edit', '机顶盒配置 · 常用信息'), ('15-media-player', '媒体播放器 · 四项功能'), ('15-media-aspect', '媒体播放器 · 比例选项'), ('09-box-config', '直播源 · 机顶盒配置'), ('05-live-quality', '直播 · 真实画质菜单'), ('24-library-restoring', '媒体库 · 居中恢复连接'), ('24-jellyfin-offline', '媒体库 · 失败与重试'), ('24-library-switch', '媒体库 · 切换列表'), ('24-library-switching', '媒体库 · 切换中'), ('24-library-switch-error', '媒体库 · 切换失败'), ('17-settings-about', '关于 · Logo名称同行与整组居中')]
sections=[]
for slug,title in targets:
    images=[]
    for w,h in [(3840,2160)]:
        handle=new(str(D/'svg'/f'{slug}.svg').encode(),None);image=surface(0,w,h);cr=context(image);scale(cr,w/1920,h/1080)
        assert render(handle,cr);name=f'{slug}-{w}x{h}.png';assert not write(image,str(OUT/name).encode());destroy(cr);finish(image);unref(handle)
        images.append(f'<a href="adaptations/{name}">{w} × {h} 原图</a>')
    sections.append(f'<section><h2>{title}</h2><p>'+ ' · '.join(images)+f'</p><img src="adaptations/{slug}-3840x2160.png" loading="lazy" alt="{title}"></section>')
html='''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>JellyLive · TV适配效果</title><style>:root{color-scheme:dark}body{margin:0;background:#090c17;color:#f6f5ff;font:16px/1.8 system-ui,sans-serif}main{max-width:1500px;margin:auto;padding:32px}h1{font-size:28px}h2{font-size:22px}p{color:#a9adc6}a{color:#b9a7ff}img{display:block;width:100%;height:auto;border:1px solid #363e5d}section{margin:40px 0}a:focus-visible{outline:3px solid white;outline-offset:4px}</style><main><a href="tv-review.html">返回本轮更新</a><h1>Android TV · 4K固定布局预览</h1><p>由当前SVG按3840×2160导出；主画册缩略图为1080p。保持16:9与相同逻辑构图，重点比较文字、卡片和安全边距。这些是设计预览，不是Android运行截图。</p>__SECTIONS__</main></html>'''
(D/'adaptation.html').write_text(html.replace('__SECTIONS__',''.join(sections)))
print(f'Exported {len(targets)} TV resolution previews.')
