package com.landslide.shitu.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.landslide.shitu.R
import com.landslide.shitu.ShituApp
import com.landslide.shitu.data.Settings
import com.landslide.shitu.data.SettingsStore
import com.landslide.shitu.local.StorageAccess
import com.landslide.shitu.service.Notifier
import com.landslide.shitu.shizuku.ShizukuState
import com.landslide.shitu.ui.components.CommonDialog
import com.landslide.shitu.ui.components.ListItem
import com.landslide.shitu.ui.components.ListItemInput
import com.landslide.shitu.ui.components.ListItemToggle
import com.landslide.shitu.ui.components.Section
import com.landslide.shitu.ui.components.SectionHeader
import com.landslide.shitu.ui.components.ShituButton
import com.landslide.shitu.ui.components.ShituField
import com.landslide.shitu.ui.components.ShituSwitch
import com.landslide.shitu.ui.components.ShituToolbar
import com.landslide.shitu.ui.design.AppMetrics
import com.landslide.shitu.ui.design.AppType
import com.landslide.shitu.ui.design.ShituIcons
import com.landslide.shitu.ui.design.destructive
import com.landslide.shitu.ui.design.hairline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
private fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ShituToolbar(title, onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/** 数字输入对话框：单值提示走 Alert，多值表单才用页面。 */
@Composable
fun NumberEditDialog(
    title: String,
    value: Int,
    range: IntRange,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember(title, value) { mutableStateOf(value.toString()) }
    CommonDialog(
        title = title,
        dismissLabel = stringResource(R.string.set_cancel),
        confirmLabel = stringResource(R.string.set_confirm),
        onDismiss = onDismiss,
        onConfirm = {
            val parsed = text.toIntOrNull()
            if (parsed != null) onConfirm(parsed.coerceIn(range.first, range.last))
        },
        content = {
            ShituField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() } },
                placeholder = "${range.first}–${range.last}",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
    )
}

@Composable
fun RunSettingsScreen(settings: Settings, store: SettingsStore, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Pair<String, IntRange>?>(null) }
    SettingsPage(stringResource(R.string.set_section_run_conditions), onBack) {
        item {
            Text(
                stringResource(R.string.set_run_conditions_desc),
                style = AppType.Footnote.style(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            Section {
                val order = com.landslide.shitu.core.ConditionOrder.normalize(settings.conditionOrder)
                order.forEachIndexed { index, id ->
                    val title = when (id) {
                        com.landslide.shitu.core.ConditionOrder.CHARGING -> stringResource(R.string.set_run_only_charging)
                        com.landslide.shitu.core.ConditionOrder.LOCK_TIMEOUT -> stringResource(R.string.set_run_lock_timeout_off)
                        com.landslide.shitu.core.ConditionOrder.BATTERY -> stringResource(R.string.set_run_min_battery_off)
                        com.landslide.shitu.core.ConditionOrder.SCREEN_ON -> stringResource(R.string.set_run_only_screen_on)
                        else -> stringResource(R.string.set_run_only_screen_off)
                    }
                    val checked = when (id) {
                        com.landslide.shitu.core.ConditionOrder.CHARGING -> settings.runOnlyCharging
                        com.landslide.shitu.core.ConditionOrder.LOCK_TIMEOUT -> settings.lockTimeoutMin > 0
                        com.landslide.shitu.core.ConditionOrder.BATTERY -> settings.runMinBatteryPct > 0
                        com.landslide.shitu.core.ConditionOrder.SCREEN_ON -> settings.runOnlyScreenOn
                        else -> settings.runOnlyScreenOff
                    }
                    ConditionRow(
                        index = index + 1,
                        title = title,
                        subtitle = when (id) {
                            com.landslide.shitu.core.ConditionOrder.LOCK_TIMEOUT -> if (checked) {
                                stringResource(R.string.set_run_lock_timeout_on, settings.lockTimeoutMin)
                            } else {
                                ""
                            }

                            com.landslide.shitu.core.ConditionOrder.BATTERY -> if (checked) {
                                stringResource(R.string.set_run_min_battery, settings.runMinBatteryPct)
                            } else {
                                ""
                            }

                            else -> ""
                        },
                        checked = checked,
                        onCheckedChange = { on ->
                            scope.launch {
                                when (id) {
                                    com.landslide.shitu.core.ConditionOrder.CHARGING -> store.setRunOnlyCharging(on)
                                    com.landslide.shitu.core.ConditionOrder.LOCK_TIMEOUT ->
                                        store.setLockTimeoutMin(if (on) 30 else 0)

                                    com.landslide.shitu.core.ConditionOrder.BATTERY ->
                                        store.setRunMinBatteryPct(if (on) 20 else 0)

                                    com.landslide.shitu.core.ConditionOrder.SCREEN_ON -> store.setRunOnlyScreenOn(on)
                                    else -> store.setRunOnlyScreenOff(on)
                                }
                            }
                        },
                        canUp = index > 0,
                        canDown = index < order.lastIndex,
                        onUp = {
                            val list = order.toMutableList()
                            val tmp = list[index - 1]
                            list[index - 1] = list[index]
                            list[index] = tmp
                            scope.launch { store.setConditionOrder(list) }
                        },
                        onDown = {
                            val list = order.toMutableList()
                            val tmp = list[index + 1]
                            list[index + 1] = list[index]
                            list[index] = tmp
                            scope.launch { store.setConditionOrder(list) }
                        },
                        divider = index < order.lastIndex,
                    )
                    if (id == com.landslide.shitu.core.ConditionOrder.LOCK_TIMEOUT && checked) {
                        ListItemInput(
                            title = stringResource(R.string.set_run_lock_timeout_min),
                            value = "${settings.lockTimeoutMin}",
                            onClick = { editing = "lock" to 1..240 },
                            divider = index < order.lastIndex,
                        )
                    }
                    if (id == com.landslide.shitu.core.ConditionOrder.BATTERY && checked) {
                        ListItemInput(
                            title = stringResource(R.string.set_run_min_battery_pct),
                            value = "${settings.runMinBatteryPct}",
                            onClick = { editing = "battery" to 1..100 },
                            divider = index < order.lastIndex,
                        )
                    }
                }
            }
        }
    }
    editing?.let { (key, range) ->
        val current = if (key == "lock") settings.lockTimeoutMin else settings.runMinBatteryPct
        NumberEditDialog(
            title = if (key == "lock") {
                stringResource(R.string.set_run_lock_timeout_min)
            } else {
                stringResource(R.string.set_run_min_battery_pct)
            },
            value = current,
            range = range,
            onDismiss = { editing = null },
            onConfirm = { v ->
                scope.launch {
                    if (key == "lock") store.setLockTimeoutMin(v) else store.setRunMinBatteryPct(v)
                }
                editing = null
            },
        )
    }
}

@Composable
private fun ConditionRow(
    index: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    divider: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$index",
                style = AppType.Subheadline.style(),
                color = scheme.onSurfaceVariant,
                modifier = Modifier.width(20.dp),
            )
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(title, style = AppType.Body.style(), color = scheme.onSurface)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = AppType.Footnote.style(), color = scheme.onSurfaceVariant)
                }
            }
            TextButton(
                onClick = onUp,
                enabled = canUp,
                modifier = Modifier.size(32.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(ShituIcons.ChevronUp, null, modifier = Modifier.size(18.dp))
            }
            TextButton(
                onClick = onDown,
                enabled = canDown,
                modifier = Modifier.size(32.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(ShituIcons.ChevronDown, null, modifier = Modifier.size(18.dp))
            }
            ShituSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
        if (divider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp)
                    .height(1.dp)
                    .background(scheme.hairline),
            )
        }
    }
}

@Composable
fun DetectionSettingsScreen(settings: Settings, store: SettingsStore, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Pair<String, IntRange>?>(null) }
    SettingsPage(stringResource(R.string.set_section_detection_timing), onBack) {
        item {
            Section {
                ListItemToggle(
                    title = stringResource(R.string.set_fast_while_active),
                    subtitle = stringResource(R.string.set_fast_while_active_desc),
                    checked = settings.fastWhileActive,
                    onCheckedChange = { scope.launch { store.setFastWhileActive(it) } },
                )
                if (settings.fastWhileActive) {
                    ListItemInput(
                        title = stringResource(R.string.set_fast_interval_sec),
                        value = stringResource(R.string.set_seconds_value, settings.fastIntervalSec),
                        onClick = { editing = "fast" to 3..60 },
                    )
                }
                ListItemToggle(
                    title = stringResource(R.string.set_settle_detect),
                    subtitle = stringResource(R.string.set_settle_detect_desc),
                    checked = settings.settleDetect,
                    onCheckedChange = { scope.launch { store.setSettleDetect(it) } },
                )
                if (settings.settleDetect) {
                    ListItemInput(
                        title = stringResource(R.string.set_settle_gap_sec),
                        value = stringResource(R.string.set_seconds_value, settings.settleGapSec),
                        onClick = { editing = "settle" to 2..15 },
                        divider = false,
                    )
                }
            }
        }
    }
    editing?.let { (key, range) ->
        val current = when (key) {
            "fast" -> settings.fastIntervalSec
            else -> settings.settleGapSec
        }
        NumberEditDialog(
            title = if (key == "fast") {
                stringResource(R.string.set_fast_interval_sec)
            } else {
                stringResource(R.string.set_settle_gap_sec)
            },
            value = current,
            range = range,
            onDismiss = { editing = null },
            onConfirm = { v ->
                scope.launch {
                    if (key == "fast") store.setFastIntervalSec(v) else store.setSettleGapSec(v)
                }
                editing = null
            },
        )
    }
}

@Composable
fun ParamsSettingsScreen(settings: Settings, store: SettingsStore, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<ParamItem?>(null) }
    val items: List<ParamItem> = listOf(
        ParamItem(stringResource(R.string.set_scan_budget_sec), settings.scanBudgetSec, 5..120) {
            store.setScanBudgetSec(it)
        },
        ParamItem(stringResource(R.string.set_max_per_run), settings.maxPerRun, 1..500) {
            store.setMaxPerRun(it)
        },
        ParamItem(stringResource(R.string.set_max_per_minute), settings.maxPerMinute, 10..600) {
            store.setMaxPerMinute(it)
        },
        ParamItem(stringResource(R.string.set_max_run_sec), settings.maxRunSec, 10..300) {
            store.setMaxRunSec(it)
        },
        ParamItem(stringResource(R.string.set_stable_sec), settings.stableSec, 0..300) {
            store.setStableSec(it)
        },
        ParamItem(stringResource(R.string.set_loop_window_min), settings.loopWindowMin, 5..120) {
            store.setLoopWindowMin(it)
        },
        ParamItem(stringResource(R.string.set_loop_threshold), settings.loopThreshold, 2..10) {
            store.setLoopThreshold(it)
        },
        ParamItem(stringResource(R.string.set_fail_threshold), settings.failThreshold, 1..20) {
            store.setFailThreshold(it)
        },
        ParamItem(stringResource(R.string.set_low_battery_pct), settings.lowBatteryPct, 0..50) {
            store.setLowBatteryPct(it)
        },
        ParamItem(stringResource(R.string.set_log_keep_days), settings.logKeepDays, 1..365) {
            store.setLogKeepDays(it)
        },
        ParamItem(stringResource(R.string.set_log_keep_count), settings.logKeepCount, 1_000..200_000) {
            store.setLogKeepCount(it)
        },
    )
    SettingsPage(stringResource(R.string.set_section_run_params), onBack) {
        item {
            Text(
                stringResource(R.string.set_run_params_desc),
                style = AppType.Footnote.style(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            Section {
                items.forEachIndexed { index, item ->
                    ListItemInput(
                        title = item.title,
                        value = "${item.value}",
                        onClick = { editing = item },
                        divider = index < items.lastIndex,
                    )
                }
            }
        }
    }
    editing?.let { item ->
        NumberEditDialog(
            title = item.title,
            value = item.value,
            range = item.range,
            onDismiss = { editing = null },
            onConfirm = { v ->
                scope.launch { item.set(v) }
                editing = null
            },
        )
    }
}

private data class ParamItem(
    val title: String,
    val value: Int,
    val range: IntRange,
    val set: suspend (Int) -> Unit,
)

@Composable
fun StorageSettingsScreen(settings: Settings, onSelfCheck: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(StorageAccess.isGranted(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = StorageAccess.isGranted(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    SettingsPage(stringResource(R.string.set_section_storage_access), onBack) {
        item {
            Section {
                ListItem(
                    title = if (granted) {
                        stringResource(R.string.set_storage_granted_desc)
                    } else {
                        stringResource(R.string.set_storage_not_granted_desc)
                    },
                    divider = false,
                    trailing = {
                        ShituButton(
                            text = if (granted) {
                                stringResource(R.string.set_storage_granted)
                            } else {
                                stringResource(R.string.set_storage_open)
                            },
                            onClick = { StorageAccess.openSettings(context) },
                            primary = !granted,
                        )
                    },
                )
            }
        }
        item {
            Section(title = "Shizuku") {
                ListItem(
                    title = stringResource(R.string.set_shizuku_channel),
                    subtitle = stringResource(R.string.set_shizuku_channel_desc),
                    trailing = {
                        ShituButton(
                            text = stringResource(R.string.set_open_shizuku),
                            primary = false,
                            onClick = {
                                val launch = context.packageManager
                                    .getLaunchIntentForPackage("moe.shizuku.manager")
                                runCatching {
                                    if (launch != null) {
                                        context.startActivity(launch)
                                    } else {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://shizuku.rikka.app/"),
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                }
                            },
                        )
                    },
                    divider = false,
                )
            }
        }
        item {
            Section(title = stringResource(R.string.set_section_self_check)) {
                ListItem(
                    title = stringResource(R.string.set_run_self_check),
                    subtitle = lastCheckText(settings),
                    trailing = {
                        ShituButton(stringResource(R.string.set_run_self_check), onSelfCheck)
                    },
                    divider = false,
                )
            }
        }
    }
}

@Composable
private fun lastCheckText(s: Settings): String {
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
        stringResource(R.string.set_summary_last_check, formatted)
    }
}

@Composable
fun NotifySettingsScreen(app: ShituApp, settings: Settings, store: SettingsStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    SettingsPage(stringResource(R.string.set_section_notify), onBack) {
        item {
            Section {
                ListItemToggle(
                    title = stringResource(R.string.set_allow_notifications),
                    subtitle = stringResource(R.string.set_allow_notifications_desc),
                    checked = settings.notifyEnabled,
                    onCheckedChange = { scope.launch { store.setNotifyEnabled(it) } },
                    divider = false,
                )
            }
        }
        item {
            Section {
                ListItem(
                    title = stringResource(R.string.set_test_notification),
                    subtitle = stringResource(R.string.set_test_notification_desc),
                    onClick = {
                        runCatching {
                            Notifier(context).ensureChannels()
                            Notifier(context).event(
                                context.getString(R.string.set_test_notification_title),
                                context.getString(R.string.set_test_notification_body),
                            )
                        }
                    },
                    divider = false,
                )
                ListItem(
                    title = stringResource(R.string.set_system_notification),
                    subtitle = stringResource(R.string.set_system_notification_desc),
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        }
                    },
                    divider = false,
                )
            }
        }
    }
}
