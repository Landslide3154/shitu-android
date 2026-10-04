package com.landslide.shitu.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.landslide.shitu.ShituApp
import com.landslide.shitu.core.RuleConflictChecker
import com.landslide.shitu.data.Settings
import com.landslide.shitu.data.db.LogEntity
import com.landslide.shitu.data.db.RuleEntity
import com.landslide.shitu.data.db.RuleState
import com.landslide.shitu.engine.HealthChecker
import com.landslide.shitu.shizuku.ShizukuState
import com.landslide.shitu.ui.logs.LogScreen
import com.landslide.shitu.ui.rules.RuleEditScreen
import com.landslide.shitu.ui.rules.RuleListScreen
import com.landslide.shitu.ui.settings.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** App 根界面：三个标签页 + 规则编辑页 + 自检结果弹窗。 */
@Composable
fun ShituRoot(app: ShituApp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())

    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<RuleEntity?>(null) }
    var rules by remember { mutableStateOf<List<RuleEntity>>(emptyList()) }
    var logs by remember { mutableStateOf<List<LogEntity>>(emptyList()) }
    var state by remember { mutableStateOf(app.bridge.state()) }
    var health by remember { mutableStateOf<List<HealthChecker.Item>?>(null) }
    var checking by remember { mutableStateOf(false) }
    var askBattery by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    suspend fun refresh() {
        rules = app.repo.allRules()
        logs = app.repo.recentLogs(500)
        state = app.bridge.state()
    }

    LaunchedEffect(Unit) {
        refresh()
        askBattery = !isIgnoringBattery(context)
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_000)
            state = app.bridge.state()
        }
    }

    fun selfCheck() {
        scope.launch {
            checking = true
            when (app.bridge.state()) {
                ShizukuState.NOT_INSTALLED ->
                    snackbar.showSnackbar("没找到 Shizuku，请先安装并启动它")
                ShizukuState.NOT_RUNNING ->
                    snackbar.showSnackbar("Shizuku 未运行，请先在 Shizuku 里启动服务")
                ShizukuState.NO_PERMISSION -> {
                    app.bridge.requestPermission()
                    snackbar.showSnackbar("已弹出授权请求，请在 Shizuku 里允许「拾图」")
                }
                else -> Unit
            }
            if (app.bridge.state() != ShizukuState.READY) app.bridge.bindWithRetry(2)
            val first = rules.firstOrNull()
            val report = HealthChecker(
                bridge = app.bridge,
                probeSrcDir = first?.srcPath ?: "/sdcard/Android/data",
                probeDstDir = first?.dstPath ?: RuleEntity.DEFAULT_DST,
                environmentLine = { app.bridge.environmentLine() },
            ).run()
            health = report
            checking = false
            app.settings.setLastSelfCheck(renderReport(report))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Share, contentDescription = null) },
                    label = { Text("规则") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.List, contentDescription = null) },
                    label = { Text("日志") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("设置") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val current = editing
            when {
                current != null -> RuleEditScreen(
                    initial = current,
                    bridge = app.bridge,
                    conflicts = RuleConflictChecker.check(
                        rules.filter { it.id != current.id } + current,
                    ),
                    onSave = { r ->
                        scope.launch {
                            if (r.id == 0L) app.repo.insertRule(r) else app.repo.updateRule(r)
                            editing = null
                            refresh()
                            snackbar.showSnackbar("已保存「${r.name}」")
                        }
                    },
                    onCancel = { editing = null },
                )

                tab == 0 -> RuleListScreen(
                    rules = rules,
                    state = state,
                    pauseAll = settings.pauseAll,
                    movedTotal = rules.sumOf { it.totalMoved },
                    onSelfCheck = { selfCheck() },
                    onRequestPermission = { app.bridge.requestPermission() },
                    onTogglePauseAll = { scope.launch { app.settings.setPauseAll(it); snackbar.showSnackbar(if (it) "已暂停全部" else "已恢复") } },
                    onAdd = {
                        val now = System.currentTimeMillis()
                        editing = RuleEntity(
                            name = "新规则",
                            srcPath = RuleEntity.PICKER_ROOT,
                            dstPath = RuleEntity.DEFAULT_DST,
                            createdAt = now,
                            updatedAt = now,
                        )
                    },
                    onEdit = { editing = it },
                    onToggleEnabled = { r, on ->
                        scope.launch {
                            app.repo.setRuleEnabled(r, on)
                            refresh()
                        }
                    },
                    onRunNow = { r ->
                        scope.launch {
                            snackbar.showSnackbar("正在跑「${r.name}」…")
                            if (app.bridge.state() != ShizukuState.READY) app.bridge.bindWithRetry(1)
                            val result = app.runOne(r, settings)
                            refresh()
                            snackbar.showSnackbar(
                                result.error ?: "「${r.name}」：搬 ${result.moved} · 失败 ${result.failed} · 跳过 ${result.skipped}",
                            )
                        }
                    },
                    onUndo = { r ->
                        scope.launch {
                            if (app.bridge.state() != ShizukuState.READY) app.bridge.bindWithRetry(1)
                            val s = app.undoRule(r.id)
                            refresh()
                            snackbar.showSnackbar(
                                "撤回完成：成功 ${s.done} · 跳过 ${s.skipped}" +
                                    (s.notes.firstOrNull()?.let { "（例：$it）" } ?: ""),
                            )
                        }
                    },
                    onResume = { r ->
                        scope.launch {
                            app.resumeRule(r)
                            refresh()
                        }
                    },
                    onDelete = { r ->
                        scope.launch {
                            app.repo.deleteRule(r.id)
                            refresh()
                            snackbar.showSnackbar("已删除「${r.name}」")
                        }
                    },
                )

                tab == 1 -> LogScreen(
                    logs = logs,
                    rules = rules,
                    onExport = {
                        scope.launch {
                            snackbar.showSnackbar("正在导出…")
                            val path = runCatching { app.exportLogs() }
                                .getOrElse { "导出失败：${it.message}" }
                            refresh()
                            snackbar.showSnackbar("日志已导出到 $path")
                        }
                    },
                )

                else -> SettingsScreen(
                    settings = settings,
                    store = app.settings,
                    onSelfCheck = { selfCheck() },
                )
            }
        }
    }

    if (checking) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("正在自检…") },
            text = {
                Column {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("正在用临时文件验证当前系统上能不能搬（不会碰你的图片）。", style = MaterialTheme.typography.bodySmall)
                }
            },
        )
    }

    health?.let { report ->
        val text = renderReport(report)
        AlertDialog(
            onDismissRequest = { health = null },
            title = { Text(if (report.all { it.passed }) "自检通过（能搬）" else "自检发现问题") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(report) { item ->
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(
                                (if (item.passed) "✅ " else "❌ ") + item.name,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(item.detail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    copyToClipboard(context, text)
                    health = null
                    scope.launch { snackbar.showSnackbar("自检结果已复制") }
                }) { Text("复制结果") }
            },
            dismissButton = { TextButton(onClick = { health = null }) { Text("关闭") } },
        )
    }

    if (askBattery) {
        AlertDialog(
            onDismissRequest = { askBattery = false },
            title = { Text("让它一直在后台搬") },
            text = {
                Text(
                    "请把「拾图」加入电池优化白名单，否则 HyperOS 可能在息屏后把它冻结。" +
                        "另外建议在系统设置里开启「自启动」、省电策略设为「无限制」，并在最近任务里加锁。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askBattery = false
                    requestIgnoreBattery(context)
                }) { Text("去设置") }
            },
            dismissButton = { TextButton(onClick = { askBattery = false }) { Text("以后再说") } },
        )
    }
}

private fun renderReport(report: List<HealthChecker.Item>): String =
    report.joinToString("\n") { (if (it.passed) "[通过] " else "[失败] ") + it.name + "：" + it.detail }

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(ClipData.newPlainText("拾图自检", text))
}

private fun isIgnoringBattery(context: Context): Boolean {
    val pm = context.getSystemService(PowerManager::class.java) ?: return true
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

@Suppress("BatteryLife")
private fun requestIgnoreBattery(context: Context) {
    runCatching {
        context.startActivity(
            Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}")),
        )
    }
}
