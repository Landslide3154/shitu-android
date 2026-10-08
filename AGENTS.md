# 拾图 shitu — 开发约定

> 跨项目通用规则见全局 `C:\Users\godis\.dsh\AGENTS.md`，本文件不重复，只写这个项目独有的东西。

## 1. 这个项目是什么

安卓 App：借 **Shizuku** 把 `Android/data/<包名>/...` 里藏着的图片**自动、持续地**搬到用户自己的目录。
Kotlin + Jetpack Compose + Room/DataStore + Shizuku（自定义 UserService，AIDL 在 `app/src/main/aidl`）。
仓库 `Landslide3154/shitu-android`；本地 `D:\code\shitu`；包名 `com.landslide.shitu`；许可 GPL-3.0。

关键位置：`app/build.gradle.kts`（版本与 SDK 的唯一来源）、`engine/`（搬运与规则引擎）、`service/`（前台服务）、`shizuku/`（UserService 与授权）、`settings.gradle.kts`（依赖镜像）。

## 2. 常用命令（可直接复制执行）

| 目的 | 命令 |
| --- | --- |
| 构建 Release | `.\gradlew.bat assembleRelease`（JDK 21；Android SDK 在 `D:\Android`） |
| 单元测试 | `.\gradlew.bat test`（`app/src/test` 下 core / engine 纯逻辑测试；改引擎逻辑必跑） |
| 真机截图取证 | `D:\soft\scrcpy\adb.exe exec-out screencap -p > x.png` |
| 生成应用图标 | `python tools\make_icon.py`（含通知小图标那套 24dp） |

## 3. 硬约束（必须 / 禁止）

- 必须：每次改动提交后 `versionName` **+0.0.1**、`versionCode` **+1**，**只改 `app/build.gradle.kts` 一处**；同时在 `docs/CHANGELOG.md` 顶部加一节，**面向使用者**写（说人话，不写函数名/文件名）。
- 必须：commit message 用 Conventional Commits（中英混合均可）。
- 必须：改完做真机验证（用户明确要求），记录追加到 `docs/2026-10-04-shitu-验证记录.md`。
- 禁止：再应用 `org.jetbrains.kotlin.android` 插件——**AGP 9 起 Kotlin 已内置**。
- 禁止：顺手抬高 `targetSdk`（保持 36）。规格也写 36，但 AndroidX（compose-ui 1.12 / core-ktx 1.19）硬性要求编译期 API 37，故 `compileSdk = 37`；compileSdk 只影响编译期可见 API，运行行为由 targetSdk 决定。`minSdk = 30`。
- 禁止：改依赖镜像只顾一边——`settings.gradle.kts` 里本机优先阿里云（本机 `dl.google.com` 不通），CI 上直连 google/mavenCentral 更稳。

## 4. 架构边界与因果（从代码看不出为什么）

- **UserService 跑在 uid=2000**：所以不能用 `Environment`/`StatFs` 这些受权限/进程模型影响的 API，只能用纯 `java.io`。
- **Shizuku 授权只认服务端名单**：`adb pm grant` 对它无效，别指望用命令绕过授权。

## 5. 版本与发布

版本号唯一来源是 `app/build.gradle.kts`。发版**走本机，不要用 GitHub Actions**（用户 2026-10-04 明确）：

1. `.\gradlew.bat assembleRelease` → 产物 `app/build/outputs/apk/release/app-release.apk`
2. 真机装一遍、点一遍主流程
3. `git tag vX.Y.Z` 并推送 tag
4. 用 `~/.git-credentials` 里的 token 走 REST API 建 Release；**附件必须直接 POST** `https://uploads.github.com/repos/<owner>/<repo>/releases/<id>/assets?name=<文件名>`（拿 `upload_url` 做正则替换会拼出非法 URI，实测踩过）
5. 附件用**本机构建**的 `app-release.apk`

CI（`.github/workflows/release.yml`）**只保留 `workflow_dispatch`**，去掉 push tag 自动触发，仅应急用：4 个 Secrets `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`；需 `chmod +x gradlew`（Windows 提交的仓库 exec 位会丢）；不能再用 `android-actions/setup-android`（其内部 sdkmanager 会报错），改为自己调 `cmdline-tools/latest/bin/sdkmanager`。

会漂移的当前值（当前版本号、真机型号/系统与 Shizuku 版本、截图分辨率、进度）→ 记忆空间「拾图 shitu」，本文件不写。

## 6. 已知坑

- **真机 uiautomator dump 不可用** → 只能 `screencap` 截图目测坐标。
- `adb shell input tap` **打不到 Compose DropdownMenu 的弹出项**（弹出窗口不可聚焦，点击落到 Activity 窗口）→ 用 `input keyevent KEYCODE_DPAD_DOWN` ×N + `KEYCODE_ENTER` 选中；AlertDialog 是独立可聚焦窗口，`input tap` 正常。
- **MIUI 有系统图标缓存** → 改图标后要重启手机才刷新；重启后 Shizuku 需重新启动（用电脑跑一次启动脚本）。
- **HyperOS 默认拦开机自启** → 需在应用设置里手动开「自启动」。
- PowerShell 给 git 传含全角括号/加号的中文 `-m` 会被拆 → 改用 `-F` 消息文件（utf8NoBOM）。

## 7. 指针

- 全局规则：`C:\Users\godis\.dsh\AGENTS.md`（git 推送见 §2、shell 与编码见 §3、MCP 见 §5、图标见 §7）——本节不复述。
- 记忆空间：拾图 shitu（真机与进度快照）。
- 设计依据：`docs/superpowers/specs/`、`docs/superpowers/plans/`——**改行为前先读对应规格，改完同步更新**；`docs/CHANGELOG.md` 记版本；`README.md` 面向普通手机用户（不是开发者）。
- CI 密钥准备：`.ci-setup/set_secrets.py`。
- 界面口径：说人话；能少点一步就少点一步；控件放右上角，方便右手单手点。
