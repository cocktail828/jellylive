"""Scoped TV playback and box-config design refinements; no Android implementation."""
import re


def install(g):
    text, rect, icon, center = [g[n] for n in ('text', 'rect', 'icon', 'centered_text')]

    def media_wait(opening=False):
        # Both acquisition and buffering share one non-focusable status surface.
        s = rect(0, 0, 1920, 1080, '#080c18', 0) if opening else g['media_player']()
        s += '<g data-role="playback-wait" data-focusable="false" data-live="polite">'
        s += rect(760, 476, 400, 128, '#101629', 16, fill_opacity=.94)
        left = 960-(76+g['estimated_text_width']('正在缓冲…', 34))/2
        s += icon('refresh', left, 516, 48, '#c5b4ff')
        s += text(left+76, 555, '正在缓冲…', 34, weight=600)
        return s + '</g>'

    def media_aspect():
        s = g['media_player'](active='画面比例')
        x, y, w, h = 888, 524, 336, 328
        s += g['frosted_backdrop'](s, x, y, w, h)
        s += rect(x, y, w, h, '#191f34', 16, '#59627d', 1, fill_opacity=.65)
        s += rect(x+8, y+76, w-16, h-84, '#080d1c', 10, fill_opacity=.22)
        s += text(x+28, y+52, '画面比例', 30, weight=650)
        for i, label in enumerate(['原始', '填充', '拉伸']):
            ry = y+84+i*76
            s += rect(x+12, ry, w-24, 68, 'url(#purple)' if i == 0 else 'none', 10,
                      '#fff' if i == 0 else 'none', 3 if i == 0 else 1)
            s += text(x+32, ry+46, label, 28, weight=600 if i == 0 else 400)
            if i == 0:
                s += icon('check', x+w-62, ry+22, 26, '#e0d2ff')
        return s

    def box_menu(large=False):
        # Protected focus is appropriate here: this task belongs to the source entry.
        background = g['sources_page'](populated=True)
        background = re.sub(r'stroke="#(?:fff|ffffff)"', 'stroke="none"', background)
        s = background + rect(0, 0, 1920, 1080, '#080c18', 0, fill_opacity=.84)
        s += '<g data-role="box-config-menu" data-origin="sources" data-restore="box-config">'
        s += rect(400, 140, 1120, 800, '#171c32', 16, '#59627d', 1)
        s += text(448, 224 if large else 216, '机顶盒配置', 60 if large else 40, weight=650)
        s += text(448, 292 if large else 276, '已保存配置，无需重复填写。', 39 if large else 28, '#b9bfd0')
        rows = ['从机顶盒导入', '导入配置文件', '手动修改', '导出配置文件']
        for i, title in enumerate(rows):
            y = (336+i*140) if large else (324+i*120)
            h = 124 if large else 100
            s += rect(448, y, 1024, h, 'url(#purple)' if i == 0 else '#20263e', 14,
                      '#fff' if i == 0 else 'none', 4 if i == 0 else 0)
            s += text(480, y+(80 if large else 62), title, 48 if large else 32, weight=600)
            s += icon('forward', 1416, y+h/2-16, 32)
        s += text(448, 920 if large else 872, '导出文件包含账号资料，请妥善保存。', 26, '#b9bfd0')
        return s + '</g>'

    def box_import_error():
        s = g['box_import_design']()
        s += text(100, 458, '无法连接机顶盒，请检查 IP、端口和调试授权后重试。', 28, '#f2b4bb')
        return s

    g['media_wait'] = media_wait

    def additional():
        for slug, title, body in [
            ('15-media-aspect', '媒体播放器：画面比例浮层', media_aspect()),
            ('15-media-opening', '媒体播放：进入时统一缓冲提示', media_wait(opening=True)),
            ('09-box-config', '直播源：机顶盒配置', box_menu()),
            ('09-box-config-large', '机顶盒配置：150%字号', box_menu(large=True)),
            ('09-box-config-import-error', '机顶盒配置：读取失败保留输入', box_import_error()),
        ]:
            g['emit'](slug, title, body)
            g['SCREENS'][-1]['kind'] = 'state'
    return additional
