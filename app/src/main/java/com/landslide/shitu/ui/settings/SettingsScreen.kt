package com.landslide.shitu.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.landslide.shitu.R
import com.landslide.shitu.core.UpdateChecker
import com.landslide.shitu.core.UpdateInstaller
import com.landslide.shitu.data.AppLocale
import com.landslide.shitu.data.Settings
import com.landslide.shitu.data.SettingsStore
import com.landslide.shitu.local.StorageAccess
import com.landslide.shitu.ui.components.SectionCard
import com.landslide.shitu.ui.components.SwitchRow
import com.landslide.shitu.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val REPO_URL = "https://github.com/Landslide3154/shitu-android"

/** 检查更新的弹窗内容；[apkUrl] 不为空时才有「下载并安装」。 */
private data class UpdateDialog(
    val title: String,
    val body: String,
    val pageUrl: String?,
    val apkUrl: String? = null,
    val version: String = "",
)

/**
 * 设置页。卡片顺序固定为：语言 → 存储访问 → 运行条件 → 检测速度 → 开关 → 外观 →
 * 复制记录 → 规则管理 → 自检 → 关于。
 *
 * 常用项直接露在卡片里，十几根数字输入框收进「运行参数」，默认不展开。
 */
@Composable
fun SettingsScreen(
    settings: Settings,
    store: SettingsStore,
    onSelfCheck: () -> Unit,
    /** 「已经复制过的内容」账本里有多少条 */
    copiedCount: Int = 0,
    onClearCopied: () -> Unit = {},
    /** 现有规则条数（规则管理卡片） */
    rulesCount: Int = 0,
    onExportRules: () -> Unit = {},
    /** 导入：把选中的文本文件读成字符串交给上层解析 */
    onImportRules: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var askClearCopied by remember { mutableStateOf(false) }
    var showRunParams by remember { mutableStateOf(false) }
    var importFailed by remember { mutableStateOf(false) }

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

    // 下载/安装状态：下载在 IO 线程，进度写回这里显示
    var downloading by remember { mutableStateOf(false) }
    var progressPct by remember { mutableStateOf(-1) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    // 语言要先写进 SharedPreferences（setLanguage 内部做了），再重建 Activity，
    // 这样 attachBaseContext 才能同步读到新语言。
    fun pickLanguage(value: Int) {
        if (settings.language == value) return
        scope.launch {
            store.setLanguage(value)
            AppLocale.findActivity(context)?.recreate()
        }
    }

    // 导入规则：系统文件选择器只收纯文本，读成字符串后交给上层解析入库
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            importFailed = false
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)
                            ?.use { it.bufferedReader().readText() }
                    }.getOrNull()
                }
                if (text.isNullOrBlank()) importFailed = true else onImportRules(text)
            }
        }
    }

    /**
     * 应用内下载并安装。
     *
     * 没开「安装未知应用」时先去授权页——这是系统限制，普通应用没法绕过；
     * 用户回来后再点一次即可（此时文件已缓存，会直接进安装器）。
     */
    fun startUpdateDownload(d: UpdateDialog) {
        val apkUrl = d.apkUrl ?: return
        if (downloading) return
        downloadError = null
        if (!UpdateInstaller.canInstall(context)) {
            UpdateInstaller.requestInstallPermission(context)
            downloadError = context.getString(R.string.set_install_permission_needed)
            return
        }
        downloading = true
        progressPct = -1
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    UpdateInstaller.download(context, apkUrl, d.version) { read, total ->
                        // 服务器没给总大小时返回 -1，界面显示「正在下载…」
                        progressPct = if (total > 0) {
                            ((read * 100) / total).toInt().coerceIn(0, 100)
                        } else {
                            -1
                        }
                    }
                }
            }
            downloading = false
            result.onSuccess { apk ->
                runCatching { UpdateInstaller.install(context, apk) }
                    .onSuccess { updateDialog = null }
                    .onFailure { e ->
                        downloadError = context.getString(
                            R.string.set_install_failed,
                            e.message ?: e.javaClass.simpleName,
                        )
                    }
            }.onFailure { e ->
                downloadError = context.getString(
                    R.string.set_download_failed,
                    e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }

    // 自检详情由 Lead 弹窗展示，这里只留一行「上次自检：时间」
    val lastCheckTime = remember(settings.lastSelfCheckAt) {
        settings.lastSelfCheckAt?.let {
            runCatching {
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    .format(Date(it * 1000L))
            }.getOrNull()
        }
    }
    val lastCheckLabel = when {
        lastCheckTime != null -> stringResource(R.string.set_last_self_check, lastCheckTime)
        settings.lastSelfCheck != null -> stringResource(R.string.set_self_check_earlier)
        else -> stringResource(R.string.set_no_self_check)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 1. 语言：切换后立即重建 Activity，界面马上变
        SectionCard(title = stringResource(R.string.set_section_language)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = settings.language == AppLocale.SYSTEM,
                    onClick = { pickLanguage(AppLocale.SYSTEM) },
                    label = { Text(stringResource(R.string.set_language_system)) },
                )
                FilterChip(
                    selected = settings.language == AppLocale.CHINESE,
                    onClick = { pickLanguage(AppLocale.CHINESE) },
                    label = { Text(stringResource(R.string.set_language_chinese)) },
                )
                FilterChip(
                    selected = settings.language == AppLocale.ENGLISH,
                    onClick = { pickLanguage(AppLocale.ENGLISH) },
                    label = { Text(stringResource(R.string.set_language_english)) },
                )
            }
        }

        // 2. 存储访问：没有 Shizuku 时的后备通道
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

        // 3. 运行条件：默认全关 = 和以前一样，任何时候都能搬
        SectionCard(title = stringResource(R.string.set_section_run_conditions)) {
            Text(
                stringResource(R.string.set_run_conditions_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SwitchRow(
                title = stringResource(R.string.set_run_only_charging),
                checked = settings.runOnlyCharging,
                onCheckedChange = { scope.launch { store.setRunOnlyCharging(it) } },
            )
            SwitchRow(
                title = stringResource(R.string.set_run_only_screen_off),
                checked = settings.runOnlyScreenOff,
                onCheckedChange = { scope.launch { store.setRunOnlyScreenOff(it) } },
            )
            SwitchRow(
                title = stringResource(R.string.set_run_only_night),
                checked = settings.runOnlyNight,
                onCheckedChange = { scope.launch { store.setRunOnlyNight(it) } },
            )
            if (settings.runOnlyNight) {
                // 22 → 7 这种跨午夜的时段是常态，两个小框并排放，省地方
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    intField(
                        stringResource(R.string.set_night_start_hour),
                        settings.nightStartHour,
                        0..23,
                        Modifier.weight(1f),
                    ) { scope.launch { store.setNightStartHour(it) } }
                    intField(
                        stringResource(R.string.set_night_end_hour),
                        settings.nightEndHour,
                        0..23,
                        Modifier.weight(1f),
                    ) { scope.launch { store.setNightEndHour(it) } }
                }
            }
            SwitchRow(
                title = stringResource(R.string.set_run_min_battery, settings.runMinBatteryPct),
                checked = settings.runMinBatteryPct > 0,
                // 关掉 = 0 = 不看电量；打开先给个 20%，要改再改下面的框
                onCheckedChange = { on ->
                    scope.launch { store.setRunMinBatteryPct(if (on) 20 else 0) }
                },
            )
            if (settings.runMinBatteryPct > 0) {
                intField(
                    stringResource(R.string.set_run_min_battery_pct),
                    settings.runMinBatteryPct,
                    1..100,
                ) { scope.launch { store.setRunMinBatteryPct(it) } }
            }
        }

        // 4. 检测速度：常用三项 + 收起的数字参数
        SectionCard(title = stringResource(R.string.set_section_detection_speed)) {
            SwitchRow(
                title = stringResource(R.string.set_fast_while_active),
                checked = settings.fastWhileActive,
                onCheckedChange = { scope.launch { store.setFastWhileActive(it) } },
                subtitle = stringResource(R.string.set_fast_while_active_desc),
            )
            if (settings.fastWhileActive) {
                intField(
                    stringResource(R.string.set_fast_interval_sec),
                    settings.fastIntervalSec,
                    3..60,
                ) { scope.launch { store.setFastIntervalSec(it) } }
            }

            SwitchRow(
                title = stringResource(R.string.set_screen_off_pause),
                checked = settings.screenOffPause,
                onCheckedChange = { scope.launch { store.setScreenOffPause(it) } },
                subtitle = stringResource(
                    if (settings.screenOffPause) {
                        R.string.set_screen_off_pause_on_desc
                    } else {
                        R.string.set_screen_off_pause_off_desc
                    },
                ),
            )

            SwitchRow(
                title = stringResource(R.string.set_settle_detect),
                checked = settings.settleDetect,
                onCheckedChange = { scope.launch { store.setSettleDetect(it) } },
                subtitle = stringResource(R.string.set_settle_detect_desc),
            )
            if (settings.settleDetect) {
                intField(
                    stringResource(R.string.set_settle_gap_sec),
                    settings.settleGapSec,
                    2..15,
                ) { scope.launch { store.setSettleGapSec(it) } }
            }

            // 旧「运行参数」卡片整块收在这里：默认关着，要用再点开
            Row(
                Modifier.fillMaxWidth()
                    .clickable { showRunParams = !showRunParams }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.set_section_run_params),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (showRunParams) {
                        Icons.Filled.KeyboardArrowUp
                    } else {
                        Icons.Filled.KeyboardArrowDown
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            AnimatedVisibility(visible = showRunParams) {
                Column {
                    Text(
                        stringResource(R.string.set_run_params_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
            }
        }

        // 5. 开关
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

        // 6. 外观
        SectionCard(title = stringResource(R.string.set_section_appearance)) {
            Text(stringResource(R.string.set_theme), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = settings.themeMode == ThemeMode.SYSTEM,
                    onClick = { scope.launch { store.setThemeMode(ThemeMode.SYSTEM) } },
                    label = { Text(stringResource(R.string.set_theme_system)) },
                )
                FilterChip(
                    selected = settings.themeMode == ThemeMode.LIGHT,
                    onClick = { scope.launch { store.setThemeMode(ThemeMode.LIGHT) } },
                    label = { Text(stringResource(R.string.set_theme_light)) },
                )
                FilterChip(
                    selected = settings.themeMode == ThemeMode.DARK,
                    onClick = { scope.launch { store.setThemeMode(ThemeMode.DARK) } },
                    label = { Text(stringResource(R.string.set_theme_dark)) },
                )
            }
            SwitchRow(
                title = stringResource(R.string.set_dynamic_color),
                checked = settings.dynamicColor,
                onCheckedChange = { scope.launch { store.setDynamicColor(it) } },
                subtitle = stringResource(R.string.set_dynamic_color_desc),
            )
        }

        // 7. 复制记录：复制模式的"这张图复制过没有"账本
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

        // 8. 规则管理：导入/导出走系统文件选择器，导入的一律先暂停
        SectionCard(title = stringResource(R.string.set_section_rules)) {
            Text(
                stringResource(R.string.set_rules_count, rulesCount),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExportRules, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.set_export_rules))
                }
                OutlinedButton(
                    onClick = {
                        importFailed = false
                        importLauncher.launch(arrayOf("text/plain"))
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.set_import_rules)) }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.set_rules_import_paused_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (importFailed) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.set_rules_import_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        // 9. 自检：只有一行按钮，详情由外面的弹窗负责
        SectionCard(title = stringResource(R.string.set_section_self_check)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onSelfCheck) {
                    Text(stringResource(R.string.set_run_self_check))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    lastCheckLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 10. 关于
        SectionCard(title = stringResource(R.string.set_section_about)) {
            Text(
                stringResource(R.string.set_about_version, versionName),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                REPO_URL,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openUrl(REPO_URL) }
                    .padding(vertical = 2.dp),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    checkingUpdate = true
                    downloadError = null
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
                                title = context.getString(R.string.set_update_available_title, info.version),
                                body = info.notes.ifBlank {
                                    context.getString(R.string.set_update_notes_fallback)
                                },
                                pageUrl = info.pageUrl.takeIf { it.isNotBlank() },
                                apkUrl = info.apkUrl,
                                version = info.version,
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

    if (askClearCopied) {
        AlertDialog(
            onDismissRequest = { askClearCopied = false },
            title = { Text(stringResource(R.string.set_clear_copy_history_title)) },
            text = { Text(stringResource(R.string.set_clear_copy_history_body)) },
            confirmButton = {
                TextButton(onClick = {
                    askClearCopied = false
                    onClearCopied()
                }) { Text(stringResource(R.string.set_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { askClearCopied = false }) {
                    Text(stringResource(R.string.set_cancel))
                }
            },
        )
    }

    updateDialog?.let { d ->
        val pageUrl = d.pageUrl
        val apkUrl = d.apkUrl
        AlertDialog(
            // 下载中不让关：协程跟着界面走，关了会变成「后台装了个包」
            onDismissRequest = {
                if (!downloading) {
                    updateDialog = null
                    downloadError = null
                }
            },
            title = { Text(d.title) },
            text = {
                Column(
                    Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                ) {
                    if (downloading) {
                        Text(
                            if (progressPct >= 0) {
                                stringResource(R.string.set_downloading_pct, progressPct)
                            } else {
                                stringResource(R.string.set_downloading)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(d.body, style = MaterialTheme.typography.bodySmall)
                    downloadError?.let { message ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                when {
                    downloading -> TextButton(onClick = {}, enabled = false) {
                        Text(stringResource(R.string.set_downloading))
                    }

                    apkUrl != null -> TextButton(onClick = { startUpdateDownload(d) }) {
                        Text(
                            stringResource(
                                if (downloadError != null) {
                                    R.string.set_retry
                                } else {
                                    R.string.set_download_and_install
                                },
                            ),
                        )
                    }

                    pageUrl != null -> TextButton(onClick = {
                        openUrl(pageUrl)
                        updateDialog = null
                    }) { Text(stringResource(R.string.set_go_download)) }

                    else -> TextButton(onClick = { updateDialog = null }) {
                        Text(stringResource(R.string.set_got_it))
                    }
                }
            },
            dismissButton = {
                when {
                    downloading -> Unit
                    apkUrl != null && pageUrl != null -> TextButton(onClick = {
                        openUrl(pageUrl)
                        updateDialog = null
                    }) { Text(stringResource(R.string.set_go_download)) }

                    apkUrl != null || pageUrl != null -> TextButton(onClick = {
                        updateDialog = null
                    }) { Text(stringResource(R.string.set_later)) }

                    else -> Unit
                }
            },
        )
    }
}

/**
 * 数字输入框。
 *
 * 输入过程中允许暂时超出 [range]（比如在 10–600 里先敲了个 1），否则根本没法打多位数；
 * 只有落在范围内才写回设置，失焦时把没写回去的临时值还原成真正存下的值。
 */
@Composable
private fun intField(
    label: String,
    value: Int,
    range: IntRange,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onChange: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(6)
            text = digits
            digits.toIntOrNull()?.let { n -> if (n in range) onChange(n) }
        },
        label = { Text(stringResource(R.string.set_field_range, label, range.first, range.last)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
            .padding(vertical = 4.dp)
            .onFocusChanged { if (!it.isFocused) text = value.toString() },
    )
}
