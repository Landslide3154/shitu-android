package com.landslide.shitu.ui.logs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.landslide.shitu.data.db.Labels
import com.landslide.shitu.data.db.LogEntity
import com.landslide.shitu.data.db.LogResult
import com.landslide.shitu.data.db.RuleEntity
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
) {
    var keyword by remember { mutableStateOf("") }
    var resultFilter by remember { mutableStateOf<LogResult?>(null) }
    var ruleFilter by remember { mutableStateOf<Long?>(null) }

    val shown = logs.filter { l ->
        (resultFilter == null || l.result == resultFilter) &&
            (ruleFilter == null || l.ruleId == ruleFilter) &&
            (keyword.isBlank() ||
                (l.srcPath ?: "").contains(keyword, ignoreCase = true) ||
                (l.dstPath ?: "").contains(keyword, ignoreCase = true))
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("日志（${shown.size} / ${logs.size}）", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onExport) { Text("导出 CSV") }
        }
        Text(
            "导出文件包含完整文件路径，请留意隐私。",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it },
            label = { Text("按路径搜索") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
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

        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.id }) { l ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            "${timeFmt.format(Date(l.ts))} · ${Labels.result(l.result)} · ${l.durationMs} ms",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        l.srcPath?.let {
                            Text(
                                "源：$it",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        l.dstPath?.let {
                            Text(
                                "目标：$it",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        l.message?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
