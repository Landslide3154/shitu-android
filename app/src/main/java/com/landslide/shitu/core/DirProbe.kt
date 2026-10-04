package com.landslide.shitu.core

/**
 * 便宜的"这个目录变了没有"探测器。
 *
 * 原理：目录自身的 mtime 在**有人新建/删除/改名文件**时会变；只 stat 目录、不列文件，
 * 所以可以每几秒跑一次而几乎不耗电。首次见到的目录按"变了"处理。
 *
 * 用途：事件驱动（inotify）拿不到或不稳时的次优解——用它把"每隔几分钟整目录扫一遍"
 * 变成"每几秒看一眼，真变了才扫"。
 */
class DirProbe {

    private val lastMtime = HashMap<String, Long>()

    /** 返回 mtime 发生变化的目录；stat 返回 null（目录不存在/无权限）时跳过。 */
    suspend fun changed(dirs: Collection<String>, stat: suspend (String) -> Long?): List<String> {
        val out = ArrayList<String>()
        for (d in dirs) {
            val m = stat(d) ?: continue
            val old = lastMtime.put(d, m)
            if (old == null || old != m) out += d
        }
        return out
    }

    /** 只对齐基线、不报变化（例如刚跑完一轮全量扫描之后）。 */
    suspend fun sync(dirs: Collection<String>, stat: suspend (String) -> Long?) {
        for (d in dirs) stat(d)?.let { lastMtime[d] = it }
    }

    fun forget(dir: String) {
        lastMtime.remove(dir)
    }

    fun reset() = lastMtime.clear()
}
