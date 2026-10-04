package com.landslide.shitu.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 新建规则模板：保证每个模板都填好了该填的东西，不会给出危险的默认值。 */
class RuleTemplateTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `每个模板都能变成一条完整规则`() {
        for (t in RuleTemplate.ALL) {
            val r = t.toEntity(now)
            assertTrue("模板 ${t.key} 名字不能为空", r.name.isNotBlank())
            assertTrue("模板 ${t.key} 源目录要以 /sdcard 开头", r.srcPath.startsWith("/sdcard"))
            assertTrue("模板 ${t.key} 目标目录要以 /sdcard 开头", r.dstPath.startsWith("/sdcard"))
            assertEquals(now, r.createdAt)
            assertEquals(now, r.updatedAt)
            assertEquals(0L, r.id)
            assertTrue("模板建出来的规则默认应该是开着的", r.enabled)
        }
    }

    @Test
    fun `模板不会把整个存储根目录当源`() {
        for (t in RuleTemplate.ALL) {
            assertFalse(
                "模板 ${t.key} 把 /sdcard 当源太危险",
                t.toEntity(now).srcPath.trimEnd('/') == "/sdcard",
            )
        }
    }

    @Test
    fun `Key 唯一（弹窗靠它区分）`() {
        val keys = RuleTemplate.ALL.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `下载模板默认用复制模式（先试再移）`() {
        val t = RuleTemplate.ALL.first { it.key == "download" }
        assertEquals(com.landslide.shitu.data.db.Mode.COPY, t.toEntity(now).mode)
    }

    @Test
    fun `相册整理模板只看本层，不动子目录`() {
        val t = RuleTemplate.ALL.first { it.key == "dcim-by-app" }
        val r = t.toEntity(now)
        assertEquals("/sdcard/DCIM", r.srcPath)
        assertFalse(r.includeSubdirs)
    }
}
