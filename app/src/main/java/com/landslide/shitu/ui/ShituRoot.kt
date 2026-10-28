package com.landslide.shitu.ui

import android.animation.ValueAnimator
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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.landslide.shitu.R
import com.landslide.shitu.ShituApp
import com.landslide.shitu.core.RuleTemplate
import com.landslide.shitu.data.Settings
import com.landslide.shitu.data.db.CopiedEntity
import com.landslide.shitu.data.db.LogEntity
import com.landslide.shitu.data.db.RuleEntity
import com.landslide.shitu.data.db.RuleState
import com.landslide.shitu.engine.HealthChecker
import com.landslide.shitu.shizuku.ShizukuState
import com.landslide.shitu.ui.logs.LogScreen
import com.landslide.shitu.ui.nav.ShituDockedBar
import com.landslide.shitu.ui.nav.ShituDock
import com.landslide.shitu.ui.nav.ShituSidebar
import com.landslide.shitu.ui.nav.destinationLabel
import com.landslide.shitu.ui.rules.RuleEditScreen
import com.landslide.shitu.ui.rules.RuleListScreen
import com.landslide.shitu.ui.settings.SettingsFlow
import com.landslide.shitu.ui.design.AppMotion
import com.landslide.shitu.ui.theme.ShituSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** App 根界面：三个标签页 + 规则编辑页 + 自检结果弹窗。 */
@Composable
fun ShituRoot(
    app: ShituApp,
    /** 从「有新版本」通知点进来：直接切到设置页并打开更新弹窗 */
    autoOpenUpdate: Boolean = false,
    onAutoOpenUpdateConsumed: () -> Unit = {},
    /** 桌面快捷方式：0 规则 / 1 日志 / 2 设置 */
    startTab: Int? = null,
    onStartTabConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())

    // 旋转屏幕、折叠屏展开后 Tab 不能丢
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var settingsDetail by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RuleEntity?>(null) }
    // 编辑页在做退出动画时 editing 已经变成 null，用它撑住那几帧画面
    var lastEdit by remember { mutableStateOf<RuleEntity?>(null) }
    LaunchedEffect(editing) { editing?.let { lastEdit = it } }
    var draft by remember { mutableStateOf<RuleEntity?>(null) }
    var editingDirty by remember { mutableStateOf(false) }
    var askSave by remember { mutableStateOf(false) }
    var pendingTab by remember { mutableStateOf<Int?>(null) }
    var rules by remember { mutableStateOf<List<RuleEntity>>(emptyList()) }
    /** 刚拖动后的顺序：DataStore 还没写完时先压住刷新，避免 5 秒轮询把它顶回去 */
    var localOrder by remember { mutableStateOf<List<Long>?>(null) }
    var logs by remember { mutableStateOf<List<LogEntity>>(emptyList()) }
    var copiedCount by remember { mutableStateOf(0) }
    /** 本周统计；拿不到就不显示统计行，不占位 */
    var stats by remember { mutableStateOf<ShituApp.Stats?>(null) }
    /** 复制记录列表（设置页多选清除用） */
    var copiedItems by remember { mutableStateOf<List<CopiedEntity>>(emptyList()) }
    var state by remember { mutableStateOf(app.bridge.state()) }
    var channel by remember { mutableStateOf(app.channel()) }
    var health by remember { mutableStateOf<List<HealthChecker.Item>?>(null) }
    var checking by remember { mutableStateOf(false) }
    var askBattery by remember { mutableStateOf(false) }
    /** 通知点进来后，等设置页真正显示出来再触发一次「检查更新」 */
    var pendingUpdateCheck by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    /** 系统设置里关掉动画（开发者选项 → 动画时长 0）时，转场直接切，不做位移 */
    val reduceMotion = remember { !ValueAnimator.areAnimatorsEnabled() }

    suspend fun loadRules() {
        val order = localOrder
            ?: runCatching { app.currentSettings().ruleOrder }.getOrDefault(emptyList())
        rules = app.repo.allRules().let { list ->
            if (order.isEmpty()) {
                list
            } else {
                val position = order.withIndex().associate { (index, id) -> id to index }
                list.sortedWith(
                    compareBy<RuleEntity> { position[it.id] ?: Int.MAX_VALUE }
                        .thenBy { it.id },
                )
            }
        }
    }

    suspend fun refresh() {
        loadRules()
        logs = app.repo.recentLogs(500)
        copiedCount = app.ledger.count()
        copiedItems = runCatching { app.ledger.recent(500) }.getOrDefault(emptyList())
        stats = runCatching { app.stats() }.getOrNull()
        state = app.bridge.state()
        channel = app.channel()
    }

    fun closeEditor(nextTab: Int?) {
        editing = null
        draft = null
        editingDirty = false
        pendingTab = null
        if (nextTab != null) tab = nextTab
    }

    /** 规则手动排序：把当前显示顺序写进设置，刷新后按新顺序显示。 */
    fun moveRule(rule: RuleEntity, delta: Int) {
        val from = rules.indexOfFirst { it.id == rule.id }
        if (from < 0) return
        val to = (from + delta).coerceIn(rules.indices)
        if (to == from) return
        val reordered = rules.toMutableList().apply {
            add(to, removeAt(from))
        }
        // 先乐观更新界面，再落盘；不要等 DataStore 回来才动，否则松手时会回弹
        rules = reordered
        val ids = reordered.map { it.id }
        localOrder = ids
        scope.launch {
            val ok = runCatching { app.settings.setRuleOrder(ids) }.isSuccess
            if (ok) {
                // 期间又拖了一次的话，别把新的本地顺序清掉
                if (localOrder == ids) localOrder = null
            } else {
                localOrder = null
                refresh()
            }
        }
    }

    fun saveDraft() {        val r = draft ?: editing ?: return
        val next = pendingTab
        scope.launch {
            if (r.id == 0L) app.repo.insertRule(r) else app.repo.updateRule(r)
            closeEditor(next)
            refresh()
            snackbar.showSnackbar(context.getString(R.string.app_rule_saved, r.name))
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
    // 点「有新版本」通知进来：切到设置页，并让设置页自动打开更新弹窗（只消费一次）
    LaunchedEffect(autoOpenUpdate) {
        if (autoOpenUpdate) {
            tab = 2
            pendingUpdateCheck = true
            onAutoOpenUpdateConsumed()
        }
    }
    LaunchedEffect(startTab) {
        if (startTab != null) {
            tab = startTab
            onStartTabConsumed()
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_000)
            state = app.bridge.state()
            channel = app.channel()
        }
    }
    // 后台自己搬完东西之后，列表和日志要能自己刷新，不能一直显示旧的。
    // 这里必须走 loadRules()：直接 allRules() 会把手动排序覆盖回数据库 id 顺序。
    LaunchedEffect(tab, editing) {
        while (true) {
            loadRules()
            logs = app.repo.recentLogs(500)
            copiedCount = app.ledger.count()
            delay(5_000)
        }
    }

    fun selfCheck() {
        scope.launch {
            checking = true
            // 已经有本地通道时，Shizuku 没就绪不算问题，不要再弹「没装 Shizuku」
            if (!app.hasChannel()) {
                when (app.bridge.state()) {
                    ShizukuState.NOT_INSTALLED ->
                        snackbar.showSnackbar(context.getString(R.string.app_shizuku_missing))
                    ShizukuState.NOT_RUNNING ->
                        snackbar.showSnackbar(context.getString(R.string.app_shizuku_not_running))
                    ShizukuState.NO_PERMISSION -> {
                        app.bridge.requestPermission()
                        snackbar.showSnackbar(context.getString(R.string.app_shizuku_permission_requested))
                    }
                    else -> Unit
                }
            }
            app.ensureChannel()
            val first = rules.firstOrNull()
            val defaultSrc = if (app.channel() == ShituApp.Channel.LOCAL) {
                "/sdcard/Pictures"
            } else {
                "/sdcard/Android/data"
            }
            val report = HealthChecker(
                bridge = app.fileBridge,
                probeSrcDir = first?.srcPath ?: defaultSrc,
                probeDstDir = first?.dstPath ?: RuleEntity.DEFAULT_DST,
                environmentLine = { app.channelLine() + " / " + app.bridge.environmentLine() },
            ).run()
            health = report
            checking = false
            app.settings.setLastSelfCheck(renderReport(context, report))
        }
    }

    // 宽屏（平板 / 折叠屏展开 / 横屏）走左侧导航栏；手机仍旧是底栏
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val expanded = maxWidth >= 840.dp

        Scaffold(
            containerColor = ShituSurface.page(),
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
            // 浮起 Dock 不占 Scaffold 的底栏空间：它叠在内容上，页面自己留底部内衬。
            if (!wide && editing == null && !settingsDetail && settings.navStyle == 1) {
                ShituDockedBar(
                    selected = tab,
                    onSelect = { leaveEditor(it) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxSize()) {
            if (wide) {
                ShituSidebar(
                    selected = tab,
                    onSelect = { leaveEditor(it) },
                    expanded = expanded,
                )
            }
            Box(
                Modifier.weight(1f).fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                // 平板横屏别把内容拉满整行：最宽 840dp，居中
                Box(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                // 三个标签页之间：0.15 屏宽位移 + 300ms，淡入淡出压住换页白闪
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        if (settings.tabAnimation == 1) {
                            val d = if (reduceMotion) 0 else AppMotion.TabFadeMs
                            fadeIn(tween(d)) togetherWith fadeOut(tween(if (reduceMotion) 0 else 150))
                        } else {
                            val dir = if (targetState < initialState) -1 else 1
                            val duration = if (reduceMotion) 0 else AppMotion.PageMs
                            val shift = { full: Int -> (full * 0.25f * dir).toInt() }
                            (
                                fadeIn(tween(duration, easing = AppMotion.PageEasing)) +
                                    slideInHorizontally(
                                        tween(duration, easing = AppMotion.PageEasing),
                                    ) { shift(it) }
                                ) togetherWith (
                                fadeOut(tween(duration * 3 / 10)) +
                                    slideOutHorizontally(
                                        tween(duration, easing = AppMotion.PageEasing),
                                    ) { -shift(it) }
                                )
                        }
                    },
                    label = "tab",
                    modifier = Modifier.fillMaxSize(),
                ) { which ->
                    when (which) {
                        0 -> RuleListScreen(
                    rules = rules,
                    state = state,
                    localReady = channel == ShituApp.Channel.LOCAL,
                    stats = stats,
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
                                context.getString(
                                    if (enabled) R.string.app_rule_enabled else R.string.app_rule_disabled,
                                    rule.name,
                                ),
                            )
                        }
                    },
                    compact = settings.ruleCardsCompact,
                    onCompactChange = { compact -> scope.launch { app.settings.setRuleCardsCompact(compact) } },
                    onAdd = {
                        val now = System.currentTimeMillis()
                        // 新规则一开始三项都是空的：名称要用户自己起，目录要用户自己选（从 /sdcard 开始）
                        val fresh = RuleEntity(
                            name = "",
                            srcPath = "",
                            dstPath = "",
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
                            snackbar.showSnackbar(context.getString(R.string.app_template_deleted, t.name))
                        }
                    },
                    onCopy = { r ->
                        scope.launch {
                            val now = System.currentTimeMillis()
                            val copyName = context.getString(R.string.app_rule_copy_suffix, r.name)
                            // 副本默认「暂停」：避免复制出来就立刻重复搬一遍；改好再打开
                            app.repo.insertRule(
                                r.copy(
                                    id = 0,
                                    name = copyName,
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
                            snackbar.showSnackbar(context.getString(R.string.app_rule_copied_one, copyName))
                        }
                    },
                    onEdit = {
                        draft = it
                        editingDirty = false
                        editing = it
                    },
                    onRunNow = { r ->
                        scope.launch {
                            snackbar.showSnackbar(context.getString(R.string.app_rule_running, r.name))
                            if (!app.ensureChannel()) {
                                snackbar.showSnackbar(app.channelLine())
                                return@launch
                            }
                            val result = app.runOne(r, settings)
                            refresh()
                            snackbar.showSnackbar(
                                result.error ?: context.getString(
                                    R.string.app_rule_run_result,
                                    r.name,
                                    result.moved,
                                    result.failed,
                                    result.skipped,
                                ),
                            )
                        }
                    },
                    onUndo = { r ->
                        scope.launch {
                            if (!app.ensureChannel()) {
                                snackbar.showSnackbar(app.channelLine())
                                return@launch
                            }
                            val s = app.undoRule(r.id)
                            refresh()
                            val note = s.notes.firstOrNull()
                            snackbar.showSnackbar(
                                if (note == null) {
                                    context.getString(R.string.app_undo_finished, s.done, s.skipped)
                                } else {
                                    context.getString(R.string.app_undo_finished_note, s.done, s.skipped, note)
                                },
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
                                message = context.getString(R.string.app_rule_deleted, r.name),
                                actionLabel = context.getString(R.string.app_undo_delete),
                                withDismissAction = true,
                                duration = SnackbarDuration.Long,
                            )
                            if (res == SnackbarResult.ActionPerformed) {
                                app.repo.restoreDeleted(r, snapshot)
                                refresh()
                                snackbar.showSnackbar(context.getString(R.string.app_rule_restored, r.name))
                            }
                        }
                    },
                    onSetRulesEnabled = { ids, enabled ->
                        scope.launch {
                            app.setRulesEnabled(ids, enabled)
                            refresh()
                            snackbar.showSnackbar(
                                context.getString(
                                    if (enabled) R.string.app_rules_enabled_count else R.string.app_rules_disabled_count,
                                    ids.size,
                                ),
                            )
                        }
                    },
                    onMoveRule = { rule, delta -> moveRule(rule, delta) },
                    onDeleteSelected = { list ->
                        scope.launch {
                            list.forEach { r -> app.repo.deleteRule(r.id) }
                            refresh()
                            snackbar.showSnackbar(
                                context.getString(R.string.app_rules_deleted_count, list.size),
                            )
                        }
                    },
                )

                1 -> LogScreen(
                    logs = logs,
                    rules = rules,
                    onExport = {
                        scope.launch {
                            snackbar.showSnackbar(context.getString(R.string.app_exporting))
                            val path = runCatching { app.exportLogs() }
                                .getOrElse { context.getString(R.string.app_export_failed, it.message) }
                            refresh()
                            snackbar.showSnackbar(context.getString(R.string.app_logs_exported, path))
                        }
                    },
                    onClearAll = {
                        scope.launch {
                            app.repo.clearLogs()
                            refresh()
                            snackbar.showSnackbar(context.getString(R.string.app_logs_cleared))
                        }
                    },
                )

                else -> SettingsFlow(
                    app = app,
                    settings = settings,
                    copiedCount = copiedCount,
                    copiedItems = copiedItems,
                    rulesCount = rules.size,
                    onSelfCheck = { selfCheck() },
                    onRefresh = { scope.launch { refresh() } },
                    onSnackbar = { message -> scope.launch { snackbar.showSnackbar(message) } },
                    onDetailChanged = { settingsDetail = it },
                )
                    }
                }
            }
                    } // 内容最宽 840dp
                } // 剩余宽度 Box
            } // Row

            // 编辑页：从右边滑进来，盖在标签页上面（里面的按钮/开关照常各自响应）
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                    val editShown = editing ?: lastEdit
                    AnimatedVisibility(
                        visible = editing != null,
                        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(180)),
                        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(120)),
                    ) {
                        Surface(Modifier.fillMaxSize(), color = ShituSurface.page()) {
                            if (editShown != null) {
                                RuleEditScreen(
                                    initial = editShown,
                                    bridge = app.fileBridge,
                                    otherRules = rules.filter { it.id != editShown.id },
                                    onDraftChange = { draft = it },
                                    onDirtyChange = { editingDirty = it },
                                    onSaveAsTemplate = { r, tplName ->
                                        scope.launch {
                                            val t = RuleTemplate.fromRule(r, tplName)
                                            app.settings.setRuleTemplates(settings.ruleTemplates + t)
                                            snackbar.showSnackbar(
                                                context.getString(R.string.app_template_saved, t.name),
                                            )
                                        }
                                    },
                                    onSave = { r ->
                                        draft = r
                                        saveDraft()
                                    },
                                    onPreview = { rule -> app.previewRule(rule) },
                                    // 取消 = 放弃本次修改，直接回规则列表
                                    onCancel = { closeEditor(null) },
                                )
                            }
                        }
                    }
                }
            }
            if (!wide && editing == null && !settingsDetail && settings.navStyle == 0) {
                ShituDock(
                    selected = tab,
                    onSelect = { leaveEditor(it) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                )
            }
        } // 外层 Box
        } // Scaffold
    } // BoxWithConstraints

    if (askSave) {
        AlertDialog(
            onDismissRequest = { askSave = false },
            title = { Text(stringResource(R.string.app_unsaved_title)) },
            text = {
                Text(
                    stringResource(
                        if (pendingTab != null) R.string.app_unsaved_text_switch else R.string.app_unsaved_text,
                        draft?.name ?: stringResource(R.string.app_unsaved_default_name),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askSave = false
                    saveDraft()
                }) { Text(stringResource(R.string.app_save)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        askSave = false
                        pendingTab = null
                    }) { Text(stringResource(R.string.app_keep_editing)) }
                    TextButton(onClick = {
                        askSave = false
                        closeEditor(pendingTab)
                    }) { Text(stringResource(R.string.app_discard)) }
                }
            },
        )
    }

    if (checking) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(stringResource(R.string.app_checking_title)) },
            text = {
                Column {
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth(),
                        trackColor = ShituSurface.inset(),
                    )
                    Text(
                        stringResource(R.string.app_checking_text),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
        )
    }

    health?.let { report ->
        val text = renderReport(context, report)
        AlertDialog(
            onDismissRequest = { health = null },
            title = {
                Text(
                    stringResource(
                        if (report.all { it.passed }) R.string.app_check_passed else R.string.app_check_failed,
                    ),
                )
            },
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
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.app_result_copied)) }
                }) { Text(stringResource(R.string.app_copy_result)) }
            },
            dismissButton = {
                TextButton(onClick = { health = null }) { Text(stringResource(R.string.app_close)) }
            },
        )
    }

    if (askBattery) {
        AlertDialog(
            onDismissRequest = { askBattery = false },
            title = { Text(stringResource(R.string.app_battery_title)) },
            text = {
                Text(stringResource(R.string.app_battery_text))
            },
            confirmButton = {
                TextButton(onClick = {
                    askBattery = false
                    requestIgnoreBattery(context)
                }) { Text(stringResource(R.string.app_go_to_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { askBattery = false }) { Text(stringResource(R.string.app_later)) }
            },
        )
    }
}

private fun renderReport(context: Context, report: List<HealthChecker.Item>): String =
    report.joinToString("\n") {
        context.getString(
            if (it.passed) R.string.app_report_pass else R.string.app_report_fail,
            it.name,
            it.detail,
        )
    }

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_clip_label), text))
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
