package com.landslide.shitu.local

import android.os.Build
import android.os.Process
import com.landslide.shitu.core.ContentName
import com.landslide.shitu.shizuku.RemoteFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * 没有 Shizuku 时的本地文件通道：用 App 自己的身份做完全相同的文件操作。
 *
 * 它是 [com.landslide.shitu.shizuku.ShituUserService] 的镜像——迭代 DFS + 游标分页、
 * ATOMIC_MOVE 优先、copy 校验大小、绝不覆盖、MOVE 失败撤销副本，安全语义一条不少；
 * 差别只在身份：那份跑在 shell（uid=2000）下，能进 Android/data；这份跑在 App 进程里，
 * 靠用户给的「所有文件访问」权限工作，只碰得到普通目录（下载、相册、微信保存目录…）。
 *
 * 为什么不复用 UserService 的实现：
 * 那一份绑在 AIDL/Binder 与 shell 身份上，纯 JVM 单测跑不起来；这里只有 java.io，
 * 可以用单测把「不覆盖、不先删后搬」这些铁律钉死。
 *
 * 为什么不在这一层判断权限（不用 android.os.Environment）：
 * 「能不能用」和「怎么搬文件」是两件事；权限判断放在 [LocalFileBridge.available] 与
 * [StorageAccess]，这里才能保持可被 JVM 单测直接调用。
 */
object LocalFileOps {

    /** 单页上限，与 UserService 一致：一次列举不能把整棵目录树拉进内存。 */
    private const val PAGE_LIMIT = 500

    // ---------- 列举 ----------

    /**
     * 列出目录内容（可选递归）。
     *
     * 为什么用显式栈而不是递归/[File.walk]：
     * 1) 相册目录可能很深（几千层）或很大（几万项），递归会爆栈；
     * 2) 游标分页要求「同一个起点每次的顺序完全一致」——每层按名字排序 + 固定的栈顺序就能做到，
     *    调用方拿上一页最后一个 path 当 after 接着列，不重不漏。
     *
     * 不过滤 `.` 开头的隐藏目录：过滤是 UI 的事（选目录时不想看到 .DataStorage），
     * 引擎需要看到真实存在的目录。这一点与 shell 侧保持一致。
     */
    fun list(
        path: String,
        recursive: Boolean,
        maxDepth: Int,
        maxCount: Int,
        after: String?,
    ): List<RemoteFile> {
        val root = File(path)
        if (!root.isDirectory) return emptyList()
        val limit = maxCount.coerceIn(1, PAGE_LIMIT)
        val out = ArrayList<RemoteFile>(minOf(limit, 64))

        // 显式栈（目录, 当前深度）；addLast + removeLast = LIFO，顺序确定，游标才可复现
        val stack = ArrayDeque<Pair<File, Int>>()
        stack.addLast(root to 0)
        // after 那个文件可能已经被删了：那样 skipping 会一直为真，宁可少列也不重列
        var skipping = !after.isNullOrEmpty()

        while (stack.isNotEmpty() && out.size < limit) {
            val (dir, depth) = stack.removeLast()
            // 读不到（权限不够/刚被删）就跳过这个目录：不该因为一个子目录失败就丢掉整页
            val children = runCatching { dir.listFiles() }.getOrNull()?.sortedBy { it.name } ?: continue
            for (c in children) {
                if (out.size >= limit) break
                // 重建遍历时，after 之前的内容只「不发射」，但目录**照样要入栈**：
                // 游标恰好停在目录上时，它的子树在遍历顺序里还在后面，不入栈就会整棵子树漏掉。
                val skipThis = skipping
                if (skipping && c.absolutePath == after) skipping = false
                if (!skipThis) out.add(toRemote(c))
                // 不跟进符号链接：跟着链接走会绕圈，也会把链接目标的内容误当成这里的
                if (recursive && c.isDirectory && !isSymlink(c) &&
                    (maxDepth <= 0 || depth + 1 < maxDepth)
                ) {
                    stack.addLast(c to depth + 1)
                }
            }
        }
        return out
    }

    // ---------- 单个操作 ----------

    fun stat(path: String): RemoteFile? {
        val f = File(path)
        return if (f.exists()) toRemote(f) else null
    }

    fun exists(path: String): Boolean = runCatching { File(path).exists() }.getOrDefault(false)

    fun mkdirs(path: String) {
        // 建不出来（权限不够/路径被文件占了）不抛：真正的结果由随后的 move/copy 说话
        runCatching { File(path).mkdirs() }
    }

    /**
     * 移动。优先 ATOMIC_MOVE（同卷 rename：要么没动、要么全动，断电也不会半个文件）；
     * Android/data → 公共目录这种跨卷会抛 AtomicMoveNotSupportedException，回退 copy → 校验 → 删源。
     *
     * 铁律：目标已存在就直接返回 false，绝不覆盖；回退路径失败会撤掉副本，源永远不动。
     */
    fun move(srcPath: String, dstPath: String): Boolean {
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

    /**
     * 复制。写完后大小对不上就删掉副本返回 false——
     * 搬过去的文件必须和源一模一样，否则用户会得到一张截断的图，我们却以为搬成功了。
     */
    fun copy(srcPath: String, dstPath: String): Boolean {
        val src = File(srcPath)
        val dst = File(dstPath)
        // 注意：不能要求 src.isFile——自检要拿 /dev/null 当"空文件源"造临时文件
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

    fun delete(path: String): Boolean = runCatching { File(path).delete() }.getOrDefault(false)

    /**
     * 按内容算 15 位文件名主体。
     *
     * 读+算都在文件所在的一侧完成：文件不必先读回内存，也不会撞上 Binder 的 1MB 事务上限。
     * 读不了/是目录返回空串，调用方回退到原名。
     */
    fun contentName(path: String): String {
        val f = File(path)
        if (!f.exists() || f.isDirectory) return ""
        return runCatching {
            val md = MessageDigest.getInstance("SHA-256")
            FileInputStream(f).use { input ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
            ContentName.fromDigest(md.digest())
        }.getOrDefault("")
    }

    /**
     * 自检用的环境串。这里读到的是 App 自己的身份（untrusted_app），不是 shell。
     *
     * 全部包 runCatching：某个 /proc 字段读不到不该让整页自检失败。
     * 「所有文件访问」是否已授予**不在这里判断**——那属于权限层（[StorageAccess]），
     * 放进来会让这个方法依赖 Environment，进而逼单测造桩。
     */
    fun describeEnvironment(): String {
        val uid = runCatching { Process.myUid() }.getOrDefault(-1)
        val selinux = runCatching { File("/proc/self/attr/current").readText().trim() }.getOrDefault("?")
        val sdk = runCatching { Build.VERSION.SDK_INT }.getOrDefault(-1)
        val patch = runCatching { Build.VERSION.SECURITY_PATCH }.getOrDefault("?")
        val device = runCatching { Build.MODEL }.getOrDefault("?")
        val storage = File("/sdcard")
        val free = runCatching { storage.usableSpace }.getOrDefault(-1L)
        val total = runCatching { storage.totalSpace }.getOrDefault(-1L)
        return buildString {
            append("mode=local ")
            append("uid=").append(uid).append(' ')
            append("selinux=").append(selinux).append(' ')
            append("sdk=").append(sdk).append(' ')
            append("patch=").append(patch).append(' ')
            append("device=").append(device).append(' ')
            append("free=").append(free).append(' ')
            append("total=").append(total)
        }
    }

    // ---------- 内部工具 ----------

    /** ATOMIC_MOVE 不支持时的退路：copy → 校验 → 删源；任何一步失败都撤掉副本，绝不留双份。 */
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
