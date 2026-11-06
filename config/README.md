# 本地内置连接配置

真实连接信息只放在本地文件，代码仓库保留格式示例。容器打包脚本会自动读取以下两个可选文件：

- `.local-build/bootstrap/bestv-bootstrap.json`：原机顶盒直播配置，字段见 `examples/bestv-bootstrap.example.json`。已有导出的配置也可直接使用。
- `.local-build/bootstrap/jellyfin-bootstrap.json`：媒体库地址、用户名和密码，格式见 `examples/jellyfin-bootstrap.example.json`。

仓库内的配置输入必须已被 Git 忽略，脚本也会拒绝已经跟踪的文件。`.local-build/` 和这两个实际文件名均被 Git 忽略。请自行保管这些文件；新克隆的仓库不含真实账密。

```sh
mkdir -p .local-build/bootstrap
cp config/examples/jellyfin-bootstrap.example.json .local-build/bootstrap/jellyfin-bootstrap.json
# 编辑本地 JSON，填入自己的连接信息；可按需添加机顶盒配置。
chmod 700 .local-build/bootstrap
chmod 600 .local-build/bootstrap/*.json
sh scripts/build-container.sh
```

只使用已有构建容器。若缓存放在别处，可设置 `JELLYLIVE_GRADLE_CACHE`。配置以只读文件挂入容器，由 Gradle 作为额外 assets 合并，构建前后都不会写入 `app/src/main/assets/`。不经过此脚本的普通 Gradle 构建默认不带配置。

带配置的 APK 输出到忽略的 `private/BestvLive-v版本-configured.apk`，文件权限为 600；未带配置的 APK 输出到 `dist/`。APK 会包含这些连接信息，请仅用于自己的设备。

```sh
# 明确生成不带任何内置连接配置的 APK（即使本地文件存在）
JELLYLIVE_EMBED_CONFIG=0 sh scripts/build-container.sh

# 继续支持指定其他位置的本地文件
BESTV_EMBED_CONFIG=/path/to/box.json \
BESTV_EMBED_LIBRARY_CONFIG=/path/to/library.json \
sh scripts/build-container.sh
```

直播配置只在尚未保存机顶盒账号时导入。媒体库配置只作为首次连接默认值；已有登录和用户手动切换的媒体库优先，不会被每次启动强制覆盖。
