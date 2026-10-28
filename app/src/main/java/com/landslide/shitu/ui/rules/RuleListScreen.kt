package com.landslide.shitu.ui.rules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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

    val shown = rules.filter { rule ->
        query.isBlank() ||
            rule.name.contains(query, ignoreCase = true) ||
            rule.srcPath.contains(query, ignoreCase = true) ||
            rule.dstPath.contains(query, ignoreCase = true)
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
                IconButton(onClick = { searchOpen = !searchOpen }) {
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
                    verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
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
                            canMoveUp = index > 0,
                            canMoveDown = index < shown.lastIndex,
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
                            onMoveBy = { steps -> onMoveRule(rule, steps) },
                            modifier = Modifier.animateItem(),
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
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ToneDot(tone)
                    Spacer(Modifier.width(8.dp))
                    Text(title, style = AppType.Headline.style(emphasized = true), color = scheme.onSurface)
                }
                if (body != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(body, style = AppType.Footnote.style(), color = scheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
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
                    Box(Modifier.padding(start = 8.dp)) {
                        TextButtonText(stringResource(R.string.rules_self_check), onSelfCheck)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun ToneDot(tone: Tone) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(tone.color()))
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RuleCard(
    rule: RuleEntity,
    compact: Boolean,
    selectionMode: Boolean,
    sortMode: Boolean,
    selected: Boolean,
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
    onMoveBy: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val tone = cardTone(rule)
    var menu by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val revealPx = with(LocalDensity.current) { REVEAL_WIDTH.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    fun closeReveal() = scope.launch { offsetX.animateTo(0f, spring()) }
    val dragModifier = if (selectionMode) {
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
    val clickModifier = Modifier.combinedClickable(
        onClick = { if (selectionMode) onToggleSelected() else onEdit() },
        onLongClick = {
            if (!selectionMode) {
                closeReveal()
                menu = true
            }
        },
    )

    @Composable
    fun TrailingControl() {
        if (sortMode) {
            Icon(
                ShituIcons.GripVertical,
                contentDescription = stringResource(R.string.rules_drag_sort),
                tint = scheme.onSurfaceVariant,
                modifier = Modifier
                    .size(40.dp)
                    .pointerInput(rule.id, sortMode) {
                        detectDragGestures(
                            onDragEnd = {
                                val steps = (dragY / with(density) { 150.dp.toPx() })
                                    .roundToInt()
                                dragY = 0f
                                if (steps != 0) onMoveBy(steps)
                            },
                            onDragCancel = { dragY = 0f },
                        ) { change, amount ->
                            change.consume()
                            dragY += amount.y
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
    Box(modifier.fillMaxWidth()) {
        if (!selectionMode) {
            RevealDeleteAction { closeReveal(); onDelete() }
        }
        Surface(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .graphicsLayer { translationY = dragY }
                .then(dragModifier)
                .then(clickModifier),
            shape = squircle(AppCorner.xl),
            color = SurfaceLevel.CARD.colorOf(scheme),
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
