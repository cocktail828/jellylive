JellyLive Android 原生回归驱动

用途：在已安装的 debug APK 进程内，用真实 D-pad 事件、控件 Z/焦点检查和
UiAutomation 实屏截图验证分组、设置、媒体详情、两个播放器及快照失败降级。
私有方法反射只选择测试入口或读取状态；实际点击和方向键由 Android 分发。
驱动是独立测试 APK，不进入产品 APK，无额外依赖；只用既有容器 SDK/JDK 构建。

构建（不安装任何软件）：
  sh scripts/device/build-probe-container.sh

取得设备安装/操作授权后，再安装产品 APK 和 .local-build/waydroid-review/driver/
tv-regression-probe.apk。驱动与 debug 产品使用相同的本地测试签名。
运行示例（SERIAL 为已授权设备）：
  adb -s SERIAL shell am force-stop org.jellyfin.androidtv.bestvlive
  adb -s SERIAL shell am instrument -w -r -e phase run \
    org.jellyfin.androidtv.bestvlive.probe/.TvRegressionProbe

可选参数：-e mediaOnly true 只做媒体；-e fromStart true 从头播放；
-e skipFilter true 只用于记录旧版已知筛选崩溃后的其他基线，正式验证禁止跳过。
phase 只接受字母、数字、下划线与连字符。字体/显示设置由调用者保存并恢复，
或按用户指定目标保留。4K截图可能超过5秒idle，驱动在操作前重新显示底栏，
实际idle折叠单独检查，不修改产品超时时间。

结果位于目标应用的 externalFilesDir/tv-regression/PHASE：report.json 和 PNG。
必须检查 report.error 为空、failures 为0，并核对需要的媒体/弹层断言实际存在；
不能仅依赖 adb 命令退出码（am instrument 在进程崩溃时也可能返回0）。
报告在每项断言后保存，避免崩溃后误读上次的完成状态。

截图和诊断可能含用户影视库信息，仅放 .local-build 私有忽略目录，不提交实际数据。
这是 Android 虚拟设备回归，不代表电视遥控器硬件、全部视频解码器或全部服务认证。
测试结束可卸载 org.jellyfin.androidtv.bestvlive.probe，不要误卸载产品或清理用户数据。
