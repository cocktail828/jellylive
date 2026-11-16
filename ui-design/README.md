# 当前 UI 设计

媒体库与央视频已合入[主画册](designs/index.html)、[影视画册](designs/media.html)和[央视频画册](designs/cctv.html)。媒体库侧栏为全部媒体、电影、电视剧、观看记录、收藏；央视频侧栏为四类节目、观看记录、收藏。首页第二入口已更名央视频。仅更新设计，未修改App实现。

媒体库记录与收藏依赖当前媒体服务器账号；央视频记录与收藏在App内部独立保存；主页收藏仅用于直播源频道，三套数据互不混合。详见[媒体库规范](TV_LIBRARY_REDESIGN.md)、[央视频规范](TV_CCTV_DESIGN.md)及[统一规则](designs/INTERACTION_RULES.txt)。仅保留主画册及启动/直播/影视/央视频/设置五个主分区。媒体库独立画册、专项预览、重复图稿和临时提案已删除，正式素材归入标准目录。

面向 Android TV、16:9 与遥控器操作。此目录仅保留当前版本设计及其生成源文件。

打开 [设计总览](designs/index.html)，或分别查看 [启动](designs/startup.html)、[直播](designs/live.html)、[影视](designs/media.html)、[央视频](designs/cctv.html)、[设置](designs/settings.html)。各分区按入口、操作、子菜单和异常状态排列；[阅读顺序](designs/DESIGN_READING_ORDER.txt)提供图号索引。

当前为 146 个保留编号、138 张有效稿，分为 18 个流程。02、05、24、32、42、65、66、67 是停用编号，没有对应图像；其余原图号保留。

- `designs/previews/`：当前有效页面的 1920×1080 PNG。
- `designs/native-4k/`：已合入原画册的媒体库与央视频3840×2160 PNG，原画册放大与下载使用这些文件。
- `designs/svg/`：对应可编辑 SVG；新版媒体库与央视频为原生3840×2160，其他页面保持原1920×1080；另含页面关系和收藏操作示意。
- `designs/manifest.json`、`sections.json`、`flow-groups.json`：页面、分区与流程索引。
- `designs/INTERACTION_RULES.txt`：统一交互规则；各专项规范见同目录的 `*_REVIEW.txt`。
- `scripts/`：生成当前图稿、画册、适配预览和几何检查的源文件。

既有设置已实施：菜单默认单行、播放设置精简、媒体库新增/切换、带背景预览与真实透明度。原生验证范围另见仓库增量验收文档。

媒体库入口的图29与图101–104已实施：居中恢复连接、失败重试当前库和影视内切换媒体库。

图稿中的频道、媒体、数量与设备资料均为示例。这里是设计目标；应用运行截图与原生验收另见仓库文档。

## 重新生成

使用现有 Python 3、librsvg、Cairo、Pango 与 ImageMagick，在此目录执行：

```sh
python3 scripts/build_designs.py
python3 scripts/build_gallery.py
python3 scripts/render_designs.py
python3 scripts/verify_tv_designs.py
python3 scripts/verify_library_integration.py
python3 scripts/verify_cctv_designs.py
```

几何结果写入 `designs/tv-design-verification.json`，不替代 Android TV 的实际遥控测试。素材说明和授权见 [ASSET_NOTICES.txt](designs/ASSET_NOTICES.txt) 与 [LICENSE-JELLYFIN.txt](designs/LICENSE-JELLYFIN.txt)。

构建、依赖安装、导出和检查均须在容器内运行。`build_designs.py`复用`library_home_4k.py`与`cctv_designs.py`中的绘制函数，所有图稿直接归入主设计的SVG、预览和原生4K目录，不生成独立画册或重复素材。

当前直播源设计位于设置分区的“直播源：设置内直接管理”：设置右侧直接展示列表与操作，已进入代码实施并完成覆盖安装；本轮原生验证未重跑。媒体库浏览图稿已更新为确认后的4K方案：全部媒体、电影、电视剧、观看记录、收藏；顶部搜索与四项筛选，内容区排序，五列海报、大字号四列，随遥控焦点滚动追加。此项仅更新设计，不代表应用已经实现。

央视频正式绘图源为`scripts/cctv_designs.py`，固定少量源字段样本保存在`scripts/data/cctv-source-samples.json`供可复现生成；不含下载剧照。主画册追加图121–146，原图号不重排。长标题聚焦滚动规则见正式设计规范，静态图稿显示初始省略状态。静态设计和本地容器检查不代表真实遥控或播放接入验证。
