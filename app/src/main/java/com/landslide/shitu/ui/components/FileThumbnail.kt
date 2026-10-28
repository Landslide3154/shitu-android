package com.landslide.shitu.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.landslide.shitu.R
import com.landslide.shitu.ui.theme.ShituRadius
import com.landslide.shitu.ui.theme.ShituSurface
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 文件缩略图（日志页 / 设置页共用）：只对图片扩展名尝试解码；
 * 失败 / 无权限 / 非图片时画淡色占位块 + 信息图标，不抛异常、不阻塞主线程。
 *
 * @param path 文件完整路径；只对 jpg / png 等图片扩展名解码
 * @param modifier 外部布局修饰符
 * @param size 缩略图边长，默认 48dp（和日志行里的缩略图一致）
 */
@Composable
fun FileThumbnail(
    path: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val imagePath = path?.takeIf(::isImagePath)
    // 缓存命中就在组合时同步取；未命中则放进 IO 线程按 inSampleSize 解码
    val bitmap by produceState<Bitmap?>(
        initialValue = imagePath?.let { ThumbnailCache.get(it) },
        key1 = imagePath,
    ) {
        // key 变化（或从图片变成非图片）时先回到缓存里对应的那张，避免残留上一行
        val path = imagePath
        value = path?.let { ThumbnailCache.get(it) }
        if (path != null && value == null) {
            val key = path
            value = withContext(Dispatchers.IO) {
                ThumbnailCache.get(key) ?: decodeThumbnail(key)?.also {
                    ThumbnailCache.put(key, it)
                }
            }
        }
    }
    Box(
        modifier
            .size(size)
            .clip(ShituRadius.thumb(size))
            .background(ShituSurface.inset()),
        contentAlignment = Alignment.Center,
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = stringResource(R.string.logs_thumb_desc),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(size * 0.375f),
            )
        }
    }
}

private val IMAGE_EXTENSIONS =
    setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif")

/** 扩展名判断不区分大小写（jpg / JPEG / Heic 都算图片）。 */
private fun isImagePath(path: String): Boolean =
    path.substringAfterLast('.', "").lowercase(Locale.ROOT) in IMAGE_EXTENSIONS

/** 缩略图目标边长：48dp 在 3x 屏约 144px，解码到 144~288px 已经够用。 */
private const val THUMB_TARGET_PX = 144

/**
 * 用 BitmapFactory 解码缩略图：先只读尺寸算 inSampleSize，再按采样解码，
 * 不会把整张原图读进内存；文件不存在/无权限时返回 null，由界面画占位块。
 */
private fun decodeThumbnail(path: String): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return@runCatching null
    var sample = 1
    while (longest / (sample * 2) >= THUMB_TARGET_PX) sample *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    BitmapFactory.decodeFile(path, options)
}.getOrNull()

/**
 * 进程内单例缩略图缓存（最多 8 MB），滚动时同一路径不反复解码。
 * 淘汰时不主动 recycle：Compose 可能还在画这一帧，交给 GC 更安全。
 */
private object ThumbnailCache {
    private const val MAX_BYTES = 8 * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(path: String): Bitmap? = cache.get(path)

    fun put(path: String, bitmap: Bitmap) {
        // 单张就超预算的图不塞进去，免得刚放进去就被挤掉
        if (bitmap.byteCount <= MAX_BYTES) cache.put(path, bitmap)
    }
}
