package com.landslide.shitu.core

import com.landslide.shitu.data.db.RuleEntity

/** 规则冲突静态检查（规格 §10 页面 2）：只提示，不阻止保存。 */
object RuleConflictChecker {

    fun check(rules: List<RuleEntity>): List<String> {
        val out = ArrayList<String>()
        for (i in rules.indices) {
            for (j in i + 1 until rules.size) {
                val a = rules[i]
                val b = rules[j]
                if (a.srcPath == b.srcPath) {
                    out += "规则「${a.name}」与「${b.name}」源目录相同"
                }
                if (a.srcPath.startsWith(b.srcPath.trimEnd('/') + "/")) {
                    out += "规则「${a.name}」的源目录嵌套在「${b.name}」的源目录内（嵌套）"
                }
                if (b.srcPath.startsWith(a.srcPath.trimEnd('/') + "/")) {
                    out += "规则「${b.name}」的源目录嵌套在「${a.name}」的源目录内（嵌套）"
                }
                if (a.dstPath == b.srcPath) {
                    out += "规则「${a.name}」的目标目录正是「${b.name}」的源目录（可能形成循环）"
                }
                if (b.dstPath == a.srcPath) {
                    out += "规则「${b.name}」的目标目录正是「${a.name}」的源目录（可能形成循环）"
                }
                if (a.srcPath == b.dstPath && b.srcPath == a.dstPath) {
                    out += "规则「${a.name}」与「${b.name}」互为源与目标（可能形成循环）"
                }
            }
        }
        return out
    }
}
