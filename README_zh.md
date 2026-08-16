<div align="center">
   <img src="/app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.webp" width="256" height="256"/>

   # PicoNeverSleep
   [English](README.md) | [中文](README_zh.md) | [Русский](README_ru.md)
   
   ### 为您的 Pico VR 头戴设备添加“永不休眠”快速设置按钮。
   PicoNeverSleep 是一款专为 Pico VR 头显设计的 LSPosed 模块，可在“快速设置”面板中添加专用的“永不休眠”切换开关。只需点击一下，即可让屏幕保持常亮，非常适合测试、观看长视频或在无人看管的情况下进行下载。
</div>

## 👓 截图
<image src="Resource/Screenshot1.jpeg" width="400"/>

## 🌟 主要功能
* 🌙 **永不休眠切换：** 直接从“快速设置”中轻松防止头显进入休眠状态。
* 🔄 **重启后保持：** 通过挂钩系统启动阶段，在设备重启后自动恢复您的“永不休眠”状态。
* 🌐 **多语言支持：** 完全本地化，支持 27 种以上的语言，包括英语、中文、日语、韩语和多种欧洲语言。
* 🧹 **简洁专注：** 单一用途模块，无多余功能，不产生额外后台耗电。

## ⛏️ 前置条件
* **设备：** Pico 4 头显（支持中国版/国际版固件）。
* **权限：** 需要 **[Root 权限](https://pico4.wiki/guides/root/01-root/)** 才能将更改应用于系统文件。
* **环境：** 必须安装并激活 **[LSPosed 框架](https://github.com/JingMatrix/Vector/releases/tag/v2.0)**。
* **作用域：** 在 LSPosed 管理器中必须勾选 **系统框架 (android)** 和 **PicoVR 设置 (com.picovr.settings)**。

## 📐 如何使用？
1. 在您的头显上 **安装** `PicoNeverSleep.apk`。
2. **打开** LSPosed 管理器应用。
3. **启用** PicoNeverSleep 模块。
4. **检查作用域：** 确保已勾选 `系统框架` 和 `PicoVR 设置`。
5. **重启** 您的设备以激活挂钩。
6. **使用方法：**
    * 打开“快速设置”面板（点击停靠栏中的时钟/电池区域）。
    * 您将在列表开头看到一个新的 **永不休眠** 按钮。
    * 点击切换：图标将高亮显示，文字将更新以显示已激活。

## ⁉️ 为什么按钮没有出现？
* 确保您已在 **LSPosed 管理器** 中启用了该模块。
* 仔细检查是否已选择 **com.picovr.settings** 作用域。
* 首次启用模块后，您 **必须重启** 头显（或至少重启“设置”应用）。

## ⁉️ 它是如何工作的？
该模块挂钩到 `com.picovr.settings` 以将自定义图块注入快速设置适配器。
* 它通过切换系统属性 `pvr.factorytest.never.sleep` 来控制休眠行为。
* 由于 Pico OS 在每次启动时都会将此属性重置为 `0`，因此该模块还会挂钩 **系统服务器**（`android` 包）的启动阶段，以便在系统启动后立即从持久的 `Settings.Global` 变量中恢复您保存的状态。

## 🔃 语言支持
本应用支持 27 种语言，包括：
Čeština, Dansk, Nederlands, English (UK/US), Suomi, Français, Deutsch, Ελληνικά, Italiano, 日本語, 한국어, Melayu, Norsk bokmål, Polski, Português (PT/BR), Română, Русский, Español (ES/LA), Svenska, ไทย, Türkçe, 中文 (简体/繁體/香港)。

## 🙏 特别鸣谢：
* [Xposed 框架](https://github.com/rovo89/XposedBridge) - 此模块的基础。
* [LSPosed](https://github.com/LSPosed/LSPosed) - 适用于 Android 的现代 Xposed 实现。
* [pico4-sleep-mode](https://github.com/hhhbwc/pico4-sleep-mode) - 快速设置注入逻辑的原始灵感来源。
