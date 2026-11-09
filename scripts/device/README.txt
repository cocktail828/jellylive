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

播放能力只读诊断：-e playbackAudit true，比对当前两个电影样本的真实profile、源与协商结果。
仅接受目录恰好两个样本，其他目录需先实现明确的样本范围，避免无界诊断。
会读取播放信息但不启动视频；原始DTO可能含NAS路径，必须保持在私有忽略目录。
Main10对照只修改诊断内存副本，不改变产品profile或绕过解码能力检查。

可选参数：-e mediaOnly true 只做媒体；-e fromStart true 从头播放；
-e resumeAudit true 只读记录当前会话续播数据和本地进度（报告不含地址/账号/影片名）；
-e resumeAudit true -e expectResume true 检查冷启动能重建实际续播卡片；
-e resumeVerify true 验证真实卡片、方向键、保存位置播放与返回刷新。
-e resumeVerify true -e preferLocal true 显式验证列表第一项；仅在其他影片已有独立播放异常证据时使用，并记录验证范围。
续播播放测试优先选本地/服务器位置一致的影片，避免重置其他设备较新的位置。

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

最新TV设计回归（每种模式均可单独执行）：
  -e settingsReview true：设置类别、开关/子层、透明度、配置草稿/IME/放弃与文件取消、连接校验。
  -e mediaReview true：真实兼容媒体播放、四项底栏、比例选择与独立偏好、受控等待生命周期。
  -e searchReview true：图20键盘、光标处空格/删除/清除、系统IME返回及实际媒体结果焦点。
  -e liveReview true：实际CCTV-1/高清版本、默认高清与来回切换、列表/收藏保留及受控原流恢复。
  -e channelAudit true：只读审计频道标识/全名的重复数量，不去重，不打印私有标识。

正式矩阵设置wm size为3840x2160，font_scale分别1.0与1.5，逐模式force-stop后启动。
每份report.display应记录3840×2160、density及实际fontScale；PNG也必须3840×2160。
焦点getLocationOnScreen与getRealMetrics使用同一完整屏幕坐标；资源可用区可能只有2048px高，
不能用它裁判2160px完整截图的安全区。检查完整可见矩形，并人工查看标题/说明/弹层与IME排版。
配置放弃必须验证实际Activity已关闭且来源取得窗口焦点，不能只读取后台旧焦点标签。

媒体样本只选择当前目录实际声明支持的H.264或HEVC 8bit，不覆盖生产profile或强开Main10。
媒体等待与直播原流恢复采用受控状态验证，不能据此宣称所有网络错误均已现场发生并恢复。
实际系统文件选择器可用时用返回键取消；缺失时验证缺失提示和回原设置入口。
完整本轮矩阵、构建/签名与验证范围见docs/TV_UI_IMPLEMENTATION_VALIDATION.txt。
