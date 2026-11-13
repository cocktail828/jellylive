# 媒体库连接入口与关于页实施计划

> 按用户“开始实施”执行；使用 executing-plans 内联完成，最终集中审查。

目标：实现已确认图79、29、101–104。保持原Logo、v0.1.3/10300，不改无关页面。
规格：ui-design/designs/LIBRARY_CONNECTION_REVIEW.txt；docs/SETTINGS_TV_DESIGN.txt。

- [x] 在原生驱动新增最小入口验证，先对旧APK确认恢复提示不居中、失败页无切换入口。
- [x] LiveActivity关于页将Logo/名称同行，以页面中线定位信息组，保留标题、细暗焦点框和大字滚动。
- [x] JellyLibraryActivity恢复/重试居中，使用LibraryConnections已有隔离验证；失败只显示重试当前库/切换两个动作。
- [x] 影视内切换列表：验证成功才换库，忙/失败保持当前标记和目标焦点，返回取消等待/回列表/回错误按钮；支持新增和长名滚动。
- [x] 既有Docker打包；Waydroid固定3840×2160、字号1.0，仅验证本次入口、失败/重试/取消/切换及关于布局。
- [x] 集中审查，保存证据，各任务本地commit，不push；交付含本地配置APK，更新TASK_STATE。

文件边界：LiveActivity.java、JellyLibraryActivity.kt、LibraryConnections.kt（只开放既有挂起验证以便复用）、原生驱动及相应说明。
验证边界：受控loopback验证失败/取消/切换；真实NAS验证媒体库正常入口；不测试解码/播放器或字号矩阵。

实施记录：原生旧版差异2项先失败；4K画布/触摸默认焦点修复后22项绿。集中审查1项401目标重新登录缺口，经entry-auth-before复现后修复；最终26项受控+4项真实入口/关于绿。无延期项。
