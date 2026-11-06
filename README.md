# JellyLive

面向 Android TV 的直播和媒体库客户端，当前版本 **v0.1.1**。基于 Jellyfin Android TV 0.19.10 修改，保留上游 GPL-2.0 许可证和版权声明，原项目说明见 [docs/upstream/README.md](docs/upstream/README.md)。

支持直播频道、收藏、节目单、遥控器操作、全屏侧栏及闲置自动收起；媒体库沿用 Jellyfin SDK，支持手动连接和切换服务器。

## 配置

仓库不包含个人机顶盒资料、媒体库地址及账号密码、访问令牌或签名密钥。普通构建需要在应用内导入或填写自己的配置。

如需为自己的设备制作预配置 APK，可在仓库外准备私有 JSON，并通过下列环境变量指定文件路径：

- `BESTV_EMBED_CONFIG`：机顶盒配置，字段见 `BestvApi.FIELDS`。
- `BESTV_EMBED_LIBRARY_CONFIG`：媒体库配置，包含 `address`、`username`、`password` 三个字符串字段。

构建脚本临时生成的两个 bootstrap asset 在退出时删除，带配置的 APK 写入被 Git 忽略的 `private/`。其中的账号资料可以从 APK 中提取，此类 APK 只用于自己的设备。已有媒体库会话及手动选择的服务器优先；更换地址或账号会清空预填密码。

## 容器构建

使用已有 Docker 和包含 JDK 21、Android SDK / Build Tools 36.0.0 的构建镜像。脚本不在宿主机安装工具，也不会自动拉取镜像。默认镜像与本项目验证使用的环境一致，可用 `JELLYLIVE_BUILD_IMAGE` 指定其他已有镜像。

```sh
./scripts/build-container.sh
```

默认构建缓存位于 `.local-build/cache/`，可用 `JELLYLIVE_GRADLE_CACHE` 指定已有缓存目录；无配置 APK 输出至 `dist/`。Gradle 和依赖下载、编译、生成调试签名以及验签均在容器中完成。调试签名使用 Android 标准调试口令，仅存放在本地缓存中。

已具备 Android 构建环境时也可执行 `./gradlew :app:assembleDebug`；默认使用 Android 标准调试签名路径，或通过 `-Pjellylive.debugKeystore=路径` 指定本地调试密钥。

## 验证

容器构建完成并有 Gradle 缓存后：

```sh
./scripts/check-container.sh
```

复用已有的 17 项直播交互决策检查、13 项媒体库预配置策略检查和 11 项数字版本检查。交互检查使用模拟时钟和平台对象，实机显示、遥控器和硬件解码仍需在设备上验证。

版本采用 `v主.次.修订`，允许第四段数字；Android 整数版本号由数字版本自动计算。
