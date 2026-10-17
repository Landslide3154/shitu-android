package com.landslide.shitu.ui.rules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.landslide.shitu.R
import com.landslide.shitu.ShituApp
import com.landslide.shitu.core.RuleTemplate
import com.landslide.shitu.data.db.Labels
import com.landslide.shitu.data.db.Mode
import com.landslide.shitu.data.db.RuleEntity
import com.landslide.shitu.shizuku.ShizukuState
import com.landslide.shitu.ui.components.BannerCard
import com.landslide.shitu.ui.components.StatusDot
import com.landslide.shitu.ui.components.StatusPill
import com.landslide.shitu.ui.status.canResume
import com.landslide.shitu.ui.status.ruleStatusText
import com.landslide.shitu.ui.status.ruleTone
import com.landslide.shitu.ui.status.shizukuActionLabel
import com.landslide.shitu.ui.status.shizukuAdvice
import com.landslide.shitu.ui.status.shizukuTitle
import com.landslide.shitu.ui.status.shizukuTone
import com.landslide.shitu.ui.theme.ShituRadius
import com.landslide.shitu.ui.theme.Tone
import com.landslide.shitu.ui.theme.color
import com.landslide.shitu.ui.theme.container
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val timeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.US)

/** 左滑露出的红底「删除」块宽度 */
private val REVEAL_WIDTH = 96.dp

/**
 * 规则列表（规格 §10 页面 1）。
 *
 * 交互改为「勾选 + 批量开关」：每条规则右下角一个复选框，底部一排
 * 全选 / 开启 / 停止，勾几条就开几条、停几条。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleListScreen(
    rules: List<RuleEntity>,
    state: ShizukuState,
    /** 已获得「所有文件访问」：没有 Shizuku 时也能搬普通目录 */
    localReady: Boolean,
    movedTotal: Long,
    /** 本周统计（未接线时为 null，顶部整行不显示） */
    stats: ShituApp.Stats? = null,
    onSelfCheck: () -> Unit,
    onRequestPermission: () -> Unit,
    /** 打开 Shizuku 应用（没装则打开官网），给「未运行 / 未安装」状态一个出口 */
    onOpenShizuku: () -> Unit,
    onAdd: () -> Unit,
    onAddFromTemplate: (RuleTemplate) -> Unit,
    /** 我自己的模板（在编辑页点「存为模板」存下来的） */
    templates: List<RuleTemplate>,
    onDeleteTemplate: (RuleTemplate) -> Unit,
    onEdit: (RuleEntity) -> Unit,
    onCopy: (RuleEntity) -> Unit,
    /** 参数保留：卡片上已不再显示「立即运行」按钮（打开页面自然会跑） */
    onRunNow: (RuleEntity) -> Unit,
    onUndo: (RuleEntity) -> Unit,
    onResume: (RuleEntity) -> Unit,
    onDelete: (RuleEntity) -> Unit,
    onToggleRuleEnabled: (RuleEntity, Boolean) -> Unit,
    onEnableSelected: (List<Long>) -> Unit,
    onDisableSelected: (List<Long>) -> Unit,
    /** 对勾选中的规则批量操作 */
    onCopySelected: (List<Long>) -> Unit,
    onUndoSelected: (List<Long>) -> Unit,
    onDeleteSelected: (List<Long>) -> Unit,
    /** 简略模式：每张卡片只显示规则名 + 开关 */
    compact: Boolean,
    onCompactChange: (Boolean) -> Unit,
) {
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var showNew by remember { mutableStateOf(false) }
    // 删除前问一次：这个按钮以前紧挨着「撤回」，容易点错
    var pendingDelete by remember { mutableStateOf<RuleEntity?>(null) }
    var batchMenu by remember { mutableStateOf(false) }
    var askBatchDelete by remember { mutableStateOf(false) }
    var showWeekStats by remember { mutableStateOf(false) }
    LaunchedEffect(rules) {
        selected = selected intersect rules.map { it.id }.toSet()
    }
    val allSelected = rules.isNotEmpty() && selected.size == rules.size

    if (showNew) {
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text(stringResource(R.string.rules_new_rule)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NewRuleOption(
                        title = stringResource(R.string.rules_blank_rule),
                        detail = stringResource(R.string.rules_blank_rule_detail),
                        icon = Icons.Filled.Add,
                    ) {
                        showNew = false
                        onAdd()
                    }
                    if (templates.isEmpty()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(ShituRadius.RowShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp).padding(top = 2.dp),
                            )
                            Text(
                                stringResource(R.string.rules_no_templates_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                stringResource(R.string.rules_my_templates),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                            HorizontalDivider(
                                Modifier.weight(1f).padding(start = 10.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        templates.forEach { t ->
                            NewRuleOption(
                                title = t.name,
                                detail = t.detail(),
                                icon = Icons.Filled.Star,
                                onDelete = { onDeleteTemplate(t) },
                            ) {
                                showNew = false
                                onAddFromTemplate(t)
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.rules_template_footer_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showNew = false }) { Text(stringResource(R.string.rules_cancel)) } },
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.rules_delete_rule_title, target.name)) },
            text = {
                Text(stringResource(R.string.rules_delete_rule_body))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDelete(target)
                    },
                ) {
                    Text(stringResource(R.string.rules_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.rules_cancel)) }
            },
        )
    }

    if (askBatchDelete) {
        AlertDialog(
            onDismissRequest = { askBatchDelete = false },
            title = { Text(stringResource(R.string.rules_batch_delete_title, selected.size)) },
            text = {
                Text(stringResource(R.string.rules_batch_delete_body))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        askBatchDelete = false
                        onDeleteSelected(selected.toList())
                    },
                ) {
                    Text(stringResource(R.string.rules_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { askBatchDelete = false }) { Text(stringResource(R.string.rules_cancel)) }
            },
        )
    }

    if (showWeekStats) {
        stats?.let { WeekStatsDialog(stats = it, onDismiss = { showWeekStats = false }) }
    }

    Scaffold(
        // 外层 Scaffold 已经处理过状态栏内边距，这里再叠一次会多出一条空白
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = { showNew = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.rules_new_rule))
            }
        },
        bottomBar = {
            // 批量操作条放进 bottomBar：Scaffold 会把悬浮按钮自动抬高，避免遮住「停止」
            AnimatedVisibility(
                visible = rules.isNotEmpty(),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = allSelected,
                            onCheckedChange = { on ->
                                selected = if (on) rules.map { it.id }.toSet() else emptySet()
                            },
                        )
                        Text(
                            stringResource(R.string.rules_select_all),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(10.dp))
                        // 提示/已选数量占满中间剩余宽度：英文文案更长，不能把按钮挤出屏幕
                        Text(
                            if (selected.isEmpty()) {
                                stringResource(R.string.rules_batch_hint)
                            } else {
                                stringResource(R.string.rules_selected_count, selected.size)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected.isEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = { onEnableSelected(selected.toList()) },
                            enabled = selected.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) { Text(stringResource(R.string.rules_enable), maxLines = 1) }
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = { onDisableSelected(selected.toList()) },
                            enabled = selected.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) { Text(stringResource(R.string.rules_disable), maxLines = 1) }
                        // 「停止」右边的三个点：对勾选中的规则批量复制 / 撤回 / 删除
                        Box {
                            IconButton(
                                onClick = { batchMenu = true },
                                enabled = selected.isNotEmpty(),
                            ) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.rules_batch_actions_desc),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(
                                expanded = batchMenu,
                                onDismissRequest = { batchMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rules_copy_selected, selected.size)) },
                                    onClick = {
                                        batchMenu = false
                                        onCopySelected(selected.toList())
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rules_undo_selected)) },
                                    onClick = {
                                        batchMenu = false
                                        onUndoSelected(selected.toList())
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.rules_delete_selected),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        batchMenu = false
                                        askBatchDelete = true
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 通道状态：Shizuku 就绪 / 本地权限 / 两条都没有——三种说法，不能让人以为"用不了"
                val channelReady = state == ShizukuState.READY || localReady
                BannerCard(
                    tone = when {
                        state == ShizukuState.READY -> shizukuTone(state)
                        localReady -> Tone.OK
                        else -> shizukuTone(state)
                    },
                    icon = if (channelReady) {
                        Icons.Filled.CheckCircle
                    } else {
                        Icons.Filled.Warning
                    },
                    title = when {
                        state == ShizukuState.READY -> stringResource(shizukuTitle(state))
                        localReady -> stringResource(R.string.rules_local_mode_title)
                        else -> stringResource(shizukuTitle(state))
                    },
                    body = when {
                        state == ShizukuState.READY -> stringResource(shizukuAdvice(state))
                        localReady -> stringResource(R.string.rules_local_mode_body)
                        else -> stringResource(shizukuAdvice(state))
                    },
                    actionLabel = if (channelReady) {
                        null
                    } else {
                        shizukuActionLabel(state)?.let { stringResource(it) }
                    },
                    onAction = {
                        when (state) {
                            ShizukuState.NO_PERMISSION -> onRequestPermission()
                            ShizukuState.NOT_INSTALLED, ShizukuState.NOT_RUNNING -> onOpenShizuku()
                            else -> onSelfCheck()
                        }
                    },
                    onClick = onSelfCheck,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.rules_total_moved_label),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "$movedTotal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Text(
                        stringResource(R.string.rules_total_moved_unit),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.weight(1f))
                    FilterChip(
                        selected = compact,
                        onClick = { onCompactChange(true) },
                        label = { Text(stringResource(R.string.rules_compact)) },
                    )
                    Spacer(Modifier.width(6.dp))
                    FilterChip(
                        selected = !compact,
                        onClick = { onCompactChange(false) },
                        label = { Text(stringResource(R.string.rules_detailed)) },
                    )
                }
                // 本周统计：只有拿到数据才显示；null 时整行不占位，点了看 Top 3 来源 App
                stats?.let { s ->
                    val movedText = stringResource(R.string.rules_week_moved, s.weekMoved)
                    val failedText = if (s.weekFailed > 0) {
                        stringResource(R.string.rules_week_failed, s.weekFailed)
                    } else {
                        null
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(ShituRadius.RowShape)
                            .clickable { showWeekStats = true }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            if (failedText == null) movedText else "$movedText · $failedText",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
            HorizontalDivider()

            if (rules.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Filled.List,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.rules_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.rules_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = { showNew = true }) { Text(stringResource(R.string.rules_empty_cta)) }
                }
            } else {
                // 底部留出悬浮按钮的高度：否则最后一张卡片的按钮会被 FAB 盖住点不到
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    items(rules, key = { it.id }) { rule ->
                        RuleCard(
                            rule = rule,
                            compact = compact,
                            checked = rule.id in selected,
                            onCheckedChange = { on ->
                                selected = if (on) selected + rule.id else selected - rule.id
                            },
                            onEdit = { onEdit(rule) },
                            onCopy = { onCopy(rule) },
                            onUndo = { onUndo(rule) },
                            onResume = { onResume(rule) },
                            onDelete = { pendingDelete = rule },
                            onToggleEnabled = { on -> onToggleRuleEnabled(rule, on) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

/** 「新建规则」弹窗里的一行：图标 + 标题 + 说明，整块都能点；右侧可选的 ✕ = 删掉这条模板。 */
@Composable
private fun NewRuleOption(
    title: String,
    detail: String,
    icon: ImageVector,
    onDelete: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ShituRadius.RowShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ShituRadius.RowShape,
            )
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 6.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.rules_delete_template),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun RuleCard(
    rule: RuleEntity,
    compact: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onUndo: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 复制模式的规则在正常状态下连圆点和胶囊一起用蓝色；运行中/失败/暂停仍按状态色
    val tone = cardTone(rule)
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }

    // 左滑露出红底「删除」：滑过一半就吸附打开，否则弹回原位
    val revealPx = with(LocalDensity.current) { REVEAL_WIDTH.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    fun closeReveal() = scope.launch { offsetX.animateTo(0f, spring()) }
    val dragModifier = Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragEnd = {
                scope.launch {
                    offsetX.animateTo(
                        targetValue = if (offsetX.value < -revealPx / 2) -revealPx else 0f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    )
                }
            },
        ) { change, drag ->
            change.consume()
            scope.launch { offsetX.snapTo((offsetX.value + drag).coerceIn(-revealPx, 0f)) }
        }
    }
    // 长按卡片 = 弹菜单（复制 / 撤回 / 恢复 / 删除）；点一下 = 进编辑
    val longPressModifier = Modifier.combinedClickable(
        onClick = onEdit,
        onLongClick = {
            closeReveal()
            menu = true
        },
    )
    val ruleMenu: @Composable ColumnScope.() -> Unit = {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.rules_copy_rule)) },
            onClick = {
                menu = false
                onCopy()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.rules_undo_moved)) },
            onClick = {
                menu = false
                onUndo()
            },
        )
        if (canResume(rule)) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rules_resume)) },
                onClick = {
                    menu = false
                    onResume()
                },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.rules_delete), color = MaterialTheme.colorScheme.error) },
            onClick = {
                menu = false
                onDelete()
            },
        )
    }

    // 简略模式：一行——勾选框 + 状态点 + 规则名 + 状态胶囊 + 开关（点一下进编辑）
    if (compact) {
        Box(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)) {
            RevealDeleteAction {
                closeReveal()
                onDelete()
            }
            Card(
                Modifier
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .then(dragModifier),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(longPressModifier)
                        .padding(start = 4.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = onCheckedChange)
                    StatusDot(tone)
                    Text(
                        rule.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    // 简略模式也保留模式颜色：小标签贴在状态胶囊左边
                    ModeTag(rule.mode)
                    Spacer(Modifier.width(6.dp))
                    StatusPill(stringResource(ruleStatusText(rule)), tone)
                    Spacer(Modifier.width(10.dp))
                    Switch(checked = rule.enabled, onCheckedChange = onToggleEnabled)
                    // 菜单锚在这个 1dp 的小盒子上，弹出来就贴着卡片右上角
                    Box(Modifier.size(1.dp)) {
                        DropdownMenu(
                            expanded = menu,
                            onDismissRequest = { menu = false },
                            content = ruleMenu,
                        )
                    }
                }
            }
        }
        return
    }

    // 详细模式：整张卡片可点 = 进编辑，长按 = 弹菜单（里面的按钮/开关/勾选框照常各自响应）
    Box(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        RevealDeleteAction {
            closeReveal()
            onDelete()
        }
        Card(
            Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .then(dragModifier),
        ) {
            Column(Modifier.then(longPressModifier).padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 勾选放在标题行，不再单独占一行（底部批量栏用）
                Checkbox(checked = checked, onCheckedChange = onCheckedChange)
                StatusDot(tone, size = 10.dp)
                Text(
                    rule.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                StatusPill(stringResource(ruleStatusText(rule)), tone)
                Spacer(Modifier.width(10.dp))
                // 每条规则自己的开关（也能用底部「全选 + 开启/停止」批量操作）
                Switch(checked = rule.enabled, onCheckedChange = onToggleEnabled)
                // 菜单锚在这个 1dp 的小盒子上，弹出来就贴着卡片右上角
                Box(Modifier.size(1.dp)) {
                    DropdownMenu(
                        expanded = menu,
                        onDismissRequest = { menu = false },
                        content = ruleMenu,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            // 两行路径都从同一列开始，去掉箭头，读起来不用左右找
            Row(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.rules_path_from),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.width(34.dp),
                )
                Text(
                    rule.srcPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.rules_path_to),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.width(34.dp),
                )
                Text(
                    rule.dstPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(4.dp))
            val suffix = if (rule.suffix.isBlank()) {
                stringResource(R.string.rules_suffix_none)
            } else {
                rule.suffix
            }
            val renameSuffix = if (rule.contentRename) {
                stringResource(R.string.rules_content_rename_suffix)
            } else {
                ""
            }
            // 模式单独成胶囊（移动=绿、复制=蓝），后面跟间隔/后缀
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    Labels.mode(context, rule.mode),
                    modeTone(rule.mode),
                )
                Text(
                    stringResource(
                        R.string.rules_card_meta_detail,
                        rule.intervalMinutes,
                        suffix,
                        renameSuffix,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            rule.pauseReason?.let {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(
                    R.string.rules_card_stats,
                    rule.lastRunAt?.let { timeFmt.format(Date(it)) } ?: "—",
                    rule.lastMoved,
                    rule.totalMoved,
                    rule.totalFailed,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/** 模式配色：移动=绿、复制=蓝（与日志里的 COPIED 一致）。 */
private fun modeTone(mode: Mode): Tone = if (mode == Mode.COPY) Tone.COPY else Tone.OK

/**
 * 卡片状态色：复制模式的规则在正常状态下也用蓝色（和日志里的「已复制」一致）。
 * 运行中 / 失败 / 暂停 / 已停止仍然用原本的状态色，避免把异常状态也染成「正常蓝」。
 */
private fun cardTone(rule: RuleEntity): Tone {
    val status = ruleTone(rule)
    return if (rule.mode == Mode.COPY && status == Tone.OK) Tone.COPY else status
}

/** 很小的模式标签：移动=绿、复制=蓝；比状态胶囊更轻，放简略模式行里不抢空间。 */
@Composable
private fun ModeTag(mode: Mode) {
    val tone = modeTone(mode)
    Box(
        Modifier
            .clip(ShituRadius.PillShape)
            .background(tone.container())
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            Labels.mode(LocalContext.current, mode),
            style = MaterialTheme.typography.labelSmall,
            color = tone.color(),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** 本周统计弹窗：已搬 / 失败 + Top 3 来源 App；没有数据就直说「本周还没搬过」。 */
@Composable
private fun WeekStatsDialog(stats: ShituApp.Stats, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rules_week_dialog_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(
                        R.string.rules_week_dialog_summary,
                        stats.weekMoved,
                        stats.weekFailed,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (stats.topApps.isEmpty()) {
                    Text(
                        stringResource(R.string.rules_week_dialog_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Text(
                        stringResource(R.string.rules_week_top_apps),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                    )
                    stats.topApps.forEach { (app, count) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                app,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                stringResource(R.string.rules_week_top_item, count),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.rules_week_close)) }
        },
    )
}

/** 卡片背后那层：左滑露出来的红底「删除」，点它才真的进删除确认 */
@Composable
private fun BoxScope.RevealDeleteAction(onClick: () -> Unit) {
    Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(REVEAL_WIDTH)
                .clip(ShituRadius.CardShape)
                .background(MaterialTheme.colorScheme.error)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    stringResource(R.string.rules_delete),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onError,
                )
            }
        }
    }
}
