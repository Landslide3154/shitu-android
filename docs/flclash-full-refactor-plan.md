# shitu × FlClash 全量重构规划（执行中）

> **进度：P0 令牌层、P1 组件库、P2 导航/设置入口/主题中心、P3 规则/日志/编辑页、P4 主要功能包 已完成（0.30.3 中间版本，未发布）。**
> 已确认：功能全量 F1–F9；默认主色 `#007AFF`；液态玻璃不做；WebDAV 不做。
> 剩余：P4 的拖拽排序（现为上移/下移）、P5 动效收尾、P6 回归发版。

> 依据：`D:\code\FlClash-DesignKit`（FlClash 0.9.2 逆向：令牌/组件/页面/设置 IA/动效/源码副本）
> 与 https://github.com/chen08209/FlClash 。本文只引用数值与结构，**不复制** FlClash 的 GPL 代码、品牌 Logo、国旗与服务图标、插画素材。
> 现状基线：shitu `0.30.1`（versionCode 39）。本规划执行完版本号按项目规则 +0.0.1 → **0.30.2 / 40**。

## 0. 先把「完全」定义清楚

FlClash 是 Flutter + 外部 `material_ui` 包；shitu 是 Android Compose。下列能力可以做到等价，下列只能做到「视觉/手感等价」或明确不做——先讲清，避免验收时扯皮。

| 项 | FlClash | 在 shitu 的落地方式 | 保真度 |
| --- | --- | --- | --- |
| 配色 | HCT 种子 → 4 种风格 → tone 覆写 | 引入 `com.google.android.material:material` 的 HCT 工具（Apache-2.0，已在本机缓存），1:1 移植 `seededColorScheme` | 100% |
| 表面四级 | page/card/block/inset | `SurfaceLevel` + `CompositionLocal`，卡片自动取下一层 | 100% |
| 字号 | iOS 13 档 | 同尺寸/行高/字重建 13 档；字体用系统字体 | 100%（字形不同） |
| 超椭圆 | `RoundedSuperellipseBorder` | 自绘 superellipse `Shape`（每角 20 段采样，按尺寸缓存） | ≈95%（曲率参数不可得） |
| 圆角阶梯 | 0/4/8/16/20/24/28/34/full + `fit()` | 同阶梯，全局注入 `ShituShapes` | 100% |
| 导航 | 侧栏 48/220 + 浮起 Dock 62 | 侧栏自绘；Dock 自绘（3 根弹簧近似 8 根） | 90% |
| 底栏透镜/果冻 | 8 弹簧 + 逐帧几何 | 选中板/图标跟随用 spring，果冻只做一次回弹 | ≈70%，不建议逐帧复刻 |
| 液态玻璃 | 平台模糊 | `dev.chrisbanes.haze`（1.7.2 已缓存）做背景模糊 + 着色，默认关 | 90%（Android 12+） |
| 主题切换 | 旧帧截图淡出 | `GraphicsLayer.toImageBitmap()` 抓旧帧后淡出 | ≈95% |
| 图标 | 124 个自绘 24 网格描边 | 采购 Lucide（ISC）同网格 1.8 描边，约 24 个，自绘补 3 个 | 90%（造型不同） |
| 公告/品牌/插画 | FlClash 自有素材 | 不复制；空态插画自绘 | — |
| 桌面标题栏/快捷键/托盘 | 有 | Android 不适用 | — |

**结论**：设计系统、页面骨架、设置体系、交互与功能可以做到 FlClash 的同一套语言；底栏物理动画与液态玻璃按上表降级，其余不缩水。

## 1. 现状差距（对照 0.30.1）

| 维度 | 现在 | FlClash 目标 | 差距 |
| --- | --- | --- | --- |
| 主色 | `#1F6FEB` 或动态取色 | `#007AFF` 种子 + 8 色板 + 4 风格 + 纯黑 | 缺风格/色板/纯黑 |
| 表面 | 已有 page/card/block/inset | 同四级，但卡片内部自动推进 | 目前靠手动传色 |
| 暗色 | tone 15/21/27，onSurface 97 | 同（已对齐） | 基本达标 |
| 字号 | M3 默认（body 14/16） | iOS 13 档（body 17/22） | 全量替换 |
| 圆角 | 5 档普通圆角 | 9 档超椭圆 | 形状与档位都不够 |
| 卡片 | `Card` + `surfaceBright` | 无边框/无阴影的「可点表面」，自动取层级 | 需重写 |
| 行 | 48/56 混杂，自带实现 | 七种 `ListItem` 构造，统一 56、左右 16/8、14% 发丝线 | 需重写 |
| 导航 | 手机底栏 3 项；≥600dp 侧栏 | 浮起 Dock（62/21/28/10）+ 可折叠侧栏（48/220/40/3dp 主色条） | 需重做 |
| 工具栏 | 各页自拼 | 72 高、pageTitle 24/30 w600 | 需统一 |
| 设置 | 6 张长卡滚动 | 入口页 + 二级页 + 七种行 | 需重排 IA |
| 动效 | tab 300ms/0.15，开关缩放 | 页面 450ms/0.25、开关 300ms easeOutBack、弹层 404ms/菜单弹簧 | 需重写参数 |
| 图标 | Material Filled | 24 网格 1.8 描边 | 需替换 |
| 功能 | 主题只有模式/动态色；备份只有两个按钮；规则无搜索/多选/排序 | 主题中心、备份恢复页、搜索/多选/排序、通知、空态等 | 见 §2 |

## 2. 功能包（不只是换皮）

| 编号 | 功能 | 具体内容 | 数据改动 | 推荐 |
| --- | --- | --- | --- | --- |
| F1 | **主题中心** | 实时预览；模式（跟随/浅/深，带缩略图）；纯黑；8 色板 + 跟随系统 + 自定义取色 + 重置；4 种风格（vivid/tinted/soft/monochrome）；导航样式（浮起 Dock/停靠）；标签动画（滑动/淡入）；液态玻璃（关/标准/极致）；文字缩放 0.8–1.4 | Settings +7 字段；主题按 (seed,dark,style,pureBlack) 缓存 | 必做 |
| F2 | **设置 IA 重构** | 一级入口（搬运 / 数据与备份 / 外观 / 关于）全部 `ListItem.next`；二级页：运行条件、检测时机、运行参数、存储与权限、通知、复制记录、备份与恢复、主题、关于；七种行类型 | 仅 UI 重排，不改语义 | 必做 |
| F3 | **备份与恢复页** | 立即备份（系统文件选择器写文件）；备份信息（时间/规则数/设置项数）；恢复：选文件 → 预览 → 恢复范围（全量/仅规则/仅设置）→ destructive 确认 → 结果；兼容 v1 备份 | BackupIo v2（带版本/时间/计数）；Settings 新键 | 必做 |
| F4 | **规则页增强** | 搜索（名称/源/目标）；状态筛选 chips（全部/运行中/待命/暂停/失败）；多选模式（批量启用/暂停/删除/导出）；拖拽排序并持久化；空态/无结果态 | DataStore `ruleOrder: List<Long>`（不动 Room） | 必做 |
| F5 | **日志增强** | 搜索 + 结果/规则筛选 sheet；滚动离开底部时暂停自动刷新；行详情 sheet；导出 CSV；清空确认；空态/无结果 | 无 | 必做 |
| F6 | **通知设置** | 总开关（`notifyEnabled` 已有数据但无 UI）；搬运完成/失败/前台服务分类；发送测试通知；跳系统通知设置 | 视 [Notifier] 现状补 1–2 字段 | 必做 |
| F7 | **系统集成** | 桌面长按快捷方式（立即搬运/日志/设置）；开关/长按/危险操作的触觉反馈 | manifest shortcuts + Intent 参数 | 建议 |
| F8 | **概览卡** | 规则页顶部：通道状态、累计搬运、本周、启用规则数、运行条件摘要、立即搬运/自检 | 无（现有数据） | 建议 |
| F9 | **空/错/载三态** | 插画（自绘矢量）+ 标题 + 说明 + 动作；加载延迟 400ms；错峰 60ms 入场 | 无 | 必做 |
| F10 | WebDAV 备份 | 远端备份/恢复、连接测试 | 需要联网与凭据存储；**违反当前「只有检查更新联网」的口径**，需改 README/隐私说明 | 默认不做 |

> 功能取舍会直接决定 §4 的工期；请在确认时勾选。

## 3. 设计系统落地（技术方案）

### 3.1 颜色：把 FlClash 的生成式色板搬过来

新增依赖 `com.google.android.material:material:1.14.0`（只用 `com.google.android.material.color.utilities`，Apache-2.0；本机 Gradle 缓存已有，R8 会裁掉未用部分）。

新建 `ui/design/AppColor.kt`，逐条移植 `lib/ui/color.dart`：

- `ColorStyle { VIVID, TINTED, SOFT, MONOCHROME }` → `SchemeContent / SchemeTonalSpot / SchemeMonochrome`。
- `seededColorScheme(seed: Color, dark: Boolean, style: ColorStyle, pureBlack: Boolean): ColorScheme`。
- 主色约束：亮 tone 35–60、暗 tone 50–75（种子 +3）；`onPrimary` 随 tone 切白/深字。
- 表面：`surfaceChroma 3`、`paperChroma 1.5`、暗色写死 15/21/27，`onSurface` 暗色 97。
- `tinted`：表面按 `min(seed.chroma/6, 10)` 带色；其余风格近灰。
- `pureBlack`：`surface` 归零，其余表面整体下移 page 的 tone。
- 状态色：success/warning 固定 green/orange 调色板（tone 40/80，容器 90/30），destructive `#FF383C` / `#FF4245`，与 `error` 分开。
- 主题缓存：LRU 64，键 `(seedArgb, dark, style, pureBlack)`，避免每次重组重算 HCT。

### 3.2 表面层级：从「手动传色」改为「作用域自动推进」

新建 `ui/design/Surface.kt`：

```kotlin
enum class SurfaceLevel { PAGE, CARD, BLOCK, INSET;
  fun colorOf(cs: ColorScheme): Color
  fun next(): SurfaceLevel
}
val LocalSurfaceLevel = compositionLocalOf { SurfaceLevel.PAGE }
```

- `AppSurface` 组件读取 `LocalSurfaceLevel.current.next()` 作为自己的底，并向内容 `provide` 该层级。
- 卡片不再接收 `containerColor`；页面根 `provide(PAGE)`。
- `chrome` = 暗 `surfaceContainerLow` / 亮 `surfaceContainerHighest`；浮层 = 暗 `block` / 亮 `card`。
- 所有旧 `ShituSurface.page()/card()` 调用点改为作用域读取；旧 `Surface.kt` 删除。

### 3.3 字体：13 档 + 半速跟随系统

新建 `ui/design/AppType.kt`：

| token | size/leading | 字重 | M3 槽位（兼容旧代码） |
| --- | --- | --- | --- |
| display | 40/44 | 400/700 | displayLarge |
| largeTitle | 34/41 | 400/700 | displayMedium/headlineLarge |
| title1 | 28/34 | 400/700 | headlineMedium |
| pageTitle | 24/30 | 600/700 | headlineSmall |
| title2 | 22/28 | 400/700 | headlineSmall/titleLarge |
| title3 | 20/25 | 400 | titleLarge |
| headline | 17/22 | 600 | titleMedium |
| body | 17/22 | 400 | bodyLarge |
| callout | 16/21 | 400 | bodyLarge |
| subheadline | 15/20 | 400 | bodyMedium |
| footnote | 13/18 | 400 | bodySmall |
| caption1 | 12/16 | 400 | labelMedium |
| caption2 | 11/13 | 400 | labelSmall |

- `letterSpacing = 0`，`lineHeight = leading`。
- `MaterialTheme.typography` 全部槽位重映射 → 旧页面立即变大变「iOS」。
- 文字缩放：`fontScale = clamp(system, 0.8, 1.4)`，实际 `1 + (fontScale-1)*0.5`；主题中心再用 `textScale` 乘一次。
- 数字（统计/时间）用 tabular figures（`FontFeatureSettings "tnum"`）。

### 3.4 形状：自绘超椭圆

新建 `ui/design/AppShape.kt`：

- `SquircleShape(radius: Dp, exponent: Float = 4f)`：按 `|x/a|^n + |y/b|^n = 1` 每角采样 20 段生成 `Path`；`radius >= minSide/2` 退化为胶囊；路径按 `(w,h,r)` 缓存。
- 阶梯：`none 0 / xs 4 / sm 8 / md 16 / lg 20 / xl 24 / xxl 28 / alert 34 / full`。
- `AppCorner.fit(shortestSide)` 按「不超过最短边 1/3 的最大档」。
- 嵌套规则：`outer = inner + inset`（菜单 24 = 行 16 + 8），不新增 token。
- 全局 `ShituShapes`：卡片 md(16)、通栏卡 xl(24)、对话框 alert(34)、Sheet 顶部 xxl(28)、Snackbar xs(4)、输入框 md(16)、按钮/胶囊 full、chip sm(8)。
- 卡片默认 `elevation=0`、无边框；只有浮层/概览卡允许阴影。

### 3.5 间距与尺寸（直接采用）

| token | 值 | 用途 |
| --- | --- | --- |
| `ListRowMinHeight` | 56 | 所有列表/设置行 |
| `ListRowPadding` | 纵 10、横 16（带尾控件右侧 8） | 行内衬 |
| `SectionHeader` | L16 R16 T24 B8 | 组标题 |
| `PagePadding` | 16 | 页面左右 |
| `PageToolbarHeight` | 72 | 页面工具栏 |
| `SheetToolbarHeight` | 48 / `SheetHeader` 68 | 弹层 |
| `DockHeight` | 62（内衬 4、离边 21、图标 28、标签 10） | 移动底栏 |
| `SidebarWidth` | 48 / 220（项 40、gap 4、图标 24） | 侧栏 |
| `SegmentedHeight` | 44（内衬 4） | 分段 |
| `DialogWidth` | 348（内容 300、动作 48） | 告警 |
| breakpoints | 600 / 840 | 导航 / 内容密度 |

### 3.6 动效

新建 `ui/design/AppMotion.kt`，按 FlClash 场景表：

- 页面转场 450ms、位移 0.25 页宽（Android 上取 0.15–0.25 可调）、`CubicBezierEasing(0.2, 0, 0, 1)`；旧页 0–30% 淡出、新页 15–55% 淡入。
- 开关 300ms `easeOutBack`，按压 150ms、抬起 200ms；轨道 52×32、拇指 24（按下 40）。
- 分段 250ms `easeOutCubic`；文字 200ms。
- 告警 1.2→1.0、404ms；菜单 spring(400, 0.754)；Sheet 300ms。
- 空态错峰 60ms、上移 12dp；骨架 1600ms 余弦；加载延迟 400ms。
- 统一 `reduceMotion`：`ValueAnimator.areAnimatorsEnabled()` 为 false 时全部 0ms/`snap()`。

### 3.7 图标

- 建立 `ui/design/ShituIcons.kt`：24×24 网格、stroke 1.8、round cap/join、单色 `ImageVector`。
- 约 24 个：rules/list、logs、settings、plus、search、filter、sort、check、close、chevron-right/down/up、more、play、undo、copy、trash、folder、download、upload、refresh、shield、bell、palette、info、warning、clock。
- 来源 Lucide（ISC-2.0），在 `NOTICE` 注明；FlClash 自绘造型不复制。
- 禁用 Material Filled 混用；`IconButton` 一律 48 触控区 + `contentDescription`。

### 3.8 组件清单（全部带 6 态：默认/按下/选中/禁用/错误/聚焦）

`AppSurface`、`Section`/`SectionHeader`、`ListItem`（7 种）、`CommonSwitch`、`PillButton`、`TonalButtonGroup`、`CommonChip`、`SegmentedControl`、`CommonDialog`、`AppSheet`、`AppField`、`StatusChip`、`SelectionMark`、`EmptyState`、`LoadingRing`、`SkeletonPill`、`SearchField`、`Toolbar`、`Dock`、`Sidebar`、`ThemePreview`。

组件一律放在 `ui/components/` 或 `ui/design/`，**不读业务数据**；业务包装留在页面层（对应 FlClash 的 `ui/` 与 `widgets/` 分层）。

## 4. 分阶段执行（每阶段可独立编译、真机看效果）

> 每个阶段结束：`assembleDebug` + 真机截图 + 阶段小结；**P1、P2、P4 后各停一次**给你确认，避免方向跑偏后返工。
> 旧文件不一次性删除：先建新体系，页面逐个切换，最后清理。

### P0 · 令牌层（约 2–3 人日）

**新建** `ui/design/`：`AppColor.kt`、`AppType.kt`、`AppShape.kt`、`AppSurface.kt`、`AppMotion.kt`、`AppMetrics.kt`、`AppTheme.kt`。
**改造**：`ui/theme/Theme.kt` → 调用 `AppTheme`；`ui/theme/Shapes.kt` 改为超椭圆别名；`Tone.kt` 拆成 `AppStatusColors`；`Surface.kt`/`ColorTones.kt` 由新实现替代。
**依赖**：`libs.versions.toml` + `app/build.gradle.kts` 加 `material`（HCT）。
**验收**：4 风格 × 明暗 × 纯黑共 16 套色板单测；关键表面 tone 与 FlClash 文档一致（±2）；`ColorTonesTest` 重写为 HCT 断言。

### P1 · 组件库（约 4–6 人日）

**新建**：`AppSurface.kt`、`Section.kt`、`ListItem.kt`（7 构造）、`CommonSwitch.kt`、`PillButton.kt`、`Chip.kt`、`SegmentedControl.kt`、`CommonDialog.kt`、`AppSheet.kt`、`AppField.kt`、`SearchField.kt`、`EmptyState.kt`、`LoadingRing.kt`、`SkeletonPill.kt`、`ShituIcons.kt`。
**替换**：`Status.kt`（Pill/Banner/SectionCard 全部退役）、`ShituField.kt`、`ShituSwitch.kt`、`ConfirmDialog.kt`。
**调试页**：`ui/dev/DesignShowcase.kt`（仅 debug 入口，正式包不含）逐组件 6 态，真机截图存档。
**验收**：行高 56、圆角/字号/内衬逐项对照 §3；200% 字号不裁切；触点 ≥48dp；无边框卡片。

### P2 · 导航壳 + 设置入口 + 主题中心（约 4–6 人日）

**重写** `ui/ShituRoot.kt`：一份 `NavDestination`（规则/日志/设置）→ 手机浮起 Dock / 平板侧栏；`ShituToolbar`（72/pageTitle 24）；页面内容 16 边距、840 上限。
**新建**：`ui/nav/Dock.kt`、`Sidebar.kt`、`Toolbar.kt`；`ui/settings/SettingsHomeScreen.kt`（三段入口页）；`ui/settings/theme/ThemeScreen.kt`（F1 主题中心，含实时预览/色板/风格/纯黑/导航样式/字号/玻璃）。
**改**：`MainActivity` 系统栏、insets、快捷方式入口。
**验收**：600/840 断点无跳变；Dock 选中态中性底+主色；侧栏 48/220 + 3dp 主色条；主题切换即时生效。

### P3 · 页面重排（约 6–8 人日）

| 页面 | 做法 |
| --- | --- |
| 规则 | `RuleListScreen` 拆为 `RuleListScreen` / `RuleCard` / `RuleSearchBar` / `RuleBatchBar`；顶部概览卡；卡片 24、卡间 8；空态 |
| 日志 | `LogScreen` 重写：工具栏 + 搜索 + 筛选 sheet + 行详情 sheet + 暂停刷新；行卡 16；清空确认 |
| 设置二级页 | 运行条件 / 检测时机 / 运行参数 / 存储与权限 / 通知 / 复制记录 / 备份与恢复 / 关于，全部 `Section + ListItem` |
| 规则编辑 | 改为 `FormPage`：`FormRow` 左标题右输入、无独立描边；底部动作条；未保存确认沿用 |
| 目录选择 | `DirPickerDialog` → `AppSheet`（28 顶角、行 56、面包屑） |
| 更新/自检 | 更新改 `AppSheet`（版本/说明/进度/安装）；自检改结果页或 Sheet |
| 引导 | 电池优化/存储授权/Shizuku 用统一 `CommonDialog` |

**验收**：每页空/错/载三态；长文本 1 行省略；所有弹层走统一组件。

### P4 · 功能包（约 6–10 人日，按 §2 勾选）

- **F3 备份恢复**：`BackupIo` v2 + `BackupScreen` + 恢复范围弹层 + 预览；旧 v1 文件可导入。
- **F4 规则增强**：搜索/筛选/多选/批量/拖拽排序（`ruleOrder` 存 DataStore）+ 模板管理入口。
- **F5 日志增强**：筛选 sheet、滚动暂停、行详情、导出。
- **F6 通知**：开关/分类/测试通知/系统设置跳转（看 `Notifier.kt` 现有能力）。
- **F7 快捷方式/触觉**：manifest `shortcuts.xml`，`MainActivity` 解析 `EXTRA_TAB`；开关/删除 `HapticFeedback`。
- **F9 空态**：自绘插画（`Canvas` 矢量，不引入位图）。
- **F1 补全**：主题色自定义取色器（HSV）、重置确认。

**验收**：功能逐项真机走查；新增单测覆盖备份 roundtrip、排序过滤、设置默认值。

### P5 · 动效、玻璃与无障碍（约 3–4 人日）

- 主题切换旧帧淡出（`rememberGraphicsLayer().toImageBitmap()`）。
- Dock 指示器 spring、侧栏宽度 250ms、弹层/菜单参数、开关 300ms。
- 液态玻璃（可选）：`haze` 包 Dock/侧栏/工具栏，默认关、仅 Android 12+；提供「关/标准/极致」。
- 焦点环 2dp、TalkBack 顺序、减少动效、高对比度、RTL（中文/英文）。

**验收**：`Settings.Global.ANIMATOR_DURATION_SCALE=0` 全部跳过；滚动 jank < 1%；对比度 APCA 正文 ≥60。

### P6 · 回归与交付（约 2 人日）

- `testDebugUnitTest`（现 236 + 新增）、`assembleRelease`、真机逐页（明/暗/纯黑/4 风格/200% 字号/横屏/平板宽度）。
- 像素校验：截图取 page/card/block/inset/primary/onSurface，与 FlClash 实测值 ΔE ≤3。
- 更新 `docs/CHANGELOG.md`、`docs/2026-10-04-shitu-验证记录.md`、`docs/版本历史.md`、README 截图（如界面变化大）。
- 版本 0.30.2 / 40；本地 commit，**不推送**（按规矩等发话）。

**总工期**：约 **27–39 人日**（不含 WebDAV 与发版）。本轮由我分阶段执行，每个阶段可编译、可回看。

## 5. 验收标准（硬指标）

| 项 | 标准 |
| --- | --- |
| 颜色 | 16 套方案关键表面与 FlClash 文档/实测 tone 差 ≤2，ΔE ≤3；主色白字对比 ≥3:1 |
| 字号 | 全部文本来自 13 档；无裸 `fontSize`；200% 系统字号（半速后 150%）不裁切 |
| 形状 | 无裸圆角；全部走 `AppCorner`/`AppCorner.fit()`；卡片无边框、elevation 0 |
| 层级 | 任意卡片内部自动取下一层；调用点不传表面色 |
| 行/触控 | 行高 ≥56；可点区 ≥48×48 |
| 导航 | 600/840 断点行为固定；Dock/侧栏选中态一致；Tab 状态可恢复 |
| 动效 | 页面 ≤450ms/≤0.25 宽；开关 300ms；reduce motion 全跳过 |
| 功能 | §2 勾选项逐条真机验收；备份 v1/v2 双向兼容 |
| 回归 | 现有 236 测试全过；新增测试全过；Release 构建成功；真机无 crash |
| 可访问性 | TalkBack 能完成「新增规则→搬一张图→看日志」；焦点顺序合理 |

## 6. 风险与回滚

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 全量重写面大，回归难 | 高 | 分 6 阶段、每阶段可编译 + 真机截图；旧文件最后删；git 分阶段提交，可单阶段 revert |
| `material` 依赖体积/冲突 | 中 | 只用 color.utilities；R8 裁剪；release 体积对比 |
| 超椭圆自绘性能 | 中 | Path 按尺寸缓存；只在卡片/弹层，不给列表每行独立阴影 |
| 液态玻璃掉帧 | 中 | 默认关；仅 Dock/侧栏；Android 12+；可一键关 |
| HCT 与动态取色冲突 | 中 | 提供「跟随系统 / 固定色板」二选一；动态取色走同一 tone 覆写 |
| 设置字段/备份变更 | 中 | 新键全部有默认值；BackupIo 兼容旧文件；单测 roundtrip |
| 排序功能改数据 | 中 | 用 DataStore `ruleOrder`，不动 Room schema；删除规则时过滤陈旧 id |
| 与本项目「不主动重构」冲突 | — | 本轮由你明确要求全量重构，范围以本文件为准 |

**回滚**：每个阶段一个 commit；P0/P1 保留旧 `ui/theme` 与旧组件到 P3 结束，出问题可先切回旧页面。主题 v2 不做双 UI flag（维护成本高于收益），用 git 回滚。

## 7. 交付物

1. `ui/design/` 令牌与主题（含 16 套方案单测）。
2. 组件库 + `DesignShowcase` 截图。
3. 新导航壳、设置入口页、主题中心、二级设置页。
4. 规则/日志/编辑/选择器/弹层新页面。
5. 勾选的功能包（备份、搜索/多选/排序、通知、快捷方式、空态等）。
6. 文档：本规划、组件契约摘要、设置 IA 图、验证记录、CHANGELOG、README 截图。
7. 真机截图：明/暗/纯黑、4 风格、手机/平板/横屏、200% 字号、各功能页。

## 8. 已确认的四件事

1. **功能范围**：全量 F1–F9。
2. **默认主色**：FlClash 蓝 `#007AFF` + 8 色板 + 跟随系统壁纸。
3. **液态玻璃**：不做。
4. **WebDAV 备份**：不做（保持「只有检查更新联网」）。

> 你确认后我按 P0→P6 执行；若只选部分功能，我会把对应阶段从计划里删掉再开工。


