package com.landslide.shitu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.landslide.shitu.R
import com.landslide.shitu.shizuku.FileBridge
import com.landslide.shitu.shizuku.RemoteFile
import com.landslide.shitu.ui.theme.ShituSurface
import com.landslide.shitu.ui.theme.ShituRadius

/**
 * 目录选择器：从 /sdcard 往下点，也可直接输入路径。
 *
 * 视觉上不用 AlertDialog 的默认白底：整块用卡片层（[ShituSurface.card]），
 * 每个文件夹是一行 12dp 圆角的「抬起来」的小槽（[ShituSurface.inset]），
 * 点上去有 ripple —— 跟 App 其它卡片同一个语言，不会出现「一块纯白」的割裂感。
 */
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
    val readFailed = stringResource(R.string.app_dir_read_failed)
    val rowShape = ShituRadius.RowShape

    LaunchedEffect(path) {
        loading = true
        error = null
        entries = runCatching {
            bridge.list(path, false, 1, 500, null)
                // 只列目录，且不列 . 开头的系统隐藏目录（否则从 /sdcard 进去满屏都是 .DataStorage 之类）
                .filter { it.isDirectory && !it.name.startsWith(".") }
                .sortedBy { it.name.lowercase() }
        }.onFailure { error = it.message ?: readFailed }.getOrDefault(emptyList())
        loading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = ShituRadius.DialogShape,
            color = ShituSurface.card(),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.app_dir_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.app_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                ShituField(
                    value = manual,
                    onValueChange = { manual = it },
                    label = stringResource(R.string.app_dir_path_label),
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { path = manual.trim() }) {
                            Text(stringResource(R.string.app_dir_go))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                // 当前路径单独一块淡底色，长路径也能看清，不再是一行小字贴在白底上
                Surface(
                    shape = ShituRadius.RowShape,
                    color = ShituSurface.block(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text(
                        stringResource(R.string.app_dir_current, path),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }

                if (loading) {
                    LinearProgressIndicator(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
                error?.let {
                    Text(
                        stringResource(R.string.app_dir_read_failed_detail, it),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        DirRow(
                            label = stringResource(R.string.app_dir_up),
                            shape = rowShape,
                            leadingUp = true,
                            onClick = { path = parentOf(path) },
                        )
                    }
                    if (entries.isEmpty() && !loading) {
                        item {
                            Text(
                                stringResource(R.string.app_dir_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                    }
                    items(entries, key = { it.path }) { f ->
                        DirRow(
                            label = f.name,
                            shape = rowShape,
                            onClick = {
                                path = f.path
                                manual = f.path
                            },
                        )
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.app_cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onPick(path) }) {
                        Text(stringResource(R.string.app_dir_pick))
                    }
                }
            }
        }
    }
}

/** 文件夹行：圆角、浅一档的底色、点击有 ripple，右侧箭头表示「进去」。 */
@Composable
private fun DirRow(
    label: String,
    shape: RoundedCornerShape,
    leadingUp: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ShituSurface.inset())
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingUp) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!leadingUp) {
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun parentOf(path: String): String {
    val trimmed = path.trimEnd('/')
    val parent = trimmed.substringBeforeLast('/', "")
    return if (parent.isEmpty()) "/" else parent
}
