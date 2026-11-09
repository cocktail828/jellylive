# 当前 UI 设计

面向 Android TV、16:9 与遥控器操作。此目录仅保留当前版本设计及其生成源文件。

打开 [设计总览](designs/index.html)，或分别查看 [启动](designs/startup.html)、[直播](designs/live.html)、[影视](designs/media.html)、[设置](designs/settings.html)。各分区按入口、操作、子菜单和异常状态排列；[阅读顺序](designs/DESIGN_READING_ORDER.txt)提供图号索引。

当前为 89 个保留编号、86 张有效稿，分为 17 个流程。05、32、42 是停用编号，没有对应图像；其余原图号保留。

- `designs/previews/`：当前有效页面的 1920×1080 PNG。
- `designs/svg/`：对应可编辑 SVG；另含页面关系和收藏操作示意。
- `designs/adaptation.html`：720p/4K、大字号适配预览。
- `designs/startup-animation.html`：启动阶段与动态效果预览。
- `designs/manifest.json`、`sections.json`、`flow-groups.json`：页面、分区与流程索引。
- `designs/INTERACTION_RULES.txt`：统一交互规则；各专项规范见同目录的 `*_REVIEW.txt`。
- `scripts/`：生成当前图稿、画册、适配预览和几何检查的源文件。

图稿中的频道、媒体、数量与设备资料均为示例。这里是设计目标；应用运行截图与原生验收另见仓库文档。

## 重新生成

使用现有 Python 3、librsvg、Cairo、Pango 与 ImageMagick，在此目录执行：

```sh
python3 scripts/build_designs.py
python3 scripts/build_gallery.py
python3 scripts/render_designs.py
python3 scripts/build_motion.py
python3 scripts/build_overview.py
python3 scripts/export_tv_adaptations.py
python3 scripts/verify_tv_designs.py
```

几何结果写入 `designs/tv-design-verification.json`，不替代 Android TV 的实际遥控测试。素材说明和授权见 [ASSET_NOTICES.txt](designs/ASSET_NOTICES.txt) 与 [LICENSE-JELLYFIN.txt](designs/LICENSE-JELLYFIN.txt)。
