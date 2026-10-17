package com.landslide.shitu.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.landslide.shitu.R
import com.landslide.shitu.core.UpdateChecker
import com.landslide.shitu.data.Settings
import com.landslide.shitu.data.SettingsStore
import com.landslide.shitu.local.StorageAccess
import com.landslide.shitu.ui.components.SectionCard
import com.landslide.shitu.ui.components.SwitchRow
import com.landslide.shitu.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class UpdateDialog(val title: String, val body: String, val url: String?)

/** 设置页（规格 §8 全部参数 + §10 页面 4）。按主题分组，每组一张卡。 */
@Composable
fun SettingsScreen(
    settings: Settings,
    store: SettingsStore,
    onSelfCheck: () -> Unit,
    /** 「已经复制过的内容」账本里有多少条 */
    copiedCount: Int = 0,
    onClearCopied: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var askClearCopied by remember { mutableStateOf(false) }

    // 「所有文件访问」是在系统设置里打开的，回到 App 时要重新读一次，不能一直显示旧状态
    var allFilesGranted by remember { mutableStateOf(StorageAccess.isGranted(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allFilesGranted = StorageAccess.isGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 版本号从系统读，别再写死（0.1.0 那次就是写死后忘了改）
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateDialog by remember { mutableStateOf<UpdateDialog?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 十一个数字框太占地方，默认收起，点标题栏才展开
        SectionCard(
            title = stringResource(R.string.set_section_run_params),
            collapsible = true,
            initiallyExpanded = false,
        ) {
            Text(
                stringResource(R.string.set_run_params_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            intField(stringResource(R.string.set_scan_budget_sec), settings.scanBudgetSec, 5..120) {
                scope.launch { store.setScanBudgetSec(it) }
            }
            intField(stringResource(R.string.set_max_per_run), settings.maxPerRun, 1..500) {
                scope.launch { store.setMaxPerRun(it) }
            }
            intField(stringResource(R.string.set_max_per_minute), settings.maxPerMinute, 10..600) {
                scope.launch { store.setMaxPerMinute(it) }
            }
            intField(stringResource(R.string.set_max_run_sec), settings.maxRunSec, 10..300) {
                scope.launch { store.setMaxRunSec(it) }
            }
            intField(stringResource(R.string.set_stable_sec), settings.stableSec, 0..300) {
                scope.launch { store.setStableSec(it) }
            }
            intField(stringResource(R.string.set_loop_window_min), settings.loopWindowMin, 5..120) {
                scope.launch { store.setLoopWindowMin(it) }
            }
            intField(stringResource(R.string.set_loop_threshold), settings.loopThreshold, 2..10) {
                scope.launch { store.setLoopThreshold(it) }
            }
            intField(stringResource(R.string.set_fail_threshold), settings.failThreshold, 1..20) {
                scope.launch { store.setFailThreshold(it) }
            }
            intField(stringResource(R.string.set_low_battery_pct), settings.lowBatteryPct, 0..50) {
                scope.launch { store.setLowBatteryPct(it) }
            }
            intField(stringResource(R.string.set_log_keep_days), settings.logKeepDays, 1..365) {
                scope.launch { store.setLogKeepDays(it) }
            }
            intField(stringResource(R.string.set_log_keep_count), settings.logKeepCount, 1_000..200_000) {
                scope.launch { store.setLogKeepCount(it) }
            }
        }

        // 没有 Shizuku 时的后备通道：普通目录（Pictures/DCIM/Download…）靠这个权限搬
        SectionCard(title = stringResource(R.string.set_section_storage_access)) {
            Text(
                if (allFilesGranted) {
                    stringResource(R.string.set_storage_granted_desc)
                } else {
                    stringResource(R.string.set_storage_not_granted_desc)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { StorageAccess.openSettings(context) },
                enabled = !allFilesGranted,
            ) {
                Text(
                    if (allFilesGranted) {
                        stringResource(R.string.set_storage_granted)
                    } else {
                        stringResource(R.string.set_storage_open)
                    },
                )
            }
        }

        SectionCard(title = stringResource(R.string.set_section_detection_speed)) {
            SwitchRow(
                title = stringResource(R.string.set_fast_while_active),
                checked = settings.fastWhileActive,
                onCheckedChange = { scope.launch { store.setFastWhileActive(it) } },
            )
            intField(stringResource(R.string.set_fast_interval_sec), settings.fastIntervalSec, 3..60) {
                scope.launch { store.setFastIntervalSec(it) }
            }
            Text(
                stringResource(R.string.set_fast_while_active_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            SwitchRow(
                title = stringResource(R.string.set_screen_off_pause),
                checked = settings.screenOffPause,
                onCheckedChange = { scope.launch { store.setScreenOffPause(it) } },
            )
            Text(
                if (settings.screenOffPause) {
                    stringResource(R.string.set_screen_off_pause_on_desc)
                } else {
                    stringResource(R.string.set_screen_off_pause_off_desc)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            SwitchRow(
                title = stringResource(R.string.set_settle_detect),
                checked = settings.settleDetect,
                onCheckedChange = { scope.launch { store.setSettleDetect(it) } },
            )
            intField(stringResource(R.string.set_settle_gap_sec), settings.settleGapSec, 2..15) {
                scope.launch { store.setSettleGapSec(it) }
            }
            Text(
                stringResource(R.string.set_settle_detect_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = stringResource(R.string.set_section_switches)) {
            SwitchRow(
                title = stringResource(R.string.set_low_battery_pause),
                checked = settings.lowBatteryPause,
                onCheckedChange = { scope.launch { store.setLowBatteryPause(it) } },
            )
            SwitchRow(
                title = stringResource(R.string.set_fast_drain),
                checked = settings.fastDrain,
                onCheckedChange = { scope.launch { store.setFastDrain(it) } },
            )
            SwitchRow(
                title = stringResource(R.string.set_notify_enabled),
                checked = settings.notifyEnabled,
                onCheckedChange = { scope.launch { store.setNotifyEnabled(it) } },
            )
        }

        SectionCard(title = stringResource(R.string.set_section_appearance)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.set_theme), style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = settings.themeMode == ThemeMode.SYSTEM,
                        onClick = { scope.launch { store.setThemeMode(ThemeMode.SYSTEM) } },
                        label = { Text(stringResource(R.string.set_theme_system)) },
                    )
                    Spacer(Modifier.width(6.dp))
                    FilterChip(
                        selected = settings.themeMode == ThemeMode.LIGHT,
                        onClick = { scope.launch { store.setThemeMode(ThemeMode.LIGHT) } },
                        label = { Text(stringResource(R.string.set_theme_light)) },
                    )
                    Spacer(Modifier.width(6.dp))
                    FilterChip(
                        selected = settings.themeMode == ThemeMode.DARK,
                        onClick = { scope.launch { store.setThemeMode(ThemeMode.DARK) } },
                        label = { Text(stringResource(R.string.set_theme_dark)) },
                    )
                }
            }
            SwitchRow(
                title = stringResource(R.string.set_dynamic_color),
                checked = settings.dynamicColor,
                onCheckedChange = { scope.launch { store.setDynamicColor(it) } },
            )
            Text(
                stringResource(R.string.set_dynamic_color_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = stringResource(R.string.set_section_self_check)) {
            val lastSelfCheck = settings.lastSelfCheck
            Text(
                if (lastSelfCheck != null) {
                    stringResource(R.string.set_last_self_check, lastSelfCheck)
                } else {
                    stringResource(R.string.set_no_self_check)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onSelfCheck) { Text(stringResource(R.string.set_run_self_check)) }
        }

        // 复制模式的"这张图复制过没有"账本：清空后同样的图会再被复制一次进来
        SectionCard(title = stringResource(R.string.set_section_copy_history)) {
            Text(
                stringResource(R.string.set_copy_history_desc, copiedCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { askClearCopied = true },
                enabled = copiedCount > 0,
            ) { Text(stringResource(R.string.set_clear_copy_history_button, copiedCount)) }
        }

        if (askClearCopied) {
            AlertDialog(
                onDismissRequest = { askClearCopied = false },
                title = { Text(stringResource(R.string.set_clear_copy_history_title)) },
                text = {
                    Text(
                        stringResource(R.string.set_clear_copy_history_body),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        askClearCopied = false
                        onClearCopied()
                    }) { Text(stringResource(R.string.set_clear_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { askClearCopied = false }) { Text(stringResource(R.string.set_cancel)) }
                },
            )
        }

        SectionCard(title = stringResource(R.string.set_section_about)) {
            Text(
                stringResource(R.string.set_about_version, versionName),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "https://github.com/Landslide3154/shitu-android",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    checkingUpdate = true
                    scope.launch {
                        val info = withContext(Dispatchers.IO) {
                            runCatching { UpdateChecker.fetchLatest() }.getOrNull()
                        }
                        checkingUpdate = false
                        updateDialog = when {
                            info == null -> UpdateDialog(
                                context.getString(R.string.set_update_failed_title),
                                context.getString(R.string.set_update_failed_body),
                                null,
                            )
                            UpdateChecker.isNewer(versionName, info.version) -> UpdateDialog(
                                context.getString(R.string.set_update_available_title, info.version),
                                info.notes.ifBlank {
                                    context.getString(R.string.set_update_notes_fallback)
                                },
                                info.apkUrl ?: info.pageUrl,
                            )
                            else -> UpdateDialog(
                                context.getString(R.string.set_update_latest_title),
                                context.getString(R.string.set_update_latest_body, versionName, info.version),
                                null,
                            )
                        }
                    }
                },
                enabled = !checkingUpdate,
            ) {
                Text(
                    if (checkingUpdate) {
                        stringResource(R.string.set_checking_update)
                    } else {
                        stringResource(R.string.set_check_update)
                    },
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.set_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    updateDialog?.let { d ->
        AlertDialog(
            onDismissRequest = { updateDialog = null },
            title = { Text(d.title) },
            text = {
                Text(
                    d.body,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                if (d.url != null) {
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(d.url))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                        updateDialog = null
                    }) { Text(stringResource(R.string.set_go_download)) }
                } else {
                    TextButton(onClick = { updateDialog = null }) { Text(stringResource(R.string.set_got_it)) }
                }
            },
            dismissButton = if (d.url != null) {
                { TextButton(onClick = { updateDialog = null }) { Text(stringResource(R.string.set_later)) } }
            } else {
                null
            },
        )
    }
}

@Composable
private fun intField(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t.filter { it.isDigit() }.take(6)
            text.toIntOrNull()?.let { if (it in range) onChange(it) }
        },
        label = { Text(stringResource(R.string.set_field_range, label, range.first, range.last)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}
