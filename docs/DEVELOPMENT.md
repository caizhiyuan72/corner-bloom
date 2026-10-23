# 开发说明

## 结构

- `MainActivity`：分类设置、应用选择、位置编辑入口。
- `PageHost` / `PageMotion`：保留页面栈、整页横移、返回与中断处理。
- `BloomView`：手势展开、选择及 Canvas 动画。
- `LauncherGeometry` / `FanGeometry`：实际展开与预览共用的布局，图标大小与展开间距分别计算。
- `PositionEditorView` / `ArrangementDraft` / `EditorMotion`：位置和排序草稿、拖动与换位动画。
- `AppEntry` / `RoundedIcons`：后台加载应用、生成并复用圆角位图，保留原有色彩和透明度。
- `OverlayService` / `OverlayRecovery`：入口窗口、生命周期、有限恢复与运行状态。
- `TapAccessibilityService` / `EditorDeleteBridge` / `DeleteHold`：可选无障碍窗口、轻点补点和长按删除。
- `Prefs` / `BootReceiver`：本地设置、可选开机启动。

## 主机端检查

`tests/*Test.java` 为独立 Java 检查，用 JDK 编译对应生产类和测试类后运行其 `main` 方法。包含手势、布局、恢复策略、删除状态、位置草稿和动画曲线；`LauncherGeometryTest` 遍历 4050 种组合。

生产类与 Android 平台替身的协议检查：

```bash
python3 tests/overlay_service_protocol.py
python3 tests/position_editor_protocol.py
python3 tests/page_host_protocol.py
python3 tests/rounded_icons_protocol.py
BLOOM_ECJ_JAR=/path/to/ecj.jar python3 tests/tap_service_protocol.py
```

需要 Python 3 和 Java。最后一项另需 ECJ；其他脚本支持 JDK 编译，部分也支持通过 `BLOOM_ECJ_JAR` 指定 ECJ。

检查覆盖窗口恢复、锁屏与服务断开、编辑草稿保存/回退、拖动与取消、页面进入中返回、后台清理，以及圆角图标的颜色与透明度。绘图和生命周期替身不能验证 Android 实际渲染、厂商通知或输入法表现。

## 设计参考

设置分组及页面切换参考 [XGesture](https://github.com/qpst4/XGesture) 的导航方式；以原生 Java 独立实现，没有复制其应用源码或引入 Compose。

页面保持实色、原尺寸横向进入，下层页面移动四分之一宽度。节奏参考 [Miuix 0.9.4 的默认转场](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/miuix-nav/src/commonMain/kotlin/top/yukonga/miuix/kmp/nav/transition/NavTransitions.kt)，采用 500ms 单调曲线；关闭系统动画时直接到位。

## 发布

清单、`app/build.gradle` 和首页页脚保持相同版本名。内部 `versionCode` 持续递增，以支持旧安装覆盖升级。发布包与 SHA-256 位于 `downloads/`，目录只保留当前版本；签名私钥与构建中间文件均不提交。
