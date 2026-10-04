package com.landslide.shitu.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.landslide.shitu.data.Settings
import com.landslide.shitu.data.SettingsStore
import com.landslide.shitu.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/** 设置页（规格 §8 全部参数 + §10 页面 4）。 */
@Composable
fun SettingsScreen(
    settings: Settings,
    store: SettingsStore,
    onSelfCheck: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("运行参数", style = MaterialTheme.typography.titleMedium)

        intField("单轮扫描时间预算（秒）", settings.scanBudgetSec, 5..120) {
            scope.launch { store.setScanBudgetSec(it) }
        }
        intField("单轮最多搬运（个）", settings.maxPerRun, 1..500) {
            scope.launch { store.setMaxPerRun(it) }
        }
        intField("每分钟最多搬运（个）", settings.maxPerMinute, 10..600) {
            scope.launch { store.setMaxPerMinute(it) }
        }
        intField("单轮最长运行（秒）", settings.maxRunSec, 10..300) {
            scope.launch { store.setMaxRunSec(it) }
        }
        intField("文件稳定期（兜底等待，秒）", settings.stableSec, 0..300) {
            scope.launch { store.setStableSec(it) }
        }
        intField("重复抑制窗口（分钟）", settings.loopWindowMin, 5..120) {
            scope.launch { store.setLoopWindowMin(it) }
        }
        intField("重复抑制阈值（次）", settings.loopThreshold, 2..10) {
            scope.launch { store.setLoopThreshold(it) }
        }
        intField("连续失败阈值（次）", settings.failThreshold, 1..20) {
            scope.launch { store.setFailThreshold(it) }
        }
        intField("低电量暂停阈值（%）", settings.lowBatteryPct, 0..50) {
            scope.launch { store.setLowBatteryPct(it) }
        }
        intField("日志保留天数", settings.logKeepDays, 1..365) {
            scope.launch { store.setLogKeepDays(it) }
        }
        intField("日志保留条数", settings.logKeepCount, 1_000..200_000) {
            scope.launch { store.setLogKeepCount(it) }
        }

        HorizontalDivider()
        Text("检测速度", style = MaterialTheme.typography.titleMedium)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("正在用手机时加快检测")
            Switch(checked = settings.fastWhileActive, onCheckedChange = { scope.launch { store.setFastWhileActive(it) } })
        }
        intField("加快检测间隔（秒）", settings.fastIntervalSec, 3..60) {
            scope.launch { store.setFastIntervalSec(it) }
        }
        Text(
            "开着它：屏幕亮着时每隔上面这个秒数看一眼源目录有没有变化（只看目录时间，很省电），" +
                "一变就立刻搬，所以下载完十几秒内就能进相册。屏幕熄灭后回到每条规则自己的间隔。",
            style = MaterialTheme.typography.bodySmall,
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("熄屏后暂停扫描")
            Switch(checked = settings.screenOffPause, onCheckedChange = { scope.launch { store.setScreenOffPause(it) } })
        }
        Text(
            if (settings.screenOffPause) {
                "开着：熄屏或锁屏时停下（省电）；亮屏解锁后会自动继续，不用手动点。"
            } else {
                "关着（默认）：熄屏后仍按每条规则自己的间隔继续搬（推荐，晚上充电时也能把没搬完的清掉）。"
            },
            style = MaterialTheme.typography.bodySmall,
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("写完就搬（不等满稳定期）")
            Switch(checked = settings.settleDetect, onCheckedChange = { scope.launch { store.setSettleDetect(it) } })
        }
        intField("写完判定窗口（秒）", settings.settleGapSec, 2..15) {
            scope.launch { store.setSettleGapSec(it) }
        }
        Text(
            "同一个文件两次看到的「大小 + 修改时间」都没变、间隔超过上面这个秒数，就认为它写完了，" +
                "立刻搬走——不用干等「文件稳定期」。正在下载的文件每隔一会儿就变一次，不会被误判。" +
                "关掉它则退回按「文件稳定期」等待。",
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider()
        Text("开关", style = MaterialTheme.typography.titleMedium)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("低电量/省电模式暂停")
            Switch(checked = settings.lowBatteryPause, onCheckedChange = { scope.launch { store.setLowBatteryPause(it) } })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("加速清空（一轮结束立刻开下一轮）")
            Switch(checked = settings.fastDrain, onCheckedChange = { scope.launch { store.setFastDrain(it) } })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("事件通知（暂停/失败/未就绪）")
            Switch(checked = settings.notifyEnabled, onCheckedChange = { scope.launch { store.setNotifyEnabled(it) } })
        }

        HorizontalDivider()
        Text("外观", style = MaterialTheme.typography.titleMedium)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("主题")
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = settings.themeMode == ThemeMode.SYSTEM,
                    onClick = { scope.launch { store.setThemeMode(ThemeMode.SYSTEM) } },
                    label = { Text("跟随系统") },
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = settings.themeMode == ThemeMode.LIGHT,
                    onClick = { scope.launch { store.setThemeMode(ThemeMode.LIGHT) } },
                    label = { Text("浅色") },
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = settings.themeMode == ThemeMode.DARK,
                    onClick = { scope.launch { store.setThemeMode(ThemeMode.DARK) } },
                    label = { Text("深色") },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("动态取色（Material You）")
            Switch(checked = settings.dynamicColor, onCheckedChange = { scope.launch { store.setDynamicColor(it) } })
        }
        Text(
            "安卓 12 及以上：颜色跟着壁纸走，整个 App 和系统一个色调。关掉就用固定的蓝色。",
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider()
        Text("自检", style = MaterialTheme.typography.titleMedium)
        Text(
            settings.lastSelfCheck?.let { "上次自检：$it" } ?: "还没做过自检。",
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = onSelfCheck) { Text("立即自检") }

        HorizontalDivider()
        Text("关于", style = MaterialTheme.typography.titleMedium)
        // 版本号从系统读，别再写死（0.1.0 那次就是写死后忘了改）
        val context = LocalContext.current
        val versionName = remember {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "?"
        }
        Text("拾图 Shitu $versionName · GPL-3.0", style = MaterialTheme.typography.bodyMedium)
        Text(
            "https://github.com/Landslide3154/shitu-android",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "本 App 完全离线、不申请「所有文件访问」权限；所有文件操作都借 Shizuku 的 adb 身份完成。" +
                "系统更新后若搬不动，请先跑一次自检。",
            style = MaterialTheme.typography.bodySmall,
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
        label = { Text("$label（${range.first}–${range.last}）") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

