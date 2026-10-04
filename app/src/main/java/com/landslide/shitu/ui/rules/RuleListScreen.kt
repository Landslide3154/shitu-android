package com.landslide.shitu.ui.rules

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.landslide.shitu.data.db.Labels
import com.landslide.shitu.data.db.RuleEntity
import com.landslide.shitu.data.db.RuleState
import com.landslide.shitu.shizuku.ShizukuState
import com.landslide.shitu.shizuku.display
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.US)

/**
 * 规则列表（规格 §10 页面 1）。
 *
 * 交互改为「勾选 + 批量开关」：每条规则右下角一个复选框，底部一排
 * 全选 / 开启 / 停止，勾几条就开几条、停几条。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleListScreen(
    rules: List<RuleEntity>,
    state: ShizukuState,
    movedTotal: Long,
    onSelfCheck: () -> Unit,
    onRequestPermission: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (RuleEntity) -> Unit,
    onRunNow: (RuleEntity) -> Unit,
    onUndo: (RuleEntity) -> Unit,
    onResume: (RuleEntity) -> Unit,
    onDelete: (RuleEntity) -> Unit,
    onToggleRuleEnabled: (RuleEntity, Boolean) -> Unit,
    onEnableSelected: (List<Long>) -> Unit,
    onDisableSelected: (List<Long>) -> Unit,
    /** 简略模式：每张卡片只显示规则名 + 开关 */
    compact: Boolean,
    onCompactChange: (Boolean) -> Unit,
) {
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    LaunchedEffect(rules) {
        selected = selected intersect rules.map { it.id }.toSet()
    }
    val allSelected = rules.isNotEmpty() && selected.size == rules.size

    Scaffold(
        // 外层 Scaffold 已经处理过状态栏内边距，这里再叠一次会多出一条空白
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "新建规则")
            }
        },
        bottomBar = {
            // 批量操作条放进 bottomBar：Scaffold 会把悬浮按钮自动抬高，避免遮住「停止」
            if (rules.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = allSelected,
                            onCheckedChange = { on ->
                                selected = if (on) rules.map { it.id }.toSet() else emptySet()
                            },
                        )
                        Text("全选", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(8.dp))
                        Text("已选 ${selected.size}", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = { onEnableSelected(selected.toList()) },
                            enabled = selected.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        ) { Text("开启") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { onDisableSelected(selected.toList()) },
                            enabled = selected.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        ) { Text("停止") }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 顶部状态条（尽量紧凑，给规则卡留空间）
            Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = onSelfCheck,
                        label = {
                            Text(
                                "Shizuku：${state.display()}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (state == ShizukuState.NO_PERMISSION) {
                        TextButton(onClick = onRequestPermission) {
                            Text("请求授权", maxLines = 1)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("累计 $movedTotal 张", style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("卡片：", style = MaterialTheme.typography.bodySmall)
                    FilterChip(
                        selected = compact,
                        onClick = { onCompactChange(true) },
                        label = { Text("简略") },
                    )
                    Spacer(Modifier.width(6.dp))
                    FilterChip(
                        selected = !compact,
                        onClick = { onCompactChange(false) },
                        label = { Text("详细") },
                    )
                    Spacer(Modifier.weight(1f))
                    if (compact) {
                        Text("简略模式：只显示名称和开关", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            HorizontalDivider()

            if (rules.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(24.dp)) {
                    Text("还没有规则。", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "点右下角「新建规则」：源目录从存储根目录 /sdcard 开始往下点，" +
                            "选到你想清空的图片文件夹（例如 Android/data/com.qidian.QDReader/files/...）；" +
                            "目标目录默认 /sdcard/DCIM/杂图。首次建议先用「复制」模式试一天。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(rules, key = { it.id }) { rule ->
                        RuleCard(
                            rule = rule,
                            compact = compact,
                            checked = rule.id in selected,
                            onCheckedChange = { on ->
                                selected = if (on) selected + rule.id else selected - rule.id
                            },
                            onEdit = { onEdit(rule) },
                            onRunNow = { onRunNow(rule) },
                            onUndo = { onUndo(rule) },
                            onResume = { onResume(rule) },
                            onDelete = { onDelete(rule) },
                            onToggleEnabled = { on -> onToggleRuleEnabled(rule, on) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleCard(
    rule: RuleEntity,
    compact: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRunNow: () -> Unit,
    onUndo: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
) {
    val paused = rule.state == RuleState.PAUSED_LOOP || rule.state == RuleState.PAUSED_ERROR

    // 简略模式：一行——勾选框 + 规则名 + 开关（点名字进编辑）
    if (compact) {
        Card(
            onClick = onEdit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 3.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = onCheckedChange)
                Text(
                    rule.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (paused) {
                    Text(
                        "已暂停",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Switch(checked = rule.enabled, onCheckedChange = onToggleEnabled)
            }
        }
        return
    }

    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    rule.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (rule.enabled) Labels.state(rule.state) else "已停止",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (rule.enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    )
                    // 每条规则自己的开关（也能用底部「全选 + 开启/停止」批量操作）
                    Switch(checked = rule.enabled, onCheckedChange = onToggleEnabled)
                }
            }
            Text(
                rule.srcPath,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "→ ${rule.dstPath}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "模式 ${Labels.mode(rule.mode)} · 间隔 ${rule.intervalMinutes} 分钟" +
                    " · 后缀 ${if (rule.suffix.isBlank()) "无" else rule.suffix}",
                style = MaterialTheme.typography.bodySmall,
            )
            rule.pauseReason?.let {
                Text(
                    "暂停原因：$it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                "上次 ${rule.lastRunAt?.let { timeFmt.format(Date(it)) } ?: "—"} · " +
                    "上次搬 ${rule.lastMoved} · 累计 ${rule.totalMoved} · 失败 ${rule.totalFailed}",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = onRunNow) { Text("立即运行") }
                    TextButton(onClick = onEdit) { Text("编辑") }
                    TextButton(onClick = onUndo) { Text("撤回") }
                    if (paused) TextButton(onClick = onResume) { Text("恢复") }
                    TextButton(onClick = onDelete) { Text("删除") }
                }
                // 右下角：这一条是否被勾选（勾选后用底部「开启 / 停止」批量操作）
                Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            }
        }
    }
}
