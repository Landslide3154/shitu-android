package com.landslide.shitu.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.landslide.shitu.shizuku.FileBridge
import com.landslide.shitu.shizuku.RemoteFile

/** Shizuku 目录选择器：默认打开 /sdcard/Android/data，可逐级进入，也可直接输入路径。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirPickerDialog(
    bridge: FileBridge,
    startPath: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var path by remember { mutableStateOf(startPath) }
    var manual by remember { mutableStateOf(startPath) }
    var entries by remember { mutableStateOf<List<RemoteFile>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(path) {
        loading = true
        error = null
        entries = runCatching {
            bridge.list(path, false, 1, 500, null)
                // 只列目录，且不列 . 开头的系统隐藏目录（否则从 /sdcard 进去满屏都是 .DataStorage 之类）
                .filter { it.isDirectory && !it.name.startsWith(".") }
                .sortedBy { it.name.lowercase() }
        }.onFailure { error = it.message ?: "读取失败" }.getOrDefault(emptyList())
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择目录") },
        text = {
            Column {
                OutlinedTextField(
                    value = manual,
                    onValueChange = { manual = it },
                    label = { Text("路径（也可直接输入）") },
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { path = manual.trim() }) { Text("前往") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "当前：$path",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let {
                    Text("读取失败：$it", color = MaterialTheme.colorScheme.error)
                }
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    item {
                        ListItem(
                            headlineContent = { Text("⬆ 上一级") },
                            modifier = Modifier.clickable { path = parentOf(path) },
                        )
                    }
                    items(entries, key = { it.path }) { f ->
                        ListItem(
                            headlineContent = { Text(f.name) },
                            modifier = Modifier.clickable {
                                path = f.path
                                manual = f.path
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(path) }) { Text("选择此目录") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun parentOf(path: String): String {
    val trimmed = path.trimEnd('/')
    val parent = trimmed.substringBeforeLast('/', "")
    return if (parent.isEmpty()) "/" else parent
}
