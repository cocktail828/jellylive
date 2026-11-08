# JellyLive

面向 Android TV、使用遥控器操作的直播与媒体库客户端，当前版本 **v0.1.3**。

基于 Jellyfin Android TV 0.19.10 修改，保留 [GPL-2.0 许可证](LICENSE)和版权声明；原项目说明见 [上游 README](docs/upstream/README.md)。

## 界面预览

以下为 v0.1.3 在 Waydroid 中的实际运行截图，包含 1080p 和 4K 横屏页面。频道、海报与观看进度来自配置的服务。

| 直播与设置 | 媒体库与播放 |
| :---: | :---: |
| **直播主页**<br>![直播主页：预览、频道列表与快捷入口](docs/screenshots/live-home.png) | **媒体库首页**<br>![媒体库首页：继续观看与电影、剧集、最近添加入口](docs/screenshots/library-home.png) |
| **全屏频道侧栏**<br>![直播全屏：透明频道侧栏与当前频道焦点](docs/screenshots/live-sidebar.png) | **电影列表**<br>![电影海报列表：评分、名称、搜索、筛选与排序](docs/screenshots/library-movies.png) |
| **播放设置**<br>![播放设置：分类导航与可聚焦的设置项](docs/screenshots/settings-playback.png) | **媒体播放器**<br>![媒体播放器：进度条、播放控制与局部倍速选项](docs/screenshots/media-player.png) |

## 功能

- **电视直播**：频道分组、收藏、节目单、预览与全屏播放；支持配置机顶盒直播、M3U 来源及 Jellyfin 直播频道。
- **遥控器交互**：方向键、确认键、返回键完成主要操作；直播全屏长按确认可收藏或取消收藏，频道侧栏与底部信息栏闲置自动收起。
- **媒体库**：连接和切换服务器；首页展示继续观看及电影、剧集、最近添加入口，并显示已知内容数量。
- **浏览与筛选**：海报列表、媒体搜索，按类型、观看状态、年份、分辨率、地区筛选；支持名称、评分、发布时间、时长等排序，选择即生效。
- **媒体播放**：续播、快进快退、触摸拖动进度、音轨与字幕选择、0.5x–2x 倍速（步进 0.25x），以及剧集自动播放下一集。
- **电视布局**：16:9 横屏、焦点高亮、电视安全区与大字号适配；播放器使用两行信息栏和局部选项弹层。

直播与媒体搜索分别限定各自的内容范围。“继续观看”展示未看完的媒体，合并当前服务器、当前账号的服务端记录与本机有效进度；播放返回首页时自动刷新。

## 兼容范围

媒体库使用 Jellyfin SDK，标准 Jellyfin 走原有接口；飞牛影视提供部分接口兼容，包括目录、封面、详情和续播读取。

当前飞牛兼容播放路径依赖设备直放能力，尚未接入已验证的重封装或转码兜底。本机进度保存和续播已验证，飞牛端最新进度的跨设备同步尚未确认。

Waydroid 已用于原生页面、焦点、播放器弹层及续播流程验证。显示分辨率设为 4K 不会增加解码能力；HEVC Main10 等格式能否播放，应以实际电视能力和实播结果为准。排查记录见 [Main10 能力协商](docs/HEVC_MAIN10_WAYDROID_DIAGNOSIS.txt)。

## 配置

仓库不包含个人机顶盒资料、媒体库地址及账号密码、访问令牌或签名密钥。普通构建需要在应用内导入或填写自己的配置。

本地打包会自动读取两个可选私有文件，文件均被 Git 忽略：

- `.local-build/bootstrap/bestv-bootstrap.json`：机顶盒直播配置。
- `.local-build/bootstrap/jellyfin-bootstrap.json`：媒体库 `address`、`username`、`password`。

格式示例和操作说明见 [本地内置连接配置](config/README.md)。也支持用 `BESTV_EMBED_CONFIG`、`BESTV_EMBED_LIBRARY_CONFIG` 指定其他私有文件。仓库内的输入必须未被跟踪且已被 Git 忽略。

文件只读挂入容器，由 Gradle 作为额外 assets 合并，不写入源码 assets。带配置的 APK 输出到忽略的 `private/`；设置 `JELLYLIVE_EMBED_CONFIG=0` 可明确构建无内置配置的 APK。已有媒体库会话及用户手动选择的服务器优先。

## 容器构建

使用已有 Docker 和包含 JDK 21、Android SDK / Build Tools 36.0.0 的构建镜像。脚本不在宿主机安装工具，也不会自动拉取镜像。默认镜像与本项目验证使用的环境一致，可用 `JELLYLIVE_BUILD_IMAGE` 指定其他已有镜像。

```sh
sh scripts/build-container.sh
```

默认构建缓存位于 `.local-build/cache/`，可用 `JELLYLIVE_GRADLE_CACHE` 指定已有缓存目录；无配置 APK 输出至 `dist/`。Gradle 和依赖下载、编译、生成调试签名以及验签均在容器中完成。

脚本通过 `-Pjellylive.debugKeystore` 使用缓存中的本地调试密钥；密钥采用 Android 标准调试口令，不要求宿主机具备 Android 构建环境。

版本采用 `v主.次.修订`，允许第四段数字；Android 整数版本号由数字版本自动计算。

## 验证与开发记录

容器构建完成并有 Gradle 缓存后：

```sh
sh scripts/check-container.sh
sh scripts/check-library-container.sh
```

检查覆盖直播交互与长按收藏、配置和版本规则、图片鉴权、媒体搜索/筛选/排序/分页、目录缓存、普通与大字号布局、播放器状态、续播和自动下一集。

Android 原生回归驱动的构建与使用见 [原生验证说明](scripts/device/README.txt)。驱动需要在取得设备操作与安装授权后单独安装，测试截图与原始诊断保存在本地忽略目录。

- [设计与实现对应关系](docs/JELLYLIVE_DESIGN_IMPLEMENTATION.txt)
- [Waydroid 页面与焦点验证](docs/WAYDROID_REGRESSION.txt)
- [继续观看修复与验证范围](docs/CONTINUE_WATCHING_VALIDATION.txt)
