# 当前 UI 设计

面向 Android TV、16:9 与遥控器操作。此目录仅保留当前版本设计及其生成源文件。

打开 [设计总览](designs/index.html)，或分别查看 [启动](designs/startup.html)、[直播](designs/live.html)、[影视](designs/media.html)、[设置](designs/settings.html)。各分区按入口、操作、子菜单和异常状态排列；[阅读顺序](designs/DESIGN_READING_ORDER.txt)提供图号索引。

当前为 107 个保留编号、91 张有效稿。02、05、24、32、42、58、62、65、66、67、68、80、81、88、93、100 是停用编号，没有对应图像；原图号保持。

- `designs/previews/`：当前有效页面的 1920×1080 PNG。
- `designs/svg/`：对应可编辑 SVG；另含页面关系和收藏操作示意。
- `designs/adaptation.html`：3840×2160（4K）固定布局预览。
- `designs/startup-animation.html`：启动阶段与动态效果预览。
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
python3 scripts/build_motion.py
python3 scripts/export_tv_adaptations.py
python3 scripts/verify_tv_designs.py
```

几何结果写入 `designs/tv-design-verification.json`，不替代 Android TV 的实际遥控测试。素材说明和授权见 [ASSET_NOTICES.txt](designs/ASSET_NOTICES.txt) 与 [LICENSE-JELLYFIN.txt](designs/LICENSE-JELLYFIN.txt)。

当前直播源设计位于设置分区的“直播源：设置内直接管理”：设置右侧直接展示列表与操作，已进入代码实施并完成覆盖安装；本轮原生验证未重跑。影视图稿已按现有实现刷新标题、继续观看、分类卡片与元信息；电影、剧集、收藏均去掉分页，使用固定工具栏下的多行海报与焦点滚动追加。

2026-11-18：移除应用字号与字幕字号设置、字号缩放及大字号图稿生成分支；保留固定电视文字层级。验收以4K为准，1080p仅作画册缩略图。本轮按用户要求不打包、不安装，设计与编译检查不代表修改后的原生实机通过。
