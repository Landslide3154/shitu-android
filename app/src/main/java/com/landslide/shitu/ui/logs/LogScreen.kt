package com.landslide.shitu.ui.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.landslide.shitu.R
import com.landslide.shitu.data.db.Labels
import com.landslide.shitu.data.db.LogEntity
import com.landslide.shitu.data.db.LogResult
import com.landslide.shitu.data.db.RuleEntity
import com.landslide.shitu.ui.components.AppSheet
import com.landslide.shitu.ui.components.CommonChip
import com.landslide.shitu.ui.components.DangerConfirmDialog
import com.landslide.shitu.ui.components.EmptyState
import com.landslide.shitu.ui.components.FileThumbnail
import com.landslide.shitu.ui.components.SearchField
import com.landslide.shitu.ui.components.ShituButton
import com.landslide.shitu.ui.components.ShituToolbar
import com.landslide.shitu.ui.design.AppCorner
import com.landslide.shitu.ui.design.AppType
import com.landslide.shitu.ui.design.ShituIcons
import com.landslide.shitu.ui.design.SurfaceLevel
import com.landslide.shitu.ui.design.floatingColor
import com.landslide.shitu.ui.design.squircle
import com.landslide.shitu.ui.design.success
import com.landslide.shitu.ui.design.destructive
import com.landslide.shitu.ui.status.logTone
import com.landslide.shitu.ui.theme.Tone
import com.landslide.shitu.ui.theme.color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

/**
 * 日志页（FlClash 的记录列表）：工具栏 + 搜索 + 行卡 + 筛选 sheet。
 * 筛选项收进 sheet，列表本身只保留结果色小胶囊，避免把日志挤成几行。
 */
@OptIn(ExperimentalLayoutApi::class)
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
    var detail by remember { mutableStateOf<LogEntity?>(null) }
    var paused by remember { mutableStateOf(false) }
    var frozen by remember { mutableStateOf<List<LogEntity>?>(null) }
    val ruleName = rules.firstOrNull { it.id == ruleFilter }?.name
    val activeFilters = (if (resultFilter != null) 1 else 0) + (if (ruleFilter != null) 1 else 0)

    val shown = logs.filter { l ->
        (resultFilter == null || l.result == resultFilter) &&
            (ruleFilter == null || l.ruleId == ruleFilter) &&
            (keyword.isBlank() ||
                (l.srcPath ?: "").contains(keyword, ignoreCase = true) ||
                (l.dstPath ?: "").contains(keyword, ignoreCase = true))
    }
    val displayed = if (paused) frozen ?: shown else shown

    Column(Modifier.fillMaxSize()) {
        ShituToolbar(title = stringResource(R.string.logs_title)) {
            IconButton(
                onClick = {
                    paused = !paused
                    frozen = if (paused) shown else null
                },
            ) {
                Icon(
                    if (paused) ShituIcons.Play else ShituIcons.Pause,
                    contentDescription = if (paused) "继续刷新" else "暂停刷新",
                    modifier = Modifier.size(24.dp),
                    tint = if (paused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { showFilters = true }) {
                Icon(
                    ShituIcons.Filter,
                    contentDescription = stringResource(R.string.logs_filter),
                    modifier = Modifier.size(24.dp),
                )
            }
            IconButton(onClick = onExport) {
                Icon(ShituIcons.Download, null, modifier = Modifier.size(24.dp))
            }
            IconButton(onClick = { askClear = true }, enabled = logs.isNotEmpty()) {
                Icon(ShituIcons.Trash, null, modifier = Modifier.size(24.dp))
            }
        }
        Text(
            stringResource(R.string.logs_count, displayed.size, logs.size) + if (paused) " · 已暂停" else "",
            style = AppType.Footnote.style(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        SearchField(
            value = keyword,
            onValueChange = { keyword = it },
            placeholder = "搜索源/目标路径",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (activeFilters > 0) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                resultFilter?.let {
                    CommonChip(Labels.result(context, it), selected = true, onClick = { resultFilter = null })
                }
                ruleFilter?.let {
                    CommonChip(ruleName ?: stringResource(R.string.logs_unnamed_rule), selected = true, onClick = { ruleFilter = null })
                }
            }
        }
        if (displayed.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = stringResource(R.string.logs_empty_title),
                    body = if (logs.isEmpty()) {
                        stringResource(R.string.logs_empty_no_logs)
                    } else {
                        stringResource(R.string.logs_empty_no_match)
                    },
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(displayed, key = { it.id }) { log ->
                    LogCard(
                        log = log,
                        onClick = { detail = log },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (showFilters) {
        AppSheet(onDismiss = { showFilters = false }, title = stringResource(R.string.logs_filter)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    stringResource(R.string.logs_by_result),
                    style = AppType.Headline.style(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CommonChip(
                        stringResource(R.string.logs_all_results),
                        selected = resultFilter == null,
                        onClick = { resultFilter = null },
                    )
                    LogResult.entries.forEach { result ->
                        CommonChip(
                            Labels.result(context, result),
                            selected = resultFilter == result,
                            onClick = { resultFilter = if (resultFilter == result) null else result },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.logs_by_rule),
                    style = AppType.Headline.style(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CommonChip(
                        stringResource(R.string.logs_all_rules),
                        selected = ruleFilter == null,
                        onClick = { ruleFilter = null },
                    )
                    rules.forEach { rule ->
                        CommonChip(
                            rule.name.ifBlank { stringResource(R.string.logs_unnamed_rule) },
                            selected = ruleFilter == rule.id,
                            onClick = { ruleFilter = if (ruleFilter == rule.id) null else rule.id },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                ShituButton(
                    text = stringResource(R.string.logs_done),
                    onClick = { showFilters = false },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    detail?.let { log ->
        AppSheet(onDismiss = { detail = null }, title = "日志详情") {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FileThumbnail(path = log.dstPath ?: log.srcPath)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            Labels.result(context, log.result),
                            style = AppType.Headline.style(emphasized = true),
                            color = logTone(log.result).color(),
                        )
                        Text(
                            timeFmt.format(Date(log.ts)) + " · ${log.durationMs} ms",
                            style = AppType.Footnote.style(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                DetailLine("来源", log.srcPath ?: "—")
                DetailLine("目标", log.dstPath ?: "—")
                log.message?.let { DetailLine("说明", it) }
            }
        }
    }

    if (askClear) {
        DangerConfirmDialog(
            title = stringResource(R.string.logs_clear_dialog_title),
            body = stringResource(R.string.logs_clear_dialog_body, logs.size),
            confirmLabel = stringResource(R.string.logs_clear_confirm),
            dismissLabel = stringResource(R.string.logs_cancel),
            onConfirm = {
                askClear = false
                onClearAll()
            },
            onDismiss = { askClear = false },
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, style = AppType.Caption1.style(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = AppType.Subheadline.style(), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun LogCard(log: LogEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val tone = logTone(log.result)
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = squircle(AppCorner.md),
        color = SurfaceLevel.CARD.colorOf(scheme),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            FileThumbnail(path = log.dstPath ?: log.srcPath)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LogChip(log.result, tone)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${timeFmt.format(Date(log.ts))} · ${log.durationMs} ms",
                        style = AppType.Caption1.style(),
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    log.srcPath ?: "—",
                    style = AppType.Footnote.style(),
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "→ " + (log.dstPath ?: "—"),
                    style = AppType.Footnote.style(),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LogChip(result: LogResult, tone: Tone) {
    val color = tone.color()
    Row(
        Modifier
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Labels.result(LocalContext.current, result),
            style = AppType.Caption1.style(emphasized = true),
            color = color,
        )
    }
}
