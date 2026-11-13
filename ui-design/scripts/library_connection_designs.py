"""Media-entry recovery designs; Android TV, incumbent palette, design only."""
from html import escape


def additional(g):
    rect, icon, header = g['rect'], g['icon'], g['header']
    ink, muted, value = '#f6f5ff', '#b9bfd0', '#c5b8ff'

    def tx(x, y, text, size=30, color=ink, weight=400, anchor='middle'):
        return f'<text x="{x}" y="{y}" text-anchor="{anchor}" fill="{color}" font-size="{size}" font-weight="{weight}">{escape(text)}</text>'

    def restoring():
        s = header('我的媒体库')
        s += '<g data-role="library-restoring" data-center-x="960">'
        s += icon('refresh', 928, 408, 64, value)
        s += tx(960, 514, '客厅媒体库', 30, muted)
        s += tx(960, 580, '正在恢复连接…', 44, weight=650)
        s += tx(960, 638, '正在连接服务器并验证账号', 28, muted)
        return s + '</g>'

    def failed():
        s = header('我的媒体库')
        s += '<g data-role="library-connection-error" data-center-x="960">'
        s += icon('link', 928, 342, 64, value)
        s += tx(960, 466, '客厅媒体库 · 未连接', 30, muted)
        s += tx(960, 532, '暂时无法连接媒体库', 46, weight=650)
        s += tx(960, 596, '请确认媒体库已开启，或切换到其他媒体库。', 28, muted)
        s += g['centered_button'](626, 650, 320, '重试连接', True, h=88)
        s += g['centered_button'](974, 650, 320, '切换媒体库', h=88)
        return s + '</g>'

    def switch(state='ready'):
        s = header('切换媒体库') + tx(960, 240, '当前：客厅媒体库', 30, muted)
        for i, name in enumerate(['客厅媒体库 · 示例账号', '家庭影音 · 访客账号', '书房媒体库 · 示例账号']):
            y = 320 + i * 120
            focused = i == 1
            s += rect(400, y, 1120, 100, 'url(#purple)' if focused else '#1b2038', 14, '#fff' if focused else '#363e5d', 4 if focused else 1)
            s += tx(432, y + 63, name, 32, weight=600, anchor='start')
            if i == 0:
                s += tx(1436, y + 63, '当前', 28, value, anchor='end') + icon('check', 1456, y + 34, 32, value)
            elif focused and state != 'ready':
                s += tx(1488, y + 63, '连接中…' if state == 'busy' else '未连接', 28, anchor='end')
        if state == 'busy':
            s += tx(960, 756, '正在连接家庭影音…', 28, muted)
        elif state == 'error':
            s += tx(960, 756, '家庭影音连接失败，请重试或选择其他媒体库。', 28, '#f2b4bb')
            s += tx(960, 806, '当前媒体库仍为客厅媒体库', 28, muted)
        return s

    # Revise figure 29 in place; append new states without renumbering old figures.
    index = next(i for i, screen in enumerate(g['SCREENS']) if screen['id'] == '24-jellyfin-offline')
    g['SCREENS'].pop(index)
    g['emit']('24-jellyfin-offline', '媒体库：连接失败与恢复', failed())
    entry = g['SCREENS'].pop()
    g['SCREENS'].insert(index, entry)
    for slug, title, body in [
        ('24-library-restoring', '媒体库：居中恢复连接', restoring()),
        ('24-library-switch', '媒体库：切换已保存媒体库', switch()),
        ('24-library-switching', '媒体库：切换连接中', switch('busy')),
        ('24-library-switch-error', '媒体库：切换失败保留原库', switch('error')),
    ]:
        g['emit'](slug, title, body)
        g['SCREENS'][-1]['kind'] = 'state'
