# 拾图 Shitu

把指定目录（尤其是 `Android/data/<包名>/...` 这种普通 App 进不去的目录）里的图片，**全自动、后台常驻、持续地**搬到用户指定的目标目录；搬完相册立刻可见；可暂停、可撤回、绝不误删。

- 中文名：拾图　·　仓库名：`shitu-android`　·　包名：`com.landslide.shitu`
- 仓库地址：https://github.com/Landslide3154/shitu-android
- 许可：GPL-3.0
- 当前状态：**0.1.0 代码完成，已验证**——61 个 JVM 单测 + 12 个真机用例全绿；真机上能经 Shizuku 读写别的 App 的 `Android/data`、自检 6 项全绿、界面四页中文可用。仅剩「重启手机后能否自动恢复」待验证（见验证记录 V10）

## 开发前必读

📄 **[设计规格](docs/superpowers/specs/2026-10-04-shitu-design.md)** —— 唯一开发依据，包含：

- 背景、目标与非目标、术语
- 在本机与真机上实测的 10 条证据（E1–E10）
- 总体架构、组件边界、AIDL 接口、Room 数据模型
- 全部运行参数默认值、"搬空"语义、命名管线、重复抑制
- 20 条错误处理、权限清单、测试计划、12 条验收标准
- 里程碑、风险与缓解、HyperOS 3 设置清单
- 附录 A：可复现的权限探针脚本；附录 B：实现细节备忘

🛠 **[实现计划](docs/superpowers/plans/2026-10-04-shitu-implementation.md)** —— 15 个任务、每步含代码/命令/预期结果与 commit，按 TDD 小步推进

🧪 **[验证记录](docs/2026-10-04-shitu-验证记录.md)** —— 与实现计划的偏差、已跑通的验证、待真机的项目

## 构建与运行（本机实测可用）

```powershell
# 1) 依赖与 SDK（本机已装好）
#    SDK: D:\Android（platform-tools / platforms;android-37 / build-tools;37.0.0 / cmdline-tools）
#    本机 dl.google.com 与 services.gradle.org 直连不通：
#      - Gradle 分发包来自本机缓存 ~/.gradle/wrapper/dists
#      - Maven 依赖走阿里云镜像（已在 settings.gradle.kts 里配好 google/public/gradle-plugin）
#    SDK 组件可用腾讯镜像下载：https://mirrors.cloud.tencent.com/AndroidSDK/

# 2) 构建 debug APK
.\gradlew.bat :app:assembleDebug
# 产物：app\build\outputs\apk\debug\app-debug.apk

# 3) 跑 JVM 单元测试（61 个）
.\gradlew.bat :app:testDebugUnitTest

# 4) 真机用例（Room 三表 + Shizuku 端到端），需先接手机并授权
.\gradlew.bat :app:connectedDebugAndroidTest
```

**工具链版本**：Gradle 9.5.0 · AGP 9.2.1 · Kotlin 2.3.20 · KSP 2.3.4 · JDK 25
`minSdk 30 / targetSdk 36 / compileSdk 37`（compileSdk 由 36 提升到 37：Compose 1.12 与 core-ktx 1.19 硬性要求；targetSdk 仍按规格保持 36，运行行为不变）
> AGP 9 起 Kotlin 支持内置，**不要**再应用 `org.jetbrains.kotlin.android` 插件（会直接报错）。

## 代码结构

```
app/src/main/java/com/landslide/shitu/
├── core/       NamePolicy 命名管线 · ScanFilter · RateLimiter · LoopGuard · RuleConflictChecker · AppLabel
├── data/       Room 三表(规则/条目/日志) · DataStore 全参数 · RuleRepository · 日志 CSV 导出
├── shizuku/    AIDL 7 接口 · ShituUserService(shell 身份) · ShizukuBridge(状态机/退避绑定) · FileBridge
├── engine/     TransferExecutor(单文件事务) · RuleEngine(一轮 Run) · UndoService · HealthChecker(自检)
├── service/    WatchService(specialUse 常驻) · BootReceiver · RestartReceiver · FallbackWorker · Notifier
└── ui/         规则列表/编辑 · 日志 · 设置 · Shizuku 目录选择器 · 自检弹窗
app/src/test/        JVM 单测（纯逻辑 + FakeFileBridge，61 个用例）
app/src/androidTest/ 真机用例（Room 三表、Shizuku 端到端、自检全绿）
```

## 关键事实（实测结论，2026-10-04）

- 目标机：Redmi K90 Pro Max（25102RKBEC / HyperOS 3.0.309.0.WPMCNXM.C11 / Android 16 / 安全补丁 2026-07-01），Shizuku 13.6.0.r1091（adb 模式，非 root）。
- Shizuku 服务进程与 adb shell **同一身份**（`uid=2000(shell)`、`u:r:shell:s0`、含 `ext_data_rw`），因此可读、写、改名、删除别的 App 的 `Android/data`，并可把文件移动出来。
- 移动/复制进 `/sdcard/Pictures/...` 后 **8 秒内自动出现在 MediaStore**，不需要额外扫描。
- 该能力是"系统补丁级"的（参见 Shizuku issue #1574 / #1807），**可能被系统更新破坏**，因此 App 内置自检按钮。

## 技术栈

Kotlin + Jetpack Compose + Room + DataStore + WorkManager + Shizuku API 13.x（Maven Central）
minSdk 30 / targetSdk 36 / compileSdk 36

## 环境备注

本机 Android SDK 安装在 `D:\Android`（`local.properties` 已指向它）。`D:\soft\scrcpy\adb.exe` 可用于真机调试。
本机 **dl.google.com / services.gradle.org 直连不通**，因此仓库依赖走阿里云镜像、Gradle 分发用本机缓存；安装 SDK 组件可用腾讯镜像。

## 非目标（v1）

不搬视频/音频/文档、不支持外置 SD 卡与工作资料、不做去重/云同步、不负责 Shizuku 自身开机自启、不上架应用商店、不做"预览/试运行"步骤。
