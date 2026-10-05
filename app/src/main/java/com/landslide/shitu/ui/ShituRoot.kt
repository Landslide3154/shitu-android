package com.landslide.shitu.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import com.landslide.shitu.core.RuleTemplate
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
    // 编辑页在做退出动画时 editing 已经变成 null，用它撑住那几帧画面
    var lastEdit by remember { mutableStateOf<RuleEntity?>(null) }
    LaunchedEffect(editing) { editing?.let { lastEdit = it } }
    var draft by remember { mutableStateOf<RuleEntity?>(null) }
    var editingDirty by remember { mutableStateOf(false) }
    var askSave by remember { mutableStateOf(false) }
    var pendingTab by remember { mutableStateOf<Int?>(null) }
    var rules by remember { mutableStateOf<List<RuleEntity>>(emptyList()) }
    var logs by remember { mutableStateOf<List<LogEntity>>(emptyList()) }
    var copiedCount by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf(app.bridge.state()) }
    var health by remember { mutableStateOf<List<HealthChecker.Item>?>(null) }
    var checking by remember { mutableStateOf(false) }
    var askBattery by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    suspend fun refresh() {
        rules = app.repo.allRules()
        logs = app.repo.recentLogs(500)
        copiedCount = app.ledger.count()
        state = app.bridge.state()
    }

    fun closeEditor(nextTab: Int?) {
        editing = null
        draft = null
        editingDirty = false
        pendingTab = null
        if (nextTab != null) tab = nextTab
    }

    fun saveDraft() {
        val r = draft ?: editing ?: return
        val next = pendingTab
        scope.launch {
            if (r.id == 0L) app.repo.insertRule(r) else app.repo.updateRule(r)
            closeEditor(next)
            refresh()
            snackbar.showSnackbar("已保存「${r.name}」")
        }
    }

    /** 离开编辑页（返回键 / 点标签页 / 取消）统一走这里：改过就先问一句。 */
    fun leaveEditor(nextTab: Int?) {
        when {
            editing == null -> if (nextTab != null) tab = nextTab
            !editingDirty -> closeEditor(nextTab)
            else -> {
                pendingTab = nextTab
                askSave = true
            }
        }
    }

    if (editing != null) {
        BackHandler { leaveEditor(null) }
    } else if (tab != 0) {
        // 在日志/设置页按返回，回到规则页，而不是直接退出 App
        BackHandler { tab = 0 }
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
    // 后台自己搬完东西之后，列表和日志要能自己刷新，不能一直显示旧的
    LaunchedEffect(tab, editing) {
        while (true) {
            rules = app.repo.allRules()
            logs = app.repo.recentLogs(500)
            copiedCount = app.ledger.count()
            delay(5_000)
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
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                // 点提示条本身也能关掉（「撤销删除」那个按钮自己响应自己的点击）
                Snackbar(
                    snackbarData = data,
                    modifier = Modifier.clickable { data.dismiss() },
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { leaveEditor(0) },
                    icon = { Icon(Icons.Filled.Share, contentDescription = null) },
                    label = { Text("规则") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { leaveEditor(1) },
                    icon = { Icon(Icons.Filled.List, contentDescription = null) },
                    label = { Text("日志") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { leaveEditor(2) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("设置") },
                )
            }
        },
    ) { padding ->
        // Box 在外、Column 在内：这样下面的编辑页转场是 Column 的兄弟，不会被 ColumnScope 抢走
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                // 三个标签页之间淡入淡出
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                    label = "tab",
                    modifier = Modifier.fillMaxSize(),
                ) { which ->
                    when (which) {
                        0 -> RuleListScreen(
                    rules = rules,
                    state = state,
                    movedTotal = rules.sumOf { it.totalMoved },
                    onSelfCheck = { selfCheck() },
                    onRequestPermission = { app.bridge.requestPermission() },
                    onOpenShizuku = {
                        val pkg = "moe.shizuku.manager"
                        val launch = context.packageManager.getLaunchIntentForPackage(pkg)
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
                    onToggleRuleEnabled = { rule, enabled ->
                        scope.launch {
                            app.setRulesEnabled(listOf(rule.id), enabled)
                            refresh()
                            snackbar.showSnackbar(
                                if (enabled) "已开启「${rule.name}」" else "已停止「${rule.name}」",
                            )
                        }
                    },
                    onEnableSelected = { ids ->
                        scope.launch {
                            val n = app.setRulesEnabled(ids, enabled = true)
                            refresh()
                            snackbar.showSnackbar("已开启 $n 条规则")
                        }
                    },
                    onDisableSelected = { ids ->
                        scope.launch {
                            val n = app.setRulesEnabled(ids, enabled = false)
                            refresh()
                            snackbar.showSnackbar("已停止 $n 条规则")
                        }
                    },
                    onCopySelected = { ids ->
                        scope.launch {
                            val now = System.currentTimeMillis()
                            var n = 0
                            rules.filter { it.id in ids }.forEach { r ->
                                app.repo.insertRule(
                                    r.copy(
                                        id = 0,
                                        name = "${r.name} 副本",
                                        enabled = false,
                                        state = RuleState.IDLE,
                                        pauseReason = null,
                                        lastRunAt = null,
                                        lastMoved = 0,
                                        lastFailed = 0,
                                        totalMoved = 0,
                                        totalFailed = 0,
                                        consecutiveFailures = 0,
                                        createdAt = now,
                                        updatedAt = now,
                                    ),
                                )
                                n++
                            }
                            refresh()
                            snackbar.showSnackbar("已复制 $n 条（都是暂停状态，改好再开）")
                        }
                    },
                    onUndoSelected = { ids ->
                        scope.launch {
                            if (app.bridge.state() != ShizukuState.READY) app.bridge.bindWithRetry(1)
                            var done = 0
                            var skipped = 0
                            rules.filter { it.id in ids }.forEach { r ->
                                val s = app.undoRule(r.id)
                                done += s.done
                                skipped += s.skipped
                            }
                            refresh()
                            snackbar.showSnackbar("撤回完成：成功 $done · 跳过 $skipped")
                        }
                    },
                    onDeleteSelected = { ids ->
                        scope.launch {
                            val targets = rules.filter { it.id in ids }
                            // 删之前逐条留底：底部提示里点「撤销删除」能整批放回去
                            val snapshots = targets.associate { it.id to app.repo.snapshotForRule(it.id) }
                            targets.forEach { app.repo.deleteRule(it.id) }
                            refresh()
                            val names = if (targets.size == 1) "「${targets.first().name}」" else "${targets.size} 条规则"
                            val res = snackbar.showSnackbar(
                                message = "已删除 $names",
                                actionLabel = "撤销删除",
                                withDismissAction = true,
                                duration = SnackbarDuration.Long,
                            )
                            if (res == SnackbarResult.ActionPerformed) {
                                targets.forEach { r ->
                                    snapshots[r.id]?.let { app.repo.restoreDeleted(r, it) }
                                }
                                refresh()
                                snackbar.showSnackbar("已恢复 $names，什么都没少")
                            }
                        }
                    },
                    compact = settings.ruleCardsCompact,
                    onCompactChange = { compact -> scope.launch { app.settings.setRuleCardsCompact(compact) } },
                    onAdd = {
                        val now = System.currentTimeMillis()
                        val fresh = RuleEntity(
                            name = "新规则",
                            srcPath = RuleEntity.PICKER_ROOT,
                            dstPath = RuleEntity.DEFAULT_DST,
                            createdAt = now,
                            updatedAt = now,
                        )
                        draft = fresh
                        editingDirty = false
                        editing = fresh
                    },
                    onAddFromTemplate = { template ->
                        val fresh = template.toEntity(System.currentTimeMillis())
                        draft = fresh
                        editingDirty = false
                        editing = fresh
                    },
                    templates = settings.ruleTemplates,
                    onDeleteTemplate = { t ->
                        scope.launch {
                            app.settings.setRuleTemplates(settings.ruleTemplates.filterNot { it.id == t.id })
                            snackbar.showSnackbar("已删除模板「${t.name}」")
                        }
                    },
                    onCopy = { r ->
                        scope.launch {
                            val now = System.currentTimeMillis()
                            // 副本默认「暂停」：避免复制出来就立刻重复搬一遍；改好再打开
                            app.repo.insertRule(
                                r.copy(
                                    id = 0,
                                    name = "${r.name} 副本",
                                    enabled = false,
                                    state = RuleState.IDLE,
                                    pauseReason = null,
                                    lastRunAt = null,
                                    lastMoved = 0,
                                    lastFailed = 0,
                                    totalMoved = 0,
                                    totalFailed = 0,
                                    consecutiveFailures = 0,
                                    createdAt = now,
                                    updatedAt = now,
                                ),
                            )
                            refresh()
                            snackbar.showSnackbar("已复制「${r.name} 副本」（默认暂停，改好再开）")
                        }
                    },
                    onEdit = {
                        draft = it
                        editingDirty = false
                        editing = it
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
                            // 删之前留一份底：万一误点，下面那条带「撤销删除」的提示能把规则连记录一起放回去
                            val snapshot = app.repo.snapshotForRule(r.id)
                            app.repo.deleteRule(r.id)
                            refresh()
                            val res = snackbar.showSnackbar(
                                message = "已删除「${r.name}」",
                                actionLabel = "撤销删除",
                                withDismissAction = true,
                                duration = SnackbarDuration.Long,
                            )
                            if (res == SnackbarResult.ActionPerformed) {
                                app.repo.restoreDeleted(r, snapshot)
                                refresh()
                                snackbar.showSnackbar("已恢复「${r.name}」，什么都没少")
                            }
                        }
                    },
                )

                1 -> LogScreen(
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
                    onClearAll = {
                        scope.launch {
                            app.repo.clearLogs()
                            refresh()
                            snackbar.showSnackbar("日志已清空")
                        }
                    },
                )

                else -> SettingsScreen(
                    settings = settings,
                    store = app.settings,
                    onSelfCheck = { selfCheck() },
                    copiedCount = copiedCount,
                    onClearCopied = {
                        scope.launch {
                            val n = app.ledger.clear()
                            refresh()
                            snackbar.showSnackbar("已清空复制记录（$n 条）")
                        }
                    },
                )
                    }
                }
            }

            // 编辑页：从右边滑进来，盖在标签页上面（里面的按钮/开关照常各自响应）
                val editShown = editing ?: lastEdit
                AnimatedVisibility(
                    visible = editing != null,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(180)),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(120)),
                ) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        if (editShown != null) {
                            RuleEditScreen(
                                initial = editShown,
                                bridge = app.bridge,
                                otherRules = rules.filter { it.id != editShown.id },
                                onDraftChange = { draft = it },
                                onDirtyChange = { editingDirty = it },
                                onSaveAsTemplate = { r, tplName ->
                                    scope.launch {
                                        val t = RuleTemplate.fromRule(r, tplName)
                                        app.settings.setRuleTemplates(settings.ruleTemplates + t)
                                        snackbar.showSnackbar("已存为模板「${t.name}」——新建规则时可选")
                                    }
                                },
                                onSave = { r ->
                                    draft = r
                                    saveDraft()
                                },
                                // 取消 = 放弃本次修改，直接回规则列表
                                onCancel = { closeEditor(null) },
                            )
                        }
                    }
                }
            }
        }

    if (askSave) {
        AlertDialog(
            onDismissRequest = { askSave = false },
            title = { Text("还没保存") },
            text = {
                Text(
                    "「${draft?.name ?: "这条规则"}」改过了。要保存吗？" +
                        if (pendingTab != null) "保存/放弃后会切到对应页面。" else "",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askSave = false
                    saveDraft()
                }) { Text("保存") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        askSave = false
                        pendingTab = null
                    }) { Text("继续编辑") }
                    TextButton(onClick = {
                        askSave = false
                        closeEditor(pendingTab)
                    }) { Text("放弃修改") }
                }
            },
        )
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
