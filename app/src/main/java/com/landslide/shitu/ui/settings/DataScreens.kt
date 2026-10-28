package com.landslide.shitu.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.landslide.shitu.R
import com.landslide.shitu.ShituApp
import com.landslide.shitu.core.UpdateChecker
import com.landslide.shitu.data.BackupIo
import com.landslide.shitu.data.Settings
import com.landslide.shitu.data.db.CopiedEntity
import com.landslide.shitu.ui.components.CommonDialog
import com.landslide.shitu.ui.components.FileThumbnail
import com.landslide.shitu.ui.components.ListItem
import com.landslide.shitu.ui.components.ListItemAction
import com.landslide.shitu.ui.components.ListItemNext
import com.landslide.shitu.ui.components.SelectionMark
import com.landslide.shitu.ui.components.Section
import com.landslide.shitu.ui.components.ShituButton
import com.landslide.shitu.ui.components.ShituToolbar
import com.landslide.shitu.ui.design.AppType
import com.landslide.shitu.ui.design.ShituIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 复制记录页（FlClash 的列表编辑页）：多选后只清选中的。 */
@Composable
fun CopyHistoryScreen(
    records: List<CopiedEntity>,
    total: Int,
    onClearSelected: (List<String>) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
) {
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var askClearAll by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    Column(Modifier.fillMaxSize()) {
        ShituToolbar(stringResource(R.string.set_section_copy_history), onBack = onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "共 $total 条。勾选后只清掉选中的记录；清空后这些图会被当成没复制过，下次遇到会再复制一份。",
                    style = AppType.Footnote.style(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(records, key = { it.fingerprint }) { record ->
                val checked = record.fingerprint in selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SelectionMark(
                        selected = checked,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    FileThumbnail(path = record.dstPath)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            record.dstPath ?: "（位置未知）",
                            style = AppType.Subheadline.style(),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                        )
                        Text(
                            fmt.format(Date(record.firstSeenAt)),
                            style = AppType.Caption1.style(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = {
                            selected = if (checked) {
                                selected - record.fingerprint
                            } else {
                                selected + record.fingerprint
                            }
                        },
                    ) {
                        Text(if (checked) "取消" else "选择")
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShituButton(
                text = if (selected.isEmpty()) "清除选中" else "清除选中（${selected.size}）",
                onClick = {
                    onClearSelected(selected.toList())
                    selected = emptySet()
                },
                enabled = selected.isNotEmpty(),
                primary = false,
                modifier = Modifier.weight(1f),
            )
            ShituButton(
                text = stringResource(R.string.set_clear_all),
                onClick = { askClearAll = true },
                primary = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (askClearAll) {
        CommonDialog(
            title = stringResource(R.string.set_clear_copy_history_title),
            body = stringResource(R.string.set_clear_copy_history_body_count, total),
            confirmLabel = stringResource(R.string.set_clear_confirm),
            dismissLabel = stringResource(R.string.set_cancel),
            destructive = true,
            onDismiss = { askClearAll = false },
            onConfirm = {
                askClearAll = false
                selected = emptySet()
                onClearAll()
            },
        )
    }
}

/** 备份与恢复页：导出/导入 + 导入前预览规则条数。 */
@Composable
fun BackupSettingsScreen(
    app: ShituApp,
    rulesCount: Int,
    onRefresh: () -> Unit,
    onSnackbar: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingText by remember { mutableStateOf<String?>(null) }
    var pendingCount by remember { mutableStateOf(0) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                    }.getOrNull()
                }
                if (text.isNullOrBlank()) {
                    onSnackbar(context.getString(R.string.set_rules_import_failed))
                } else {
                    pendingText = text
                    pendingCount = runCatching { BackupIo.decode(text).rules.size }.getOrDefault(0)
                }
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        ShituToolbar(stringResource(R.string.set_section_backup), onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Section {
                    ListItem(
                        title = stringResource(R.string.set_rules_count, rulesCount),
                        subtitle = stringResource(R.string.set_backup_desc2),
                        divider = false,
                    )
                }
            }
            item {
                Section {
                    ListItemAction(
                        title = stringResource(R.string.set_backup_export),
                        onClick = {
                            scope.launch {
                                onSnackbar(context.getString(R.string.app_exporting))
                                val path = runCatching { app.exportBackup() }
                                    .getOrElse { context.getString(R.string.app_export_failed, it.message) }
                                onSnackbar(context.getString(R.string.app_rules_exported, path))
                            }
                        },
                        leading = { androidx.compose.material3.Icon(ShituIcons.Upload, null) },
                    )
                    ListItemAction(
                        title = stringResource(R.string.set_backup_import),
                        onClick = { importLauncher.launch(arrayOf("text/plain")) },
                        leading = { androidx.compose.material3.Icon(ShituIcons.Download, null) },
                        divider = false,
                    )
                }
            }
        }
    }
    pendingText?.let { text ->
        CommonDialog(
            title = stringResource(R.string.set_restore_confirm_title),
            body = stringResource(R.string.set_restore_confirm_body, pendingCount),
            confirmLabel = stringResource(R.string.set_backup_import),
            dismissLabel = stringResource(R.string.set_cancel),
            destructive = true,
            onDismiss = { pendingText = null },
            onConfirm = {
                pendingText = null
                scope.launch {
                    val n = runCatching { app.importBackup(text) }.getOrDefault(0)
                    onRefresh()
                    onSnackbar(context.getString(R.string.set_restore_done, n))
                }
            },
        )
    }
}

/** 关于页：版本 / 仓库 / 更新 / 许可 / 隐私 / 自检。 */
@Composable
fun AboutSettingsScreen(
    app: ShituApp,
    settings: Settings,
    onSelfCheck: () -> Unit,
    onSnackbar: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<com.landslide.shitu.core.UpdateInfo?>(null) }
    var failed by remember { mutableStateOf(false) }
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }
    Column(Modifier.fillMaxSize()) {
        ShituToolbar(stringResource(R.string.set_section_about), onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Section {
                    ListItem(
                        title = "拾图 Shitu $version",
                        subtitle = "GPL-3.0 · github.com/Landslide3154/shitu-android",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://github.com/Landslide3154/shitu-android"),
                                    ),
                                )
                            }
                        },
                        divider = false,
                    )
                }
            }
            item {
                Section("更新") {
                    ListItemAction(
                        title = if (checking) "正在检查…" else stringResource(R.string.set_check_update),
                        onClick = {
                            if (!checking) {
                                checking = true
                                failed = false
                                scope.launch {
                                    val info = withContext(Dispatchers.IO) {
                                        runCatching { UpdateChecker.fetchLatest() }.getOrNull()
                                    }
                                    checking = false
                                    if (info == null) failed = true else update = info
                                }
                            }
                        },
                        divider = false,
                    )
                }
            }
            item {
                Section("自检") {
                    ListItemAction(
                        title = stringResource(R.string.set_run_self_check),
                        onClick = onSelfCheck,
                        divider = false,
                    )
                }
            }
            item {
                Section("其他") {
                    ListItemAction(
                        title = "开源许可",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://www.gnu.org/licenses/gpl-3.0.html"),
                                    ),
                                )
                            }
                        },
                    )
                    ListItem(
                        title = "隐私",
                        subtitle = stringResource(R.string.set_privacy_note),
                        divider = false,
                    )
                }
            }
        }
    }
    if (failed) {
        CommonDialog(
            title = stringResource(R.string.set_update_failed_title),
            body = stringResource(R.string.set_update_failed_body),
            confirmLabel = stringResource(R.string.set_got_it),
            onDismiss = { failed = false },
            onConfirm = { failed = false },
        )
    }
    update?.let { info ->
        val newer = UpdateChecker.isNewer(version, info.version)
        CommonDialog(
            title = if (newer) {
                context.getString(R.string.set_update_available_title, info.version)
            } else {
                context.getString(R.string.set_update_latest_title)
            },
            body = if (newer) info.notes.ifBlank { context.getString(R.string.set_update_notes_fallback) } else {
                context.getString(R.string.set_update_latest_body, version, info.version)
            },
            confirmLabel = if (newer) stringResource(R.string.set_go_download) else stringResource(R.string.set_got_it),
            dismissLabel = stringResource(R.string.set_later),
            onDismiss = { update = null },
            onConfirm = {
                if (newer) {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(info.pageUrl)),
                        )
                    }
                }
                update = null
            },
        )
    }
}
