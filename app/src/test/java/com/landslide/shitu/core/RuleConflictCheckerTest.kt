package com.landslide.shitu.core

import com.landslide.shitu.data.db.Mode
import com.landslide.shitu.data.db.RuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleConflictCheckerTest {

    private fun rule(
        id: Long,
        src: String,
        dst: String,
        name: String = "r$id",
        subdirs: Boolean = false,
        maxDepth: Int? = null,
        enabled: Boolean = true,
    ) = RuleEntity(
        id = id, name = name, srcPath = src, includeSubdirs = subdirs, maxDepth = maxDepth,
        dstPath = dst, mode = Mode.MOVE, enabled = enabled, createdAt = 0, updatedAt = 0,
    )

    // ---------- 0.9.1：真机上用户遇到的那两条规则 ----------
    // 「起点」/sdcard/DCIM/QDReader → /sdcard/DCIM/杂图（未勾子目录）
    // 「DCIM」/sdcard/DCIM → /sdcard/DCIM/杂图（未勾子目录）

    @Test
    fun `不勾包含子目录时 两条规则都不该有提醒`() {
        val qidian = rule(1, "/sdcard/DCIM/QDReader", "/sdcard/DCIM/杂图", name = "起点")
        val dcim = rule(2, "/sdcard/DCIM", "/sdcard/DCIM/杂图", name = "DCIM")

        assertEquals(emptyList<String>(), RuleConflictChecker.check(qidian, listOf(dcim)))
        assertEquals(emptyList<String>(), RuleConflictChecker.check(dcim, listOf(qidian)))
    }

    @Test
    fun `别的规则自己的问题不放在这张编辑页上`() {
        // 「起点」自己没问题；另有一条规则自己目标在源里面——那问题不该出现在「起点」的编辑页
        val qidian = rule(1, "/sdcard/DCIM/QDReader", "/sdcard/DCIM/杂图", name = "起点")
        val other = rule(2, "/sdcard/Movies", "/sdcard/Movies/out", name = "影片", subdirs = true)

        assertTrue(RuleConflictChecker.check(other, emptyList()).any { it.contains("目标目录") })
        assertTrue(RuleConflictChecker.check(qidian, listOf(other)).isEmpty())
    }

    @Test
    fun `勾了包含子目录 目标在源里面才提醒`() {
        val off = rule(2, "/sdcard/DCIM", "/sdcard/DCIM/杂图", name = "DCIM")
        val on = off.copy(includeSubdirs = true)

        assertTrue(RuleConflictChecker.check(off, emptyList()).isEmpty())
        assertTrue(RuleConflictChecker.check(on, emptyList()).any { it.contains("目标目录") })
    }

    @Test
    fun `勾了包含子目录 源目录嵌套才提醒`() {
        val qidian = rule(1, "/sdcard/DCIM/QDReader", "/sdcard/DCIM/杂图", name = "起点")
        val dcimOff = rule(2, "/sdcard/DCIM", "/sdcard/DCIM/杂图", name = "DCIM")
        val dcimOn = dcimOff.copy(includeSubdirs = true)

        // 「DCIM」没勾子目录 → 不会扫到「起点」的源目录 → 不提醒
        assertTrue(RuleConflictChecker.check(dcimOff, listOf(qidian)).isEmpty())
        // 勾上之后才提醒（提醒的是"会扫到同一批图片"，并点名是哪条规则）
        val onIssues = RuleConflictChecker.check(dcimOn, listOf(qidian))
        assertEquals(1, onIssues.count { it.contains("扫到同一批图片") })
        assertTrue(onIssues.any { it.contains("「起点」") })
        // 反过来：编辑「起点」时，是「DCIM」勾了子目录，同样要提醒
        assertTrue(
            RuleConflictChecker.check(qidian, listOf(dcimOn)).any { it.contains("扫到同一批图片") },
        )
        assertTrue(RuleConflictChecker.check(qidian, listOf(dcimOff)).isEmpty())
    }

    @Test
    fun `同一条规则跟多条规则抢图片时只出一条提醒`() {
        val newcomer = rule(
            1, "/sdcard", "/sdcard/DCIM", name = "新规则", subdirs = true,
        )
        val others = listOf(
            rule(2, "/sdcard/DCIM", "/sdcard/DCIM/杂图", name = "DCIM"),
            rule(3, "/sdcard/Pictures", "/sdcard/Pictures/out", name = "相册"),
        )
        val issues = RuleConflictChecker.check(newcomer, others)
        assertEquals(1, issues.count { it.contains("扫到同一批图片") })
        val overlapLine = issues.single { it.contains("扫到同一批图片") }
        assertTrue(overlapLine.contains("「DCIM」") && overlapLine.contains("「相册」"))
    }

    @Test
    fun `最大深度限制到子目录之外时不算冲突`() {
        val qidian = rule(1, "/sdcard/DCIM/QDReader", "/sdcard/DCIM/杂图", name = "起点")
        val shallow = rule(
            2, "/sdcard/DCIM", "/sdcard/DCIM/杂图", name = "DCIM", subdirs = true, maxDepth = 1,
        )
        val deep = shallow.copy(maxDepth = 2)

        assertTrue(RuleConflictChecker.check(shallow, listOf(qidian)).isEmpty())
        assertTrue(
            RuleConflictChecker.check(deep, listOf(qidian)).any { it.contains("扫到同一批图片") },
        )
    }

    @Test
    fun `停用的规则不参与比较`() {
        val qidian = rule(1, "/sdcard/DCIM/QDReader", "/sdcard/DCIM/杂图", name = "起点", subdirs = true)
        val dcimStopped = rule(
            2, "/sdcard/DCIM", "/sdcard/DCIM/杂图", name = "DCIM", subdirs = true, enabled = false,
        )
        assertTrue(RuleConflictChecker.check(qidian, listOf(dcimStopped)).isEmpty())
    }

    // ---------- 与别的规则相关的三条老口径 ----------

    @Test
    fun `两条规则源相同要告警`() {
        val a = rule(1, "/sdcard/Android/data/com.a/files", "/sdcard/Pictures/x")
        val b = rule(2, "/sdcard/Android/data/com.a/files", "/sdcard/Pictures/y")
        assertEquals(1, RuleConflictChecker.check(a, listOf(b)).count { it.contains("源目录相同") })
    }

    @Test
    fun `目标目录被别的规则当源要告警`() {
        val a = rule(1, "/sdcard/Android/data/com.a/files", "/sdcard/Pictures/拾图")
        val b = rule(2, "/sdcard/Pictures/拾图", "/sdcard/DCIM/other")
        assertTrue(RuleConflictChecker.check(a, listOf(b)).any { it.contains("接着搬走") })
    }

    @Test
    fun `互为来源和目标只报一条`() {
        val a = rule(1, "/sdcard/A", "/sdcard/B")
        val b = rule(2, "/sdcard/B", "/sdcard/A")
        val issues = RuleConflictChecker.check(a, listOf(b))
        assertEquals(1, issues.size)
        assertTrue(issues.single().contains("来回搬"))
    }

    // ---------- 自己内部的矛盾 ----------

    @Test
    fun `源与目标相同时要告警`() {
        val r = rule(1, "/sdcard/DCIM", "/sdcard/DCIM")
        assertTrue(RuleConflictChecker.check(r, emptyList()).any { it.contains("是同一个") })
    }

    @Test
    fun `源在目标里面要告警（与子目录无关）`() {
        val r = rule(1, "/sdcard/DCIM/杂图", "/sdcard/DCIM")
        assertTrue(RuleConflictChecker.check(r, emptyList()).any { it.contains("在目标目录里面") })
        assertTrue(
            RuleConflictChecker.check(r.copy(includeSubdirs = true), emptyList())
                .any { it.contains("在目标目录里面") },
        )
    }

    @Test
    fun `无冲突时返回空列表`() {
        val a = rule(1, "/sdcard/Android/data/com.a/files", "/sdcard/Pictures/x")
        val b = rule(2, "/sdcard/Android/data/com.b/files", "/sdcard/Pictures/y")
        assertTrue(RuleConflictChecker.check(a, listOf(b)).isEmpty())
    }
}
