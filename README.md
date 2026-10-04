# 拾图 Shitu

把指定目录（尤其是 `Android/data/<包名>/...` 这种普通 App 进不去的目录）里的图片，**全自动、后台常驻、持续地**搬到用户指定的目标目录；搬完相册立刻可见；可暂停、可撤回、绝不误删。

- 中文名：拾图　·　仓库名：`shitu-android`　·　包名：`com.landslide.shitu`
- 许可：GPL-3.0
- 当前状态：**设计阶段**（规格已完稿，待评审 → 实现计划 → 开发）

## 开发前必读

📄 **[设计规格](docs/superpowers/specs/2026-10-04-shitu-design.md)** —— 唯一开发依据，包含：

- 背景、目标与非目标、术语
- 在本机与真机上实测的 10 条证据（E1–E10）
- 总体架构、组件边界、AIDL 接口、Room 数据模型
- 全部运行参数默认值、"搬空"语义、命名管线、重复抑制
- 20 条错误处理、权限清单、测试计划、12 条验收标准
- 里程碑、风险与缓解、HyperOS 3 设置清单
- 附录 A：可复现的权限探针脚本；附录 B：实现细节备忘

## 关键事实（实测结论，2026-10-04）

- 目标机：Redmi K90 Pro Max（25102RKBEC / HyperOS 3.0.309.0.WPMCNXM.C11 / Android 16 / 安全补丁 2026-07-01），Shizuku 13.6.0.r1091（adb 模式，非 root）。
- Shizuku 服务进程与 adb shell **同一身份**（`uid=2000(shell)`、`u:r:shell:s0`、含 `ext_data_rw`），因此可读、写、改名、删除别的 App 的 `Android/data`，并可把文件移动出来。
- 移动/复制进 `/sdcard/Pictures/...` 后 **8 秒内自动出现在 MediaStore**，不需要额外扫描。
- 该能力是"系统补丁级"的（参见 Shizuku issue #1574 / #1807），**可能被系统更新破坏**，因此 App 内置自检按钮。

## 技术栈

Kotlin + Jetpack Compose + Room + DataStore + WorkManager + Shizuku API 13.x（Maven Central）
minSdk 30 / targetSdk 36 / compileSdk 36

## 环境备注

本机 `D:\Android`（PiliPlus 的 local.properties 指向的 SDK）**当前不存在**，首次开发需重新安装 Android SDK；`D:\soft\scrcpy\adb.exe` 可用于真机调试。

## 非目标（v1）

不搬视频/音频/文档、不支持外置 SD 卡与工作资料、不做去重/云同步、不负责 Shizuku 自身开机自启、不上架应用商店、不做"预览/试运行"步骤。
