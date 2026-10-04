package com.landslide.shitu.ui.logs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.landslide.shitu.data.db.Labels
import com.landslide.shitu.data.db.LogEntity
import com.landslide.shitu.data.db.LogResult
import com.landslide.shitu.data.db.RuleEntity
import com.landslide.shitu.ui.components.SectionCard
import com.landslide.shitu.ui.components.StatusPill
import com.landslide.shitu.ui.status.logTone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

/** 日志页（规格 §10 页面 3）：筛选 + 列表 + 导出 CSV。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    logs: List<LogEntity>,
    rules: List<RuleEntity>,
    onExport: () -> Unit,
    onClearAll: () -> Unit,
) {
    var keyword by remember { mutableStateOf("") }
    var resultFilter by remember { mutableStateOf<LogResult?>(null) }
    var ruleFilter by remember { mutableStateOf<Long?>(null) }
    var askClear by remember { mutableStateOf(false) }

    if (askClear) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            title = { Text("清空全部日志？") },
            text = {
                Text(
                    "会把 ${logs.size} 条日志记录都删掉。只删记录——规则、已经搬过去的图片文件都不受影响。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        askClear = false
                        onClearAll()
                    },
                ) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { askClear = false }) { Text("取消") }
            },
        )
    }

    val shown = logs.filter { l ->
        (resultFilter == null || l.result == resultFilter) &&
            (ruleFilter == null || l.ruleId == ruleFilter) &&
            (keyword.isBlank() ||
                (l.srcPath ?: "").contains(keyword, ignoreCase = true) ||
                (l.dstPath ?: "").contains(keyword, ignoreCase = true))
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("日志", style = MaterialTheme.typography.titleLarge)
                Text(
                    "显示 ${shown.size} 条 / 共 ${logs.size} 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { askClear = true },
                    enabled = logs.isNotEmpty(),
                ) { Text("全部清除") }
                TextButton(onClick = onExport) { Text("导出 CSV") }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 搜索 + 两级筛选收进一张卡，别把列表挤到屏幕外
        SectionCard(title = "筛选") {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                label = { Text("按文件路径搜索") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = resultFilter == null,
                    onClick = { resultFilter = null },
                    label = { Text("全部结果") },
                )
                LogResult.entries.forEach { r ->
                    FilterChip(
                        selected = resultFilter == r,
                        onClick = { resultFilter = if (resultFilter == r) null else r },
                        label = { Text(Labels.result(r)) },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = ruleFilter == null,
                    onClick = { ruleFilter = null },
                    label = { Text("全部规则") },
                )
                rules.forEach { r ->
                    FilterChip(
                        selected = ruleFilter == r.id,
                        onClick = { ruleFilter = if (ruleFilter == r.id) null else r.id },
                        label = { Text(r.name) },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    "导出的 CSV 含完整文件路径，分享前留意隐私。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (shown.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("没有符合条件的日志", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (logs.isEmpty()) "还没搬过东西；去规则页点「立即运行」试试" else "换个筛选条件看看",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(shown, key = { it.id }) { l ->
                    // 新日志插进来 / 清空时其余条目滑一下，不要瞬移
                    LogRow(l, Modifier.animateItem())
                }
            }
        }
    }
}

@Composable
private fun LogRow(l: LogEntity, modifier: Modifier = Modifier) {
    val tone = logTone(l.result)
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StatusPill(Labels.result(l.result), tone)
                Spacer(Modifier.weight(1f))
                Text(
                    timeFmt.format(Date(l.ts)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    " · ${l.durationMs} ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            l.srcPath?.let {
                Text(
                    "源  $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            l.dstPath?.let {
                Text(
                    "目标  $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            l.message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (l.result == LogResult.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
