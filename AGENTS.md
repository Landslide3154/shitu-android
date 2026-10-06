# AGENTS.md — 拾图 shitu

安卓 App：借 **Shizuku** 把 `Android/data/<包名>/...` 里藏着的图片**自动、持续地**搬到用户自己的目录。
仓库 `Landslide3154/shitu-android`；本地目录 `D:\code\shitu`；包名 `com.landslide.shitu`；许可 GPL-3.0。
技术栈：Kotlin + Jetpack Compose + Room/DataStore + Shizuku（自定义 UserService，aidl 在 `app/src/main/aidl`）。

## 版本号约定（必须遵守）

- 每次改动提交后：`versionName` **+0.0.1**、`versionCode` **+1**（当前 `0.12.2` / `21`），只改 `app/build.gradle.kts` 一处
- 同步在 `docs/CHANGELOG.md` 顶部加一节，**面向使用者**写（说人话，不写函数名/文件名）
- commit message 用 Conventional Commits（中英混合均可）

## 构建与测试

- 构建：`.\gradlew.bat assembleRelease`（JDK 21）；本机 Android SDK 在 `D:\Android`
- 依赖镜像写在 `settings.gradle.kts`：本机 `dl.google.com` 不通，所以优先阿里云；CI 上直连 google/mavenCentral 更稳——改镜像时两边都要考虑
- **AGP 9 起 Kotlin 内置**，不能再应用 `org.jetbrains.kotlin.android`
- `compileSdk = 37`、`targetSdk = 36`、`minSdk = 30`：规格写 36，但 AndroidX（compose-ui 1.12 / core-ktx 1.19）硬性要求编译期 API 37。compileSdk 只影响编译期可见 API，运行行为由 targetSdk 决定，别顺手把 targetSdk 也抬上去
- 单测：`.\gradlew.bat test`（`app/src/test` 下的 core / engine 纯逻辑测试，改引擎逻辑必跑）

## 真机验证（改完必须做，用户明确要求）

- 目标机 Redmi K90 Pro Max（HyperOS 3 / Android 16 / Shizuku 13.6.0）
- 本机 adb：`D:\soft\scrcpy\adb.exe`
- 该机 **uiautomator dump 不可用**，只能 `adb exec-out screencap -p > x.png` 目测坐标（截图 876x1904 → 原始 1200x2608）
- `adb shell input tap` **打不到 Compose DropdownMenu 的弹出项**（弹出窗口不可聚焦，点击落到 Activity 窗口）→ 用 `input keyevent KEYCODE_DPAD_DOWN` ×N + `KEYCODE_ENTER` 选中；AlertDialog 是独立可聚焦窗口，`input tap` 正常
- 验证记录写在 `docs/2026-10-04-shitu-验证记录.md`

## 发版（用户 2026-10-04 明确：不要走 GitHub Actions，就用本机）

1. `.\gradlew.bat assembleRelease` → 产物 `app/build/outputs/apk/release/app-release.apk`
2. 真机装一遍、点一遍主流程
3. `git tag vX.Y.Z` 并推送 tag
4. 用 `~/.git-credentials` 里的 token 走 REST API 建 Release；**附件必须直接 POST**
   `https://uploads.github.com/repos/<owner>/<repo>/releases/<id>/assets?name=<文件名>`
   （拿 `upload_url` 做正则替换会拼出非法 URI，实测踩过）
5. 附件用**本机构建**的 `app-release.apk`

CI（`.github/workflows/release.yml`）**只保留 `workflow_dispatch`**，去掉 push tag 自动触发，仅应急用：
4 个 Secrets `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`；需 `chmod +x gradlew`（Windows 提交的仓库 exec 位会丢）；不能再用 `android-actions/setup-android`（其内部 sdkmanager 会报错），改为自己调 `cmdline-tools/latest/bin/sdkmanager`。

## 文档约定

- `docs/superpowers/specs/` 与 `docs/superpowers/plans/` 是设计依据——**改行为前先读对应规格**，改完同步更新
- `docs/CHANGELOG.md` 记版本；`README.md` 面向使用者（普通手机用户，不是开发者）
- `tools/make_icon.py` 生成应用图标（含通知小图标那套 24dp）；`.ci-setup/set_secrets.py` 是 CI 密钥准备脚本

## 已知坑（都已在代码里绕开，别改回去）

- **Shizuku 授权只认服务端名单**，`adb pm grant` 无效
- **UserService 跑在 uid=2000**：不能用 `Environment`/`StatFs`，只能用纯 `java.io`
- **HyperOS 默认拦开机自启**：需在应用设置里手动开「自启动」
- **通知/桌面图标在 MIUI 有系统缓存**：改图标后要重启手机才刷新；重启后 Shizuku 需要重新启动（用电脑跑一次启动脚本）
- 排查 PowerShell 给 git 传含全角括号/加号的中文 `-m` 会被拆——改用 `-F` 消息文件（utf8NoBOM）

## 协作口径

- 界面文字面向普通使用者，说人话；能少点一步就少点一步；控件放右上角，方便右手单手点
- 全仓推送到 GitHub：改完立即 `git push`，遵循全局 `~/.dsh/AGENTS.md` 的推送原则
- 用户的项目状态快照与更多实测踩坑见 DSH 记忆空间「拾图 shitu」，本文件只放规则
