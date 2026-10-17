package com.landslide.shitu.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.landslide.shitu.ui.theme.ShituRadius

/**
 * 全 App 唯一的输入框：圆角、字号、placeholder 都在这里定。
 *
 * 页面里不要再直接用 [OutlinedTextField]，否则又会出现各处不一致。
 *
 * 用户的原始反馈是「新建规则里的输入框字太大、框太大、圆角太小」：
 * - 字号：Material3 默认 bodyLarge（16sp）→ 统一 bodyMedium（14sp），placeholder 同步 14sp；
 * - 圆角：默认 4dp → 统一 14dp（[ShituRadius.Field]）；
 * - 高度：暂时保持 Material3 的 56dp 最小高度，不硬压（硬压会裁文字）；真机看完再决定要不要动。
 */
@Composable
fun ShituField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        readOnly = readOnly,
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = ShituRadius.FieldShape,
        keyboardOptions = keyboardOptions,
        label = if (label != null) {
            { Text(label) }
        } else {
            null
        },
        placeholder = if (placeholder != null) {
            { Text(placeholder, style = MaterialTheme.typography.bodyMedium) }
        } else {
            null
        },
        trailingIcon = trailingIcon,
        supportingText = supportingText,
    )
}
