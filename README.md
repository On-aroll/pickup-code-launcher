# 快递取件

一个用于快速打开常用快递取件码和电商待取列表的 Android 工具，附带网页版入口。

作者：EyanLiu。本仓库从 [EidenLiu/pickup-code-launcher](https://github.com/EidenLiu/pickup-code-launcher) 迁移并继续维护。

## 下载

请前往 GitHub Releases 下载最新版 APK：

https://github.com/On-aroll/pickup-code-launcher/releases/latest

首次安装时，Android 可能会提示允许从当前浏览器安装应用。

## 已实现

- 快速打开菜鸟、淘宝和拼多多取件码
- 一键跳转到淘宝订单列表、拼多多订单页
- 查看京东、小红书待取快递
- 查看抖音、快手、哔哩哔哩订单列表
- 对应 App 无法打开时自动尝试网页入口
- 支持应用长按快捷操作
- 支持将任意入口固定为独立桌面图标
- 支持五按钮 Android 桌面小组件
- 不读取短信、通知、相册或账号数据
- 不需要网络权限和后端服务

## 更新记录

### v2.5.9（2026-09-14）

- 抖音入口改为在 App 内打开官方商城网页版（带登录态），内含「我的订单」入口

### v2.5.8（2026-09-14）

- 快手入口改为优先打开「快手小店」/「我的钱包」，不再停留在刷视频首页
- 快手极速版优先唤起（nebula 优先于主 App）

### v2.5.7（2026-09-14）

- 修复抖音入口唤起顺序：优先唤起抖音商城 App（主抖音的搜索路由不再抢占）
- 网页版与 iOS 文档同步调整抖音入口

### v2.5.6（2026-09-14）

- 新增哔哩哔哩订单入口，直达会员购「我的」页（含待收货）
- 网页版补齐哔哩哔哩入口，修正抖音网页兜底链接

### v2.1.0（2026-09-13）

- 新增淘宝、拼多多「待取件」直达入口，一键打开待取列表页
- 网页版同步补齐待取件入口，修正淘宝取件入口的页面指向
- iOS 快捷指令文档补充待取件配置
- 接入 GitHub Actions，推送版本标签后自动构建并发布 APK
- 移除菜鸟入口，精简为淘宝、拼多多、京东、小红书
- 拼多多入口改为直达「我的订单」页，取件与收货状态一目了然
- 恢复菜鸟身份码与包裹入口
- 长按桌面图标快捷入口支持用户自定义
- App 首页与长按菜单入口顺序可调整，长按菜单支持勾选显示项
- 拼多多入口优先定位待收货列表（含待取件入口），并保留订单列表与个人中心兜底
- 淘宝待取入口改为「我的订单」列表页，并优先定位「待收货」分页
- 入口顺序支持长按拖动调整，箭头按钮保留作微调
- 新增抖音、快手订单入口，同步 App、网页、iOS 快捷指令

### v2.5.0（2026-09-14）

## Android 构建

需要 JDK 17 和 Android SDK 35：

```powershell
.\gradlew.bat assembleDebug
```

调试 APK 输出到：

```text
app/build/outputs/apk/debug/app-debug.apk
```

推送 `v` 开头的 git 标签（如 `v2.1.0`）会自动触发 GitHub Actions 构建并发布 Release。

## Android 发布版签名

发布前请创建自己的签名证书，并在后续版本中一直使用同一个证书：

```powershell
keytool -genkeypair -v -keystore release-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias kuaidi_qujian
```

复制 `keystore.properties.example` 为 `keystore.properties`，填入签名信息，然后执行：

```powershell
.\gradlew.bat assembleRelease
```

签名文件和密码不能上传到公开仓库。以后更新应用必须继续使用同一个签名文件。

如果使用 GitHub Actions 发布，可以把 keystore 以 base64 编码后存为仓库 Secret，并在仓库中配置 `KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEYSTORE_ALIAS`，流水线会自动进行签名。未配置这些 Secret 时，Actions 会构建调试签名的 APK，同样可以安装使用。

## 链接维护

应用使用第三方 App 的内部页面入口，这些入口并非稳定的开放 API。发布前应在安装了最新版对应 App 的真机上逐个测试。

## 常见问题

**点击入口后打开的是 App 首页，而不是取件页。**

第三方 App 更新后可能调整内部页面地址。请先把对应 App 升级到最新版再试；仍不行时，使用应用内置的网页入口，或到 Issues 反馈。

**待取件列表是空的。**

待取件列表需要在对应 App 内登录同一账号。列表内容来自平台数据，本应用只负责跳转。

## 开源许可

本项目使用 MIT License。

当前版本：2.5.9

最后核对日期：2026-09-14。
