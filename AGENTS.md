# 拾图 shitu — 开发约定

> 跨项目通用规则（git、shell 与编码、图标、归位判据）见全局 `~/.dsh/AGENTS.md`，本文件不重复。
> 本机路径、真机型号、环境版本、版本快照、本机踩坑在 `AGENTS.local.md`（不入库）。

## 1. 这个项目是什么

安卓 App：把 `Android/data/<包名>/...` 里藏着的图片，以及普通目录之间的图片，**自动、持续地**搬到用户自己的目录。
有 Shizuku 时借它的 shell 身份进 `Android/data`；没有时用系统「所有文件访问」搬普通目录。
Kotlin + Jetpack Compose + Room/DataStore + Shizuku（自定义 UserService，AIDL 在 `app/src/main/aidl`）。
仓库 `Landslide3154/shitu-android`；包名 `com.landslide.shitu`。

## 2. 常用命令

| 目的 | 命令 |
| --- | --- |
| 构建 Release | `.\gradlew.bat assembleRelease` |
| 单元测试 | `.\gradlew.bat test`（`app/src/test` 下 core / engine / shizuku / ui 的 JVM 纯逻辑测试；改引擎逻辑必跑） |
| 真机截图取证 | `adb exec-out screencap -p > x.png` |
| 生成应用图标 | `python tools\make_icon.py`（含通知小图标那套 24dp） |

## 3. 硬约束（必须 / 禁止）

- 必须：每次改动提交后 `versionName` **+0.0.1**、`versionCode` **+1**，**只改 `app/build.gradle.kts` 一处**；同时在 `docs/CHANGELOG.md` 顶部加一节，**面向使用者**写（说人话，不写函数名/文件名）。
- 必须：改完做真机验证，记录追加到 `docs/2026-10-04-shitu-验证记录.md`。
- 禁止：再应用 `org.jetbrains.kotlin.android` 插件——**AGP 9 起 Kotlin 已内置**，应用它会直接报错。
- 禁止：顺手抬高 `targetSdk`（保持 36），或把 `compileSdk` 改回 36——规格写的是 36，但 AndroidX 硬性要求编译期 API 37；compileSdk 只影响编译期可见 API，运行行为由 targetSdk 决定。`minSdk = 30` 同理不动。
- 禁止：改依赖镜像只顾一边——`settings.gradle.kts` 按 `CI` 环境变量分叉：本机优先国内镜像，CI 上直连 google/mavenCentral 更稳。
- 禁止：把 keystore 或口令写进仓库——release 签名读仓库外的 properties 文件。

## 4. 架构边界与因果（从代码看不出为什么）

- **UserService 跑在 shell 身份（uid=2000）**：不能用 `Environment`/`StatFs` 这类要问系统「当前用户 / 调用包名」的 API，只能用纯 `java.io`。
- **Shizuku 授权只认服务端名单**：`adb pm grant` 对它无效，别指望用命令绕过授权，也别写「引导用户去授权页」以外的兜底逻辑。
- **两条通道不是等价替代**：有 Shizuku 走 `ShizukuBridge`（shell 身份，能进 `Android/data`）；没有才用 `local/` 的「所有文件访问」通道。
  `MANAGE_EXTERNAL_STORAGE` 同样进不了 `Android/data`/`Android/obb`（系统限制），所以 `Android/data` 相关能力必须保留 Shizuku，别把本地通道写成它的替代。
- **联网只有「检查更新」一处**：`core/UpdateChecker` 请求 GitHub Releases，其余功能一律离线；新增任何联网行为前先改 README/手册的隐私口径。
- **UI 样式只有一个来源**：圆角一律引用 `app/src/main/java/com/landslide/shitu/ui/theme/Shapes.kt` 的 `ShituRadius`（已接进 `ShituShapes` / MaterialTheme），输入框一律用 `app/src/main/java/com/landslide/shitu/ui/components/ShituField.kt`；不要裸写圆角数字、也不要直接用 `OutlinedTextField`。

## 5. 版本与发布

版本号与 SDK 的唯一来源是 `app/build.gradle.kts`。发版**走本机，不要用 GitHub Actions**；发版步骤（构建 → 真机装一遍 → **停下等用户发话** → push/tag/Release）、README 与截图规矩、CI 应急流程（4 个 Secrets、缺 platform 37）逐字外移到 [docs/release-process.md](docs/release-process.md)。

## 6. 已知坑

- **`adb shell input tap` 打不到 Compose DropdownMenu 的弹出项**：弹出窗口不按可聚焦窗口处理，点击落到 Activity 窗口，菜单既不关也不响应 → 自动化验证改用 `input keyevent KEYCODE_DPAD_DOWN` ×N + `KEYCODE_ENTER` 选中；AlertDialog 是独立可聚焦窗口，`input tap` 正常。
- **系统图标缓存**：覆盖安装后桌面/应用信息可能仍显示旧图 → 处理步骤、命令与「要不要重启手机」见 `AGENTS.local.md` §4（本机实测）。

## 7. 指针

- 全局规则：`~/.dsh/AGENTS.md`。
- 改行为前先读规格、改完同步更新：`docs/superpowers/specs/`、`docs/superpowers/plans/`。
- 版本演进地图：`docs/版本历史.md`；逐版说明：`docs/CHANGELOG.md`；`README.md` 面向普通手机用户。
- CI 密钥准备：`.ci-setup/set_secrets.py`。
