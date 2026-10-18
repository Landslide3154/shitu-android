package com.landslide.shitu.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.landslide.shitu.R
import com.landslide.shitu.core.ConditionOrder
import com.landslide.shitu.core.UpdateChecker
import com.landslide.shitu.core.UpdateInstaller
import com.landslide.shitu.data.AppLocale
import com.landslide.shitu.data.Settings
import com.landslide.shitu.data.SettingsStore
import com.landslide.shitu.data.db.CopiedEntity
import com.landslide.shitu.local.StorageAccess
import com.landslide.shitu.ui.components.FileThumbnail
import com.landslide.shitu.ui.components.HelpDot
import com.landslide.shitu.ui.components.ShituField
import com.landslide.shitu.ui.theme.ShituRadius
import com.landslide.shitu.ui.theme.ThemeMode
import com.landslide.shitu.ui.theme.Tone
import com.landslide.shitu.ui.theme.color
import com.landslide.shitu.ui.theme.container
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
 * 设置页。卡片顺序固定为：存储访问 → 运行条件 → 检测时机 → 复制记录 →
 * 设置管理 → 外观与语言 → 自检 → 关于。
 *
 * 每张卡标题右侧都有帮助问号，开关和关键按钮旁也有。
 * 常用项直接露在卡片里，十几根数字输入框收进「运行参数」，默认不展开。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    store: SettingsStore,
    onSelfCheck: () -> Unit,
    /** 「已经复制过的内容」账本里有多少条 */
    copiedCount: Int = 0,
    /** 账本明细（最多 500 条），点开复制记录列表用 */
    copiedItems: List<CopiedEntity> = emptyList(),
    onClearCopied: () -> Unit = {},
    /** 只清掉选中的 fingerprint */
    onClearCopiedSelected: (List<String>) -> Unit = {},
    /** 现有规则条数（设置管理卡片） */
    rulesCount: Int = 0,
    /** 把规则和各项设置一起导出成备份文件 */
    onExportBackup: () -> Unit = {},
    /** 导入：把选中的备份文件读成字符串交给上层解析 */
    onImportBackup: (String) -> Unit = {},
    /** 从「有新版本」通知进来时，设置页一显示就自动检查一次 */
    autoCheckUpdate: Boolean = false,
    onAutoCheckConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var askClearCopied by remember { mutableStateOf(false) }
    var showCopySheet by remember { mutableStateOf(false) }
    var showRunParams by remember { mutableStateOf(false) }
    var importFailed by remember { mutableStateOf(false) }
    val copySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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

    /** 「检查更新」的唯一入口：手动点按钮、点「有新版本」通知都走这里。 */
    fun checkForUpdate() {
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
                    title = context.getString(
                        R.string.set_update_available_title,
                        info.version,
                    ),
                    body = info.notes.ifBlank {
                        context.getString(R.string.set_update_notes_fallback)
                    },
                    pageUrl = info.pageUrl.takeIf { it.isNotBlank() },
                    apkUrl = info.apkUrl,
                    version = info.version,
                )

                else -> UpdateDialog(
                    context.getString(R.string.set_update_latest_title),
                    context.getString(
                        R.string.set_update_latest_body,
                        versionName,
                        info.version,
                    ),
                    null,
                )
            }
        }
    }

    // 点「有新版本」通知进来：设置页一显示就自动查一次（只消费一次）
    LaunchedEffect(autoCheckUpdate) {
        if (autoCheckUpdate) {
            onAutoCheckConsumed()
            checkForUpdate()
        }
    }

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

    // 导入备份：系统文件选择器只收纯文本，读成字符串后交给上层解析入库
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
                if (text.isNullOrBlank()) importFailed = true else onImportBackup(text)
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
        // 1. 存储访问：说明和按钮同一行，已授权时按钮是绿色的
        SettingsCard(
            title = stringResource(R.string.set_section_storage_access),
            help = stringResource(R.string.set_help_storage_access),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (allFilesGranted) {
                        stringResource(R.string.set_storage_granted_desc)
                    } else {
                        stringResource(R.string.set_storage_not_granted_desc)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                if (allFilesGranted) {
                    FilledTonalButton(
                        onClick = { StorageAccess.openSettings(context) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Tone.OK.container(),
                            contentColor = Tone.OK.color(),
                        ),
                    ) {
                        Text(stringResource(R.string.set_storage_granted))
                    }
                } else {
                    OutlinedButton(onClick = { StorageAccess.openSettings(context) }) {
                        Text(stringResource(R.string.set_storage_open))
                    }
                }
            }
        }

        // 2. 运行条件：统一优先级列表，从上到下先命中先算
        SettingsCard(
            title = stringResource(R.string.set_section_run_conditions),
        ) {
            Text(
                stringResource(R.string.set_run_conditions_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val lockTimeoutOn = settings.lockTimeoutMin > 0
            val batteryOn = settings.runMinBatteryPct > 0
            val order = ConditionOrder.normalize(settings.conditionOrder)
            val chargingLabel = stringResource(R.string.set_run_only_charging)
            val lockTimeoutLabel = if (lockTimeoutOn) {
                stringResource(R.string.set_run_lock_timeout_on, settings.lockTimeoutMin)
            } else {
                stringResource(R.string.set_run_lock_timeout_off)
            }
            val batteryLabel = if (batteryOn) {
                stringResource(R.string.set_run_min_battery, settings.runMinBatteryPct)
            } else {
                stringResource(R.string.set_run_min_battery_off)
            }
            val screenOnLabel = stringResource(R.string.set_run_only_screen_on)
            val screenOffLabel = stringResource(R.string.set_run_only_screen_off)
            val labels = mapOf(
                ConditionOrder.CHARGING to chargingLabel,
                ConditionOrder.LOCK_TIMEOUT to lockTimeoutLabel,
                ConditionOrder.BATTERY to batteryLabel,
                ConditionOrder.SCREEN_ON to screenOnLabel,
                ConditionOrder.SCREEN_OFF to screenOffLabel,
            )

            fun moveCondition(id: String, delta: Int) {
                val list = ConditionOrder.normalize(settings.conditionOrder).toMutableList()
                val from = list.indexOf(id)
                val to = from + delta
                if (from < 0 || to !in list.indices) return
                val tmp = list[from]
                list[from] = list[to]
                list[to] = tmp
                scope.launch { store.setConditionOrder(list) }
            }

            order.forEachIndexed { index, id ->
                val checked = when (id) {
                    ConditionOrder.CHARGING -> settings.runOnlyCharging
                    ConditionOrder.LOCK_TIMEOUT -> lockTimeoutOn
                    ConditionOrder.BATTERY -> batteryOn
                    ConditionOrder.SCREEN_ON -> settings.runOnlyScreenOn
                    else -> settings.runOnlyScreenOff
                }
                ConditionRow(
                    index = index + 1,
                    title = labels.getValue(id),
                    checked = checked,
                    onCheckedChange = { on ->
                        scope.launch {
                            when (id) {
                                ConditionOrder.CHARGING -> store.setRunOnlyCharging(on)
                                // 打开先给 30 分钟；关掉 = 0 = 不启用
                                ConditionOrder.LOCK_TIMEOUT -> store.setLockTimeoutMin(
                                    if (on) settings.lockTimeoutMin.takeIf { it in 1..240 } ?: 30 else 0,
                                )
                                ConditionOrder.BATTERY -> store.setRunMinBatteryPct(if (on) 20 else 0)
                                ConditionOrder.SCREEN_ON -> store.setRunOnlyScreenOn(on)
                                else -> store.setRunOnlyScreenOff(on)
                            }
                        }
                    },
                    canUp = index > 0,
                    canDown = index < order.lastIndex,
                    onUp = { moveCondition(id, -1) },
                    onDown = { moveCondition(id, 1) },
                )
                if (id == ConditionOrder.LOCK_TIMEOUT && lockTimeoutOn) {
                    intField(
                        stringResource(R.string.set_run_lock_timeout_min),
                        settings.lockTimeoutMin,
                        1..240,
                    ) { scope.launch { store.setLockTimeoutMin(it) } }
                }
                if (id == ConditionOrder.BATTERY && batteryOn) {
                    intField(
                        stringResource(R.string.set_run_min_battery_pct),
                        settings.runMinBatteryPct,
                        1..100,
                    ) { scope.launch { store.setRunMinBatteryPct(it) } }
                }
            }
        }

        // 3. 检测时机：快检 + 写完就搬；高级参数默认收起
        SettingsCard(
            title = stringResource(R.string.set_section_detection_timing),
        ) {
            SettingSwitchRow(
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

            SettingSwitchRow(
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

        // 4. 复制记录：点开列表，勾选后只清选中的
        SettingsCard(
            title = stringResource(R.string.set_section_copy_history),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.set_copy_history_count, copiedCount),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { showCopySheet = true }) {
                    Text(stringResource(R.string.set_copy_history_view))
                }
            }
        }

        // 5. 设置管理：规则 + 各项设置一起备份
        SettingsCard(
            title = stringResource(R.string.set_section_backup),
        ) {
            Text(
                stringResource(R.string.set_rules_count, rulesCount),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.set_backup_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExportBackup, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.set_export_backup))
                }
                OutlinedButton(
                    onClick = {
                        importFailed = false
                        importLauncher.launch(arrayOf("text/plain"))
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.set_import_backup)) }
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

        // 6. 外观与语言：语言、主题、动态取色合并成一张卡
        SettingsCard(
            title = stringResource(R.string.set_section_appearance_language),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.set_language), style = MaterialTheme.typography.bodyLarge)
            }
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
            Row(
                Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.set_theme), style = MaterialTheme.typography.bodyLarge)
            }
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
            SettingSwitchRow(
                title = stringResource(R.string.set_dynamic_color),
                checked = settings.dynamicColor,
                onCheckedChange = { scope.launch { store.setDynamicColor(it) } },
                subtitle = stringResource(R.string.set_dynamic_color_desc),
            )
        }

        // 7. 自检：只有一行按钮，详情由外面的弹窗负责
        SettingsCard(
            title = stringResource(R.string.set_section_self_check),
        ) {
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

        // 8. 关于
        SettingsCard(
            title = stringResource(R.string.set_section_about),
        ) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { checkForUpdate() },
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
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.set_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showCopySheet) {
        ModalBottomSheet(
            onDismissRequest = { showCopySheet = false },
            sheetState = copySheetState,
        ) {
            CopyHistorySheet(
                records = copiedItems.take(500),
                total = copiedCount,
                onClearSelected = onClearCopiedSelected,
                onClearAll = { askClearCopied = true },
                onClose = { showCopySheet = false },
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
                    showCopySheet = false
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

/** 设置卡片：和 SectionCard 同款外观，标题行右侧多一个帮助问号。 */
@Composable
private fun SettingsCard(
    title: String,
    help: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ShituRadius.CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/** 一行「标题 + 开关」；标题旁带帮助问号，说明文字在下面。 */
@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    help: String? = null,
    subtitle: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * 复制记录列表：勾选后只清掉选中的；「全部清空」仍走外面的确认弹窗。
 */
@Composable
private fun CopyHistorySheet(
    records: List<CopiedEntity>,
    total: Int,
    onClearSelected: (List<String>) -> Unit,
    onClearAll: () -> Unit,
    onClose: () -> Unit,
) {
    var selected by remember { mutableStateOf(emptySet<String>()) }

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.set_section_copy_history),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.set_copy_history_count, total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(R.string.set_copy_sheet_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )

        if (records.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().height(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.set_copy_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f, fill = false).padding(top = 4.dp),
            ) {
                items(records, key = { it.fingerprint }) { item ->
                    val checked = item.fingerprint in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (checked) {
                                    selected - item.fingerprint
                                } else {
                                    selected + item.fingerprint
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                selected = if (checked) {
                                    selected - item.fingerprint
                                } else {
                                    selected + item.fingerprint
                                }
                            },
                        )
                        FileThumbnail(path = item.dstPath, size = 44.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            item.dstPath ?: stringResource(R.string.set_copy_unknown_path),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    onClearSelected(selected.toList())
                    selected = emptySet()
                    onClose()
                },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.set_clear_selected_count, selected.size))
            }
            OutlinedButton(
                onClick = onClearAll,
                enabled = total > 0,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.set_clear_all))
            }
        }
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
    ShituField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(6)
            text = digits
            digits.toIntOrNull()?.let { n -> if (n in range) onChange(n) }
        },
        label = stringResource(R.string.set_field_range, label, range.first, range.last),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
            .padding(vertical = 4.dp)
            .onFocusChanged { if (!it.isFocused) text = value.toString() },
    )
}

/**
 * 运行条件的一行：序号 + 名称 + 上下移动 + 开关。
 *
 * 顺序就是优先级：从上到下先命中先算。放在这里而不是 SettingSwitchRow，
 * 是因为每一行都要额外的移动按钮，且数字序号要固定宽度对齐。
 */
@Composable
private fun ConditionRow(
    index: Int,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            index.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(18.dp),
        )
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        TextButton(onClick = onUp, enabled = canUp, modifier = Modifier.width(38.dp)) {
            Text("↑")
        }
        TextButton(onClick = onDown, enabled = canDown, modifier = Modifier.width(38.dp)) {
            Text("↓")
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
