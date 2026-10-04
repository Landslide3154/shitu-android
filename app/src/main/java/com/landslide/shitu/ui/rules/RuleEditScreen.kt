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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    conflicts: List<String>,
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

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("编辑规则", style = MaterialTheme.typography.titleLarge)

        // 配置提醒放到最上面：改的时候一眼能看到哪里有问题
        conflicts.forEach { c ->
            BannerCard(
                tone = Tone.WARN,
                icon = Icons.Filled.Warning,
                title = "配置提醒",
                body = c,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        SectionCard(title = "规则与源目录") {
            OutlinedTextField(
                value = rule.name,
                onValueChange = { rule = rule.copy(name = it) },
                label = { Text("规则名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = rule.srcPath,
                onValueChange = {},
                readOnly = true,
                label = { Text("源目录（从这里把图片搬走）") },
                trailingIcon = { TextButton(onClick = { picking = "src" }) { Text("选择") } },
                supportingText = {
                    Text(
                        when {
                            srcIsRoot -> "这是整个存储根目录，会搬走手机里几乎所有图片"
                            srcIsAllAppData ->
                                "这是所有 App 的数据根目录，会搬走各个 App 里的图片；" +
                                    "建议点「选择」往下选到具体那个 App"
                            else -> "从 /sdcard 开始往下点，选到具体的图片文件夹最安全"
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
                Text("包含子目录")
            }
            Text(
                if (rule.includeSubdirs) {
                    "会把源目录下面所有层级的图片都搬走" +
                        if (dstInsideSrc) "；注意目标目录就在源目录里面，会把刚搬进去的文件又当成源" else ""
                } else {
                    "只搬源目录这一层里的图片，子文件夹不看（目标目录在源目录里时更安全）"
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
                label = { Text("最大深度（留空 = 不限）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionCard(title = "目标与模式") {
            OutlinedTextField(
                value = rule.dstPath,
                onValueChange = {},
                readOnly = true,
                label = { Text("目标目录（搬到这里）") },
                trailingIcon = { TextButton(onClick = { picking = "dst" }) { Text("选择") } },
                supportingText = { Text("建议放在 DCIM 或 Pictures 下，相册才能立刻看到") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Mode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = rule.mode == m,
                        onClick = { rule = rule.copy(mode = m) },
                        shape = SegmentedButtonDefaults.itemShape(i, Mode.entries.size),
                    ) { Text(if (m == Mode.MOVE) "移动" else "复制") }
                }
            }
            Text(
                if (rule.mode == Mode.MOVE) "移动：源文件会被搬到目标目录（推荐）"
                else "复制：源文件保留，只复制一份到目标目录（第一天建议用它）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        SectionCard(title = "频率与文件名") {
            Text("轮询间隔：${rule.intervalMinutes} 分钟", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = rule.intervalMinutes.toFloat(),
                onValueChange = { rule = rule.copy(intervalMinutes = it.toInt().coerceIn(1, 30)) },
                valueRange = 1f..30f,
                steps = 28,
            )

            OutlinedTextField(
                value = rule.extensions,
                onValueChange = { rule = rule.copy(extensions = it) },
                label = { Text("扩展名白名单（逗号分隔）") },
                modifier = Modifier.fillMaxWidth(),
            )

            // 文件名后缀：可自定义（原来的「加来源 App 后缀」开关改成这里填）
            OutlinedTextField(
                value = rule.suffix,
                onValueChange = { rule = rule.copy(suffix = it) },
                label = { Text("文件名后缀") },
                singleLine = true,
                supportingText = {
                    Text(
                        "填 {app} = 自动用来源 App 名（封面_{app}.png → 封面_起点读书.png）；" +
                            "填自己的文字 = 固定后缀（如 _拾图）；留空 = 不改文件名",
                    )
                },
                placeholder = { Text("{app}") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = {
                    templateName = rule.name.ifBlank { "我的模板" }
                    askTemplateName = true
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            ) { Text("存为模板") }
            TextButton(onClick = onCancel) { Text("取消") }
            Button(
                onClick = {
                    val fixed = if (rule.name.isBlank()) rule.copy(name = "新规则") else rule
                    when {
                        srcIsRoot -> confirmRoot = true
                        // 只有"勾了包含子目录 + 目标在源里面"才需要确认：
                        // 不勾子目录时，子文件夹（含目标目录）根本不会被扫描，是安全的
                        dstInsideSrc && rule.includeSubdirs -> confirmNested = true
                        else -> onSave(fixed)
                    }
                },
                enabled = rule.srcPath.isNotBlank() && rule.dstPath.isNotBlank(),
            ) { Text("保存并开始搬运") }
        }
    }

    if (askTemplateName) {
        AlertDialog(
            onDismissRequest = { askTemplateName = false },
            title = { Text("存为模板") },
            text = {
                Column {
                    Text("把这份设置存下来，以后新建规则时可以一键套用（存模板不会保存这条规则）。")
                    OutlinedTextField(
                        value = templateName,
                        onValueChange = { templateName = it },
                        label = { Text("模板名字") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    askTemplateName = false
                    val fixed = if (rule.name.isBlank()) rule.copy(name = "新规则") else rule
                    onSaveAsTemplate(fixed, templateName.trim().ifBlank { rule.name.ifBlank { "我的模板" } })
                }) { Text("存下来") }
            },
            dismissButton = { TextButton(onClick = { askTemplateName = false }) { Text("取消") } },
        )
    }

    picking?.let { which ->
        DirPickerDialog(
            bridge = bridge,
            startPath = if (which == "src") RuleEntity.PICKER_ROOT else "/sdcard/DCIM",
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
            title = { Text("目标目录在源目录里面") },
            text = {
                Text(
                    "你选的源目录是 ${rule.srcPath}，目标目录 ${rule.dstPath} 在它里面。" +
                        "这种配法虽然不会再搬已搬好的文件（App 会自动跳过目标目录），" +
                        "但建议把目标放到源目录外面，规则更清楚、也更好撤回。" +
                        "确定要这样保存吗？",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmNested = false
                    onSave(rule.copy(name = if (rule.name.isBlank()) "新规则" else rule.name))
                }) { Text("就这样保存") }
            },
            dismissButton = {
                TextButton(onClick = { confirmNested = false }) { Text("回去改") }
            },
        )
    }

    if (confirmRoot) {
        AlertDialog(
            onDismissRequest = { confirmRoot = false },
            title = { Text("确定要搬整个存储？") },
            text = {
                Text(
                    "你把源目录选成了 /sdcard（整个手机存储）。保存后，手机里几乎所有图片都会按这条规则被搬走" +
                        "（移动模式下原图会离开原位）。如果只是想清理某个 App 的图片，" +
                        "请回去点进 Android/data/<包名>/… 再选一层。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRoot = false
                    onSave(rule.copy(name = if (rule.name.isBlank()) "新规则" else rule.name))
                }) { Text("我知道，就这么办") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRoot = false }) { Text("回去重选") }
            },
        )
    }
}
