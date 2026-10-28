package com.landslide.shitu.ui.rules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.landslide.shitu.R
import com.landslide.shitu.ShituApp
import com.landslide.shitu.core.RuleTemplate
import com.landslide.shitu.data.db.Labels
import com.landslide.shitu.data.db.Mode
import com.landslide.shitu.data.db.RuleEntity
import com.landslide.shitu.data.db.RuleState
import com.landslide.shitu.shizuku.ShizukuState
import com.landslide.shitu.ui.components.CommonChip
import com.landslide.shitu.ui.components.DangerConfirmDialog
import com.landslide.shitu.ui.components.EmptyState
import com.landslide.shitu.ui.components.ListItemAction
import com.landslide.shitu.ui.components.Section
import com.landslide.shitu.ui.components.SelectionMark
import com.landslide.shitu.ui.components.ShituButton
import com.landslide.shitu.ui.components.ShituSwitch
import com.landslide.shitu.ui.components.ShituToolbar
import com.landslide.shitu.ui.components.SearchField
import com.landslide.shitu.ui.components.TonalChip
import com.landslide.shitu.ui.design.AppCorner
import com.landslide.shitu.ui.design.AppSurface
import com.landslide.shitu.ui.design.AppType
import com.landslide.shitu.ui.design.ShituIcons
import com.landslide.shitu.ui.design.SurfaceLevel
import com.landslide.shitu.ui.design.destructive
import com.landslide.shitu.ui.design.floatingColor
import com.landslide.shitu.ui.design.hairline
import com.landslide.shitu.ui.design.squircle
import com.landslide.shitu.ui.design.tabular
import com.landslide.shitu.ui.status.canResume
import com.landslide.shitu.ui.status.ruleStatusText
import com.landslide.shitu.ui.status.ruleTone
import com.landslide.shitu.ui.status.shizukuActionLabel
import com.landslide.shitu.ui.status.shizukuAdvice
import com.landslide.shitu.ui.status.shizukuTitle
import com.landslide.shitu.ui.status.shizukuTone
import com.landslide.shitu.ui.theme.Tone
import com.landslide.shitu.ui.theme.color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val timeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.US)
private val REVEAL_WIDTH = 96.dp

/** 一次排序拖拽的全局状态：被拖项用 offsetY 跟手，其余项按 targetIndex 让位。 */
private data class SortDragState(
    val seq: Int,
    val id: Long,
    val startIndex: Int,
    val targetIndex: Int,
    val offsetY: Float,
    val stridePx: Float,
)

@Composable
fun RuleListScreen(
    rules: List<RuleEntity>,
    state: ShizukuState,
    localReady: Boolean,
    movedTotal: Long,
    stats: ShituApp.Stats? = null,
    onSelfCheck: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    onAdd: () -> Unit,
    onAddFromTemplate: (RuleTemplate) -> Unit,
    templates: List<RuleTemplate>,
    onDeleteTemplate: (RuleTemplate) -> Unit,
    onEdit: (RuleEntity) -> Unit,
    onCopy: (RuleEntity) -> Unit,
    onRunNow: (RuleEntity) -> Unit,
    onUndo: (RuleEntity) -> Unit,
    onResume: (RuleEntity) -> Unit,
    onDelete: (RuleEntity) -> Unit,
    onToggleRuleEnabled: (RuleEntity, Boolean) -> Unit,
    compact: Boolean,
    onCompactChange: (Boolean) -> Unit,
    onSetRulesEnabled: (List<Long>, Boolean) -> Unit = { _, _ -> },
    onDeleteSelected: (List<RuleEntity>) -> Unit = {},
    onMoveRule: (RuleEntity, Int) -> Unit = { _, _ -> },
) {
    var showNew by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<RuleEntity?>(null) }
    var showWeekStats by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var askBatchDelete by remember { mutableStateOf(false) }
    var sortMode by rememberSaveable { mutableStateOf(false) }
    var sortDrag by remember { mutableStateOf<SortDragState?>(null) }
    var sortSeq by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val shown = rules.filter { rule ->
        query.isBlank() ||
            rule.name.contains(query, ignoreCase = true) ||
            rule.srcPath.contains(query, ignoreCase = true) ||
            rule.dstPath.contains(query, ignoreCase = true)
    }
    val itemSpacing = if (compact) 4.dp else 8.dp

    // 退出排序模式时清掉半路的位移，避免下一张卡带着旧偏移出现
    LaunchedEffect(sortMode) {
        if (!sortMode) sortDrag = null
    }

    fun startSortDrag(rule: RuleEntity, heightPx: Int) {
        // 按当前列表重新取索引：拖过一次后，卡片的位置变了，不能再用组合时捕获的旧 index
        val index = rules.indexOfFirst { it.id == rule.id }
        if (index < 0) return
        sortSeq += 1
        sortDrag = SortDragState(
            seq = sortSeq,
            id = rule.id,
            startIndex = index,
            targetIndex = index,
            offsetY = 0f,
            stridePx = (heightPx + with(density) { itemSpacing.toPx() }).coerceAtLeast(1f),
        )
    }

    fun updateSortDrag(id: Long, deltaY: Float, count: Int) {
        val drag = sortDrag ?: return
        if (drag.id != id || count <= 0) return
        val minOffset = -drag.startIndex * drag.stridePx
        val maxOffset = (count - 1 - drag.startIndex).coerceAtLeast(0) * drag.stridePx
        val offset = (drag.offsetY + deltaY).coerceIn(minOffset, maxOffset)
        val target = (drag.startIndex + (offset / drag.stridePx).roundToInt())
            .coerceIn(0, count - 1)
        sortDrag = drag.copy(offsetY = offset, targetIndex = target)
    }

    fun finishSortDrag(rule: RuleEntity) {
        val drag = sortDrag ?: return
        if (drag.id != rule.id) return
        scope.launch {
            // 先弹到目标格，再提交顺序；两件事同一帧完成，松手不会回弹。
            val settle = (drag.targetIndex - drag.startIndex) * drag.stridePx
            animate(
                initialValue = drag.offsetY,
                targetValue = settle,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            ) { value, _ ->
                val current = sortDrag
                if (current?.seq == drag.seq) sortDrag = current.copy(offsetY = value)
            }
            val current = sortDrag
            if (current?.seq == drag.seq) {
                if (drag.targetIndex != drag.startIndex) {
                    onMoveRule(rule, drag.targetIndex - drag.startIndex)
                }
                sortDrag = null
            }
        }
    }

    fun cancelSortDrag(id: Long) {
        val drag = sortDrag ?: return
        if (drag.id != id) return
        scope.launch {
            animate(
                initialValue = drag.offsetY,
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            ) { value, _ ->
                val current = sortDrag
                if (current?.seq == drag.seq) sortDrag = current.copy(offsetY = value)
            }
            if (sortDrag?.seq == drag.seq) sortDrag = null
        }
    }

    if (showNew) {
        NewRuleSheet(
            templates = templates,
            onDismiss = { showNew = false },
            onAdd = {
                showNew = false
                onAdd()
            },
            onAddFromTemplate = {
                showNew = false
                onAddFromTemplate(it)
            },
            onDeleteTemplate = onDeleteTemplate,
        )
    }
    pendingDelete?.let { target ->
        DangerConfirmDialog(
            title = stringResource(R.string.rules_delete_rule_title, target.name),
            body = stringResource(R.string.rules_delete_rule_body),
            confirmLabel = stringResource(R.string.rules_delete),
            dismissLabel = stringResource(R.string.rules_cancel),
            onConfirm = {
                pendingDelete = null
                onDelete(target)
            },
            onDismiss = { pendingDelete = null },
        )
    }
    if (showWeekStats) {
        stats?.let { WeekStatsSheet(stats = it, onDismiss = { showWeekStats = false }) }
    }
    if (askBatchDelete) {
        DangerConfirmDialog(
            title = stringResource(R.string.rules_batch_delete_title),
            body = stringResource(R.string.rules_batch_delete_body, selected.size),
            confirmLabel = stringResource(R.string.rules_delete_selected),
            dismissLabel = stringResource(R.string.rules_cancel),
            onConfirm = {
                askBatchDelete = false
                onDeleteSelected(rules.filter { it.id in selected })
                selected = emptySet()
            },
            onDismiss = { askBatchDelete = false },
        )
    }

    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ShituToolbar(
                title = stringResource(R.string.app_nav_rules),
            ) {
                IconButton(onClick = {
                    if (sortMode) sortMode = false
                    searchOpen = !searchOpen
                }) {
                    Icon(ShituIcons.Search, null, modifier = Modifier.size(24.dp))
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(ShituIcons.More, null, modifier = Modifier.size(24.dp))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        shape = squircle(AppCorner.xl),
                        containerColor = floatingColor(MaterialTheme.colorScheme),
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (compact) {
                                        stringResource(R.string.rules_menu_detailed)
                                    } else {
                                        stringResource(R.string.rules_menu_compact)
                                    },
                                )
                            },
                            onClick = {
                                showMenu = false
                                onCompactChange(!compact)
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (selectionMode) {
                                        stringResource(R.string.rules_menu_exit_select)
                                    } else {
                                        stringResource(R.string.rules_menu_select)
                                    },
                                )
                            },
                            onClick = {
                                showMenu = false
                                selectionMode = !selectionMode
                                selected = emptySet()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (sortMode) R.string.rules_menu_sort_done else R.string.rules_menu_sort,
                                    ),
                                )
                            },
                            onClick = {
                                showMenu = false
                                sortMode = !sortMode
                                if (sortMode) {
                                    // 排序只在完整列表上做；带搜索条件时索引和持久化顺序对不上
                                    query = ""
                                    searchOpen = false
                                }
                                selectionMode = false
                                selected = emptySet()
                            },
                        )
                        if (stats != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rules_week_dialog_title)) },
                                onClick = {
                                    showMenu = false
                                    showWeekStats = true
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = { showNew = true }) {
                    Icon(ShituIcons.Plus, null, modifier = Modifier.size(24.dp))
                }
            }
            AnimatedVisibility(visible = searchOpen) {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.rules_search_hint),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (rules.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = stringResource(R.string.rules_empty_title),
                        body = stringResource(R.string.rules_empty_body),
                        actionLabel = stringResource(R.string.rules_empty_cta),
                        onAction = { showNew = true },
                    )
                }
            } else if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = stringResource(R.string.rules_no_match_title),
                        body = stringResource(R.string.rules_no_match_body),
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 130.dp),
                    verticalArrangement = Arrangement.spacedBy(itemSpacing),
                ) {
                    item {
                        OverviewCard(
                            rules = rules,
                            state = state,
                            localReady = localReady,
                            movedTotal = movedTotal,
                            stats = stats,
                            onSelfCheck = onSelfCheck,
                            onRequestPermission = onRequestPermission,
                            onOpenShizuku = onOpenShizuku,
                        )
                    }
                    itemsIndexed(shown, key = { _, rule -> rule.id }) { index, rule ->
                        RuleCard(
                            rule = rule,
                            compact = compact,
                            selectionMode = selectionMode,
                            sortMode = sortMode,
                            selected = rule.id in selected,
                            index = index,
                            canMoveUp = query.isBlank() && index > 0,
                            canMoveDown = query.isBlank() && index < shown.lastIndex,
                            onMoveUp = { onMoveRule(rule, -1) },
                            onMoveDown = { onMoveRule(rule, 1) },
                            onEdit = { onEdit(rule) },
                            onCopy = { onCopy(rule) },
                            onRunNow = { onRunNow(rule) },
                            onUndo = { onUndo(rule) },
                            onResume = { onResume(rule) },
                            onDelete = { pendingDelete = rule },
                            onToggleEnabled = { on -> onToggleRuleEnabled(rule, on) },
                            onToggleSelected = {
                                selected = if (rule.id in selected) {
                                    selected - rule.id
                                } else {
                                    selected + rule.id
                                }
                            },
                            sortDrag = sortDrag,
                            onSortDragStart = { heightPx -> startSortDrag(rule, heightPx) },
                            onSortDrag = { deltaY -> updateSortDrag(rule.id, deltaY, rules.size) },
                            onSortDragEnd = { finishSortDrag(rule) },
                            onSortDragCancel = { cancelSortDrag(rule.id) },
                            modifier = if (sortMode) Modifier else Modifier.animateItem(),
                        )
                    }
                }
            }
        }
        if (selectionMode && selected.isNotEmpty()) {
            BatchBar(
                count = selected.size,
                onEnable = {
                    onSetRulesEnabled(selected.toList(), true)
                    selected = emptySet()
                },
                onPause = {
                    onSetRulesEnabled(selected.toList(), false)
                    selected = emptySet()
                },
                onDelete = {
                    askBatchDelete = true
                },
                onCancel = {
                    selectionMode = false
                    selected = emptySet()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 88.dp),
            )
        }
    }
}

@Composable
private fun OverviewCard(
    rules: List<RuleEntity>,
    state: ShizukuState,
    localReady: Boolean,
    movedTotal: Long,
    stats: ShituApp.Stats?,
    onSelfCheck: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val ready = state == ShizukuState.READY || localReady
    val tone = when {
        state == ShizukuState.READY -> shizukuTone(state)
        localReady -> Tone.OK
        else -> shizukuTone(state)
    }
    val title = when {
        state == ShizukuState.READY -> stringResource(shizukuTitle(state))
        localReady -> stringResource(R.string.rules_local_mode_title)
        else -> stringResource(shizukuTitle(state))
    }
    val body = when {
        state == ShizukuState.READY -> null
        localReady -> stringResource(R.string.rules_local_mode_body)
        else -> stringResource(shizukuAdvice(state))
    }
    val action = if (ready) null else shizukuActionLabel(state)?.let { stringResource(it) }
    val unit = stringResource(R.string.rules_unit_photos)
    val compactStats = LocalDensity.current.fontScale > 1.2f
    val totalLabel = stringResource(
        if (compactStats) R.string.rules_overview_total_short else R.string.rules_overview_total,
    )
    val enabledLabel = stringResource(
        if (compactStats) R.string.rules_overview_enabled_short else R.string.rules_overview_enabled,
    )
    AppSurface(
        level = SurfaceLevel.CARD,
        shape = squircle(AppCorner.xl),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                if (body == null && action == null) {
                    // 已就绪：标题和按钮包在同一个自适应宽度里，按钮中心严格对准标题中心
                    Row(verticalAlignment = Alignment.Top) {
                        ToneDot(tone, Modifier.padding(top = 6.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(title, style = AppType.Headline.style(emphasized = true), color = scheme.onSurface)
                            Spacer(Modifier.height(8.dp))
                            ShituButton(
                                text = stringResource(R.string.rules_self_check),
                                primary = false,
                                onClick = onSelfCheck,
                            )
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ToneDot(tone)
                        Spacer(Modifier.width(8.dp))
                        Text(title, style = AppType.Headline.style(emphasized = true), color = scheme.onSurface)
                    }
                    if (body != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(body, style = AppType.Footnote.style(), color = scheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (action != null) {
                            ShituButton(
                                text = action,
                                primary = false,
                                onClick = {
                                    when (state) {
                                        ShizukuState.NO_PERMISSION -> onRequestPermission()
                                        ShizukuState.NOT_INSTALLED, ShizukuState.NOT_RUNNING -> onOpenShizuku()
                                        else -> onSelfCheck()
                                    }
                                },
                            )
                        } else {
                            ShituButton(
                                text = stringResource(R.string.rules_self_check),
                                primary = false,
                                onClick = onSelfCheck,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatLine(totalLabel, "$movedTotal $unit")
                StatLine(stringResource(R.string.rules_overview_week), "${stats?.weekMoved ?: 0} $unit")
                StatLine(enabledLabel, "${rules.count { it.enabled }} / ${rules.size}")
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        Modifier.width(136.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = AppType.Caption1.style(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = AppType.Subheadline.style(emphasized = true).tabular(),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

@Composable
private fun TextButtonText(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = AppType.Subheadline.style(emphasized = true),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun ToneDot(tone: Tone, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).clip(CircleShape).background(tone.color()))
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RuleCard(
    rule: RuleEntity,
    compact: Boolean,
    selectionMode: Boolean,
    sortMode: Boolean,
    selected: Boolean,
    index: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onRunNow: () -> Unit,
    onUndo: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleSelected: () -> Unit,
    sortDrag: SortDragState?,
    onSortDragStart: (Int) -> Unit,
    onSortDrag: (Float) -> Unit,
    onSortDragEnd: () -> Unit,
    onSortDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val tone = cardTone(rule)
    var menu by remember { mutableStateOf(false) }
    var cardHeightPx by remember { mutableIntStateOf(0) }
    val revealPx = with(LocalDensity.current) { REVEAL_WIDTH.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val drag = sortDrag
    val isDragging = drag?.id == rule.id
    // 进入排序模式时把可能露出的左滑删除收回去，拖拽期间不再有横向位移
    LaunchedEffect(sortMode) {
        if (sortMode) offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy))
    }
    // 其他卡片按目标位置让位：向下拖时上方的项上移，向上拖时下方的项下移。
    val shiftTarget = when {
        drag == null || isDragging -> 0f
        drag.targetIndex > drag.startIndex && index in (drag.startIndex + 1)..drag.targetIndex ->
            -drag.stridePx
        drag.targetIndex < drag.startIndex && index in drag.targetIndex until drag.startIndex ->
            drag.stridePx
        else -> 0f
    }
    val shift = remember { Animatable(0f) }
    val dragActive = sortDrag != null
    LaunchedEffect(shiftTarget, dragActive) {
        if (!dragActive) {
            // 落位提交时索引会同时变化，这里必须瞬间归零，不能跟着动画滑回去
            shift.snapTo(0f)
        } else {
            shift.animateTo(
                targetValue = shiftTarget,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }
    val lift by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
        label = "sortLift",
    )
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 10.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
        label = "sortElevation",
    )
    fun closeReveal() = scope.launch { offsetX.animateTo(0f, spring()) }
    val dragModifier = if (selectionMode || sortMode) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) {
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
    }
    val clickModifier = if (sortMode) {
        // 排序时整张卡只负责拖拽，避免长按误开菜单或点击误进编辑
        Modifier
    } else {
        Modifier.combinedClickable(
            onClick = { if (selectionMode) onToggleSelected() else onEdit() },
            onLongClick = {
                if (!selectionMode) {
                    closeReveal()
                    menu = true
                }
            },
        )
    }

    @Composable
    fun TrailingControl() {
        if (sortMode) {
            Icon(
                ShituIcons.GripVertical,
                contentDescription = stringResource(R.string.rules_drag_sort),
                tint = scheme.onSurfaceVariant,
                modifier = Modifier
                    .size(40.dp)
                    .pointerInput(rule.id, sortMode, index) {
                        detectDragGestures(
                            onDragStart = { onSortDragStart(cardHeightPx) },
                            onDragEnd = onSortDragEnd,
                            onDragCancel = onSortDragCancel,
                        ) { change, amount ->
                            change.consume()
                            onSortDrag(amount.y)
                        }
                    }
                    .padding(8.dp),
            )
        } else if (selectionMode) {
            SelectionMark(selected)
        } else {
            ShituSwitch(checked = rule.enabled, onCheckedChange = onToggleEnabled)
        }
    }
    Box(modifier.fillMaxWidth().zIndex(if (isDragging) 1f else 0f)) {
        // 排序时整层删除背景不参与组合，拖到任何位置都不会露出红色删除块。
        // 普通模式仍保留左滑删除；进入排序会先把已露出的删除收回。
        if (!selectionMode && !sortMode) {
            RevealDeleteAction { closeReveal(); onDelete() }
        }
        Surface(
            modifier = Modifier
                .onGloballyPositioned { cardHeightPx = it.size.height }
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .graphicsLayer {
                    translationY = when {
                        isDragging -> drag.offsetY
                        dragActive -> shift.value
                        else -> 0f
                    }
                    val scale = 1f + lift * 0.02f
                    scaleX = scale
                    scaleY = scale
                }
                .then(dragModifier)
                .then(clickModifier),
            shape = squircle(AppCorner.xl),
            color = SurfaceLevel.CARD.colorOf(scheme),
            shadowElevation = elevation,
        ) {
            if (compact) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        rule.name,
                        style = AppType.Body.style(emphasized = true),
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    TonalChip(Labels.mode(context, rule.mode), color = modeTone(rule.mode).color())
                    Spacer(Modifier.width(6.dp))
                    RuleStatusChip(rule)
                    Spacer(Modifier.width(8.dp))
                    TrailingControl()
                }
            } else {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ToneDot(tone)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            rule.name,
                            style = AppType.Headline.style(emphasized = true),
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TrailingControl()
                    }
                    Spacer(Modifier.height(10.dp))
                    DetailPath(stringResource(R.string.rules_source_path), rule.srcPath)
                    DetailPath(stringResource(R.string.rules_target_path), rule.dstPath)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RuleStatusChip(rule)
                        Spacer(Modifier.width(6.dp))
                        TonalChip(Labels.mode(context, rule.mode), color = modeTone(rule.mode).color())
                        Spacer(Modifier.weight(1f))
                        Text(
                            rule.lastRunAt?.let { timeFmt.format(Date(it)) }
                                ?: stringResource(R.string.rules_last_run_never),
                            style = AppType.Caption1.style(),
                            color = scheme.onSurfaceVariant,
                        )
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
                        style = AppType.Footnote.style(),
                        color = scheme.onSurfaceVariant,
                    )
                    rule.pauseReason?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = AppType.Footnote.style(), color = scheme.error)
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.TopEnd).size(1.dp)) {
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                shape = squircle(AppCorner.xl),
                containerColor = floatingColor(scheme),
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rules_edit)) },
                    onClick = { menu = false; onEdit() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rules_run_now)) },
                    onClick = { menu = false; onRunNow() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rules_move_up)) },
                    enabled = canMoveUp,
                    onClick = { menu = false; onMoveUp() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rules_move_down)) },
                    enabled = canMoveDown,
                    onClick = { menu = false; onMoveDown() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rules_copy_rule)) },
                    onClick = { menu = false; onCopy() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rules_undo_moved)) },
                    onClick = { menu = false; onUndo() },
                )
                if (canResume(rule)) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rules_resume)) },
                        onClick = { menu = false; onResume() },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rules_delete), color = scheme.destructive) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

/** 详情模式的两行路径：标签定宽，路径左端对齐。 */
@Composable
private fun DetailPath(label: String, path: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = AppType.Caption1.style(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp).padding(top = 1.dp),
        )
        Text(
            path.ifBlank { "—" },
            style = AppType.Footnote.style(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RuleStatusChip(rule: RuleEntity) {    val color = cardTone(rule).color()
    Row(
        Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(
            stringResource(ruleStatusText(rule)),
            style = AppType.Caption1.style(emphasized = true),
            color = color,
        )
    }
}

@Composable
private fun BatchBar(
    count: Int,
    onEnable: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = squircle(AppCorner.xl),
        color = floatingColor(MaterialTheme.colorScheme),
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.rules_selected_count, count),
                    style = AppType.Subheadline.style(emphasized = true),
                    modifier = Modifier.weight(1f),
                )
                TextButtonText(stringResource(R.string.rules_cancel), onCancel)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShituButton(
                    stringResource(R.string.rules_enable),
                    onEnable,
                    primary = false,
                    modifier = Modifier.weight(1f),
                )
                ShituButton(
                    stringResource(R.string.rules_disable),
                    onPause,
                    primary = false,
                    modifier = Modifier.weight(1f),
                )
                ShituButton(
                    stringResource(R.string.rules_delete_selected),
                    onDelete,
                    primary = true,
                    destructive = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NewRuleSheet(
    templates: List<RuleTemplate>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onAddFromTemplate: (RuleTemplate) -> Unit,
    onDeleteTemplate: (RuleTemplate) -> Unit,
) {
    com.landslide.shitu.ui.components.AppSheet(onDismiss = onDismiss, title = stringResource(R.string.rules_new_rule)) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NewRuleOption(
                title = stringResource(R.string.rules_blank_rule),
                detail = stringResource(R.string.rules_blank_rule_detail),
                icon = ShituIcons.Plus,
                onClick = onAdd,
            )
            if (templates.isEmpty()) {
                Text(
                    stringResource(R.string.rules_no_templates_hint),
                    style = AppType.Footnote.style(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                Text(
                    stringResource(R.string.rules_my_templates),
                    style = AppType.Headline.style(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                templates.forEach { t ->
                    NewRuleOption(
                        title = t.name,
                        detail = t.detail(),
                        icon = ShituIcons.Rules,
                        onDelete = { onDeleteTemplate(t) },
                        onClick = { onAddFromTemplate(t) },
                    )
                }
            }
            Text(
                stringResource(R.string.rules_template_footer_hint),
                style = AppType.Footnote.style(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

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
            .clip(squircle(AppCorner.md))
            .background(SurfaceLevel.INSET.colorOf(MaterialTheme.colorScheme))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 6.dp)) {
            Text(title, style = AppType.Body.style(emphasized = true), color = MaterialTheme.colorScheme.onSurface)
            Text(
                detail,
                style = AppType.Footnote.style(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(ShituIcons.Close, null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun WeekStatsSheet(stats: ShituApp.Stats, onDismiss: () -> Unit) {
    com.landslide.shitu.ui.components.AppSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.rules_week_dialog_title),
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                stringResource(R.string.rules_week_dialog_summary, stats.weekMoved, stats.weekFailed),
                style = AppType.Body.style(),
            )
            if (stats.topApps.isEmpty()) {
                Text(
                    stringResource(R.string.rules_week_dialog_empty),
                    style = AppType.Footnote.style(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Text(
                    stringResource(R.string.rules_week_top_apps),
                    style = AppType.Headline.style(),
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
                            style = AppType.Body.style(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(R.string.rules_week_top_item, count),
                            style = AppType.Body.style(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 左滑露出的删除块。 */
@Composable
private fun BoxScope.RevealDeleteAction(onClick: () -> Unit) {
    Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(REVEAL_WIDTH)
                .clip(squircle(AppCorner.xl))
                .background(MaterialTheme.colorScheme.error)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(ShituIcons.Trash, null, tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(R.string.rules_delete),
                    style = AppType.Caption1.style(),
                    color = MaterialTheme.colorScheme.onError,
                )
            }
        }
    }
}

private fun modeTone(mode: Mode): Tone = if (mode == Mode.COPY) Tone.COPY else Tone.OK

private fun cardTone(rule: RuleEntity): Tone {
    val status = ruleTone(rule)
    return if (rule.mode == Mode.COPY && status == Tone.OK) Tone.COPY else status
}
