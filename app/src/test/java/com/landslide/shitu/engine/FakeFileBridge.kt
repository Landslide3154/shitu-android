package com.landslide.shitu.engine

import com.landslide.shitu.core.ContentName
import com.landslide.shitu.shizuku.BridgeException
import com.landslide.shitu.shizuku.FileBridge
import com.landslide.shitu.shizuku.RemoteFile

/**
 * 内存版 FileBridge：模拟 UserService 的行为（含"永不覆盖"、DFS 字典序、游标分页），
 * 供 core/engine 的单测使用，不依赖真机。
 */
open class FakeFileBridge : FileBridge {

    val files = LinkedHashMap<String, RemoteFile>()
    val dirs = linkedSetOf<String>()

    var failMove = false
    var failCopy = false
    var failDelete = false
    var listThrows = false
    var envLine = "uid=2000(fake) selinux=u:r:shell:s0 sdk=36 patch=2026-07-01"

    init {
        files["/dev/null"] = RemoteFile("/dev/null", "null", 0, 0, false, false)
        dirs += "/"
        dirs += "/sdcard"
    }

    fun put(path: String, size: Long = 100, mtime: Long = 1_000): RemoteFile {
        val f = RemoteFile(path, path.substringAfterLast('/'), size, mtime, false, false)
        files[path] = f
        markParents(path)
        return f
    }

    fun putDir(path: String) {
        dirs += path
        markParents(path)
    }

    private fun markParents(path: String) {
        var p = path.substringBeforeLast('/', "")
        while (p.isNotEmpty()) {
            dirs += p
            p = p.substringBeforeLast('/', "")
        }
    }

    private fun entriesOf(dir: String): List<RemoteFile> {
        val prefix = dir.trimEnd('/') + "/"
        val direct = files.values.filter {
            it.path.startsWith(prefix) && !it.path.removePrefix(prefix).contains('/')
        }
        val subDirs = dirs.filter {
            it.startsWith(prefix) && it != dir && !it.removePrefix(prefix).contains('/')
        }.map { RemoteFile(it, it.substringAfterLast('/'), 0, 0, true, false) }
        return (direct + subDirs).sortedBy { it.name }
    }

    override suspend fun list(
        path: String,
        recursive: Boolean,
        maxDepth: Int,
        maxCount: Int,
        after: String?,
    ): List<RemoteFile> {
        if (listThrows) throw BridgeException("fake list failure")
        val root = path.trimEnd('/')
        val limit = maxCount.coerceIn(1, 500)
        val out = ArrayList<RemoteFile>()
        var skipping = !after.isNullOrEmpty()
        val stack = ArrayDeque<Pair<String, Int>>()
        stack.addLast(root to 0)
        while (stack.isNotEmpty() && out.size < limit) {
            val (dir, depth) = stack.removeLast()
            for (c in entriesOf(dir)) {
                if (out.size >= limit) break
                if (skipping) {
                    if (c.path == after) skipping = false
                    continue
                }
                out.add(c)
                if (recursive && c.isDirectory && (maxDepth <= 0 || depth + 1 < maxDepth)) {
                    stack.addLast(c.path to depth + 1)
                }
            }
        }
        return out
    }

    override suspend fun stat(path: String): RemoteFile? =
        files[path] ?: if (path.trimEnd('/') in dirs) {
            RemoteFile(path, path.substringAfterLast('/'), 0, 0, true, false)
        } else {
            null
        }

    override suspend fun exists(path: String): Boolean =
        files.containsKey(path) || path.trimEnd('/') in dirs

    override suspend fun mkdirs(path: String) {
        putDir(path.trimEnd('/'))
    }

    override suspend fun move(src: String, dst: String): Boolean {
        if (failMove) return false
        val f = files[src] ?: return false
        if (files.containsKey(dst)) return false // 永不覆盖
        files.remove(src)
        files[dst] = f.copy(path = dst, name = dst.substringAfterLast('/'))
        markParents(dst)
        return true
    }

    override suspend fun copy(src: String, dst: String): Boolean {
        if (failCopy) return false
        val f = files[src] ?: return false
        if (files.containsKey(dst)) return false
        files[dst] = f.copy(path = dst, name = dst.substringAfterLast('/'))
        markParents(dst)
        return true
    }

    override suspend fun delete(path: String): Boolean {
        if (failDelete) return false
        return files.remove(path) != null
    }

    /**
     * 假的内容名：用「大小 + 修改时间」当"内容指纹"（真机上是整个文件的 SHA-256）。
     * 这样单测里"同内容 → 同名"的行为能被验证，也方便断言具体名字。
     */
    override suspend fun contentName(path: String): String {
        val f = files[path] ?: return ""
        val seed = "size=${f.size};mtime=${f.mtimeMillis}"
        return ContentName.fromDigest(seed.toByteArray(Charsets.UTF_8))
    }

    override suspend fun describeEnvironment(): String = envLine
}
