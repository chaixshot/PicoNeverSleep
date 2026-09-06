<div align="center">
<img src="/app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.webp" width="128" height="128"/>

# PicoNeverSleep

[English](README.md) | [中文](README_zh.md) | [Русский](README_ru.md)

### 为您的 Pico VR 头戴设备添加“永不休眠”快捷设置按钮

PicoNeverSleep 是一款专为 Pico VR 头显设计的 LSPosed 模块，可在“快捷设置”面板中添加专用的“永不休眠”切换开关。只需轻轻一点，即可让屏幕保持常亮，非常适合测试、观看长视频或无人值守下载。

</div>

## 👓 截图

<image src="Resource/Screenshot1.jpeg" width="400"/>

## 🌟 核心功能

* **🌙 永不休眠切换：** 直接从“快捷设置”中轻松防止头显进入休眠状态。
* **🔄 重启后保持：** 通过挂钩系统启动阶段，在设备重启后自动恢复您的“永不休眠”状态。
* **🌐 多语言支持：** 完全本地化，支持 27+ 种语言，包括英语、中文、日语、韩语和多种欧洲语言。
* **🧹 纯粹专注：** 单一用途模块，无多余功能，不产生额外后台耗电。

## ⛏️ 必备条件

* **设备：** Pico 4 头戴设备（支持海外版和中国版固件）。
* **超级用户：** 需要 **[Root 权限](https://github.com/chaixshot/more-picohaxx)** 以修改系统文件。
* **环境：** 必须安装并激活 **[LSPosed 框架](https://github.com/JingMatrix/Vector/releases/tag/v2.0)**。
* **权限：** 应用请求时请授予 Root 权限。
* **LSPosed 作用域：** 确保在 LSPosed 模块作用域中勾选了 `System Framework (android)` 和 `PicoVR Settings (com.picovr.settings)`。

## 📐 如何使用？

1. 在您的头显上 **安装** `PicoNeverSleep.apk`。
2. **打开** LSPosed Manager 应用。
3. **启用** PicoNeverSleep 模块。
4. **检查作用域：** 确保同时勾选了 `System Framework` 与 `PicoVR Settings`。
5. **重启** 设备以激活挂钩。
6. **使用方法：**
    * 打开“快捷设置”面板（点击 Dock 栏中的时钟/电池区域）。
    * 您将在列表开头看到一个新的 **永不休眠** 按钮。
    * 点击即可切换：图标将高亮显示，文字也将更新以指示已激活。

## ⁉️ 为什么按钮没有出现？

* 确保您已在 **LSPosed Manager** 中启用了该模块。
* 仔细检查是否已勾选 **com.picovr.settings** 作用域。
* 首次启用模块后，您 **必须重启** 头显（或至少重启“设置”应用）。

## ⁉️ 它是如何工作的？

该模块挂钩至 `com.picovr.settings`，以将自定义快捷磁贴注入到快捷设置适配器中。

* 通过切换系统属性 `pvr.factorytest.never.sleep` 来控制休眠行为。
* 由于 Pico OS 在每次开机时都会将此属性重置为 `0`，因此该模块还挂钩了 **System Server**（`android` 包）的启动阶段，以便在系统启动完成后立即从持久化的 `Settings.Global` 变量中恢复您保存的状态。

## 🔃 语言支持

本应用支持 27 种语言，包括：
Čeština, Dansk, Nederlands, English (UK/US), Suomi, Français, Deutsch, Ελληνικά, Italiano, 日本語, 한국어, Melayu, Norsk bokmål, Polski, Português (PT/BR), Română, Русский, Español (ES/LA), Svenska, ไทย, Türkçe, 中文 (简体/繁體/香港)。

## 🙏 特别鸣谢

* [Xposed Framework](https://github.com/rovo89/XposedBridge) - 本模块的基石。
* [LSPosed](https://github.com/LSPosed/LSPosed) - 现代化的 Android Xposed 实现。
* [pico4-sleep-mode](https://github.com/hhhbwc/pico4-sleep-mode) - 快捷设置注入逻辑的灵感来源。
