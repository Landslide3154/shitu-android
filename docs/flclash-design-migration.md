# FlClash 设计语言 · shitu 文件级改造清单

> **状态：P0–P4 已实现（0.30.1，2026-10-28）**。执行记录见 `docs/2026-10-04-shitu-验证记录.md` §20。
> P2 里的「重置设置」本项目原本没有该功能，本轮未新增；其余清单已落地，单测 236。

> 依据：`D:\code\FlClash-DesignKit`（FlClash 0.9.2 逆向，令牌/组件/动效/IA）。
> 本文只给**改哪个文件、改什么、优先级、验收**，不复制 GPL 代码与品牌资源。
> 现状基线：shitu `0.30.0`（`app/build.gradle.kts:24`），Compose M3 + dynamicColor + 自建 `ShituRadius`/`Tone`。

## 1. 结论先说

shitu **不需要重做设计系统**：已有单一圆角来源、单一输入框、语义色 `Tone`、动态取色。相对 FlClash，真正的差距只有 5 个：

1. **表面层级反了**：shitu 卡片用 `surfaceContainerLow`（比页面深），FlClash 亮色是「灰页 + 近白卡」。
2. **没有层级令牌**：卡片/块/槽的颜色散落在 `SectionCard` 等组件里，没有 `page/card/block/inset` 概念。
3. **深色对比偏弱**：没有 FlClash 的 tone 15/21/27 与 `onSurface` 97 覆写。
4. **导航只服务手机**：`NavigationBar` 固定 3 项，平板/折叠屏无 `NavigationRail`。
5. **设置行高度 48**：低于 FlClash 的 56，长文案与开关同排时偏挤。

其余（13 档 iOS 字号、超椭圆、液态玻璃、底栏透镜）**不建议照搬**，理由见 §6。

## 2. 现状文件台账

| 文件 | 现状 | 作用 |
| --- | --- | --- |
| `app/src/main/java/com/landslide/shitu/ui/theme/Theme.kt` | `#1F6FEB` 种子 + dynamicColor；无 surface 覆写 | 主题装配 |
| `.../ui/theme/Shapes.kt` | Card 20 / Dialog 28 / Field 14 / Row 12 / Pill 50；接进 `ShituShapes` | 唯一圆角来源 ✅ |
| `.../ui/theme/Tone.kt` | OK/BUSY/WARN/ERROR/IDLE/COPY；固定绿/蓝 | 语义色 ✅ |
| `.../ui/components/ShituField.kt` | 14dp 圆角、14sp、56 高 | 唯一输入框 ✅ |
| `.../ui/components/Status.kt` | `StatusPill`/`BannerCard`/`SectionCard`/`SwitchRow` | 卡片与行基座 |
| `.../ui/ShituRoot.kt` | `Scaffold` + `NavigationBar` 3 项 + `AnimatedContent` | 壳层与导航 |
| `.../ui/settings/SettingsScreen.kt` | 1112 行；`SettingCard`/`SectionCard` 组合 | 设置页 |
| `.../ui/rules/RuleListScreen.kt` | 866 行；规则卡列表 | 主列表 |
| `.../ui/rules/RuleEditScreen.kt` | 716 行；表单页 | 编辑 |
| `.../ui/logs/LogScreen.kt` | 471 行；日志流 | 日志 |
| `MainActivity.kt` | `enableEdgeToEdge` + 系统栏图标反色 | 系统栏 |
| `app/src/main/res/values/themes.xml` / `values-night` | 启动主题 | 冷启动 |

## 3. 差距矩阵（FlClash → shitu）

| 令牌/模式 | FlClash | shitu 现状 | 建议 | 判定 |
| --- | --- | --- | --- | --- |
| 页面底 | `surfaceContainer`（亮 94 / 暗 6） | `surface` 默认 | 亮色改 `surfaceContainer`，暗色保持 `surface` | 需适配 |
| 卡片 | `surfaceBright`（亮 98 / 暗 15） | `surfaceContainerLow` | 亮色改 `surfaceBright`；暗色改 `surfaceContainer` | 需适配 |
| 卡内块 | `surfaceContainer`（亮 94 / 暗 21） | 无 | 新增 `ShituSurface.Block` | 需适配 |
| 槽/轨道 | `surfaceContainerHighest`（亮 90 / 暗 27） | 无 | 新增 `Inset` | 需适配 |
| 主色 | 种子；暗色 +3 tone | `#1F6FEB` 或动态 | 保留；若要「更像」，把暗色主色提亮一档 | 可选 |
| 圆角 | 0/4/8/16/20/24/28/34/1000 | 20/28/14/12/50 | **保留现有**；Field 可 14→16 | 可直接套用 |
| 字体 | iOS 13 档（正文 17） | M3 默认（body 14/16） | **不改**（Android 惯例） | 不建议照搬 |
| 行高 | 56 | `SwitchRow` 48 | 改 56；或至少 52 | 需适配 |
| 分隔线 | `onSurface` 14% 发丝线 | 依赖 Card 间距 | 可选：组内行加 14% 分隔 | 需适配 |
| 导航 | 侧栏 48/220 + 底栏 62 | `NavigationBar` | 加 ≥600dp 的 `NavigationRail` | 需适配 |
| 动效 | 弹簧 + 450ms 转场 | `tween` 默认 | 转场 300ms/0.15 位移；开关用 spring | 需适配 |
| 超椭圆 | squircle | 圆弧 | 不做 | 不建议 |
| 液态玻璃 | 有 | 无 | 不做 | 不建议 |

## 4. 文件级任务（按优先级）

### P0 · 表面层级（1–2 人日）

**新建** `ui/theme/Surface.kt`

```kotlin
object ShituSurface {
    @Composable fun page() = if (isDarkScheme()) MaterialTheme.colorScheme.surface
        else MaterialTheme.colorScheme.surfaceContainer
    @Composable fun card() = if (isDarkScheme()) MaterialTheme.colorScheme.surfaceContainer
        else MaterialTheme.colorScheme.surfaceBright
    @Composable fun block() = if (isDarkScheme()) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainer
    @Composable fun inset() = MaterialTheme.colorScheme.surfaceContainerHighest
}
```

- `[ ]` `Theme.kt`：保持 `MaterialTheme(colorScheme = scheme, shapes = ShituShapes)`；不改 scheme 生成，先在消费端换色。
- `[ ]` `MainActivity.kt` / `ShituRoot.kt`：`Scaffold(containerColor = ShituSurface.page())`。
- `[ ]` `Status.kt:170-175`：`SectionCard` 的 `containerColor` 由 `surfaceContainerLow` 改为 `ShituSurface.card()`。
- `[ ]` `Status.kt` 内新增 `Block`/`Inset` 用法：状态胶囊底、进度条轨道、缩略图槽。
- 验收：亮色「灰页 + 白卡」对比可见；暗色卡片比页面亮一档；动态取色下仍成立。

### P1 · 深色层级与对比（1 人日）

- `[ ]` `Theme.kt`：在 `dynamicDarkColorScheme` / `darkColorScheme` 后 `.copy(surfaceContainer = …, surfaceContainerHigh = …, surfaceContainerHighest = …, onSurface = …)`，目标 tone 15/21/27、onSurface 97（用 `Color.hsl` 或预置 ARGB，需真机校色）。
- `[ ]` 保留 `Tone.kt` 的固定成功/复制色（已符合 FlClash「状态色不随种子漂移」原则）。
- 验收：暗色标题对比 APCA ≥ 60；卡片与页面肉眼可分。

### P2 · 设置行与 IA（2–3 人日）

- `[ ]` `Status.kt:229` `SwitchRow` 的 `heightIn(min = 48.dp)` → `56.dp`；副标题与开关之间留 12dp。
- `[ ]` `SettingsScreen.kt`：按 FlClash 七种行类型盘点现有条目，补齐「危险操作」统一确认（`AlertDialog` + `error` 色确认按钮）。
- `[ ]` 一级分组保持现有 4–6 组；每组 ≤ 7 行；组名用「启动/搬运/通知/关于」。
- `[ ]` 危险操作清单：删除规则、清空复制记录、清空日志、重置设置——逐项确认文案含对象名。
- 验收：长文案 + 开关同排不挤；危险操作 100% 有确认。

### P3 · 自适应导航（2–3 人日）

- `[ ]` `ShituRoot.kt:229-260`：`BoxWithConstraints`；`maxWidth >= 600.dp` 时用 `NavigationRail`（左侧，宽 80），否则 `NavigationBar`。
- `[ ]` 选中态：Rail 用「中性底 + 主色图标/指示条」，不要整块主色。
- `[ ]` 平板横屏：内容区最大宽 840dp 居中，避免长行。
- 验收：折叠屏展开/手机竖屏切换无状态丢失；Tab 状态由 `rememberSaveable` 保持。

### P4 · 动效收敛（1–2 人日）

- `[ ]` `ShituRoot.kt` 的 `AnimatedContent`：位移从全宽改 `0.15 × width`，时长 300ms，`FastOutSlowInEasing`。
- `[ ]` 开关、状态切换用 `animateFloatAsState(spring(dampingRatio = 0.8f, stiffness = 300f))`。
- `[ ]` 尊重 `Settings.Global.ANIMATOR_DURATION_SCALE == 0`（减弱动效）。
- 验收：转场不闪白；列表滚动 jank < 1%。

### P5 · 回归（1 人日）

- `[ ]` `.\gradlew.bat test`（236 个单测）全过。
- `[ ]` `.\gradlew.bat assembleRelease` 成功。
- `[ ]` 真机逐页检查：亮/暗、动态色开/关、200% 字号、横屏。
- `[ ]` 按项目规则：`versionName +0.0.1`、`versionCode +1`、`docs/CHANGELOG.md` 顶部加一节、验证记录追加到 `docs/2026-10-04-shitu-验证记录.md`。

**合计：8–12 人日**（不含真机测试与发版）。

## 5. 不建议照搬

| 项 | 原因 |
| --- | --- |
| 13 档 iOS 字号 | Android 用户心智与 Roboto 度量不同；shitu 现有 14/16 更稳 |
| 超椭圆 squircle | Compose 无原生；自绘成本 > 收益 |
| 液态玻璃 | Android 无系统级模糊，`RenderEffect` 成本高 |
| 底栏透镜/果冻 | 需要 8 组弹簧 + 逐帧几何，维护成本极高 |
| 34dp 告警圆角 | shitu 现有 28 更贴 Android |
| 品牌色板/国旗/服务图标 | 版权与品牌风险 |

## 6. 技能使用（已装入 shitu）

已在 `D:\code\shitu\.dsh\skills\` 建 6 个 junction（实体在 `~/.dsh/skill-store`）：

| 技能 | 在 shitu 里的用法 |
| --- | --- |
| `flclash-design-audit` | 对 shitu 现有 UI 做一次取证审计，产出基线 |
| `design-token-extraction` | 把 `Theme.kt`/`Shapes.kt`/`Tone.kt` 抽成令牌表 |
| `component-contract-writing` | 给 `SectionCard`/`SwitchRow`/`ShituField` 写契约卡 |
| `settings-ia-mapping` | 盘点 `SettingsScreen.kt` 的 1112 行 |
| `motion-spec-extraction` | 统一 `AnimatedContent` 与开关动效 |
| `cross-platform-design-mapping` | 若将来做 Windows/桌面版 |

> 项目级技能只在本机、本项目的会话里注入；`.dsh/skills/` 已在该项目 `.gitignore`。

## 7. 风险

| 风险 | 缓解 |
| --- | --- |
| 改表面色后动态取色「发灰」 | 先在设置页灰度，保留旧色一键回退 |
| 56dp 行高使设置页变长 | 只对带副标题的行用 56，纯开关行保持 48 |
| Rail 与底部导航状态不同步 | 单一 `tab` 状态 + `rememberSaveable` |
| 深色 tone 覆写与 dynamicColor 冲突 | 用 `copy()` 只改 4 个角色，不改 seed |
