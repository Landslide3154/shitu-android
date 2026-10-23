# 拾图 shitu · 发版流程

> 本文件是 `AGENTS.md` §5 的展开版，内容于 2026-10-24 从 `AGENTS.md` **逐字外移**，目的是让每轮注入的 `AGENTS.md` 回到预算内。
> 规则本身不变：**版本号与 SDK 的唯一来源是 `app/build.gradle.kts`；发版走本机，不要用 GitHub Actions。**

## 1. 发版步骤

版本号与 SDK 的唯一来源是 `app/build.gradle.kts`。发版**走本机，不要用 GitHub Actions**：

1. `.\gradlew.bat assembleRelease` → 产物 `app/build/outputs/apk/release/app-release.apk`
2. 真机装一遍、点一遍主流程
3. **到这里停下**：汇报「已提交、已装机、未推送」，等用户发话（`git push` / tag / Release 的口径见全局 `AGENTS.md` 的「Git 与 GitHub」节）。
4. 用户说推送后：`git push origin main` → `git tag vX.Y.Z` 并推 tag → 建 Release；附件名带版本号（`shitu-X.Y.Z.apk`）、用**本机构建**的 `app-release.apk`；上传资产的 URL 规矩见技能 `github-rest-cli`。

## 2. README 与截图

- **README 与截图**：中英两版顶部都保留「中文 / English」切换条、默认中文；UI 或图标改动后重拍 `docs/screenshots/`，并核对两版 README 的图片引用。

## 3. CI（应急用）

CI（`.github/workflows/release.yml`）**只保留 `workflow_dispatch`**，仅应急用：要 4 个 Secrets `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`；`chmod +x gradlew`（Windows 提交会丢 exec 位）；runner 自带 SDK 里没有编译期要的 platform 37，得自己调 `cmdline-tools/*/bin/sdkmanager` 装（`android-actions/setup-android` 内部的 sdkmanager 会报错，不要用）。
