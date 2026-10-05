package com.landslide.shitu.shizuku

/**
 * engine 与 Shizuku 的解耦接口（规格 §5 边界原则）：
 * engine 只依赖这个接口，单测注入 FakeFileBridge，真机注入 ShizukuBridge。
 */
interface FileBridge {
    suspend fun list(
        path: String,
        recursive: Boolean,
        maxDepth: Int,
        maxCount: Int,
        after: String?,
    ): List<RemoteFile>

    suspend fun stat(path: String): RemoteFile?

    suspend fun exists(path: String): Boolean

    suspend fun mkdirs(path: String)

    suspend fun move(src: String, dst: String): Boolean

    suspend fun copy(src: String, dst: String): Boolean

    suspend fun delete(path: String): Boolean

    /** 按文件内容算出的 15 位文件名主体；读不了/算失败时返回空串。 */
    suspend fun contentName(path: String): String

    /** 自检用：uid / SELinux / SDK / 安全补丁 / 可用空间。 */
    suspend fun describeEnvironment(): String
}

/** 桥不可用时的统一异常（未绑定 / binder 已断开 / 调用失败）。 */
class BridgeException(message: String, cause: Throwable? = null) : Exception(message, cause)
