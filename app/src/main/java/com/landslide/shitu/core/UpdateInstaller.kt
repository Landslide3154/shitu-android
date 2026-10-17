package com.landslide.shitu.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/**
 * 检查更新之后的「下载并安装」。
 *
 * 只做两件事：把 APK 下到 `cacheDir/update/`、再用 FileProvider 交给系统安装器。
 * 不碰签名校验（签名由系统安装器把关：签名不一致会直接拒装），也不静默安装
 * （普通应用没有那个权限，也不该有）。
 */
object UpdateInstaller {

    /** 与 AndroidManifest 里 provider 的 authorities 保持一致 */
    private const val AUTHORITY_SUFFIX = ".fileprovider"

    fun downloadedFile(context: Context, version: String): File {
        val dir = File(context.cacheDir, "update").apply { runCatching { mkdirs() } }
        return File(dir, "shitu-$version.apk")
    }

    /**
     * 阻塞下载；返回落盘的 APK。已有同版本文件时直接复用（断点续传没做，重下最省事）。
     * [onProgress] 回调 (已下载字节, 总字节)；总字节可能为 -1（服务器没给 Content-Length）。
     */
    fun download(
        context: Context,
        url: String,
        version: String,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): File {
        val target = downloadedFile(context, version)
        if (target.exists() && target.length() > 0) return target

        val tmp = File(target.parentFile, "${target.name}.part")
        val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "shitu-android")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("下载失败（HTTP $code）")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(256 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        read += n
                        onProgress(read, total)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }

        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        if (!target.exists() || target.length() <= 0) error("下载的文件是空的")
        return target
    }

    /** 系统是否允许本应用安装 APK（「安装未知应用」开关）。 */
    fun canInstall(context: Context): Boolean =
        runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(true)

    fun requestInstallPermission(context: Context) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** 交给系统安装器；没开「安装未知应用」时由系统弹窗引导。 */
    fun install(context: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            context.packageName + AUTHORITY_SUFFIX,
            apk,
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
