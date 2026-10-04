package com.landslide.shitu.ui.rules

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.landslide.shitu.core.RuleTemplate
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
    onAddFromTemplate: (RuleTemplate) -> Unit,
    /** 我自己的模板（在编辑页点「存为模板」存下来的） */
    templates: List<RuleTemplate>,
    onDeleteTemplate: (RuleTemplate) -> Unit,
    onEdit: (RuleEntity) -> Unit,
    onCopy: (RuleEntity) -> Unit,
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
    var showNew by remember { mutableStateOf(false) }
    LaunchedEffect(rules) {
        selected = selected intersect rules.map { it.id }.toSet()
    }
    val allSelected = rules.isNotEmpty() && selected.size == rules.size

    if (showNew) {
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("新建规则") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    NewRuleOption("空白规则", "源目录从 /sdcard 开始自己往下选") {
                        showNew = false
                        onAdd()
                    }
                    if (templates.isEmpty()) {
                        Text(
                            "还没有自己的模板。想让某条规则变成模板：点开那条规则的「编辑」，" +
                                "在编辑页下面点「存为模板」。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text("我的模板", style = MaterialTheme.typography.labelLarge)
                        templates.forEach { t ->
                            NewRuleOption(
                                title = t.name,
                                detail = t.detail(),
                                onDelete = {
                                    showNew = false
                                    onDeleteTemplate(t)
                                },
                            ) {
                                showNew = false
                                onAddFromTemplate(t)
                            }
                        }
                    }
                    Text(
                        "选完会进编辑页，还能随便改；点「保存并开始搬运」才真的建出来。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("取消") } },
        )
    }

    Scaffold(
        // 外层 Scaffold 已经处理过状态栏内边距，这里再叠一次会多出一条空白
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = { showNew = true }) {
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
                    if (compact) {
                        Text("简略：只看名称和开关", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("卡片", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(4.dp))
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
                }
            }
            HorizontalDivider()

            if (rules.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(24.dp)) {
                    Text("还没有规则。", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "点右下角「新建规则」：源目录从存储根目录 /sdcard 开始往下点，" +
                            "选到你想清空的图片文件夹（例如 Android/data/com.qidian.QDReader/files/...）；" +
                            "目标目录默认 /sdcard/DCIM。首次建议先用「复制」模式试一天。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                // 底部留出悬浮按钮的高度：否则最后一张卡片的按钮会被 FAB 盖住点不到
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    items(rules, key = { it.id }) { rule ->
                        RuleCard(
                            rule = rule,
                            compact = compact,
                            checked = rule.id in selected,
                            onCheckedChange = { on ->
                                selected = if (on) selected + rule.id else selected - rule.id
                            },
                            onEdit = { onEdit(rule) },
                            onCopy = { onCopy(rule) },
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

/** 「新建规则」弹窗里的一行：标题 + 说明，点哪都算选中；右侧可选的 ✕ = 删掉这条模板。 */
@Composable
private fun NewRuleOption(
    title: String,
    detail: String,
    onDelete: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Close, contentDescription = "删除模板", modifier = Modifier.size(18.dp))
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
    onCopy: () -> Unit,
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

    // 详细模式：整张卡片可点 = 进编辑（里面的按钮/开关/勾选框照常各自响应）
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
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
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // 5 个按钮要挤在一行里，内边距收紧一点；挤不下还能左右划（字体放大时）
                    val pad = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    TextButton(onClick = onRunNow, contentPadding = pad) { Text("立即运行") }
                    TextButton(onClick = onEdit, contentPadding = pad) { Text("编辑") }
                    TextButton(onClick = onCopy, contentPadding = pad) { Text("复制") }
                    TextButton(onClick = onUndo, contentPadding = pad) { Text("撤回") }
                    if (paused) TextButton(onClick = onResume, contentPadding = pad) { Text("恢复") }
                    TextButton(onClick = onDelete, contentPadding = pad) { Text("删除") }
                }
                // 右下角：这一条是否被勾选（勾选后用底部「开启 / 停止」批量操作）
                Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            }
        }
    }
}
