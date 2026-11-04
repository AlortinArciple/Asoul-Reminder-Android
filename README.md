> Bilibili屿汐烛华 vibecoding产物，日程检测基于asoul.love网站礼貌化爬取，私人使用，莫要给网站加压力，公开仓库用于自动更新。

# Asoul Reminder（Android）

ASOUL 日程、B 站动态与直播间状态提醒的 Android 客户端：Kotlin + Jetpack Compose + WorkManager，带通知栏常驻的「后台监控」前台服务。

- 平台：Android 8.0（API 26）及以上
- 技术栈：Kotlin、Jetpack Compose、WorkManager、AlarmManager 精确闹钟、Coroutines
- 相关仓库：[Asoul Reminder Windows 桌面端](https://github.com/AlortinArciple/Asoul-Reminder-Desktop-Windows)（业务规则相同，代码独立）

## 下载

公开版 APK（不含任何 `config.json` 或 Cookie）在 [Releases](https://github.com/AlortinArciple/Asoul-Reminder-Android/releases) 页面：

```text
AsoulReminder-Android-debug.apk
```

使用 debug 签名，安装时需要在系统里允许「安装未知来源应用」；每个 Release 的说明里都附了对应版本的 SHA256。

## 安装

1. 下载 APK 并安装。
2. 打开应用，允许通知（Android 13+ 必需）。
3. 允许「闹钟和提醒」，否则开播窗口只能用非精确调度。
4. 建议在系统电池设置里把本应用设为「不受限制」，避免厂商省电策略杀掉后台。

## 第一次使用

两种方式任选其一：

**应用内登录（推荐）**

设置 → 配置与数据 → 「B 站账号 / 扫码登录」，在内置 WebView 里用哔哩哔哩 App 扫码或账号密码登录（可能触发滑块或短信验证，按页面提示完成）。程序只从请求头里取 `SESSDATA`、`bili_jct`、`DedeUserID` 等凭证，弹窗确认后才用 Android Keystore 加密写入本机。

**导入桌面配置**

设置 → 配置与数据 → 导入配置，选择桌面版导出的 `config.json`。导入后的 Cookie 同样加密保存。

登录后回到各页面手动刷新一次——首次动态只建立基线，不会提醒历史内容。

## 功能

- 动态、日程、直播间、设置四个页面。
- 直接读取 ASOUL 日程；`calendar/latest` 返回 403 时改读首页。
- 读取启用成员的最新 B 站动态与直播间状态，动态里的 B 站表情支持 GIF 动图。
- 新动态与开播提醒进入通知中心；首次抓取成员动态只建立基线，之后的新动态才提醒。
- 日程可添加到系统日历、系统闹钟或应用内精确提醒；系统闹钟默认开播前 10 分钟，系统日历默认开播前 1 小时（闹钟公开 API 不支持指定日期，日期需在时钟 App 里确认）。
- 日程新增/变动只提醒还没结束的日程（开始后 30 分钟宽限），并用持久化记录去重——站点每周重建日历页、把几周前的日程重新放回列表时，不会再把历史日程当成新增弹一遍。
- 开播窗口（开始前 10 分钟到开始后 2 分钟）每 30 秒检查一次；检测到开播后每 20 分钟查下播；下播后每 3 分钟刷动态，最长 1 小时。
- 直播期间只提醒一次开播：以 B 站 `live_time` 作为场次标识去重，状态抖动与并发刷新都不会重复通知。
- `live_status=2`（轮播）按未开播处理。
- 通知点击优先用哔哩哔哩客户端打开（未安装时回退浏览器）；设置页可显式指定「哔哩哔哩打开方式」，解决双开每次弹选择器的问题。
- 通知栏常驻「后台监控」前台服务（设置中可关）：应用不在前台时也会刷新日程/动态/直播状态，并滚动检测开播窗口；系统杀进程后由开机广播与开播闹钟尽力重建。
- 每 30 分钟用 `x/web-interface/nav` 校验 Cookie，只有 `code=-101` 判定失效；失效后应用自动打开登录页（每次启动只自动弹一次）。
- 应用内检查更新：启动时静默查询 GitHub Releases（24 小时内不重复），设置页可手动检查；发现新版本直接下载 APK 并拉起系统安装器，不跳转网页。优先读 `releases.atom`，读不到才退回 `api.github.com`（该接口按出口 IP 限流，共享代理下常被他人耗尽）。

## 通知

通知按「悬浮 / 普通」两类渠道分发：

- 开播提醒与动态提醒默认悬浮横幅，日程提醒默认不悬浮。
- 设置页「悬浮通知」小节可分别开关。渠道 `importance` 创建后不可修改，因此每类准备了 `high`/`default` 两套渠道，切换时重建通知。

## 后台与调度

- 日程每 30 分钟、动态与直播状态最低每 15 分钟在后台刷新。
- 日程刷新在基础间隔上叠加 ±10% 随机抖动（由上次刷新时间稳定推导，同一时间点反复计算结果一致），连续失败时按 ×2/×3/×4 退避封顶后继续；请求带 `Accept-Encoding: gzip` 并解压响应，日程页体积从约 165 KB 降到约 21 KB。
- 日程开始前 10 分钟启动可见的前台直播监控服务，每 30 秒检查一次，持续到开始后 2 分钟。
- 下播后的「正在查找直播后的新动态」常驻通知，会在找到动态或超时后自动恢复成「后台监控运行中」。
- 建议在系统设置中允许本应用自启动/后台运行，不要使用「强行停止」——强行停止会取消所有闹钟与后台任务，直到手动再次打开应用。

## 构建

需要 JDK 17、Android SDK 36、Android Build Tools 36。仓库根目录直接构建：

```powershell
.\gradlew.bat --no-daemon assemblePublicDebug      # 生成公开版 APK
.\gradlew.bat --no-daemon testPublicDebugUnitTest  # 单元测试
.\gradlew.bat --no-daemon lintPublicDebug          # Lint
```

输出：

```text
app/build/outputs/apk/public/debug/app-public-debug.apk
```

首次构建需要联网下载 Gradle 与依赖。Linux/macOS 上如果 `gradlew` 丢了执行位，先 `chmod +x gradlew`（`.gitattributes` 已固定 `gradlew` 使用 LF）。

`app/build.gradle.kts` 里还有一个 `private` 变体（`versionNameSuffix = "-private"`），用于把 `app/src/private/assets/default_config.json` 打进 APK 自动导入配置。本仓库不包含该目录与私有配置；即使构建该变体也不会自带任何 Cookie。私人包内的 `default_config.json` 可被解包读取，**不要公开分享私有 APK**。

## 项目结构

```text
app/src/main/java/com/alortinarciple/asoulreminder/
  MainActivity.kt              Activity 入口与导航
  BilibiliLoginActivity.kt     内置 WebView 登录
  AsoulApplication.kt          应用容器、通知渠道、后台任务初始化
  ui/                          Compose 页面与交互
  ui/MainViewModel.kt          UI 状态
  data/ReminderRepository.kt   业务数据流
  data/Models.kt               模型、缓存结构
  data/AppPreferences.kt       SharedPreferences 配置
  data/SecretStore.kt          Android Keystore 加密存 Cookie
  data/BilibiliLogin.kt        Cookie 归一化、合并、nav 状态解析
  data/AsoulParser.kt          ASOUL 日程解析
  data/BilibiliParser.kt       B 站动态与直播解析
  data/WbiSign.kt              wbi 签名
  data/NetworkClient.kt        HttpURLConnection 封装
  data/ScheduleNotificationRules.kt  日程提醒窗口与去重
  data/LiveNotificationRules.kt      开播通知去重
  background/MonitorSchedule.kt      开播窗口与调度时间计算
  background/                  WorkManager、广播接收器、精确闹钟、前台服务
  notifications/               通知渠道、跳转目标、通知构建
app/src/test/                  JUnit 单元测试
```

## 测试

```powershell
.\gradlew.bat --no-daemon testPublicDebugUnitTest
```

单元测试不访问网络、不读取真实 Cookie，覆盖日程/动态解析、wbi 签名、Cookie 合并与失效判定、开播去重、日程提醒窗口、通知渠道与跳转目标、版本号比较与 Release 解析等规则。

## 隐私

- Cookie 只保存在 Android Keystore（AES/GCM）中，不写入 APK、不写日志。
- 公开版 APK 内不含任何配置文件；导入或登录的凭证只留在本机。
- 应用只访问 ASOUL 日程页、B 站公开接口，以及公开的 GitHub Releases 接口（检查更新，不带任何凭据）。
- 应用内更新下载的 APK 存到缓存目录，安装后由系统清理；不读取其他应用的安装包。

## 免责声明

本项目是非官方的个人工具，与 ASOUL、哔哩哔哩无关联。数据来自 ASOUL 公开日程页与 B 站公开接口，请自行控制请求频率，遵守相关服务条款。程序按「现状」提供，使用风险自负。
