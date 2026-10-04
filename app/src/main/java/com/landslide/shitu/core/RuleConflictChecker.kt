package com.landslide.shitu.core

import com.landslide.shitu.data.db.RuleEntity

/**
 * 规则冲突静态检查（规格 §10 页面 2）：只提示，不阻止保存。
 *
 * 两条硬口径（0.9.1 起）：
 *
 * 1. **只报正在编辑的这条规则自己的事**。别的规则之间闹矛盾，不往这张编辑页上搬
 *    （以前是把整份规则列表两两比对，于是编辑 A 会看到"B 和 C 冲突"这种与自己无关的提醒）。
 * 2. **只报真会发生的事**。不勾「包含子目录」时一轮扫描只看源目录这一层，
 *    设了「最大深度」也同理；凡是"扫进子目录才会碰到"的冲突，这时一律不提。
 *    另外已经停用的规则根本不会跑，也不参与比较（[RuleEngine] 只跑 `enabled` 的规则）。
 * 3. **同类问题合并成一条**，涉及的规则名一次点全——同一个道理说三遍就是噪音。
 */
object RuleConflictChecker {

    /**
     * [focus] = 正在编辑的这条，[others] = 其余规则（调用方按 id 把 focus 排除掉）。
     * 返回值直接给编辑页展示，没有问题时为空列表。
     */
    fun check(focus: RuleEntity, others: List<RuleEntity>): List<String> {
        val out = LinkedHashSet<String>()
        val me = focus.name.ifBlank { "这条规则" }
        val src = focus.srcPath.trimEnd('/')
        val dst = focus.dstPath.trimEnd('/')

        // ---- 1) 这条规则自己内部的矛盾 ----
        if (src.isNotBlank() && dst.isNotBlank()) {
            when {
                src == dst ->
                    out += "「$me」的源目录和目标目录是同一个，搬了还是在原地"

                // 目标在源里面：只有扫描会走进目标文件夹（勾了包含子目录、且深度没被限制）才有影响
                dst.startsWith("$src/") && scansInto(focus, dst) ->
                    out += "「$me」勾了包含子目录，扫描会连目标文件夹一起看；" +
                        "建议把目标目录放到源目录外面，规则更清楚、也更好撤回"

                // 源在目标里面：候选文件全都"已经在目标目录里"被跳过，这条规则搬不动任何东西
                src.startsWith("$dst/") ->
                    out += "「$me」的源目录在目标目录里面，扫到的文件都会被当成" +
                        "「已经在目标目录里」跳过——这条规则搬不动东西"
            }
        }

        // ---- 2) 这条规则与别的规则之间（已停用的规则不参与，它不会跑）----
        // 同一类问题合并成一条：同一个道理说三遍就是噪音（用户反馈"配置提醒太多"）
        val overlap = LinkedHashSet<String>() // 会跟这条规则抢同一批图片的规则
        val fedByMe = LinkedHashSet<String>() // 我的目标 = 它的源：我搬过去的会被它接着搬走
        val feedsMe = LinkedHashSet<String>() // 它的目标 = 我的源：它搬过来的会被我接着搬走
        val mutual = LinkedHashSet<String>() // 互为来源和目标：来回搬

        others.filter { it.enabled }.forEach { o ->
            val other = o.name.ifBlank { "另一条规则" }
            val oSrc = o.srcPath.trimEnd('/')
            val oDst = o.dstPath.trimEnd('/')

            if (src.isNotBlank() && oSrc.isNotBlank()) {
                when {
                    src == oSrc -> overlap += other
                    // 包着关系：只有"会真的扫进对方源目录"的那条才算（不勾子目录、深度不够都不算）
                    scansInto(focus, oSrc) || scansInto(o, src) -> overlap += other
                }
            }
            if (src.isNotBlank() && dst.isNotBlank() && oSrc.isNotBlank() && oDst.isNotBlank()) {
                when {
                    dst == oSrc && src == oDst -> mutual += other
                    dst == oSrc -> fedByMe += other
                    src == oDst -> feedsMe += other
                }
            }
        }

        if (overlap.isNotEmpty()) {
            out += "「$me」跟${listing(overlap)}会扫到同一批图片（源目录相同、或一个包着另一个），" +
                "几条规则抢着搬——建议把源目录收窄到互不重叠的位置"
        }
        if (mutual.isNotEmpty()) {
            out += "「$me」和${listing(mutual)}互为来源和目标，图片会在两个文件夹之间来回搬"
        }
        if (fedByMe.isNotEmpty()) {
            out += "「$me」的目标目录正是${listing(fedByMe)}的源目录，" +
                "「$me」搬过去的图片会被接着搬走"
        }
        if (feedsMe.isNotEmpty()) {
            out += "${listing(feedsMe)}的目标目录正是「$me」的源目录，" +
                "它们搬过来的图片会被「$me」接着搬走"
        }
        return out.toList()
    }

    /** 把规则名拼成「A」、「B」（提醒里点名，用户才知道该去看哪条） */
    private fun listing(names: Collection<String>): String =
        names.joinToString("、") { "「$it」" }

    /**
     * 这条规则跑一轮时，[dir] 里面的文件会不会被扫到（[dir] 通常是别的规则的源目录，
     * 或这条规则自己的目标目录）。判断口径与 [RuleEngine] → `FileBridge.list` 一致：
     * 只看本层时不进子目录；给了「最大深度」时超过深度的层级也不进。
     */
    private fun scansInto(rule: RuleEntity, dir: String): Boolean {
        val root = rule.srcPath.trimEnd('/')
        val p = dir.trimEnd('/')
        if (root.isEmpty() || p.isEmpty()) return false
        if (p == root) return true // 源目录本层一定会被扫
        if (!p.startsWith("$root/")) return false
        if (!rule.includeSubdirs) return false // 只看本层，子文件夹不进

        // [dir] 相对源目录的层数：源目录的子目录是第 1 层，里面的文件是第 2 层
        val level = p.removePrefix("$root/").count { it == '/' } + 1
        val max = rule.maxDepth ?: 0
        return max <= 0 || level + 1 <= max
    }
}
