# 拾图 UI / 操作逻辑全面优化（0.8.0）— 设计

日期：2026-10-04 · 状态：用户已批准（方案 A + 规则卡「主按钮 + ⋮ 溢出菜单」）

## 目标

用户诉求（m02767）：「shizuku 就绪和未就绪状态要有区分；对整个 app UI 和操作逻辑进行全面优化，原则为 易用、易识别、美观。」

三条原则落到可检验的规则：

- **易识别**：任何状态都必须同时用「颜色 + 图标 + 文字」表达，不靠单一灰色文字。
- **易用**：主操作一眼可见、不多点；破坏性操作要二次确认；不能用的状态要给出下一步动作按钮。
- **美观**：统一间距（页面 16dp / 卡片内 12~14dp / 元素间 8dp）、统一圆角（卡片 16dp、块 12dp）、统一字号层级（titleMedium 分组标题 / titleSmall 卡片标题 / bodySmall 说明）。

## 现状问题（调研结论）

| 位置 | 问题 |
|---|---|
| 规则页顶部 | Shizuku 状态是 `AssistChip`，就绪/未就绪只有文字差异，无色无图标；只有 `NO_PERMISSION` 才给按钮，未安装/未运行不给任何操作入口 |
| 规则页顶部 | 「累计 N 张」是灰小字；「卡片 简略/详细」占一整行并配两行说明 |
| 规则卡 | 每条规则状态（待命/运行中/已停止/失败）都是灰字，无颜色无图标 |
| 规则卡 | 详细模式一排 5~6 个文字按钮，拥挤；**删除无二次确认**且紧邻「撤回」 |
| 规则页 | 空状态是一段朴素文字，没有主行动按钮 |
| 日志页 | 搜索框 + 两排 chip 占上半屏；日志行内时间/结果/路径同色，无状态色 |
| 设置页 | 12 个数字输入框平铺，无分组，长说明堆叠 |
| 编辑页 | 模式选择是手画按钮行；冲突警告是红字 + △ 符号 |

## 设计

### 1. 状态语义（新增 `ui/theme/Tone.kt` + `ui/components/Status.kt`）

```
enum class Tone { OK, BUSY, WARN, ERROR, IDLE }
```

| Tone | 语义 | 颜色来源 |
|---|---|---|
| OK | 一切正常 / 搬运成功 | 自定义绿（浅 `#1B7F4B` / 深 `#6FD79B`，不用主题绿以免 Material You 取到偏色） |
| BUSY | 正在运行 / 正在自检 | `colorScheme.primary` |
| WARN | 需要用户处理（未授权、已暂停、跳过） | `colorScheme.tertiary` |
| ERROR | 失败 | `colorScheme.error` |
| IDLE | 待命 / 已停止 | `colorScheme.outline` |

组件：`StatusPill(text, tone, icon)`（圆点 + 文字的胶囊）、`StatusDot(tone)`、`SectionCard(title, icon) { }`（分组卡片）、`BannerCard(tone, icon, title, body, action) { }`（顶部状态卡/警告块）。

状态映射写成**纯函数**放 `ui/status/StatusMap.kt`，配单测：

- `fun shizukuTone(state: ShizukuState): Tone`
- `fun shizukuTitle(state: ShizukuState): String`
- `fun shizukuAdvice(state: ShizukuState): String`（下一步该干嘛）
- `fun shizukuActionLabel(state: ShizukuState): String?`（按钮文案；READY 时为 null）
- `fun ruleTone(rule: RuleEntity): Tone` / `fun ruleStatusText(rule: RuleEntity): String`

### 2. 规则页

- 顶部改成 **状态卡**：`BannerCard` 用 tone 底色 + 图标 + 一句话状态 + 右侧动作按钮（`打开 Shizuku` / `请求授权` / `重试`），READY 时收成一条细的绿色状态条（省空间），点整块 = 跑自检。
- 「累计 N 张」做成小统计块（图标 + 数字 + 单位），与状态卡同一行或下一行右侧。
- 「简略/详细」改成右上角一个 `IconToggle`（列表/详细图标），去掉「卡片」标签与说明文字；切换后的提示用 snackbar。
- 规则卡：标题行左侧加 `StatusDot`，行尾 `StatusPill`；详细模式按钮区改为 `立即运行`（FilledTonal）+ `编辑`（Text）+ `⋮`（IconButton，DropdownMenu 里放 复制 / 撤回 / 恢复（仅暂停时）/ 删除（红字））。
- 删除：`AlertDialog` 二次确认（标题「删除「X」？」、正文说明不影响已搬走的图、确认按钮红字）。
- 空状态：大图标 + 「还没有规则」+ 一句引导 + 主按钮「新建第一条规则」。
- 批量条：勾选后出现「已选 N 条」+ 开启/停止；未勾选时按钮禁用并有说明。

### 3. 设置页

分组卡片（每张 `SectionCard`）：运行参数 / 检测速度 / 开关 / 外观 / 自检 / 关于。
不常用数字参数收进「高级参数」可展开区（`运行参数` 卡片内 `AnimatedVisibility`），常用项（单轮最多搬运、间隔相关）留在外面。

### 4. 日志页

一行筛选（结果 FilterChip 组纵向滚动 → 保留但压成一行 + 规则下拉 `ExposedDropdownMenuBox`）+ 搜索框收进可展开；每条日志：左侧 4dp tone 竖条 + 结果彩色标签 + 时间；路径仍两行截断。空状态给「还没有日志」。

### 5. 编辑页

冲突警告改成 `BannerCard(tone = WARN/ERROR)`；模式选择改用 M3 `SegmentedButton`；底部保持「存为模板 / 取消 / 保存并开始搬运」，主按钮 `Button`。

## 实施顺序

1. Tone + 组件 + StatusMap（含单测）
2. 规则页（状态卡、规则卡、溢出菜单、删除确认、空状态、批量条）
3. 设置页分组
4. 日志页
5. 编辑页
6. 真机逐屏截图验证 → 0.8.0 发版（本机流程）

## 不做（YAGNI）

- 不改三个标签页 + 编辑页的导航结构（用户选方案 A）
- 不引入新依赖、不换主题库
- 不做动画编排、不做自定义字体
