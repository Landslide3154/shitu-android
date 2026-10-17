package com.landslide.shitu.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.landslide.shitu.R

/**
 * 带圆圈的问号：点一下弹 [text] 的功能说明。
 *
 * 正文由调用方传入（各页面自己加字符串资源）；标题和按钮共用
 * [R.string.help_title] / [R.string.help_ok]。
 */
@Composable
fun HelpDot(text: String, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }

    IconButton(
        onClick = { show = true },
        modifier = modifier.size(40.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = stringResource(R.string.help_title),
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp),
        )
    }

    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text(stringResource(R.string.help_title)) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { show = false }) {
                    Text(stringResource(R.string.help_ok))
                }
            },
        )
    }
}
