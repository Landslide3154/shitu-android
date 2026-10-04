package com.landslide.shitu.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Shizuku 侧的全部接触点：权限、绑定、状态推导、指数退避重试。
 * 只实现 FileBridge，不含任何业务规则。
 */
class ShizukuBridge(private val context: Context) : FileBridge {

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val REQUEST_CODE = 4213
        const val MAX_BIND_FAILURES = 5
    }

    @Volatile private var binder: com.landslide.shitu.IShituService? = null

    @Volatile private var bindFailures = 0

    /** 记住当前的绑定参数与连接，便于替换/释放时解绑（否则 Shizuku 会在 shell 侧留下常驻进程）。 */
    @Volatile private var currentArgs: Shizuku.UserServiceArgs? = null

    @Volatile private var currentConn: ServiceConnection? = null

    @Volatile var lastError: String? = null
        private set

    // ---------- 状态 ----------

    fun state(): ShizukuState {
        // 手上已有活着的连接就说明可用：把失败计数清零（自愈），避免"曾经失败过"永久卡在 BIND_FAILED
        if (binder != null && bindFailures != 0) bindFailures = 0
        return derive(
            installed = isInstalled(),
            running = runCatching { Shizuku.pingBinder() }.getOrDefault(false),
            permitted = runCatching {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false),
            bindFailures = bindFailures,
            binderAlive = binder != null,
        )
    }

    fun isInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    }.getOrDefault(false)

    fun isPermitted(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun shizukuVersion(): String =
        runCatching { "${Shizuku.getVersion()}" }.getOrDefault("?")

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
            .onFailure { lastError = "请求权限失败：${it.message}" }
    }

    fun isBound(): Boolean = binder != null

    /** binder 断开时调用（由 Shizuku 监听器触发）。 */
    fun onBinderLost() {
        binder = null
        bindFailures++
    }

    /** 释放当前绑定（切进程/退出时用；kill=true 让 Shizuku 回收 shell 侧进程）。 */
    fun unbind(kill: Boolean = true) {
        val args = currentArgs
        val conn = currentConn
        binder = null
        currentArgs = null
        currentConn = null
        if (args != null && conn != null) {
            runCatching { Shizuku.unbindUserService(args, conn, kill) }
        }
    }

    // ---------- 绑定 ----------

    /** 单次绑定，最长等 10 秒；成功清零计数。 */
    suspend fun bindOnce(timeoutMs: Long = 10_000): Boolean {
        if (binder != null) {
            bindFailures = 0
            return true
        }
        if (runCatching { Shizuku.pingBinder() }.getOrDefault(false).not()) return false

        val ok = withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.IO) {
                suspendCancellableCoroutine { cont ->
                    val args = Shizuku.UserServiceArgs(
                        ComponentName(context.packageName, ShituUserService::class.java.name),
                    )
                        .daemon(false)
                        .processNameSuffix("shitu_service")
                        .debuggable(false)
                        .version(1)

                    val conn = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                            binder = service?.let { com.landslide.shitu.IShituService.Stub.asInterface(it) }
                            bindFailures = 0
                            lastError = null
                            if (cont.isActive) cont.resume(binder != null)
                        }

                        override fun onServiceDisconnected(name: ComponentName?) {
                            binder = null
                            bindFailures++
                            lastError = "UserService 已断开"
                            if (cont.isActive) cont.resume(false)
                        }
                    }

                    currentArgs = args
                    currentConn = conn

                    runCatching { Shizuku.bindUserService(args, conn) }
                        .onFailure {
                            bindFailures++
                            lastError = "bindUserService 失败：${it.message}"
                            if (cont.isActive) cont.resume(false)
                        }
                }
            }
        } == true

        if (!ok) {
            bindFailures++
            // 绑定失败时把这次的连接参数放掉，避免 Shizuku 侧残留半死的 UserService 进程
            unbind(kill = true)
        }
        return ok
    }

    /** 指数退避：1s/2s/4s/8s/16s，最多 5 次（规格 §6.2）。 */
    suspend fun bindWithRetry(maxAttempts: Int = MAX_BIND_FAILURES): Boolean {
        var delayMs = 1_000L
        repeat(maxAttempts) {
            if (bindOnce()) return true
            if (state() == ShizukuState.NO_PERMISSION || state() == ShizukuState.NOT_INSTALLED) return false
            kotlinx.coroutines.delay(delayMs)
            delayMs = (delayMs * 2).coerceAtMost(60_000L)
        }
        return false
    }

    // ---------- FileBridge ----------

    private fun svc(): com.landslide.shitu.IShituService =
        binder ?: throw BridgeException("UserService 未绑定（state=${state().name}）")

    private suspend fun <T> call(block: (com.landslide.shitu.IShituService) -> T): T =
        withContext(Dispatchers.IO) {
            runCatching { block(svc()) }.getOrElse { t ->
                if (t is BridgeException) throw t
                lastError = t.message
                throw BridgeException(t.message ?: t.javaClass.simpleName, t)
            }
        }

    override suspend fun list(
        path: String,
        recursive: Boolean,
        maxDepth: Int,
        maxCount: Int,
        after: String?,
    ): List<RemoteFile> = call { it.listFiles(path, recursive, maxDepth, maxCount, after) }

    override suspend fun stat(path: String): RemoteFile? = call { it.stat(path) }

    override suspend fun exists(path: String): Boolean = call { it.exists(path) }

    override suspend fun mkdirs(path: String) = call { it.mkdirs(path) }

    override suspend fun move(src: String, dst: String): Boolean = call { it.move(src, dst) }

    override suspend fun copy(src: String, dst: String): Boolean = call { it.copy(src, dst) }

    override suspend fun delete(path: String): Boolean = call { it.delete(path) }

    override suspend fun describeEnvironment(): String = call { it.describeEnvironment() }

    /** 用于自检第 1 项：Shizuku 安装/运行/授权 三态文字。 */
    fun environmentLine(): String {
        val installed = if (isInstalled()) "已安装" else "未安装"
        val running = if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) "运行中" else "未运行"
        val permitted = if (isPermitted()) "已授权" else "未授权"
        return "Shizuku $installed / $running / $permitted / 版本 ${shizukuVersion()}"
    }
}
