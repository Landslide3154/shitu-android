package com.landslide.shitu.ui.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.landslide.shitu.R
import com.landslide.shitu.data.db.Labels
import com.landslide.shitu.data.db.LogEntity
import com.landslide.shitu.data.db.LogResult
import com.landslide.shitu.data.db.RuleEntity
import com.landslide.shitu.ui.components.StatusPill
import com.landslide.shitu.ui.status.logTone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

/**
 * 日志页（规格 §10 页面 3）：搜索 + 列表 + 导出 CSV。
 *
 * 「结果 / 规则」这两排筛选以前常驻在搜索框下面，把日志列表挤得只剩几行；
 * 现在收进右上角的「筛选」按钮里，只有真的要看筛选时才弹出来。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogScreen(
    logs: List<LogEntity>,
    rules: List<RuleEntity>,
    onExport: () -> Unit,
    onClearAll: () -> Unit,
) {
    val context = LocalContext.current
    var keyword by remember { mutableStateOf("") }
    var resultFilter by remember { mutableStateOf<LogResult?>(null) }
    var ruleFilter by remember { mutableStateOf<Long?>(null) }
    var askClear by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val activeFilters =
        (if (resultFilter != null) 1 else 0) + (if (ruleFilter != null) 1 else 0)
    val ruleName = rules.firstOrNull { it.id == ruleFilter }?.name

    if (askClear) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            title = { Text(stringResource(R.string.logs_clear_dialog_title)) },
            text = {
                Text(
                    stringResource(R.string.logs_clear_dialog_body, logs.size),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        askClear = false
                        onClearAll()
                    },
                ) { Text(stringResource(R.string.logs_clear_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { askClear = false }) { Text(stringResource(R.string.logs_cancel)) }
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.logs_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.logs_count, shown.size, logs.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 右侧放筛选/导出/清除：右手单手点得到，也不占列表的纵向空间
            TextButton(onClick = { showFilters = true }) {
                Text(
                    if (activeFilters > 0) {
                        stringResource(R.string.logs_filter_count, activeFilters)
                    } else {
                        stringResource(R.string.logs_filter)
                    },
                )
            }
            TextButton(onClick = onExport) { Text(stringResource(R.string.logs_export)) }
            TextButton(
                onClick = { askClear = true },
                enabled = logs.isNotEmpty(),
            ) { Text(stringResource(R.string.logs_clear)) }
        }

        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it },
            label = { Text(stringResource(R.string.logs_search_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // 有筛选时给一行小字兜底：看得出现在筛的是哪几条，随时能一键清掉
        if (activeFilters > 0) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val filteredPrefix = stringResource(R.string.logs_filtered)
                val deletedRule = stringResource(R.string.logs_rule_deleted)
                val resultPart = if (resultFilter != null) {
                    stringResource(R.string.logs_filtered_result, Labels.result(context, resultFilter!!))
                } else {
                    ""
                }
                val rulePart = if (ruleFilter != null) {
                    stringResource(R.string.logs_filtered_rule, ruleName ?: deletedRule)
                } else {
                    ""
                }
                Text(
                    buildString {
                        append(filteredPrefix)
                        append(resultPart)
                        if (resultPart.isNotEmpty() && rulePart.isNotEmpty()) append(" · ")
                        append(rulePart)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    resultFilter = null
                    ruleFilter = null
                }) { Text(stringResource(R.string.logs_clear_filters)) }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (shown.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.logs_empty_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (logs.isEmpty()) {
                        stringResource(R.string.logs_empty_no_logs)
                    } else {
                        stringResource(R.string.logs_empty_no_match)
                    },
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

    if (showFilters) {
        ModalBottomSheet(
            onDismissRequest = { showFilters = false },
            sheetState = sheetState,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.logs_filter), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            resultFilter = null
                            ruleFilter = null
                        },
                        enabled = activeFilters > 0,
                    ) { Text(stringResource(R.string.logs_reset)) }
                }

                Text(
                    stringResource(R.string.logs_by_result),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = resultFilter == null,
                        onClick = { resultFilter = null },
                        label = { Text(stringResource(R.string.logs_all_results)) },
                    )
                    LogResult.entries.forEach { r ->
                        FilterChip(
                            selected = resultFilter == r,
                            onClick = { resultFilter = if (resultFilter == r) null else r },
                            label = { Text(Labels.result(context, r)) },
                        )
                    }
                }

                Text(
                    stringResource(R.string.logs_by_rule),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                )
                val unnamedRule = stringResource(R.string.logs_unnamed_rule)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = ruleFilter == null,
                        onClick = { ruleFilter = null },
                        label = { Text(stringResource(R.string.logs_all_rules)) },
                    )
                    rules.forEach { r ->
                        FilterChip(
                            selected = ruleFilter == r.id,
                            onClick = { ruleFilter = if (ruleFilter == r.id) null else r.id },
                            label = { Text(r.name.ifBlank { unnamedRule }) },
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        stringResource(R.string.logs_csv_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }

                Button(
                    onClick = { showFilters = false },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { Text(stringResource(R.string.logs_done)) }
            }
        }
    }
}

@Composable
private fun LogRow(l: LogEntity, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val tone = logTone(l.result)
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StatusPill(Labels.result(context, l.result), tone)
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
                    stringResource(R.string.logs_src, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            l.dstPath?.let {
                Text(
                    stringResource(R.string.logs_dst, it),
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
