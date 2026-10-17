package com.landslide.shitu.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.landslide.shitu.R
import com.landslide.shitu.ShituApp
import com.landslide.shitu.core.RuleConflictChecker
import com.landslide.shitu.data.db.Mode
import com.landslide.shitu.data.db.RuleEntity
import com.landslide.shitu.shizuku.FileBridge
import com.landslide.shitu.ui.components.BannerCard
import com.landslide.shitu.ui.components.DirPickerDialog
import com.landslide.shitu.ui.components.HelpDot
import com.landslide.shitu.ui.components.SectionCard
import com.landslide.shitu.ui.theme.Tone
import com.landslide.shitu.ui.theme.color
import java.util.Locale
import kotlinx.coroutines.launch

/** 预览里的体积：统一按 MB 显示，和界面上「约 X MB」的说法一致。 */
private fun previewSize(bytes: Long): String =
    String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)

/**
 * 和 [SectionCard] 一样的卡片，只是标题行右侧多一个帮助点。
 *
 * [SectionCard] 的标题只收字符串，塞不进可点的 HelpDot；这里按同样的圆角、底色、内边距和字号
 * 抄一份最小实现（设置页的 SettingsCard 也是这么做的），保证整页卡片外观一致。
 */
@Composable
private fun HelpSectionCard(
    title: String,
    help: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
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
                HelpDot(help)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/** 窄输入框的标签行：短标签 + 右侧帮助点，三个框共用，保证行高一致。 */
@Composable
private fun FilterLabel(label: String, help: String) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        HelpDot(help)
    }
}

/**
 * 规则编辑（规格 §10 页面 2）。
 *
 * 一张卡只放一件事：名称 / 源目录 / 目标目录 / 移动模式 / 频率 / 文件名。
 * 卡片里只留一句短说明，长解释交给对话框。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RuleEditScreen(
    initial: RuleEntity,
    bridge: FileBridge,
    /** 其余规则：只用来查"这条规则跟别的规则会不会打架"，别的规则自己的问题不在这里出现 */
    otherRules: List<RuleEntity>,
    onSave: (RuleEntity) -> Unit,
    onCancel: () -> Unit,
    /** 把当前这份设置存成模板（第二个参数是模板名） */
    onSaveAsTemplate: (RuleEntity, String) -> Unit = { _, _ -> },
    /** 把当前草稿同步给上层：返回键/切标签页时要问"存不存" */
    onDraftChange: (RuleEntity) -> Unit = {},
    onDirtyChange: (Boolean) -> Unit = {},
    /** 试运行：只数「会搬多少张」，不真的动文件（由上层接到 ShituApp.previewRule） */
    onPreview: suspend (RuleEntity) -> ShituApp.PreviewResult = { ShituApp.PreviewResult() },
) {
    var rule by remember { mutableStateOf(initial) }
    var picking by remember { mutableStateOf<String?>(null) }
    var confirmRoot by remember { mutableStateOf(false) }
    var confirmNested by remember { mutableStateOf(false) }
    var askTemplateName by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf("") }

    // 保存时缺什么就提示什么（新建的规则一开始三项都是空的）
    var missing by remember { mutableStateOf<List<String>>(emptyList()) }

    // 试运行：preview 非空 = 结果对话框开着；previewBeforeSave = 这次预览是保存流程里的确认
    var previewing by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ShituApp.PreviewResult?>(null) }
    var previewBeforeSave by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val isNew = initial.id == 0L

    // 下面几处要在 onClick 这类非 Composable 作用域里用，先在这里把资源文案取好
    val defaultTemplateName = stringResource(R.string.edit_default_template)
    val listSeparator = stringResource(R.string.edit_list_separator)
    val fieldRuleName = stringResource(R.string.edit_label_name)
    val fieldSrc = stringResource(R.string.edit_field_src)
    val fieldDst = stringResource(R.string.edit_field_dst)

    LaunchedEffect(rule) {
        onDraftChange(rule)
        onDirtyChange(rule != initial)
    }

    val srcIsRoot = rule.srcPath.trimEnd('/') == "/sdcard"
    // 所有 App 的数据根目录：不往下选到具体 App 的话，会把各个 App 里的图片都搬走
    val srcIsAllAppData = rule.srcPath.trimEnd('/') == "/sdcard/Android/data"
    val srcTrim = rule.srcPath.trimEnd('/')
    val dstTrim = rule.dstPath.trimEnd('/')
    val dstInsideSrc = srcTrim.isNotBlank() && dstTrim.isNotBlank() &&
        (dstTrim == srcTrim || dstTrim.startsWith("$srcTrim/"))

    // 提醒跟着草稿走：改了设置（比如勾上「包含子目录」）立刻重算，
    // 不会再出现"没勾子目录却提醒子目录的事"，也不会把别的规则的提醒搬进来
    val conflicts = remember(rule, otherRules) { RuleConflictChecker.check(rule, otherRules) }

    /** 保存前必须齐的三项；试运行只要源和目标（名称可以最后再起）。 */
    fun gaps(needName: Boolean): List<String> = buildList {
        if (needName && rule.name.isBlank()) add(fieldRuleName)
        if (rule.srcPath.isBlank()) add(fieldSrc)
        if (rule.dstPath.isBlank()) add(fieldDst)
    }

    /** 跑预览；[beforeSave] = 保存流程里的自动预览。 */
    fun startPreview(beforeSave: Boolean) {
        if (previewing) return
        val gapsNow = gaps(needName = beforeSave)
        if (gapsNow.isNotEmpty()) {
            missing = gapsNow
            return
        }
        previewing = true
        previewBeforeSave = beforeSave
        scope.launch {
            preview = runCatching { onPreview(rule) }
                .getOrElse { ShituApp.PreviewResult(error = it.message ?: it.toString()) }
            previewing = false
        }
    }

    /** 预览之后（或编辑老规则时）继续原来的确认链：根目录 → 目标在源内 → 保存。 */
    fun continueSave() {
        when {
            srcIsRoot -> confirmRoot = true
            // 只有"勾了包含子目录 + 目标在源里面"才需要确认：
            // 不勾子目录时，子文件夹（含目标目录）根本不会被扫描，是安全的
            dstInsideSrc && rule.includeSubdirs -> confirmNested = true
            else -> onSave(rule)
        }
    }

    fun onSaveClick() {
        if (previewing) return
        // 新建规则先试运行一次，让用户知道大概会搬多少张再决定（编辑老规则不强制）
        if (isNew) {
            startPreview(beforeSave = true)
            return
        }
        val gapsNow = gaps(needName = true)
        if (gapsNow.isNotEmpty()) {
            missing = gapsNow
            return
        }
        continueSave()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            if (isNew) stringResource(R.string.edit_title_new) else stringResource(R.string.edit_title_edit),
            style = MaterialTheme.typography.titleLarge,
        )

        // 配置提醒放到最上面：改的时候一眼能看到哪里有问题。
        // 一张卡装全部（以前一条提醒一张卡，两三条就把屏幕占掉一半）
        if (conflicts.isNotEmpty()) {
            BannerCard(
                tone = Tone.WARN,
                icon = Icons.Filled.Warning,
                title = stringResource(R.string.edit_conflicts_title),
                body = conflicts.joinToString("\n\n"),
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        // 1. 规则名称
        HelpSectionCard(
            title = stringResource(R.string.edit_label_name),
            help = stringResource(R.string.edit_help_name),
        ) {
            OutlinedTextField(
                value = rule.name,
                onValueChange = { rule = rule.copy(name = it) },
                placeholder = { Text(stringResource(R.string.edit_placeholder_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.edit_name_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }

        // 2. 源目录：目录 + 子目录/深度 + 老图/小图过滤
        HelpSectionCard(
            title = stringResource(R.string.edit_label_src),
            help = stringResource(R.string.edit_help_src),
        ) {
            OutlinedTextField(
                value = rule.srcPath,
                onValueChange = {},
                readOnly = true,
                placeholder = { Text(stringResource(R.string.edit_placeholder_dir)) },
                trailingIcon = {
                    TextButton(onClick = { picking = "src" }) { Text(stringResource(R.string.edit_pick)) }
                },
                supportingText = if (srcIsRoot || srcIsAllAppData) {
                    {
                        Text(
                            stringResource(
                                if (srcIsRoot) R.string.edit_src_root_hint
                                else R.string.edit_src_all_app_data_hint,
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = rule.includeSubdirs,
                    onCheckedChange = { rule = rule.copy(includeSubdirs = it) },
                )
                Text(
                    stringResource(R.string.edit_include_subdirs),
                    modifier = Modifier.weight(1f, fill = false),
                )
                HelpDot(stringResource(R.string.edit_help_subdirs))
            }

            // 三个数字框：外置标签行（短词 + 帮助点）+ 单行输入框。
            // 三列都是 Column(weight(1f))、结构完全一样，宽度和高度自然一致；
            // 标签放框外、统一 labelSmall + maxLines = 1，不会再被第三个框的字撑大。
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    FilterLabel(
                        stringResource(R.string.edit_label_max_depth),
                        stringResource(R.string.edit_help_max_depth),
                    )
                    OutlinedTextField(
                        value = rule.maxDepth?.toString() ?: "",
                        onValueChange = { t ->
                            val digits = t.filter { it.isDigit() }.take(2)
                            rule = rule.copy(maxDepth = digits.toIntOrNull()?.takeIf { it > 0 })
                        },
                        placeholder = { Text(stringResource(R.string.edit_unlimited)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(Modifier.weight(1f)) {
                    FilterLabel(
                        stringResource(R.string.edit_label_days),
                        stringResource(R.string.edit_help_days),
                    )
                    OutlinedTextField(
                        value = if (rule.minAgeDays > 0) rule.minAgeDays.toString() else "",
                        onValueChange = { t ->
                            val digits = t.filter { it.isDigit() }.take(3)
                            rule = rule.copy(minAgeDays = digits.toIntOrNull() ?: 0)
                        },
                        placeholder = { Text(stringResource(R.string.edit_unlimited)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(Modifier.weight(1f)) {
                    FilterLabel(
                        stringResource(R.string.edit_label_size_kb),
                        stringResource(R.string.edit_help_min_size),
                    )
                    OutlinedTextField(
                        value = if (rule.minSizeKb > 0) rule.minSizeKb.toString() else "",
                        onValueChange = { t ->
                            val digits = t.filter { it.isDigit() }.take(5)
                            rule = rule.copy(minSizeKb = digits.toIntOrNull() ?: 0)
                        },
                        placeholder = { Text(stringResource(R.string.edit_unlimited)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Text(
                stringResource(R.string.edit_filter_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }

        // 3. 目标目录：目录 + 按 App 名建子目录
        HelpSectionCard(
            title = stringResource(R.string.edit_label_dst),
            help = stringResource(R.string.edit_help_dst),
        ) {
            OutlinedTextField(
                value = rule.dstPath,
                onValueChange = {},
                readOnly = true,
                placeholder = { Text(stringResource(R.string.edit_placeholder_dir)) },
                trailingIcon = {
                    TextButton(onClick = { picking = "dst" }) { Text(stringResource(R.string.edit_pick)) }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.edit_dst_subdir_app),
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        HelpDot(stringResource(R.string.edit_help_dst_subdir))
                    }
                    Text(
                        stringResource(R.string.edit_dst_subdir_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = rule.dstSubdirApp,
                    onCheckedChange = { rule = rule.copy(dstSubdirApp = it) },
                )
            }
        }

        // 4. 移动模式
        HelpSectionCard(
            title = stringResource(R.string.edit_section_mode),
            help = stringResource(R.string.edit_help_mode),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Mode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = rule.mode == m,
                        onClick = { rule = rule.copy(mode = m) },
                        shape = SegmentedButtonDefaults.itemShape(i, Mode.entries.size),
                    ) {
                        Text(
                            stringResource(
                                if (m == Mode.MOVE) R.string.edit_mode_move else R.string.edit_mode_copy,
                            ),
                        )
                    }
                }
            }
            Text(
                stringResource(
                    if (rule.mode == Mode.MOVE) R.string.edit_mode_move_hint
                    else R.string.edit_mode_copy_hint,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (rule.mode == Mode.MOVE) Tone.OK.color() else Tone.COPY.color(),
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
        }

        // 5. 频率：这只是兜底——平时源目录一变，十几秒内就搬，不是真的等这么久
        HelpSectionCard(
            title = stringResource(R.string.edit_section_freq),
            help = stringResource(R.string.edit_help_freq),
        ) {
            Text(
                stringResource(R.string.edit_interval, rule.intervalMinutes),
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = rule.intervalMinutes.toFloat(),
                onValueChange = { rule = rule.copy(intervalMinutes = it.toInt().coerceIn(1, 30)) },
                valueRange = 1f..30f,
                steps = 28,
            )
            Text(
                stringResource(R.string.edit_help_freq),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        // 6. 文件名：后缀 → 扩展名白名单 → 按内容生成文件名
        SectionCard(title = stringResource(R.string.edit_section_file)) {
            OutlinedTextField(
                value = rule.suffix,
                onValueChange = { rule = rule.copy(suffix = it) },
                label = { Text(stringResource(R.string.edit_label_suffix)) },
                placeholder = { Text(stringResource(R.string.edit_suffix_placeholder)) },
                singleLine = true,
                supportingText = { Text(stringResource(R.string.edit_suffix_hint)) },
                trailingIcon = {
                    HelpDot(stringResource(R.string.edit_help_suffix))
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = rule.extensions,
                onValueChange = { rule = rule.copy(extensions = it) },
                label = { Text(stringResource(R.string.edit_label_extensions)) },
                singleLine = true,
                trailingIcon = {
                    HelpDot(stringResource(R.string.edit_help_extensions))
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))
            // 说明放进复选框右侧的 Column：起点天然和复选框文字对齐，不用猜复选框有多宽
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = rule.contentRename,
                    onCheckedChange = { rule = rule.copy(contentRename = it) },
                )
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.edit_content_rename),
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        HelpDot(stringResource(R.string.edit_help_content_rename))
                    }
                    Text(
                        stringResource(
                            if (rule.contentRename) R.string.edit_content_rename_on_hint
                            else R.string.edit_content_rename_off_hint,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 底部按钮：窄屏放不下会自动换行
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // 试运行按钮和它的帮助点绑在一起，窄屏换行时不会被拆开
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { startPreview(beforeSave = false) },
                    enabled = !previewing,
                ) {
                    Text(
                        stringResource(
                            if (previewing) R.string.edit_preview_running else R.string.edit_preview_title,
                        ),
                    )
                }
                HelpDot(stringResource(R.string.edit_help_preview))
            }
            TextButton(
                onClick = {
                    templateName = rule.name.ifBlank { defaultTemplateName }
                    askTemplateName = true
                },
            ) { Text(stringResource(R.string.edit_save_as_template)) }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.edit_cancel)) }
            Button(
                onClick = { onSaveClick() },
                enabled = !previewing,
            ) { Text(stringResource(R.string.edit_save_and_start)) }
        }
    }

    if (missing.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { missing = emptyList() },
            title = { Text(stringResource(R.string.edit_missing_title)) },
            text = {
                Text(
                    stringResource(R.string.edit_missing_body, missing.joinToString(listSeparator)),
                )
            },
            confirmButton = {
                TextButton(onClick = { missing = emptyList() }) { Text(stringResource(R.string.edit_got_it)) }
            },
        )
    }

    if (askTemplateName) {
        AlertDialog(
            onDismissRequest = { askTemplateName = false },
            title = { Text(stringResource(R.string.edit_save_as_template)) },
            text = {
                Column {
                    Text(stringResource(R.string.edit_template_dialog_body))
                    OutlinedTextField(
                        value = templateName,
                        onValueChange = { templateName = it },
                        label = { Text(stringResource(R.string.edit_template_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    askTemplateName = false
                    onSaveAsTemplate(rule, templateName.trim().ifBlank { rule.name.ifBlank { defaultTemplateName } })
                }) { Text(stringResource(R.string.edit_template_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { askTemplateName = false }) { Text(stringResource(R.string.edit_cancel)) }
            },
        )
    }

    // 试运行结果：普通预览只报数；保存流程里的预览多一个「确认保存」和「取消」
    preview?.let { p ->
        val limited = if (p.limited) stringResource(R.string.edit_preview_limited) else ""
        val previewError = p.error
        AlertDialog(
            onDismissRequest = {
                preview = null
                previewBeforeSave = false
            },
            title = {
                Text(
                    stringResource(
                        if (previewBeforeSave) R.string.edit_preview_save_title
                        else R.string.edit_preview_title,
                    ),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (previewError != null) {
                        Text(
                            stringResource(R.string.edit_preview_failed),
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(previewError)
                    } else if (previewBeforeSave) {
                        Text(
                            stringResource(
                                R.string.edit_preview_save_body,
                                p.candidates,
                                previewSize(p.bytes),
                            ) + limited,
                        )
                        if (p.existingTarget > 0) {
                            Text(stringResource(R.string.edit_preview_existing, p.existingTarget))
                        }
                    } else {
                        Text(
                            stringResource(
                                R.string.edit_preview_body,
                                p.candidates,
                                previewSize(p.bytes),
                                p.existingTarget,
                            ) + limited,
                        )
                    }
                }
            },
            confirmButton = {
                if (previewBeforeSave) {
                    TextButton(onClick = {
                        preview = null
                        previewBeforeSave = false
                        continueSave()
                    }) {
                        Text(
                            stringResource(
                                // 预览失败或 0 张只做提示，不拦保存
                                if (previewError != null || p.candidates == 0) R.string.edit_preview_save_anyway
                                else R.string.edit_preview_save,
                            ),
                        )
                    }
                } else {
                    TextButton(onClick = { preview = null }) { Text(stringResource(R.string.edit_got_it)) }
                }
            },
            dismissButton = if (previewBeforeSave) {
                {
                    TextButton(onClick = {
                        preview = null
                        previewBeforeSave = false
                    }) { Text(stringResource(R.string.edit_cancel)) }
                }
            } else {
                null
            },
        )
    }

    picking?.let { which ->
        DirPickerDialog(
            bridge = bridge,
            // 新建时这里是空的 → 从 /sdcard 开始；编辑已有规则则从当前目录开始，少点几层
            startPath = (if (which == "src") rule.srcPath else rule.dstPath)
                .trim()
                .ifBlank { RuleEntity.PICKER_ROOT },
            onDismiss = { picking = null },
            onPick = { p ->
                rule = if (which == "src") rule.copy(srcPath = p) else rule.copy(dstPath = p)
                picking = null
            },
        )
    }

    if (confirmNested) {
        AlertDialog(
            onDismissRequest = { confirmNested = false },
            title = { Text(stringResource(R.string.edit_nested_title)) },
            text = {
                Text(
                    stringResource(R.string.edit_nested_body, rule.srcPath, rule.dstPath),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmNested = false
                    onSave(rule)
                }) { Text(stringResource(R.string.edit_nested_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmNested = false }) { Text(stringResource(R.string.edit_nested_dismiss)) }
            },
        )
    }

    if (confirmRoot) {
        AlertDialog(
            onDismissRequest = { confirmRoot = false },
            title = { Text(stringResource(R.string.edit_root_title)) },
            text = {
                Text(stringResource(R.string.edit_root_body))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRoot = false
                    onSave(rule)
                }) { Text(stringResource(R.string.edit_root_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRoot = false }) { Text(stringResource(R.string.edit_root_dismiss)) }
            },
        )
    }
}
