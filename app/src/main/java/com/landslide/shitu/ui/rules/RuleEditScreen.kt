package com.landslide.shitu.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.landslide.shitu.data.db.Mode
import com.landslide.shitu.data.db.RuleEntity
import com.landslide.shitu.shizuku.FileBridge
import com.landslide.shitu.ui.components.DirPickerDialog

/** 规则编辑（规格 §10 页面 2）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditScreen(
    initial: RuleEntity,
    bridge: FileBridge,
    conflicts: List<String>,
    onSave: (RuleEntity) -> Unit,
    onCancel: () -> Unit,
) {
    var rule by remember { mutableStateOf(initial) }
    var picking by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("编辑规则", style = MaterialTheme.typography.titleLarge)

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
            label = { Text("源目录（默认从 Android/data 开始）") },
            trailingIcon = { TextButton(onClick = { picking = "src" }) { Text("选择") } },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(
                checked = rule.includeSubdirs,
                onCheckedChange = { rule = rule.copy(includeSubdirs = it) },
            )
            Text("包含子目录")
        }

        OutlinedTextField(
            value = rule.maxDepth?.toString() ?: "",
            onValueChange = { t ->
                val digits = t.filter { it.isDigit() }.take(2)
                rule = rule.copy(maxDepth = digits.toIntOrNull()?.takeIf { it > 0 })
            },
            label = { Text("最大深度（留空 = 不限）") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = rule.dstPath,
            onValueChange = {},
            readOnly = true,
            label = { Text("目标目录（建议留在 Pictures/DCIM）") },
            trailingIcon = { TextButton(onClick = { picking = "dst" }) { Text("选择") } },
            modifier = Modifier.fillMaxWidth(),
        )

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
        )

        Text("轮询间隔：${rule.intervalMinutes} 分钟")
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

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(
                checked = rule.addSourceAppSuffix,
                onCheckedChange = { rule = rule.copy(addSourceAppSuffix = it) },
            )
            Text("文件名加来源 App 后缀（如 封面_起点读书.png）")
        }

        conflicts.forEach {
            Text("⚠ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) { Text("取消") }
            Button(onClick = {
                if (rule.name.isBlank()) rule = rule.copy(name = "新规则")
                onSave(rule)
            }) { Text("保存并开始搬运") }
        }
    }

    picking?.let { which ->
        DirPickerDialog(
            bridge = bridge,
            startPath = if (which == "src") RuleEntity.PICKER_ROOT else "/sdcard/Pictures",
            onDismiss = { picking = null },
            onPick = { p ->
                rule = if (which == "src") rule.copy(srcPath = p) else rule.copy(dstPath = p)
                picking = null
            },
        )
    }
}
