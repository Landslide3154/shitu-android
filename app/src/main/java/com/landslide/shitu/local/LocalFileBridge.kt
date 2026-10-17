package com.landslide.shitu.local

import android.content.Context
import android.os.Build
import android.os.Environment
import com.landslide.shitu.shizuku.FileBridge
import com.landslide.shitu.shizuku.RemoteFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本地通道的 [FileBridge] 实现：给 engine 用的那张接口，背后是 App 自己的 java.io。
 *
 * 什么时候用它：没装 Shizuku / 没运行 / 没授权，但用户已经给了「所有文件访问」——
 * 此时普通目录（下载、相册、微信保存目录…）能直接搬；Android/data 里的仍然只能靠 Shizuku。
 *
 * 与 [com.landslide.shitu.shizuku.ShizukuBridge] 的分工：
 * 这里没有绑定、没有重试、没有状态机，只做「转发 + 切 IO 线程」。
 * 是否可用由调用方先用 [available] 判断；就算判断错了被误调，权限不足也只是抛异常/返回 false，
 * 不会把 App 进程弄崩。
 *
 * context 目前在方法体里用不到（可用性只问 Build / Environment），保留它是为了让接线处
 * 只持有一个对象，将来要接媒体扫描之类也不必改构造签名。
 */
class LocalFileBridge(private val context: Context) : FileBridge {

    /**
     * 能不能用本地通道完整路径读写普通目录。
     *
     * 只认 Android 11 起的「所有文件访问」（MANAGE_EXTERNAL_STORAGE）：
     * 低版本的存储权限是另一套玩法，规格里本地通道只服务 Android 11+，所以这里返回 false，
     * 让调用方继续走 Shizuku。
     */
    fun available(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    // ---------- FileBridge ----------

    override suspend fun list(
        path: String,
        recursive: Boolean,
        maxDepth: Int,
        maxCount: Int,
        after: String?,
    ): List<RemoteFile> = io { LocalFileOps.list(path, recursive, maxDepth, maxCount, after) }

    override suspend fun stat(path: String): RemoteFile? = io { LocalFileOps.stat(path) }

    override suspend fun exists(path: String): Boolean = io { LocalFileOps.exists(path) }

    override suspend fun mkdirs(path: String) = io { LocalFileOps.mkdirs(path) }

    override suspend fun move(src: String, dst: String): Boolean = io { LocalFileOps.move(src, dst) }

    override suspend fun copy(src: String, dst: String): Boolean = io { LocalFileOps.copy(src, dst) }

    override suspend fun delete(path: String): Boolean = io { LocalFileOps.delete(path) }

    override suspend fun contentName(path: String): String = io { LocalFileOps.contentName(path) }

    override suspend fun describeEnvironment(): String = io { LocalFileOps.describeEnvironment() }

    /**
     * 文件操作全是阻塞 IO：一律切到 IO 调度器。
     * 列举一个几万项的目录、算大文件 SHA-256 都要几十上百毫秒，放主线程会直接 ANR。
     */
    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}
