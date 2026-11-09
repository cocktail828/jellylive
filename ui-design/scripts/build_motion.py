#!/usr/bin/env python3
"""Build the vector startup motion preview; all assets stay local."""
from pathlib import Path
from build_designs import splash_body

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'designs'
base=(OUT/'svg/00-splash.svg').read_text()
animated=base.replace(splash_body(),splash_body(animated=True))
(OUT/'motion').mkdir(exist_ok=True)
(OUT/'motion/startup.svg').write_text(animated)
# Inline SVG uses the exact same paths and coordinates as the static drawing.
stage=animated.replace('<svg xmlns=', '<svg id="stage" xmlns=',1)
home=(OUT/'svg/02-home.svg').read_text().replace('<svg xmlns=', '<svg id="home" hidden xmlns=',1)
html='''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>JellyLive · 启动动画设计</title><style>
:root{color-scheme:dark}*{box-sizing:border-box}body{margin:0;padding:28px;background:#080c18;color:#f6f5ff;font:16px/1.8 system-ui,"Noto Sans CJK SC","Microsoft YaHei",sans-serif}main{max-width:1440px;margin:auto}header{display:flex;align-items:center;justify-content:space-between;gap:20px;flex-wrap:wrap}h1{font-size:24px;margin:0}p{color:#a9adc6}a{color:#b9a7ff}button{padding:10px 22px;border:1px solid #797095;border-radius:10px;background:#242a46;color:#f6f5ff;font:inherit;cursor:pointer}button:focus-visible,a:focus-visible{outline:3px solid white;outline-offset:4px}#stage{display:block;width:100%;height:auto;max-height:75vh;border:1px solid #363e5d;border-radius:16px;margin-top:24px}nav{display:flex;gap:12px;align-items:center;margin-top:18px}.note{font-size:14px}
</style><style>#viewport{position:relative;margin-top:24px;background:#101326;aspect-ratio:16/9;border:1px solid #363e5d;border-radius:16px;overflow:hidden}#stage,#home{display:block;width:100%;height:100%;max-height:none;border:0;border-radius:0;margin:0}#viewport [hidden]{display:none}#prefill{position:absolute;inset:0;background:#101326}button[aria-pressed=true]{border-color:white;background:#36305c}nav{flex-wrap:wrap}</style><main><header><h1>启动衔接 · 当前设计</h1><a href="index.html">返回设计总览</a></header><div id="viewport">__SVG____HOME__<div id="prefill" hidden aria-label="同色前置窗口，无品牌静态图"></div></div>
<nav aria-label="启动阶段设计"><button id="before" type="button" aria-pressed="false">前置背景</button><button id="app" type="button" aria-pressed="true">应用动画</button><button id="ready" type="button" aria-pressed="false">准备就绪</button></nav>
<nav><button id="toggle" type="button">暂停动画</button><button id="replay" type="button">从头播放</button><span id="state" role="status">图标匀速旋转 · 2秒/圈</span></nav>
<p>前置窗口只保留同色背景，去掉完整品牌静态PNG；系统启动图标层沿用同色背景，由电视系统实际渲染。应用内图标旋转、右侧 JellyLive 固定，准备好就进入主页，不等动画播完，也不增加最低停留时间。</p><p class="note">按钮仅用于评审各阶段，阶段持续时间不代表实际启动耗时；“准备就绪”展示已有主页设计，不模拟网络成功。尊重系统“减少动态效果”，恢复应用无需重播品牌动画。图标形状来自 Jellyfin Android TV，授权见 ASSET_NOTICES.txt。</p></main>
<script>
const stage=document.querySelector('#stage'),toggle=document.querySelector('#toggle'),state=document.querySelector('#state');let paused=false,phase='app';
function setPaused(value){paused=value;if(value||phase!=='app'||document.hidden)stage.pauseAnimations();else stage.unpauseAnimations();toggle.textContent=value?'播放动画':'暂停动画';toggle.setAttribute('aria-pressed',String(value));state.textContent=value?'已暂停 · 加载无需依赖动画完成':'图标匀速旋转 · 2秒/圈'}
toggle.onclick=()=>setPaused(!paused);document.querySelector('#replay').onclick=()=>{stage.setCurrentTime(0);setPaused(false)};
const reduced=window.matchMedia('(prefers-reduced-motion: reduce)');setPaused(reduced.matches);reduced.addEventListener?.('change',e=>setPaused(e.matches));
function showPhase(value){phase=value;document.querySelector('#prefill').hidden=value!=='before';stage.toggleAttribute('hidden',value!=='app');document.querySelector('#home').toggleAttribute('hidden',value!=='ready');for(const name of ['before','app','ready'])document.querySelector('#'+name).setAttribute('aria-pressed',String(name===value));toggle.disabled=value!=='app';document.querySelector('#replay').disabled=value!=='app';if(value==='app')setPaused(reduced.matches);else {stage.pauseAnimations();state.textContent=value==='before'?'同色前置背景 · 不放完整品牌静态图':'准备就绪即进入主页 · 不等待动画完成'}}
for(const name of ['before','app','ready'])document.querySelector('#'+name).onclick=()=>showPhase(name);
document.addEventListener('visibilitychange',()=>{if(document.hidden)stage.pauseAnimations();else if(phase==='app'&&!paused)stage.unpauseAnimations()});
</script></html>'''
(OUT/'startup-animation.html').write_text(html.replace('__SVG__',stage).replace('__HOME__',home))
print('Built local SVG startup animation and preview.')
