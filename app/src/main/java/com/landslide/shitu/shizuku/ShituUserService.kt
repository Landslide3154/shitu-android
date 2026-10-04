package com.landslide.shitu.shizuku

import android.os.Build
import android.os.Process
import com.landslide.shitu.IShituService
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 跑在 shell 身份（uid=2000 / u:r:shell:s0）下的执行者。
 *
 * 边界（规格 §5）：只知道文件，不知道规则；不懂命名策略、不写数据库、不提供通用 shell。
 */
class ShituUserService : IShituService.Stub() {

    companion object {
        private const val PAGE_LIMIT = 500
    }

    override fun listFiles(
        path: String,
        recursive: Boolean,
        maxDepth: Int,
        maxCount: Int,
        afterPath: String?,
    ): List<RemoteFile> {
        val root = File(path)
        if (!root.isDirectory) return emptyList()
        val limit = maxCount.coerceIn(1, PAGE_LIMIT)
        val out = ArrayList<RemoteFile>(minOf(limit, 64))

        // 迭代式 DFS（显式栈），字典序固定 → 游标可复现
        val stack = ArrayDeque<Pair<File, Int>>()
        stack.addLast(root to 0)
        var skipping = !afterPath.isNullOrEmpty()

        while (stack.isNotEmpty() && out.size < limit) {
            val (dir, depth) = stack.removeLast()
            val children = runCatching { dir.listFiles() }.getOrNull()?.sortedBy { it.name } ?: continue
            for (c in children) {
                if (out.size >= limit) break
                if (skipping) {
                    if (c.absolutePath == afterPath) skipping = false
                    continue
                }
                out.add(toRemote(c))
                if (recursive && c.isDirectory && !isSymlink(c) &&
                    (maxDepth <= 0 || depth + 1 < maxDepth)
                ) {
                    stack.addLast(c to depth + 1)
                }
            }
        }
        return out
    }

    override fun stat(path: String): RemoteFile? {
        val f = File(path)
        return if (f.exists()) toRemote(f) else null
    }

    override fun exists(path: String): Boolean = runCatching { File(path).exists() }.getOrDefault(false)

    override fun mkdirs(path: String) {
        runCatching { File(path).mkdirs() }
    }

    /** 优先原子 rename；跨卷/不支持时回退 copy → 校验 → 删源。任何情况下不覆盖目标。 */
    override fun move(srcPath: String, dstPath: String): Boolean {
        val src = File(srcPath)
        val dst = File(dstPath)
        if (!src.exists()) return false
        if (dst.exists()) return false
        dst.parentFile?.mkdirs()
        val renamed = runCatching {
            Files.move(src.toPath(), dst.toPath(), StandardCopyOption.ATOMIC_MOVE)
            !src.exists() && dst.exists()
        }.getOrDefault(false)
        if (renamed) return true
        return copyThenDelete(src, dst)
    }

    override fun copy(srcPath: String, dstPath: String): Boolean {
        val src = File(srcPath)
        val dst = File(dstPath)
        // 注意：不能要求 src.isFile —— 自检要拿 /dev/null 当"空文件源"造临时文件
        if (!src.exists() || src.isDirectory) return false
        if (dst.exists()) return false
        dst.parentFile?.mkdirs()
        return runCatching {
            stream(src, dst)
            if (dst.length() != src.length()) {
                dst.delete()
                false
            } else {
                true
            }
        }.getOrDefault(false)
    }

    override fun delete(path: String): Boolean = runCatching { File(path).delete() }.getOrDefault(false)

    /**
     * 注意：这里是 shell 身份的进程，**不能**用 Environment / StatFs 这类会去问系统"当前用户/调用包名"的 API
     * （会抛 SecurityException: callingPackage does not match UID）。只用纯 java.io 的 statvfs。
     */
    override fun describeEnvironment(): String {
        val selinux = runCatching { File("/proc/self/attr/current").readText().trim() }.getOrDefault("?")
        val storage = File("/sdcard")
        val free = runCatching { storage.usableSpace }.getOrDefault(-1L)
        val total = runCatching { storage.totalSpace }.getOrDefault(-1L)
        return buildString {
            append("uid=").append(Process.myUid()).append(' ')
            append("selinux=").append(selinux).append(' ')
            append("sdk=").append(Build.VERSION.SDK_INT).append(' ')
            append("patch=").append(Build.VERSION.SECURITY_PATCH).append(' ')
            append("device=").append(Build.MODEL).append(' ')
            append("free=").append(free).append(' ')
            append("total=").append(total)
        }
    }

    // ---------- 内部工具 ----------

    private fun copyThenDelete(src: File, dst: File): Boolean = try {
        stream(src, dst)
        if (dst.length() != src.length()) {
            dst.delete()
            false
        } else if (!src.delete()) {
            // 删源失败：撤销副本，保证"绝不先删后搬"不产生双份残留
            dst.delete()
            false
        } else {
            true
        }
    } catch (t: Throwable) {
        runCatching { dst.delete() }
        false
    }

    private fun stream(src: File, dst: File) {
        FileInputStream(src).use { input ->
            FileOutputStream(dst).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
    }

    private fun isSymlink(f: File): Boolean =
        runCatching { Files.isSymbolicLink(f.toPath()) }.getOrDefault(false)

    private fun toRemote(f: File): RemoteFile = RemoteFile(
        path = f.absolutePath,
        name = f.name,
        size = if (f.isFile) f.length() else 0L,
        mtimeMillis = f.lastModified(),
        isDirectory = f.isDirectory,
        isSymlink = isSymlink(f),
    )
}
