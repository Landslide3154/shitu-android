package com.landslide.shitu.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.landslide.shitu.R
import com.landslide.shitu.ShituApp
import com.landslide.shitu.data.Settings
import com.landslide.shitu.data.db.CopiedEntity
import com.landslide.shitu.local.StorageAccess
import com.landslide.shitu.shizuku.ShizukuState
import com.landslide.shitu.ui.components.ListItemNext
import com.landslide.shitu.ui.components.Section
import com.landslide.shitu.ui.components.ShituToolbar
import com.landslide.shitu.ui.design.AppType
import com.landslide.shitu.ui.design.ShituIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/** 设置页内部的二级路由（FlClash「入口页 + 二级页」）。 */
enum class SettingsRoute {
    HOME,
    RUN,
    DETECTION,
    PARAMS,
    STORAGE,
    NOTIFY,
    COPY_HISTORY,
    BACKUP,
    THEME,
    ABOUT,
}

/**
 * 设置体系入口（FlClash `ToolsView` 的三段式）：
 * 一级只放入口，低频项收进二级页，不再是一张 1100 行的大滚动。
 */
@Composable
fun SettingsFlow(
    app: ShituApp,
    settings: Settings,
    copiedCount: Int,
    copiedItems: List<CopiedEntity>,
    rulesCount: Int,
    onSelfCheck: () -> Unit,
    onRefresh: () -> Unit,
    onSnackbar: (String) -> Unit,
    onDetailChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var route by rememberSaveable { mutableStateOf(SettingsRoute.HOME) }
    androidx.compose.runtime.LaunchedEffect(route) {
        onDetailChanged(route != SettingsRoute.HOME)
    }
    BackHandler(enabled = route != SettingsRoute.HOME) { route = SettingsRoute.HOME }

    AnimatedContent(
        targetState = route,
        transitionSpec = {
            (
                slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(200))
                ) togetherWith (
                fadeOut(tween(120)) + slideOutHorizontally(tween(200)) { -it / 8 }
                )
        },
        label = "settingsRoute",
        modifier = Modifier.fillMaxSize(),
    ) { current ->
        when (current) {
            SettingsRoute.HOME -> SettingsHome(
                app = app,
                settings = settings,
                copiedCount = copiedCount,
                rulesCount = rulesCount,
                onNavigate = { route = it },
            )

            SettingsRoute.RUN -> RunSettingsScreen(
                settings = settings,
                store = app.settings,
                onBack = { route = SettingsRoute.HOME },
            )

            SettingsRoute.DETECTION -> DetectionSettingsScreen(
                settings = settings,
                store = app.settings,
                onBack = { route = SettingsRoute.HOME },
            )

            SettingsRoute.PARAMS -> ParamsSettingsScreen(
                settings = settings,
                store = app.settings,
                onBack = { route = SettingsRoute.HOME },
            )

            SettingsRoute.STORAGE -> StorageSettingsScreen(
                settings = settings,
                onSelfCheck = onSelfCheck,
                onBack = { route = SettingsRoute.HOME },
            )

            SettingsRoute.NOTIFY -> NotifySettingsScreen(
                app = app,
                settings = settings,
                store = app.settings,
                onBack = { route = SettingsRoute.HOME },
            )

            SettingsRoute.COPY_HISTORY -> CopyHistoryScreen(
                records = copiedItems,
                total = copiedCount,
                onClearSelected = { fingerprints ->
                    scope.launch {
                        val n = runCatching { app.ledger.deleteSelected(fingerprints) }.getOrDefault(0)
                        onRefresh()
                        onSnackbar(context.getString(R.string.app_copied_removed, n))
                    }
                },
                onClearAll = {
                    scope.launch {
                        val n = app.ledger.clear()
                        val items = runCatching { app.repo.clearCopyDoneItems() }.getOrDefault(0)
                        onRefresh()
                        onSnackbar(context.getString(R.string.app_copy_records_cleared, n, items))
                    }
                },
                onBack = { route = SettingsRoute.HOME },
            )

            SettingsRoute.BACKUP -> BackupSettingsScreen(
                app = app,
                rulesCount = rulesCount,
                onRefresh = onRefresh,
                onSnackbar = onSnackbar,
                onBack = { route = SettingsRoute.HOME },
            )

            SettingsRoute.THEME -> ThemeScreen(
                settings = settings,
                store = app.settings,
                onBack = { route = SettingsRoute.HOME },
            )

            SettingsRoute.ABOUT -> AboutSettingsScreen(
                app = app,
                settings = settings,
                onSelfCheck = onSelfCheck,
                onSnackbar = onSnackbar,
                onBack = { route = SettingsRoute.HOME },
            )
        }
    }
}

@Composable
private fun SettingsHome(
    app: ShituApp,
    settings: Settings,
    copiedCount: Int,
    rulesCount: Int,
    onNavigate: (SettingsRoute) -> Unit,
) {
    val context = LocalContext.current
    var allFilesGranted by remember { mutableStateOf(StorageAccess.isGranted(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allFilesGranted = StorageAccess.isGranted(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val shizuku = app.bridge.state()
    val storageSummary = when {
        allFilesGranted -> stringResource(R.string.set_storage_granted)
        shizuku == ShizukuState.READY -> stringResource(R.string.set_summary_storage_shizuku)
        else -> stringResource(R.string.set_summary_storage_denied)
    }
    val conditionCount = listOf(
        settings.runOnlyCharging,
        settings.runOnlyScreenOn,
        settings.runOnlyScreenOff,
        settings.runOnlyNight,
        settings.lockTimeoutMin > 0,
        settings.runMinBatteryPct > 0,
    ).count { it }
    val conditionValue = if (conditionCount == 0) {
        stringResource(R.string.set_summary_conditions_any)
    } else {
        stringResource(R.string.set_summary_conditions_count, conditionCount)
    }
    val fast = if (settings.fastWhileActive) {
        stringResource(R.string.set_seconds_value, settings.fastIntervalSec)
    } else {
        stringResource(R.string.set_off)
    }
    val detectionValue = if (settings.settleDetect) {
        stringResource(
            R.string.set_summary_fast_settle,
            fast,
            stringResource(R.string.set_seconds_value, settings.settleGapSec),
        )
    } else {
        stringResource(R.string.set_summary_fast, fast)
    }

    Column(Modifier.fillMaxSize()) {
        ShituToolbar(stringResource(R.string.app_nav_settings))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Section(title = stringResource(R.string.set_group_move)) {
                    ListItemNext(
                        title = stringResource(R.string.set_section_run_conditions),
                        value = conditionValue,
                        leading = { Icon(ShituIcons.Zap, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { onNavigate(SettingsRoute.RUN) },
                    )
                    ListItemNext(
                        title = stringResource(R.string.set_section_detection_timing),
                        value = detectionValue,
                        leading = { Icon(ShituIcons.Timer, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { onNavigate(SettingsRoute.DETECTION) },
                    )
                    ListItemNext(
                        title = stringResource(R.string.set_section_run_params),
                        value = stringResource(R.string.set_summary_max_per_run, settings.maxPerRun),
                        leading = { Icon(ShituIcons.HardDrive, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { onNavigate(SettingsRoute.PARAMS) },
                    )
                    ListItemNext(
                        title = stringResource(R.string.set_section_storage_access),
                        value = storageSummary,
                        leading = { Icon(ShituIcons.Shield, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { onNavigate(SettingsRoute.STORAGE) },
                        divider = false,
                    )
                }
            }
            item {
                Section(title = stringResource(R.string.set_group_data)) {
                    ListItemNext(
                        title = stringResource(R.string.set_section_appearance_language),
                        value = themeSummary(settings),
                        leading = { Icon(ShituIcons.Palette, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { onNavigate(SettingsRoute.THEME) },
                    )
                    ListItemNext(
                        title = stringResource(R.string.set_section_copy_history),
                        value = stringResource(R.string.set_copy_history_count, copiedCount),
                        leading = { Icon(ShituIcons.Image, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { onNavigate(SettingsRoute.COPY_HISTORY) },
                    )
                    ListItemNext(
                        title = stringResource(R.string.set_section_backup),
                        value = stringResource(R.string.set_rules_count, rulesCount),
                        leading = { Icon(ShituIcons.Database, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { onNavigate(SettingsRoute.BACKUP) },
                    )
                    ListItemNext(
                        title = stringResource(R.string.set_section_notify),
                        value = stringResource(if (settings.notifyEnabled) R.string.set_on else R.string.set_off),
                        leading = { Icon(ShituIcons.Bell, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { onNavigate(SettingsRoute.NOTIFY) },
                        divider = false,
                    )
                }
            }
            item {
                Section(title = stringResource(R.string.set_group_other)) {
                    ListItemNext(
                        title = stringResource(R.string.set_section_self_check),
                        value = lastCheckLabel(settings),
                        leading = { Icon(ShituIcons.CircleCheck, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { onNavigate(SettingsRoute.STORAGE) },
                    )
                    ListItemNext(
                        title = stringResource(R.string.set_section_about),
                        value = versionName(context),
                        leading = { Icon(ShituIcons.Info, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { onNavigate(SettingsRoute.ABOUT) },
                        divider = false,
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.set_privacy_note),
                    style = AppType.Footnote.style(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun themeSummary(s: Settings): String {
    val mode = stringResource(
        when (s.themeMode) {
            1 -> R.string.set_theme_light
            2 -> R.string.set_theme_dark
            else -> R.string.set_theme_follow_system
        },
    )
    val style = stringResource(
        when (s.colorStyle) {
            1 -> R.string.set_style_tinted
            2 -> R.string.set_style_soft
            3 -> R.string.set_style_mono
            else -> R.string.set_style_vivid
        },
    )
    return "$mode · $style"
}

private fun versionName(context: android.content.Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "?"

@Composable
private fun lastCheckLabel(s: Settings): String {
    val at = s.lastSelfCheckAt
    if (at == null) {
        return stringResource(
            if (s.lastSelfCheck != null) R.string.set_self_check_earlier else R.string.set_no_self_check,
        )
    }
    val formatted = runCatching {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(at * 1000L))
    }.getOrNull()
    return if (formatted == null) {
        stringResource(R.string.set_no_self_check)
    } else {
        stringResource(R.string.set_last_self_check, formatted)
    }
}
