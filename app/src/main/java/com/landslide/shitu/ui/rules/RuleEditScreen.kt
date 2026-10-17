package com.landslide.shitu.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.landslide.shitu.R
import com.landslide.shitu.core.RuleConflictChecker
import com.landslide.shitu.data.db.Mode
import com.landslide.shitu.data.db.RuleEntity
import com.landslide.shitu.shizuku.FileBridge
import com.landslide.shitu.ui.components.BannerCard
import com.landslide.shitu.ui.components.DirPickerDialog
import com.landslide.shitu.ui.components.SectionCard
import com.landslide.shitu.ui.theme.Tone

/** 规则编辑（规格 §10 页面 2）。 */
@OptIn(ExperimentalMaterial3Api::class)
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
) {
    var rule by remember { mutableStateOf(initial) }
    var picking by remember { mutableStateOf<String?>(null) }
    var confirmRoot by remember { mutableStateOf(false) }
    var confirmNested by remember { mutableStateOf(false) }
    var askTemplateName by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf("") }

    // 保存时缺什么就提示什么（新建的规则一开始三项都是空的）
    var missing by remember { mutableStateOf<List<String>>(emptyList()) }

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

        SectionCard(title = stringResource(R.string.edit_section_rule_src)) {
            OutlinedTextField(
                value = rule.name,
                onValueChange = { rule = rule.copy(name = it) },
                label = { Text(stringResource(R.string.edit_label_name)) },
                placeholder = { Text(stringResource(R.string.edit_placeholder_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = rule.srcPath,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.edit_label_src)) },
                placeholder = { Text(stringResource(R.string.edit_placeholder_dir)) },
                trailingIcon = { TextButton(onClick = { picking = "src" }) { Text(stringResource(R.string.edit_pick)) } },
                supportingText = {
                    Text(
                        when {
                            srcTrim.isBlank() ->
                                stringResource(R.string.edit_src_empty_hint)
                            srcIsRoot -> stringResource(R.string.edit_src_root_hint)
                            srcIsAllAppData ->
                                stringResource(R.string.edit_src_all_app_data_hint)
                            else -> stringResource(R.string.edit_src_hint)
                        },
                        color = if (srcIsRoot || srcIsAllAppData) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = rule.includeSubdirs,
                    onCheckedChange = { rule = rule.copy(includeSubdirs = it) },
                )
                Text(stringResource(R.string.edit_include_subdirs))
            }
            Text(
                if (rule.includeSubdirs) {
                    stringResource(R.string.edit_subdirs_on_hint) +
                        if (dstInsideSrc) stringResource(R.string.edit_subdirs_dst_inside) else ""
                } else {
                    stringResource(R.string.edit_subdirs_off_hint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (rule.includeSubdirs && dstInsideSrc) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            OutlinedTextField(
                value = rule.maxDepth?.toString() ?: "",
                onValueChange = { t ->
                    val digits = t.filter { it.isDigit() }.take(2)
                    rule = rule.copy(maxDepth = digits.toIntOrNull()?.takeIf { it > 0 })
                },
                label = { Text(stringResource(R.string.edit_label_max_depth)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionCard(title = stringResource(R.string.edit_section_dst_mode)) {
            OutlinedTextField(
                value = rule.dstPath,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.edit_label_dst)) },
                placeholder = { Text(stringResource(R.string.edit_placeholder_dir)) },
                trailingIcon = { TextButton(onClick = { picking = "dst" }) { Text(stringResource(R.string.edit_pick)) } },
                supportingText = { Text(stringResource(R.string.edit_dst_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Mode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = rule.mode == m,
                        onClick = { rule = rule.copy(mode = m) },
                        shape = SegmentedButtonDefaults.itemShape(i, Mode.entries.size),
                    ) { Text(if (m == Mode.MOVE) stringResource(R.string.edit_mode_move) else stringResource(R.string.edit_mode_copy)) }
                }
            }
            Text(
                if (rule.mode == Mode.MOVE) stringResource(R.string.edit_mode_move_hint)
                else stringResource(R.string.edit_mode_copy_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        SectionCard(title = stringResource(R.string.edit_section_freq_name)) {
            Text(stringResource(R.string.edit_interval, rule.intervalMinutes), style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = rule.intervalMinutes.toFloat(),
                onValueChange = { rule = rule.copy(intervalMinutes = it.toInt().coerceIn(1, 30)) },
                valueRange = 1f..30f,
                steps = 28,
            )

            OutlinedTextField(
                value = rule.extensions,
                onValueChange = { rule = rule.copy(extensions = it) },
                label = { Text(stringResource(R.string.edit_label_extensions)) },
                modifier = Modifier.fillMaxWidth(),
            )

            // 文件名后缀：可自定义（原来的「加来源 App 后缀」开关改成这里填）
            OutlinedTextField(
                value = rule.suffix,
                onValueChange = { rule = rule.copy(suffix = it) },
                label = { Text(stringResource(R.string.edit_label_suffix)) },
                singleLine = true,
                supportingText = {
                    Text(stringResource(R.string.edit_suffix_hint))
                },
                placeholder = { Text(stringResource(R.string.edit_suffix_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(6.dp))

            // 按内容命名：和上面的后缀叠加使用（后缀管"来源"，这里管"主体"）
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = rule.contentRename,
                    onCheckedChange = { rule = rule.copy(contentRename = it) },
                )
                Text(stringResource(R.string.edit_content_rename))
            }
            Text(
                if (rule.contentRename) {
                    stringResource(
                        R.string.edit_content_rename_on_hint,
                        if (rule.suffix.isBlank()) "" else stringResource(R.string.edit_content_rename_suffix_example),
                    )
                } else {
                    stringResource(R.string.edit_content_rename_off_hint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = {
                    templateName = rule.name.ifBlank { defaultTemplateName }
                    askTemplateName = true
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            ) { Text(stringResource(R.string.edit_save_as_template)) }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.edit_cancel)) }
            Button(
                onClick = {
                    // 名称/源目录/目标目录三样必须齐；缺了就弹提示，不偷偷替用户填「新规则」
                    val gaps = buildList {
                        if (rule.name.isBlank()) add(fieldRuleName)
                        if (rule.srcPath.isBlank()) add(fieldSrc)
                        if (rule.dstPath.isBlank()) add(fieldDst)
                    }
                    when {
                        gaps.isNotEmpty() -> missing = gaps
                        srcIsRoot -> confirmRoot = true
                        // 只有"勾了包含子目录 + 目标在源里面"才需要确认：
                        // 不勾子目录时，子文件夹（含目标目录）根本不会被扫描，是安全的
                        dstInsideSrc && rule.includeSubdirs -> confirmNested = true
                        else -> onSave(rule)
                    }
                },
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
            dismissButton = { TextButton(onClick = { askTemplateName = false }) { Text(stringResource(R.string.edit_cancel)) } },
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
