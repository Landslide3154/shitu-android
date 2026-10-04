package com.landslide.shitu.core

import com.landslide.shitu.data.db.Mode
import com.landslide.shitu.data.db.RuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleConflictCheckerTest {

    private fun rule(id: Long, src: String, dst: String) = RuleEntity(
        id = id, name = "r$id", srcPath = src, dstPath = dst, mode = Mode.MOVE,
        createdAt = 0, updatedAt = 0,
    )

    @Test
    fun `源目录互相嵌套要告警`() {
        val issues = RuleConflictChecker.check(
            listOf(
                rule(1, "/sdcard/Android/data/com.a/files", "/sdcard/Pictures/拾图"),
                rule(2, "/sdcard/Android/data/com.a/files/sub", "/sdcard/Pictures/拾图2"),
            ),
        )
        assertTrue(issues.any { it.contains("嵌套") })
    }

    @Test
    fun `目标目录被别的规则当源要告警`() {
        val issues = RuleConflictChecker.check(
            listOf(
                rule(1, "/sdcard/Android/data/com.a/files", "/sdcard/Pictures/拾图"),
                rule(2, "/sdcard/Pictures/拾图", "/sdcard/DCIM/other"),
            ),
        )
        assertTrue(issues.any { it.contains("循环") })
    }

    @Test
    fun `两条规则源相同要告警`() {
        val issues = RuleConflictChecker.check(
            listOf(
                rule(1, "/sdcard/Android/data/com.a/files", "/sdcard/Pictures/x"),
                rule(2, "/sdcard/Android/data/com.a/files", "/sdcard/Pictures/y"),
            ),
        )
        assertEquals(1, issues.count { it.contains("源目录相同") })
    }

    @Test
    fun `无冲突时返回空列表`() {
        val issues = RuleConflictChecker.check(
            listOf(
                rule(1, "/sdcard/Android/data/com.a/files", "/sdcard/Pictures/x"),
                rule(2, "/sdcard/Android/data/com.b/files", "/sdcard/Pictures/y"),
            ),
        )
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `目标目录在源目录里面要告警`() {
        val issues = RuleConflictChecker.check(listOf(rule(1, "/sdcard/DCIM", "/sdcard/DCIM/杂图")))
        assertTrue(issues.any { it.contains("在源目录里面") })
    }

    @Test
    fun `源与目标相同时要告警`() {
        val issues = RuleConflictChecker.check(listOf(rule(1, "/sdcard/DCIM", "/sdcard/DCIM")))
        assertTrue(issues.any { it.contains("是同一个") })
    }
}
