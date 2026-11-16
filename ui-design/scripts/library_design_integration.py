"""Merge approved library scenes into stable legacy figure slots and new slots."""
from library_home_4k import SCENES, scene_svg
import json

LEGACY = {
    'home':'11-library', 'movies':'12-library-grid',
    'empty-library':'23-library-empty', 'filter-type':'12-library-filter',
    'sort':'12-library-sort', 'empty-history':'11-library-empty-history',
    'no-results':'12-library-no-results', 'loading':'12-library-loading',
    'large-text':'12-library-large-text', 'history':'12-library-recent',
}
# Explicit order keeps new figure numbers stable, independent of preview ordering.
ADDITIONAL = ['series','history-sort','filter-year','filter-rating','filter-progress',
              'filtered','offline','append-error','metadata','focus-sidebar','focus-search','focus-sort','favorites','empty-favorites','unsupported']
FLOW = {
    'favorites':'服务器收藏列表；详情 → 图17/18；服务器成功确认取消后移除，焦点回邻近卡片；空 → 图119；不支持 → 图120',
    'empty-favorites':'服务器已成功返回零项；浏览全部媒体 → 图15；不可将读取失败当作空收藏',
    'unsupported':'当前服务器能力不可用；浏览全部媒体 → 图15；观看记录同规则，不使用本地列表冒充',
    'home':'收藏 → 图118；侧栏电影 → 图16；电视剧 → 图106；观看记录 → 图63；搜索 → 图20；筛选/排序 → 图39/40、108–110',
    'movies':'搜索 → 图20（当前电影分类）；筛选 → 图39/108–110；排序 → 图40；影片 → 图17；返回 → 图15',
    'series':'搜索 → 图20（当前电视剧分类）；筛选/排序沿用当前分类；剧集 → 图18；返回 → 图15',
    'history':'最近观看优先；排序 → 图107；确认卡片 → 图17/18；侧栏全部媒体 → 图15',
    'large-text':'四列大字号网格；确认卡片 → 图17/18；上下随焦点滚动；筛选/排序保留条件，详情返回恢复来源位置',
    'filtered':'下键进入筛选后的列表；确认卡片 → 图17/18；清除筛选后回类型按钮；返回保留当前分类与条件',
    'metadata':'确认卡片 → 图17/18；长标题聚焦后延迟滚动；缺评分/年份/进度不虚构数值；详情返回恢复原位置',
    'empty-history':'浏览全部媒体 → 图15；左键回侧栏；返回回上一级，保留观看记录入口状态',
    'empty-library':'确认重新加载；失败转连接错误；返回回原入口；加载/断连不可伪装为空库',
    'no-results':'清除筛选后回类型按钮，保留分类/查询/排序；有结果下键进入列表；返回不修改条件',
    'loading':'焦点保留侧栏；返回取消等待并回原入口；旧异步结果不得抢焦点或覆盖新页面',
    'offline':'确认重试当前媒体库；恢复后保留原分类与条件；连接恢复流程见图29/101–104',
    'append-error':'下移到末尾重试；保留卡片与滚动；确认仅重试后续内容，异步失败不抢焦点',
}
COMMON_NOTE = ' 原生3840×2160；媒体库名称在左上，侧栏全部媒体/电影/电视剧/观看记录/收藏，观看记录与收藏依赖当前媒体服务器账号，顶部搜索后为类型/年份/评分/进度；仅海报下方保留片名与年份/类型居中，海报内部不重复片名，溢出片名聚焦800ms后滚动、失焦复位，底部观看进度条，进度统一未看/在看/已看。'


def entries():
    scenes = {s[0]:s for s in SCENES}
    mapping = {**LEGACY,**{slug:f'12-library-{slug}' for slug in ADDITIONAL}}
    for slug,legacy_id in mapping.items():
        _,title,params,note = scenes[slug]
        flow = FLOW.get(slug,
            '面板上下移动，确认提交并回触发按钮，返回取消；已选勾选与白框焦点分开；详情返回恢复原分类、条件、焦点和滚动')
        if slug.startswith('focus-'):
            flow = '遥控焦点流转见TV_LIBRARY_REDESIGN.md；边界不循环；进入详情或搜索再返回恢复来源位置'
        yield slug,legacy_id,title,params,note,flow


def merge(screens,output):
    by_id = {s['id']:s for s in screens}
    assert len(screens)==105, 'Update integration figure map before changing the base slot count'
    for slug,legacy_id,title,params,note,flow in entries():
        display_note = note.replace('图16电视剧分类','图106电视剧分类')
        data = dict(id=legacy_id,title='媒体库：'+title,note=display_note+COMMON_NOTE,flow=flow,
                    kind='main' if slug in ('home','movies','series','history','favorites') else 'state',
                    nativeResolution=[3840,2160],libraryScene=slug)
        if legacy_id in by_id:
            by_id[legacy_id].update(data)
        else:
            screens.append(data)
        (output/'svg'/f'{legacy_id}.svg').write_text(scene_svg(title,params,note))
    assert len(screens)==120
    integrated = [dict(number=n,id=s['id'],scene=s['libraryScene'])
                  for n,s in enumerate(screens,1) if s.get('libraryScene')]
    (output/'library-integration.json').write_text(json.dumps({
        'figures':integrated,
        'nativeResolution':[3840,2160],'previewResolution':[1920,1080],
    },ensure_ascii=False,indent=2)+'\n')
