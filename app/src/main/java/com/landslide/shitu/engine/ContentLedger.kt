package com.landslide.shitu.engine

/**
 * 「这份内容已经搬进来过」的账本（全局，跨规则、跨源目录）。
 *
 * 为什么需要它：复制模式的重复判断原本靠"目标目录里还有没有那份文件"，
 * 但目标目录常常只是中转站——用户手工把文件移走/改名之后依据就断了，
 * 同一份内容会被反复复制进来。改成按**内容指纹**记账，就与文件后来去哪无关了。
 *
 * engine 只依赖这个接口；真机实现是 Room（`copied` + `items` 表），单测注入内存版。
 */
interface ContentLedger {

    /** 这份内容以前复制过吗？ */
    suspend fun seen(fingerprint: String): Boolean

    /**
     * 这个源文件以前已经成功搬走/复制过吗？
     *
     * 判定口径故意取得很严：**同规则 + 同源路径 + 同大小 + 同 mtime** 才算同一个文件；
     * 文件被改过（size 或 mtime 变了）就必须重新处理。
     *
     * 它是一层廉价的预检，放在算内容指纹（SHA-256）之前：复制模式的源文件会一直留在原地、
     * 每轮都被重扫，不先挡掉就要把同一批文件反复读一遍算指纹。
     */
    suspend fun handledBefore(ruleId: Long, srcPath: String, size: Long, mtime: Long): Boolean

    /** 记下"这份内容已经复制进来了"。 */
    suspend fun remember(fingerprint: String, ruleId: Long, dstPath: String?, now: Long)

    /** 账本里有多少条（设置页显示用）。 */
    suspend fun count(): Int

    /** 清空账本；返回清掉多少条。 */
    suspend fun clear(): Int
}
