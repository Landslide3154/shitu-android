package com.landslide.shitu.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleListScreen(
    rules: List<RuleEntity>,
    state: ShizukuState,
    pauseAll: Boolean,
    movedTotal: Long,
    onSelfCheck: () -> Unit,
    onRequestPermission: () -> Unit,
    onTogglePauseAll: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onEdit: (RuleEntity) -> Unit,
    onToggleEnabled: (RuleEntity, Boolean) -> Unit,
    onRunNow: (RuleEntity) -> Unit,
    onUndo: (RuleEntity) -> Unit,
    onResume: (RuleEntity) -> Unit,
    onDelete: (RuleEntity) -> Unit,
) {
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd) { Text("新建规则") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 顶部状态条：第一行 Shizuku 状态与授权，第二行暂停全部与累计数（避免窄屏挤压）
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("累计已搬 $movedTotal 张", style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("暂停全部", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Switch(checked = pauseAll, onCheckedChange = onTogglePauseAll)
                    }
                }
            }
            HorizontalDivider()

            if (rules.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(24.dp)) {
                    Text("还没有规则。", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "新建一条规则：源目录（例如 /sdcard/Android/data/com.qidian.QDReader/files）" +
                            "里的图片会自动搬到目标目录。首次建议先用「复制」模式试一天。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(rules, key = { it.id }) { rule ->
                        RuleCard(
                            rule = rule,
                            onEdit = { onEdit(rule) },
                            onToggleEnabled = { onToggleEnabled(rule, it) },
                            onRunNow = { onRunNow(rule) },
                            onUndo = { onUndo(rule) },
                            onResume = { onResume(rule) },
                            onDelete = { onDelete(rule) },
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
    onEdit: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    onUndo: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    val paused = rule.state == RuleState.PAUSED_LOOP || rule.state == RuleState.PAUSED_ERROR
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(rule.name, style = MaterialTheme.typography.titleMedium)
                Switch(checked = rule.enabled, onCheckedChange = onToggleEnabled)
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
                "模式 ${Labels.mode(rule.mode)} · 间隔 ${rule.intervalMinutes} 分钟 · 状态 ${Labels.state(rule.state)}",
                style = MaterialTheme.typography.bodySmall,
            )
            rule.pauseReason?.let {
                Text("暂停原因：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Text(
                "上次 ${rule.lastRunAt?.let { timeFmt.format(Date(it)) } ?: "—"} · " +
                    "上次搬 ${rule.lastMoved} · 累计 ${rule.totalMoved} · 失败 ${rule.totalFailed}",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRunNow) { Text("立即运行") }
                TextButton(onClick = onEdit) { Text("编辑") }
                TextButton(onClick = onUndo) { Text("撤回") }
                if (paused) TextButton(onClick = onResume) { Text("恢复") }
                TextButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}
