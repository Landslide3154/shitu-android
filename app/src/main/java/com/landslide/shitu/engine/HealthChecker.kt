package com.landslide.shitu.engine

import com.landslide.shitu.shizuku.FileBridge

/**
 * 自检 6 项（规格 §6.9，验收 A7）。
 * 只用自己创建的临时文件，绝不触碰用户的真实图片；结束时会清理临时文件。
 */
class HealthChecker(
    private val bridge: FileBridge,
    private val probeSrcDir: String,
    private val probeDstDir: String,
    private val environmentLine: () -> String = { "" },
) {
    data class Item(val name: String, val passed: Boolean, val detail: String)

    private val cleanup = ArrayList<String>()

    suspend fun run(): List<Item> {
        val out = ArrayList<Item>()
        val stamp = System.currentTimeMillis()

        // 1) Shizuku 通道：能否真正调用到 shell 身份的实现
        val env = runCatching { bridge.describeEnvironment() }.getOrElse { "" }
        val reachable = env.isNotBlank()
        val envLine = runCatching { environmentLine() }.getOrDefault("")
        out += Item(
            "1. Shizuku 通道",
            reachable,
            if (reachable) "$envLine | $env".trim() else "无法调用 UserService（未绑定或未授权）",
        )

        // 2) 源目录可列举
        val srcStat = runCatching { bridge.stat(probeSrcDir) }.getOrNull()
        val srcIsDir = srcStat?.isDirectory == true
        val listed = if (srcIsDir) {
            runCatching { bridge.list(probeSrcDir, false, 1, 10, null) }.getOrNull()
        } else {
            null
        }
        out += Item(
            "2. 可列举源目录",
            srcIsDir && listed != null,
            if (srcIsDir) "$probeSrcDir 条目=${listed?.size ?: -1}" else "源目录不存在或不是目录：$probeSrcDir",
        )

        // 3) 目标目录可写（建目录 + 造一个临时文件 + 删掉）
        val tmpDst = "$probeDstDir/_shitu_selftest_$stamp"
        val dstWritable = runCatching {
            bridge.mkdirs(probeDstDir)
            bridge.copy(NULL_DEVICE, tmpDst) && bridge.exists(tmpDst)
        }.getOrDefault(false)
        out += Item("3. 目标目录可写", dstWritable, probeDstDir)
        if (dstWritable) cleanup += tmpDst

        // 4) 源目录可写：新建 → 改名 → 删除（对应 Shizuku #1574 类写权限问题）
        val w1 = "$probeSrcDir/_shitu_w_$stamp"
        val w2 = "$probeSrcDir/_shitu_w2_$stamp"
        val srcWritable = runCatching {
            bridge.copy(NULL_DEVICE, w1) && bridge.move(w1, w2) && bridge.delete(w2)
        }.getOrDefault(false)
        out += Item("4. 源目录可写/改名/删", srcWritable, probeSrcDir)
        if (!srcWritable) {
            cleanup += w1
            cleanup += w2
        }

        // 5) 源 → 目标 跨目录移动
        val m1 = "$probeSrcDir/_shitu_mv_$stamp"
        val m2 = "$probeDstDir/_shitu_mv_$stamp"
        val movable = runCatching {
            bridge.copy(NULL_DEVICE, m1) && bridge.move(m1, m2)
        }.getOrDefault(false)
        out += Item("5. 源→目标 可移动", movable, "$m1 → $m2")
        if (movable) cleanup += m2 else cleanup += m1

        // 6) 环境信息（便于日后与今天的结论对比）
        out += Item("6. 环境信息", reachable, if (reachable) env else "不可用")

        // 清理：只删自己造的临时文件
        cleanup.forEach { runCatching { bridge.delete(it) } }
        return out
    }

    private companion object {
        /** shell 身份可读、大小为 0 的"空文件源"，用来创建临时文件（桥没有 create 接口）。 */
        const val NULL_DEVICE = "/dev/null"
    }
}
