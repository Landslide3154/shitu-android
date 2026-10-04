# 拾图 Shitu · 设计规格（v0.1）

| 项 | 内容 |
|---|---|
| 状态 | **待用户评审**（评审通过后锁版，进入实现计划） |
| 日期 | 2026-10-04 |
| 目标设备 | Redmi K90 Pro Max（型号 25102RKBEC / HyperOS 3.0.309.0.WPMCNXM.C11 / Android 16 / 安全补丁 2026-07-01） |
| 运行环境 | Shizuku 13.6.0.r1091.b844bc49（adb 模式，非 root） |
| 项目路径 | `D:\code\shitu` |
| 仓库名 | `shitu-android` |
| 包名 | `com.landslide.shitu` |
| 许可 | GPL-3.0 |
| 本文档用途 | **新会话开发的唯一依据**；实现前必须先读完本文档与附录 A 的探针结果 |

---

## 0. 一句话定义

把指定目录（尤其是 `Android/data/<包名>/...` 这类普通 App 进不去的目录）里的图片，**全自动、后台常驻、持续地**搬到用户指定的目标目录；搬完相册立刻可见；可暂停、可撤回、**绝不误删**。

---

## 1. 背景与问题

1. Android 11 起的分区存储让每个 App 把图片写进自己的地盘：公共目录（`/sdcard/Pictures/<App>`、`/sdcard/DCIM/<App>`）或私有目录（`/sdcard/Android/data/<包名>/files/...`）。相册于是堆满各种来源的文件夹。
2. 普通第三方 App 无法访问别的 App 的 `Android/data`。**Shizuku 提供相当于 adb 的 shell 身份**，可以绕过该限制（本机实测见 §3）。
3. 用户诉求（明确表达过）：
   - 设好路径后**不需要任何手动操作**，"检测到文件夹中有图片就自动移动"。
   - 能一直挂在后台。
   - 已装 Shizuku，可用。
   - 能接受常驻通知。
   - 不接受"重启后还要手动点一下"——但这受限于 Shizuku 自身的开机自启能力（见 §18、§20）。
   - 只搬"自己保存/下载的图"；不想要的图**由用户自己在系统相册里屏蔽**，不要求 App 做黑名单。

---

## 2. 目标与非目标

### 2.1 目标（v1 必须做到）

| # | 目标 |
|---|---|
| G1 | 规则化：源目录（可选含子目录）→ 目标目录；每条规则独立开关、独立间隔；移动/复制可选 |
| G2 | 全自动：常驻轮询（可配间隔）+ 打开 App 立即扫一次 + WorkManager 兜底 |
| G3 | **"搬空"语义**：源目录中所有符合白名单的图片一律搬走，**不论新旧** |
| G4 | 文件操作全部走 Shizuku；App 自身不申请 `MANAGE_EXTERNAL_STORAGE` |
| G5 | 命名安全：非法字符清洗 + 来源 App 后缀 + 冲突不覆盖 + 超长名截断 |
| G6 | 可观测：规则状态、运行日志、统计计数、CSV 导出 |
| G7 | 可逆：一键撤回符合条件的记录；任何失败**绝不删源** |
| G8 | 自检：一键验证"当前系统上能不能搬"（应对系统补丁变化） |
| G9 | 后台恢复：开机 / Shizuku 重启 / 服务被杀后自动恢复；Shizuku 未就绪时不静默失败 |

### 2.2 非目标（v1 明确不做）

- 不搬视频/音频/文档（扩展名白名单可配置，但验收只覆盖图片）。
- 不支持外置 SD 卡；不支持工作资料/多用户；不处理多存储卷。
- 不做图片去重、不做 AI 分类、不做云同步。
- 不负责让 Shizuku 自身开机自启（属 Shizuku 侧配置）。
- 不做"预览 / 试运行"步骤（用户明确要求：**直接搬**）。
- 不上架应用商店；v1 只发 GitHub Releases 侧载 APK。

---

## 3. 关键约束与实测证据

> 全部在 2026-10-04 于用户的 PC + 目标手机上实测。**测试残留已清理干净**（`/data/local/tmp` 只剩 Shizuku 自身文件；`Pictures`、`DCIM/.globalTrash`、源目录均无 `_dsh*` 残留）。

| 编号 | 证据 | 结论 |
|---|---|---|
| E1 | 设备：Redmi 25102RKBEC，Android 16（API 36），安全补丁 2026-07-01，HyperOS OS3.0 | 目标平台 |
| E2 | `adb shell` 身份 `uid=2000(shell)`、`context=u:r:shell:s0`、含 `ext_data_rw`/`ext_obb_rw` 组 | shell 有 Android/data 访问权 |
| E3 | adb shell 六项测试全过：列目录、`cat` 读取、`adb pull`、复制到 Pictures、目录内建/改名/删、**从 Android/data 移动到 Pictures** | 读取+写入+移动均可用 |
| E4 | `shizuku_server`（pid 12987）身份同样是 `uid=2000`、`u:r:shell:s0` | **Shizuku 路径与 adb shell 权限等价** |
| E5 | 用 Shizuku 官方 `rish`（从 APK 提取 `assets/rish` + `assets/rish_shizuku.dex`）重跑 E3 全部项目 | **App 将来走的那条路，实测全绿** |
| E6 | 复制/移动/改名进 `/sdcard/Pictures/...` 后 **8 秒内自动出现在 MediaStore**（`_id=9018 / 9026`），改名后同一行 `_data` 跟着变 | **搬完相册立刻可见，不需要额外扫描** |
| E7 | `/sdcard/` 根下自建非标准目录同样被 MediaStore 收录 | 目标目录可自由选择（但见 §20-4） |
| E8 | shell 删除文件后落入 `/sdcard/DCIM/.globalTrash`（系统回收站，约 30 天） | "删"不等于立刻释放空间；本方案用 rename，不产生垃圾 |
| E9 | `com.qidian.QDReader` 的 `Android/data` 下有 **328 张图**；`bookLastImage.png`、`bookFirstImage.png` **各重名 2 次**；存在 `https:readx-her-...png` 这类 **100+ 字符、含冒号**的 URL 派生名 | 必须有命名清洗与冲突策略；平铺会大量重名 |
| E10 | Shizuku issue [#1574](https://github.com/RikkaApps/Shizuku/issues/1574)、[#1807](https://github.com/RikkaApps/Shizuku/issues/1807)：Android 16 上部分机型/补丁后，Shizuku 无法读改 Android/data，且与 **Google Play 系统更新**相关 | 该能力是"补丁级"的，**可能被系统更新破坏** → 自检按钮为必需品 |

**推论（写入设计约束）**

- C-1：文件操作一律在 shell 身份下执行（Shizuku UserService），不要用 App 自身 uid 硬闯。
- C-2：不需要写媒体扫描逻辑，但保留手动"触发扫描"按钮兜底。
- C-3：必须内置自检；自检失败要能明确告诉用户"当前系统上搬不动"。
- C-4：目标目录建议在标准媒体目录内（Pictures/DCIM），允许自定义但给出提示。

---

## 4. 术语

| 术语 | 含义 |
|---|---|
| 规则 Rule | 一条「源目录 → 目标目录」的搬运配置 |
| 条目 Item | 一个被处理过的文件记录（用于去重、撤回、统计） |
| 稳定期 | 文件 mtime 距今必须 ≥ N 秒才允许搬（防止搬正在写入的文件） |
| 重复抑制 | 同一文件名在窗口内被反复搬 → 判定为"App 会重建"，自动暂停规则 |
| 搬空 | 源目录中被白名单命中的文件全部搬走，与文件新旧无关 |
| 一轮 Run | 一次调度触发的扫描+搬运过程（受时间与数量预算限制） |

---

## 5. 总体架构

```
┌──────────────────── App 进程（普通权限，可被系统随时杀）────────────────────┐
│  UI (Compose)  │  调度器：ForegroundService(specialUse) + WorkManager       │
│  规则引擎：扫描 / 过滤 / 排序 / 限速 / 断点                                   │
│  命名管线：清洗 → 后缀 → 冲突 → 截断                                          │
│  记录与撤回  │  通知  │  自检  │  日志导出                                    │
└───────────────────────────────┬───────────────────────────────────────────┘
                                │ AIDL / Binder（UserService）
┌───────────────────────────────▼───────────────────────────────────────────┐
│  Shizuku 侧进程（uid=2000 shell，u:r:shell:s0）                            │
│  ShituUserService：listFiles / stat / exists / mkdirs / move / copy / delete│
│  纯执行者：不知道"规则"是什么，只做原子文件操作                               │
└───────────────────────────────┬───────────────────────────────────────────┘
                                │
                    /sdcard/**（含 Android/data/**）
                                │ FUSE
                    系统媒体库 MediaProvider（自动收录，App 不干预）
```

**边界原则**

- App 进程 **不直接做任何文件操作**（连目标目录的读写都不做）→ 不需要"所有文件访问"权限。
- UserService **不含业务逻辑**（不懂规则、不懂命名策略、不写数据库）→ 可独立测试。
- 媒体库行为不属于本 App 的职责（E6 已证明系统会自动处理）。

---

## 6. 组件设计

### 6.1 App 进程

| 单元 | 职责 | 依赖 | 边界 |
|---|---|---|---|
| `RuleRepository` | 规则 CRUD、状态持久化（Room） | Room | 只读写自己的库 |
| `Scheduler` | 计算"哪条规则到期"，触发 Run；接收开机/解锁/打开 App 事件 | WorkManager、AlarmManager、BOOT_COMPLETED | 不直接搬文件 |
| `RuleEngine` | 一轮 Run 的编排：扫描 → 过滤 → 排序 → 限速 → 调执行器 → 记账 | UserService、DB | 唯一决定"搬哪些"的地方 |
| `NamePolicy` | 目标文件名生成（清洗/后缀/冲突/截断） | 纯函数 | 无副作用，可单测 |
| `TransferExecutor` | 逐文件执行 move/copy + 校验 + 记账 | UserService、DB | 不做过滤判断 |
| `JournalService` | 日志写入、裁剪、导出 | Room | — |
| `UndoService` | 按 Item 撤回 | UserService、DB | 只撤"本 App 搬的" |
| `HealthChecker` | 自检 6 项 | UserService | — |
| `Notifier` | 常驻通知 + 事件通知（暂停/失败/未就绪） | — | — |

### 6.2 Shizuku 桥（UserService + AIDL）

**接口（v1）**

```aidl
// IShituService.aidl
interface IShituService {
    // 分页列举，避免 Binder 1MB 事务上限；返回空列表表示结束
    List<RemoteFile> listFiles(String path, boolean recursive, int maxDepth,
                               int maxCount, String afterPath);
    RemoteFile stat(String path);          // null = 不存在
    boolean exists(String path);
    void mkdirs(String path);              // 幂等
    boolean move(String srcPath, String dstPath);  // 优先 rename；EXDEV 时 copy+fsync+delete
    boolean copy(String srcPath, String dstPath);
    boolean delete(String path);           // 仅用于撤回与自检
    String describeEnvironment();          // 自检用：uid、SELinux 上下文、SDK、可用空间
}
```

**Parcelable：`RemoteFile`**：`path`、`name`、`size`、`mtimeMillis`、`isDirectory`、`isSymlink`。

**约束**

- 分页上限：`maxCount ≤ 500` / 次（Binder 限制），递归扫描由 App 侧驱动游标循环。
- 不提供"任意 shell 命令执行"接口（安全底线）。
- 绑定失败 → 指数退避重试（1s/2s/4s/…/60s），超过 5 次进 `BIND_FAILED` 状态并通知。
- UserService 随 App 进程绑定存在；App 被杀后由调度器重建。

### 6.3 规则引擎

一轮 Run 的顺序：

1. **前置检查**：Shizuku `READY`？低电量/省电模式？（命中则跳过并记录）目标可写？
2. **取规则**：`enabled = true` 且 `now - lastRunAt >= interval`。
3. **扫描**（见 §9.2）：DFS 遍历源目录，按 §8 参数过滤。
4. **排序**：按 `mtime` 升序（先搬老的，降低"刚生成就被搬走"的概率）。
5. **限速**：单轮 ≤20 个文件、≤60 秒、单分钟累计 ≤120 个。
6. **执行**：逐个交给 `TransferExecutor`；失败不中断本轮，只记录。
7. **收尾**：更新规则统计；必要时发通知；若启用"加速清空"，立刻发起下一轮（默认关）。

### 6.4 TransferExecutor（单文件事务）

```
1. 生成目标路径（NamePolicy）
2. UserService.mkdirs(目标父目录)
3. UserService.move(src, dst)   // 复制模式用 copy
4. UserService.stat(dst) 校验：存在 && size 相同
5. 写 Item + Log
6. 失败 → attemptCount+1，记 Log，源文件保持不动
```

- 同分区（内部存储全是同一 FUSE 卷）时 `move` = `rename`，**原子、瞬时、不占双份空间**。
- 若 `rename` 返回 `EXDEV`/跨卷（本方案理论上不出现，但保留回退）：`copy → fsync → 校验 → delete 源`，且删除源前必须先校验通过。
- 绝不"先删后搬"。

### 6.5 NamePolicy（命名管线）

输入：源文件绝对路径、规则、来源 App 标签。输出：目标文件名。

1. 取 basename（不含目录）。
2. **清洗非法字符**：`/ \ : * ? " < > |`、控制字符 `0x00–0x1F`、行首尾空格与结尾的 `.` → 替换为 `_`。
3. **截断**：保留扩展名，主体截到总长度 ≤180 字节（按 UTF-8 字节算，中文 1 字 3 字节）。
4. **加来源 App 后缀**：`原名_<来源App标签>.ext`（标签取 App 显示名，同样清洗；取不到显示名时用包名最后一段）。
5. **冲突处理**（目标已存在时，依次尝试）：
   - 若已存在文件的 `size + mtime` 完全一致 → 视为同一文件：`move` 模式下直接删除源（安全，因为目标已有同样内容），`copy` 模式下跳过并记 `SKIPPED`；
   - 否则 `原名_标签_<yyyyMMdd-HHmmss>.ext`；
   - 仍冲突 → `原名_标签_<n>.ext`（n 从 1 递增）。
6. **永不覆盖**（任何情况下不覆盖目标文件）。

> 示例：`bookLastImage.png`（来源 起点读书）→ `bookLastImage_起点读书.png`；第二次出现 → `bookLastImage_起点读书_20261004-101500.png`。

### 6.6 记录与撤回

- `Item` 记录每个被处理文件：源路径、源 size/mtime、目标路径、状态。
- **撤回**（UndoService）只处理同时满足以下条件的记录：
  1. `ruleId` 属于本 App 且状态为 `MOVED`/`COPIED`；
  2. 目标文件**仍存在**且 `size` 与记录一致；
  3. **源的原始位置为空**（不会被覆盖）；
  4. 未被用户手动改动（mtime 与记录一致）。
- 任一条件不满足 → 跳过并在结果里说明原因（"目标已被你改动/源位置已被新文件占用/目标已不存在"）。
- 撤回是逐文件事务，失败不回滚已成功的部分。

### 6.7 后台与恢复

| 机制 | 规格 |
|---|---|
| 常驻服务 | `ForegroundService`，类型 **`specialUse`**（无 6 小时/24 小时上限；`dataSync` 有上限，禁用），声明 `FOREGROUND_SERVICE_SPECIAL_USE` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` |
| 常驻通知 | 常驻、不可滑动清除；含 3 个按钮：暂停全部 / 立即扫一次 / 打开 |
| 兜底 | `WorkManager` 周期任务（15 分钟，系统最小间隔）：**在 Worker 内直接调用 UserService 搬一轮**，不需要前台服务，系统杀不掉；用于服务被厂商清理后的兜底 |
| 开机 | `BOOT_COMPLETED` → 启动 `specialUse` 前台服务（Android 15 禁止开机启动的类型不含 `specialUse`，**需真机验证**，见 §20-1） |
| Shizuku 恢复 | 服务内每 30 秒探测一次 Shizuku 状态；从非 `READY` 变 `READY` 时立刻恢复调度 |
| 被杀恢复 | 服务 `onDestroy` 时用 `AlarmManager` 排一次 5 分钟后的重启（若系统拒绝，则等下一次 WorkManager 兜底或用户打开 App） |
| 用户手动 | "打开 App 立即扫一次" + 通知栏按钮 |

**Shizuku 状态机**

```
NOT_INSTALLED → (装) → NOT_RUNNING → (启动) → NO_PERMISSION → (授权) → READY
                                    ↑                                  │
                                    └──────── binder 断开 ─────────────┘
任意状态 --绑定失败5次--> BIND_FAILED（通知 + 自检入口）
```

### 6.8 通知

| 场景 | 通知 |
|---|---|
| 常驻 | "拾图正在监控 · 已搬 N 张 · 上次 HH:mm"（3 按钮） |
| 规则暂停（重复抑制/连续失败/手动） | 高优先级，说明原因与建议动作 |
| Shizuku 未就绪 | "Shizuku 未运行/未授权，点此修复" |
| 单轮失败 ≥5 个 | 汇总通知（可关闭） |
| 撤回完成 | 结果摘要（成功 N / 跳过 M 及原因） |

### 6.9 自检（HealthChecker）

一条命令跑完 6 项，出一张结果表（可复制文字）：

| # | 检查 | 失败含义 |
|---|---|---|
| 1 | Shizuku 已安装 / 正在运行 / 已授权 | 环境问题 |
| 2 | UserService 绑定成功 | 版本/权限问题 |
| 3 | 目标目录可写（建临时文件后删除） | 目标不可用 |
| 4 | 在源目录内 新建 → 改名 → 删除 一个临时文件 | 写权限（对应 #1574 类问题） |
| 5 | 在源目录内 复制一个文件 → 移动到目标 → 删除副本 | 读取+跨目录移动权限 |
| 6 | 输出环境信息：uid、SELinux 上下文、SDK、Shizuku 版本、安全补丁日期 | 便于日后对比 |

> 自检**只使用自己创建的临时文件**，绝不触碰用户的真实图片。

---

## 7. 数据模型（Room）

```kotlin
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val srcPath: String,
    val includeSubdirs: Boolean = true,
    val maxDepth: Int? = null,            // null = 不限
    val dstPath: String,
    val mode: Mode,                        // MOVE | COPY
    val intervalMinutes: Int = 5,          // 0.5–30
    val extensions: String,                // "jpg,jpeg,png,..."
    val addSourceAppSuffix: Boolean = true,
    val enabled: Boolean = true,
    val state: RuleState,                  // IDLE | RUNNING | PAUSED_MANUAL | PAUSED_ERROR | PAUSED_LOOP
    val pauseReason: String? = null,
    val lastRunAt: Long? = null,
    val lastMoved: Int = 0,
    val lastFailed: Int = 0,
    val totalMoved: Long = 0,
    val totalFailed: Long = 0,
    val consecutiveFailures: Int = 0,
    val createdAt: Long, val updatedAt: Long
)

@Entity(tableName = "items",
    indices = [Index("ruleId","srcPath", unique = true), Index("ruleId","srcSize","srcMtime")])
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    val srcPath: String,
    val srcSize: Long,
    val srcMtime: Long,
    val dstPath: String?,                  // 成功后的目标路径（撤回用）
    val status: ItemStatus,                // PENDING | MOVED | COPIED | FAILED | SKIPPED
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val processedAt: Long?
)

@Entity(tableName = "logs", indices = [Index("ts"), Index("ruleId")])
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val ruleId: Long?,
    val srcPath: String?,
    val dstPath: String?,
    val result: LogResult,                 // MOVED | COPIED | SKIPPED | FAILED | UNDONE | INFO
    val durationMs: Long = 0,
    val message: String? = null
)
```

**Settings（DataStore）**：§8 全部参数 + 通知开关 + "加速清空"开关 + 上次自检结果。

**容量控制**：`logs` 按 `ts` 裁剪（30 天或 5 万条，先到者为准）；`items` 对 `SKIPPED` 记录允许 7 天后清理（`MOVED/COPIED` 记录在撤回窗口内保留，默认保留 90 天）。

---

## 8. 运行参数（默认值，全部可改）

| 参数 | 默认 | 范围 | 说明 |
|---|---|---|---|
| 轮询间隔（每条规则） | **5 分钟** | 30 秒 – 30 分钟 | 每条规则独立 |
| 单轮扫描时间预算 | **20 秒** | 5–120 秒 | 超时中断本轮扫描，不丢进度（下次继续） |
| 单轮最多搬运 | **20 个文件** | 1–500 | 用户明确指定；**与"每分钟 ≤120 张"冲突时以本项为准**（更严格者生效） |
| 每分钟最多搬运 | 120 个文件 | 10–600 | 跨轮累计的滑动窗口 |
| 单轮最长运行 | 60 秒 | 10–300 秒 | 硬上限，到点立即停止本轮 |
| 文件稳定期 | 30 秒 | 0–300 秒 | `now - mtime ≥ 稳定期` 才搬 |
| 重复抑制窗口 | 30 分钟 | 5–120 分钟 | 同一 basename 被搬次数 |
| 重复抑制阈值 | 3 次 | 2–10 次 | 达到即 **暂停该规则** + 通知 |
| 连续失败阈值 | 5 次 | 1–20 次 | 达到即暂停该规则 |
| 低电量暂停 | 开，阈值 15% | 0–50% | 或系统处于省电模式时暂停 |
| 日志保留 | 30 天 / 50000 条 | — | 先到者为准 |
| 目标默认目录 | `/sdcard/Pictures/拾图/` | 任意 | 建议留在 Pictures/DCIM（见 C-4） |
| 扩展名白名单 | `jpg,jpeg,png,gif,webp,bmp,heic,heif,avif` | 任意 | 大小写不敏感 |
| 文件名主体上限 | 180 字节 | — | 含扩展名 |
| 来源 App 后缀 | 开 | — | `原名_<App名>.ext` |
| 加速清空 | **关** | — | 开启后一轮结束立即发起下一轮，直到该规则无待搬文件（仍受每分钟上限约束） |

> **首次搬空的耗时估算（用户情形）**：328 张 ÷ 20 张/轮 × 5 分钟 ≈ **85 分钟**。若嫌慢，开"加速清空"或临时把间隔调到 30 秒。

---

## 9. 算法与流程

### 9.1 一轮 Run 的时序

```
tick(规则到期 / 手动 / 解锁 / WorkManager)
 ├─ 前置检查（Shizuku 状态、电量、目标可写）
 ├─ 扫描：DFS（deadline = 20s）→ 候选集合
 ├─ 过滤：扩展名白名单 ∧ mtime 稳定期 ∧（复制模式）不在已处理集合 ∧ 非目录/软链
 ├─ 排序：mtime 升序
 ├─ 逐文件：限速闸门（20 张 / 60 秒 / 每分钟 120 张）
 │    ├─ NamePolicy 生成目标名
 │    ├─ mkdirs → move/copy → 校验
 │    └─ 写 Item + Log（失败则 attemptCount++，源保持不动）
 └─ 收尾：更新统计、重复抑制判定、通知、（可选）加速下一轮
```

### 9.2 扫描算法

- 迭代式 DFS（显式栈），不使用递归（避免深目录栈溢出）。
- 每访问一个目录前检查 deadline（20 秒）与"已找到候选数"上限，超限立即返回并保存 `nextCursor`（下次从该目录继续）。
- 跳过：软链（默认不跟随）、`maxDepth` 之外的层级、无权限目录（记录一次 WARN 日志，不中断）。
- 只对文件做 `stat`，不读文件内容（快；内容哈希不在 v1 范围内）。
- 目录扫描顺序固定（字典序），保证可复现与断点位置稳定。

### 9.3 稳定期判定

`now - file.mtime >= 稳定期(30s)`。目的：避免搬走"正在写入"的文件（下载中、刚生成的缩略图）。被判为"未稳定"的文件**不会**被记入 Item，下一轮重新评估。

### 9.4 限速与断点

三重闸门：`本轮已搬 ≤ 20`、`本轮耗时 ≤ 60s`、`近 60 秒内已搬 ≤ 120`。任一触发即结束本轮；未处理完的候选留给下一轮（靠 `mtime 升序` + 扫描游标保证不饿死）。

### 9.5 重复抑制

- 内存 + DB 双层计数：`basename` 在滚动 30 分钟窗口内被成功搬走的次数。
- 达到 3 次 → 该规则置 `PAUSED_LOOP`，通知说明："该目录里的文件被搬走后又被重新生成，疑似 App 会自动下载。建议：把这条规则的源目录改到更精确的位置，或改用复制模式。"
- 用户手动恢复后计数清零。

### 9.6 "搬空"语义详解

- 不区分新旧：只要在源目录里、符合白名单、过了稳定期，就搬。
- `MOVE` 模式下源文件消失 → 天然幂等；`Item` 里的 `MOVED` 记录**不用于跳过**，仅用于统计与撤回。
- 若同一路径又出现同 `size+mtime` 的文件（说明被重建/下载回来） → **照搬**，但计入重复抑制计数。

---

## 10. 界面设计

### 页面 1 · 规则列表（首页）

- 顶部状态条：Shizuku 状态徽标（绿=就绪/黄=未授权/红=未运行）+「自检」按钮 +「暂停全部」开关。
- 规则卡片：名称、源 → 目标（两行省略）、模式徽标（移动/复制）、状态（运行中/已暂停 + 原因）、上次运行时间、上次搬移数、累计数。
- 卡片右侧开关（启用/停用）；点击进编辑；长按弹出"立即运行一次 / 撤回上次 / 删除"。
- 底部 FAB：新建规则。

### 页面 2 · 规则编辑

| 字段 | 控件 |
|---|---|
| 规则名称 | 文本框 |
| 源目录 | Shizuku 目录选择器（**默认打开 `/sdcard/Android/data/`**，可输入路径） |
| 包含子目录 | 开关（默认开）+ 最大深度（可选数字） |
| 目标目录 | 目录选择器（默认 `/sdcard/Pictures/拾图/`，提示"建议放在 Pictures/DCIM"） |
| 模式 | 分段控件：移动 / 复制（默认**移动**；首次使用建议复制，见 §14 灰度） |
| 间隔 | 滑块 30 秒 – 30 分钟（默认 5 分钟） |
| 扩展名白名单 | 多选 chips |
| 来源 App 后缀 | 开关（默认开） |
| 规则冲突提示 | 保存时静态检查：源目录互相嵌套 / 目标目录被别的规则当源 / 两条规则源相同 → 弹提示（可继续保存） |

### 页面 3 · 日志

- 筛选：规则 / 结果类型 / 时间范围；搜索路径关键字。
- 列表：时间、结果徽标、源 → 目标、耗时、错误摘要。
- 导出 CSV（导出前提示"包含完整文件路径"）。

### 页面 4 · 设置

- §8 全部参数；低电量暂停；通知开关；加速清空；自检入口；关于（版本、许可、仓库地址）。

### 通知

常驻通知：标题"拾图正在监控"，内容"已搬 N 张 · 上次 HH:mm"，3 个操作按钮。

---

## 11. 错误处理与边界

| # | 场景 | 行为 |
|---|---|---|
| 1 | Shizuku 未安装/未运行/未授权 | 不发文件操作；通知引导；规则状态显示"等待 Shizuku" |
| 2 | UserService 绑定失败 | 指数退避重试 5 次 → `BIND_FAILED` + 通知 + 自检入口 |
| 3 | 源目录不存在/被删 | 该轮跳过 + WARN 日志；连续 3 轮 → 通知 |
| 4 | 源目录无权限（#1574 类） | 记录明确的权限错误；建议跑自检；不重试风暴（该轮对该目录退避 10 分钟） |
| 5 | 目标不可写 | 跳过整条规则 + 通知（不逐文件刷屏） |
| 6 | 目标空间不足 | 该文件失败；**源不删**；本轮停止；通知 |
| 7 | 文件正在被写入 | 稳定期不满足 → 本轮跳过，下轮再评估 |
| 8 | 同名冲突 | 走 §6.5 冲突链路，永不覆盖 |
| 9 | 目标已存在完全相同文件 | MOVE：删源并记 `MOVED`（附"目标已存在"标记）；COPY：跳过记 `SKIPPED` |
| 10 | 超长文件名/非法字符 | 命名管线清洗+截断 |
| 11 | 中文/emoji 文件名 | UTF-8 全程保留；仅清洗非法字符 |
| 12 | 文件在搬运途中被 App 删除 | `move` 失败 → 记 `FAILED`（原因"源已不存在"），不再重试 |
| 13 | 搬运成功但校验失败 | 记 `FAILED`；不删源；下一轮重试（attemptCount 上限 3 后跳过并在日志标注） |
| 14 | 用户手动移动/删除了已搬文件 | 撤回时跳过并说明原因 |
| 15 | 系统杀后台/内存不足 | 下轮由 WorkManager 兜底；通知不中断 |
| 16 | 手机重启 | BOOT_COMPLETED → 起服务 → 等 Shizuku 就绪 → 自动继续 |
| 17 | Shizuku 被系统更新破坏（#1807） | 自检红；通知"当前系统上搬不动"，停止重试避免耗电 |
| 18 | 数据库损坏/清数据 | 规则与日志丢失；给出"全部重建"提示；不影响源文件与目标文件 |
| 19 | 电量低/省电模式 | 暂停调度（可关），恢复后继续 |
| 20 | 同一分钟内大量候选 | 分钟闸门限速，剩余下轮 |

---

## 12. 权限清单

| 权限 | 用途 | 必需 |
|---|---|---|
| `POST_NOTIFICATIONS` | 常驻与事件通知（Android 13+ 需运行时申请） | 是 |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | 常驻服务（specialUse 类型） | 是 |
| `RECEIVE_BOOT_COMPLETED` | 开机恢复 | 是 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 引导加入电池白名单 | 是 |
| `QUERY_ALL_PACKAGES` | 把包名映射为 App 显示名（用于后缀命名） | 建议（可降级为包名末段） |
| `READ_MEDIA_IMAGES` | 仅用于界面缩略图（可选，v1 可省） | 否 |
| `MANAGE_EXTERNAL_STORAGE` | **明确不申请** | — |

---

## 13. 隐私与安全

1. **最小接口**：UserService 只暴露 7 个文件操作，不提供通用 shell 执行。
2. **不做网络**：App 无 INTERNET 权限（v1 完全离线）。
3. **日志含路径**：导出前明确提示；日志与数据库不加密（本地文件），文档如实说明。
4. **签名**：自签 APK；keystore 由开发者保管，**不入库**。
5. **开源**：GPL-3.0（含 shell 级能力的 App，开源便于审计）。
6. **自检只用临时文件**，绝不触碰真实图片。
7. **绝不覆盖、绝不先删后搬**。

---

## 14. 测试计划

| 层级 | 内容 |
|---|---|
| 单元测试 | NamePolicy（清洗/截断/后缀/冲突链路/中文/emoji/超长）；重复抑制窗口计数；限速三闸门；扫描过滤器；规则冲突检测 |
| 桥接测试 | UserService 7 个接口的正/反例（不存在的路径、无权限路径、只读目录、中文名、深目录） |
| 集成测试（真机） | 假源目录 `/sdcard/Download/_shitu_test/` → 目标目录；覆盖：新建文件被搬、正在写的文件等待、同名冲突、目标已存在、断电模拟（飞行模式/杀进程）、撤回 |
| Shizuku 场景测试 | 未安装/未运行/未授权/运行中撤销授权/重启 Shizuku/重启手机 |
| 长跑 | 连续 24 小时（间隔 1 分钟）；记录内存、电量、崩溃、ANR、日志增长 |
| 灰度（用户侧） | 第 1 天**全部用复制模式**，只开一条规则；确认规则选对后再切移动、再加第二/三条规则 |
| 回归 | 系统更新（HyperOS 补丁 / Google Play 系统更新）后跑一次自检并记录结果 |

---

## 15. 验收标准（全部可测）

| # | 标准 |
|---|---|
| A1 | 规则配好并启用后，源目录中新出现的图片 **≤1 个间隔 + 1 分钟内**落到目标目录 |
| A2 | 搬完 10 秒内，系统相册能看到（E6 已具备） |
| A3 | 同名文件不覆盖：目标里出现带来源 App 后缀/时间戳的新文件，原文件零改动 |
| A4 | 任何失败场景下，源文件**依然存在** |
| A5 | 杀掉 App / 重启手机 / 重启 Shizuku 后，无需手动操作即可恢复搬运（Shizuku 自身自启为前提） |
| A6 | 撤回能恢复符合条件的文件，并对不符合条件的逐条说明原因 |
| A7 | 自检按钮能在"能搬"与"不能搬"两种系统状态下给出正确结论（用 §3 的探针做对照） |
| A8 | 连续 24 小时运行无崩溃、无 ANR；常驻通知状态正确 |
| A9 | 规则状态与统计准确（搬移数、失败数、暂停原因） |
| A10 | 重复抑制能在"App 自动重建文件"的情形下自动暂停规则并通知 |
| A11 | 首次全量搬空 328 张（用户真实场景）在默认参数下 2 小时内完成 |
| A12 | APK 可侧载安装，界面全中文，设置项与 §8 一致 |

---

## 16. 里程碑与交付物

| 里程碑 | 内容 | 交付物 |
|---|---|---|
| M1 | 项目骨架 + Shizuku UserService + AIDL 打通 + 自检可用 | 可运行的 debug APK（自检全绿） |
| M2 | 规则 CRUD + 扫描/过滤/命名 + 单文件搬运 + 日志 | 能真实搬一个目录 |
| M3 | 后台（specialUse FGS + WorkManager + 开机 + Shizuku 状态机） | 关掉界面也能持续搬 |
| M4 | 撤回 + 重复抑制 + 限速 + 通知交互 + 导出 | 功能完整 |
| M5 | 打磨、长跑测试、HyperOS 适配清单、Release | 0.1.0 APK + Release Notes |

---

## 17. 风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| 系统更新后 Android/data 不可访问（#1574/#1807） | 功能整体失效 | 自检按钮 + 明确通知 + 停止重试；README 写明"系统更新后请先自检" |
| Shizuku 无法开机自启 | 重启后不工作 | 文档给出两条路：Shizuku 改版（pixincreate/thedjchi/yangFenTuoZi）或无线调试自动启动脚本；App 侧做到"Shizuku 一就绪就自动继续" |
| HyperOS 杀后台 | 常驻中断 | specialUse FGS + 电池白名单 + 自启动权限 + 最近任务加锁 + WorkManager 兜底 |
| 误搬 App 运行素材 | App 行为异常 | 用户只挑精确目录；重复抑制；撤回；第一天用复制模式 |
| "搬走→App 重建"死循环 | 耗电耗流量 | 重复抑制（30 分钟 3 次自动暂停） |
| rename 跨卷失败 | 搬运失败 | 回退 copy→校验→删源；本方案内部存储单一卷，属于兜底路径 |
| 首次搬空耗时长 | 体验差 | 参数可调 + "加速清空"开关 |
| Binder 事务超限 | 列目录失败 | 分页 500 条 + 游标 |
| 数据库无限增长 | 存储膨胀 | 日志保留策略 + Item 清理 |

---

## 18. 系统设置清单（HyperOS 3 + Shizuku）

开发完成后需在真机完成以下设置（写进 README 与首启引导）：

1. 设置 → 应用设置 → 拾图 → **自启动：允许**。
2. 设置 → 应用设置 → 拾图 → **省电策略：无限制**。
3. 最近任务界面给拾图**加锁**（防止一键清理）。
4. 允许**通知**权限。
5. 首启时按引导把拾图加入**电池优化白名单**。
6. Shizuku 侧：在 Shizuku 里**授权拾图**；Shizuku 自身也设为"自启动 + 无限制"。
7. Shizuku 开机自启（二选一，文档给出步骤）：
   - a) 使用支持非 root 开机自启的 Shizuku 改版（[pixincreate](https://github.com/pixincreate/Shizuku) / [thedjchi](https://github.com/thedjchi/Shizuku) / [yangFenTuoZi](https://github.com/yangFenTuoZi/Shizuku)）；
   - b) 无线调试自动启动脚本（参考 [ADB-and-Shizuku-AutoStart](https://github.com/dtdung07/ADB-and-Shizuku-AutoStart) 与 Shizuku [discussion #462](https://github.com/RikkaApps/Shizuku/discussions/462)）。

---

## 19. 命名与发布

| 项 | 值 |
|---|---|
| 中文名 | **拾图** |
| 仓库名 | `shitu-android` |
| 包名 | `com.landslide.shitu` |
| 版本 | `0.1.0`（首个可用版） |
| 许可 | GPL-3.0 |
| 分发 | GitHub Releases（自签 APK）；不上架商店 |
| 最低支持 | minSdk 30（Android 11）/ targetSdk 36 / compileSdk 36 |
| 技术栈 | Kotlin + Jetpack Compose + Room + DataStore + WorkManager + Shizuku API 13.x（Maven Central） |

---

## 20. 开发前必须真机验证的 5 件事（附默认处理）

| # | 待验证 | 默认处理（若验证失败） |
|---|---|---|
| 1 | `specialUse` 前台服务能否在 `BOOT_COMPLETED` 中启动（Android 15+ 禁止的是 dataSync/camera/mediaPlayback/phoneCall/mediaProjection/microphone，未列 specialUse） | 退化为"WorkManager 15 分钟兜底 + 打开 App 恢复"，并在文档说明"重启后第一次需要打开一次 App" |
| 2 | UserService 在 HyperOS 上被冻结后能否自动重连 | 增加前台服务内 30 秒探测 + 通知引导 |
| 3 | "低电量/省电模式暂停"是否与"搬空"预期冲突 | 默认开启但首启提示可关 |
| 4 | 目标目录放在非标准目录（如 `/sdcard/我的图库/`）时，用户实际使用的相册是否可见（E7 显示 MediaStore 收录了，但云相册可能不显示） | 默认目标在 `/sdcard/Pictures/拾图/`，自定义时给出提示 |
| 5 | 名称截断阈值（180 字节）在你的机型/目标位置是否安全 | 若出现失败则降到 150 字节 |

---

## 附录 A · 复现探针（自检功能的原型，也是验收 A7 的对照）

> 以下命令在 PC 上执行（adb 路径按本机实际情况）。其中的 `某张图.png` 需替换为实际存在的文件名（例如 `bookLastImage.png`）。`rish` 部分用于验证"Shizuku 身份"这条路径。

```powershell
$adb = "D:\soft\scrcpy\adb.exe"
$pkg = "com.qidian.QDReader"
$d   = "/sdcard/Android/data/$pkg/files"

# 1) 身份：应为 uid=2000(shell) / u:r:shell:s0
& $adb shell "id; cat /proc/self/attr/current"

# 2) 列目录 / 读取 / 复制出来 / 写-改名-删 / 移动出来
& $adb shell "ls -la '$d'"
& $adb shell "cat '$d/某张图.png' > /dev/null && echo READ_OK || echo READ_FAIL"
& $adb shell "cp '$d/某张图.png' /sdcard/Pictures/_probe.png && rm -f /sdcard/Pictures/_probe.png && echo COPY_OK || echo COPY_FAIL"
& $adb shell "touch '$d/_w1' && mv '$d/_w1' '$d/_w2' && rm -f '$d/_w2' && echo WRITE_OK || echo WRITE_FAIL"
& $adb shell "cp '$d/某张图.png' '$d/_mv.png' && mv '$d/_mv.png' /sdcard/Pictures/_mv.png && rm -f /sdcard/Pictures/_mv.png && echo MOVE_OK || echo MOVE_FAIL"

# 3) Shizuku 身份复验（可选）：从 Shizuku APK 提取 rish
$apk = (& $adb shell "pm path moe.shizuku.privileged.api").Trim() -replace '^package:',''
& $adb pull $apk "$env:TEMP\shizuku.apk"
# 解压 assets/rish 与 assets/rish_shizuku.dex → push 到 /data/local/tmp/ → sh /data/local/tmp/rish < 脚本
```

**期望结果**（2026-10-04 实测，本机全绿）：`READ_OK / COPY_OK / WRITE_OK / MOVE_OK`，rish 路径同样全绿，身份 `uid=2000(shell)`。

---

## 附录 B · 关键实现细节备忘

- **FGS 清单片段**
  ```xml
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
  <service android:name=".core.WatchService"
           android:foregroundServiceType="specialUse"
           android:exported="false">
      <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="file_organizer_watcher"/>
  </service>
  ```
- **Shizuku 依赖**：`dev.rikka.shizuku:api` + `dev.rikka.shizuku:provider`（Maven Central），`Shizuku.requestPermission()` 走标准流程；UserService 用 `Shizuku.bindUserService`。
- **MediaStore**：v1 不写扫描逻辑；保留一个"触发扫描"按钮（`MediaScannerConnection.scanFile` 或 shell `content call --method scan_volume`）。
- **本机构建环境备注**：`D:\Android`（PiliPlus 的 local.properties 指向的 SDK）**当前不存在**，开发前需重新安装 Android SDK；Gradle 8.13/9.x 与 AGP 已有缓存；本机有 adb（`D:\soft\scrcpy\adb.exe`）可直连真机调试。

---

*文档结束。评审通过后由 writing-plans 产出实现计划。*
