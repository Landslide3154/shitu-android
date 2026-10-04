package com.landslide.shitu.core

/**
 * 「文件写完了没有」的判定：不看"创建后过了多久"，而是看**大小和修改时间是否已经稳定**。
 *
 * 同一个文件被观察到两次、两次之间 size 与 mtime 都没变、且间隔达到 minConfirmMillis，
 * 就认为写完了，可以搬——哪怕它是 1 秒前刚创建的。
 *
 * 这是"固定 30 秒稳定期"的升级版：下载往往几秒就完成，等满 30 秒纯属浪费；
 * 但正在写的文件每隔一会儿就变一次 size/mtime，所以不会被误判。
 */
class FileStability(
    /** 两次观察之间至少要间隔多久才算"确认稳定" */
    var minConfirmMillis: Long = 3_000L,
    /** 最多记多少个文件，防止长期运行内存膨胀 */
    private val maxTracked: Int = 5_000,
) {

    private data class Obs(val size: Long, val mtime: Long, val at: Long)

    private val seen = HashMap<String, Obs>()

    /** 记录一次观察；返回 true 表示"已经稳定、可以搬"。 */
    fun settled(path: String, size: Long, mtime: Long, now: Long): Boolean {
        val prev = seen[path]
        if (prev != null && prev.size == size && prev.mtime == mtime) {
            return now - prev.at >= minConfirmMillis
        }
        if (seen.size >= maxTracked) seen.clear()
        seen[path] = Obs(size, mtime, now)
        return false
    }

    fun forget(path: String) {
        seen.remove(path)
    }

    fun reset() = seen.clear()

    fun trackedCount(): Int = seen.size
}
