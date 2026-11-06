# JellyLive

面向 Android TV 的直播和媒体库客户端，当前版本 **v0.1.2**。基于 Jellyfin Android TV 0.19.10 修改，保留上游 GPL-2.0 许可证和版权声明，原项目说明见 [docs/upstream/README.md](docs/upstream/README.md)。

支持直播频道、收藏、节目单、遥控器操作、全屏侧栏及闲置自动收起；媒体库沿用 Jellyfin SDK，支持手动连接和切换服务器。

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
./scripts/build-container.sh
```

默认构建缓存位于 `.local-build/cache/`，可用 `JELLYLIVE_GRADLE_CACHE` 指定已有缓存目录；无配置 APK 输出至 `dist/`。Gradle 和依赖下载、编译、生成调试签名以及验签均在容器中完成。调试签名使用 Android 标准调试口令，仅存放在本地缓存中。

脚本会通过 `-Pjellylive.debugKeystore` 指定缓存中的本地调试密钥，宿主机无需 Android 构建环境。

## 验证

容器构建完成并有 Gradle 缓存后：

```sh
./scripts/check-container.sh
./scripts/check-library-container.sh
```

当前共 144 项检查：32 项直播交互和路由、14 项确认键长按、13 项媒体库首次连接策略、11 项版本规则、16 项本地配置打包流程，以及 29 项媒体筛选/搜索/分页、18 项播放控件状态、11 项实际 HTTP 图片鉴权和元数据检查。使用现有容器与 SDK、模拟时钟或临时 HTTP 服务；实机显示、遥控器、触摸与硬件解码仍需在设备上验证。

版本采用 `v主.次.修订`，允许第四段数字；Android 整数版本号由数字版本自动计算。
