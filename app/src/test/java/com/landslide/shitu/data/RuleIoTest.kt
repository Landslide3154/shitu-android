package com.landslide.shitu.data

import com.landslide.shitu.data.db.Mode
import com.landslide.shitu.data.db.RuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleIoTest {

    private fun fullRule() = RuleEntity(
        id = 7,
        name = "微信图片",
        srcPath = "/sdcard/DCIM/微信",
        includeSubdirs = false,
        maxDepth = 3,
        dstPath = "/sdcard/Pictures/拾图",
        mode = Mode.COPY,
        intervalMinutes = 10,
        extensions = "jpg,png",
        suffix = "_{app}",
        contentRename = false,
        dstSubdirApp = true,
        minAgeDays = 30,
        minSizeKb = 100,
        enabled = true,
        createdAt = 123,
        updatedAt = 456,
    )

    @Test
    fun `导出再导入后关键字段保持不变`() {
        val src = fullRule()
        val back = RuleIo.decode(RuleIo.encode(listOf(src))).single()

        assertEquals(src.name, back.name)
        assertEquals(src.srcPath, back.srcPath)
        assertEquals(src.dstPath, back.dstPath)
        assertEquals(src.includeSubdirs, back.includeSubdirs)
        assertEquals(src.maxDepth, back.maxDepth)
        assertEquals(src.mode, back.mode)
        assertEquals(src.intervalMinutes, back.intervalMinutes)
        assertEquals(src.extensions, back.extensions)
        assertEquals(src.suffix, back.suffix)
        assertEquals(src.contentRename, back.contentRename)
        assertEquals(src.dstSubdirApp, back.dstSubdirApp)
        assertEquals(src.minAgeDays, back.minAgeDays)
        assertEquals(src.minSizeKb, back.minSizeKb)
    }

    @Test
    fun `导入的规则一律停用且 id 归零`() {
        val back = RuleIo.decode(RuleIo.encode(listOf(fullRule()))).single()
        assertEquals(0L, back.id)
        assertFalse("导入的规则不能一进来就开跑", back.enabled)
    }

    @Test
    fun `字段里的 tab 换行 中文和百分号都能往返`() {
        val tricky = fullRule().copy(
            name = "\t封面\n第二行 %20 100%",
            srcPath = "/sdcard/中文 目录\t/子目录\n",
            dstPath = "/sdcard/图%片/\\n",
            suffix = "_{app}\t%",
        )
        val text = RuleIo.encode(listOf(tricky))

        // 一条规则始终只占一行：tab 和换行都被 URL 编码掉了
        assertEquals(2, text.lineSequence().filter { it.isNotBlank() }.count())
        assertTrue("tab 应被编码", text.contains("%09"))
        assertTrue("换行应被编码", text.contains("%0A"))

        val back = RuleIo.decode(text).single()
        assertEquals(tricky.name, back.name)
        assertEquals(tricky.srcPath, back.srcPath)
        assertEquals(tricky.dstPath, back.dstPath)
        assertEquals(tricky.suffix, back.suffix)
    }

    @Test
    fun `多条规则按原顺序逐行往返`() {
        val a = fullRule().copy(id = 1, name = "A")
        val b = fullRule().copy(id = 2, name = "B", mode = Mode.MOVE)
        val back = RuleIo.decode(RuleIo.encode(listOf(a, b)))

        assertEquals(listOf("A", "B"), back.map { it.name })
        assertEquals(listOf(Mode.COPY, Mode.MOVE), back.map { it.mode })
    }

    @Test
    fun `没有规则时只导出表头_导入为空`() {
        val text = RuleIo.encode(emptyList())
        assertTrue(text.startsWith("# shitu rules v1"))
        assertTrue(RuleIo.decode(text).isEmpty())
    }

    @Test
    fun `坏行被跳过且不影响后面的好行`() {
        val text = buildString {
            appendLine("# shitu rules v1")
            appendLine()
            appendLine("只有一列")
            appendLine("名字\t只有两个字段")
            appendLine("%20\t/sdcard/DCIM\t/sdcard/Pictures") // 名称为空白
            appendLine("好规则\t/sdcard/DCIM\t/sdcard/Pictures")
        }
        val back = RuleIo.decode(text)

        assertEquals(1, back.size)
        assertEquals("好规则", back.single().name)
    }

    @Test
    fun `名称或路径是空白时整行跳过`() {
        assertTrue(RuleIo.decode("%20\t/sdcard/DCIM\t/sdcard/Pictures").isEmpty())
        assertTrue(RuleIo.decode("规则\t%20\t/sdcard/Pictures").isEmpty())
        assertTrue(RuleIo.decode("规则\t/sdcard/DCIM\t%20").isEmpty())
    }

    @Test
    fun `注释行与空行被忽略`() {
        val text = "# shitu rules v1\n# 用户自己加的注释\n\n   \n规则\t/sdcard/DCIM\t/sdcard/Pictures\n"
        assertEquals(1, RuleIo.decode(text).size)
    }

    @Test
    fun `空文本返回空列表`() {
        assertTrue(RuleIo.decode("").isEmpty())
        assertTrue(RuleIo.decode("   \n\t\n").isEmpty())
    }

    @Test
    fun `只有前三列的旧文本用默认值补齐`() {
        val r = RuleIo.decode("规则\t/sdcard/DCIM\t/sdcard/Pictures").single()

        assertEquals("规则", r.name)
        assertTrue(r.includeSubdirs)
        assertNull(r.maxDepth)
        assertEquals(Mode.MOVE, r.mode)
        assertEquals(5, r.intervalMinutes)
        assertEquals(RuleEntity.DEFAULT_EXTENSIONS, r.extensions)
        assertEquals(RuleEntity.DEFAULT_SUFFIX, r.suffix)
        assertTrue(r.contentRename)
        assertFalse(r.dstSubdirApp)
        assertEquals(0, r.minAgeDays)
        assertEquals(0, r.minSizeKb)
    }

    @Test
    fun `数值字段不是数字时回退默认值而不是整行丢弃`() {
        // 第 4 列起：包含子目录 / 深度 / 模式 / 间隔 / 扩展名 / 后缀 / 按内容命名 / 按 App 建子目录 / 最小天数 / 最小大小
        val line = "规则\t/sdcard/DCIM\t/sdcard/Pictures\tabc\tNaN\tFLY\tabc\t\t\tmaybe\tmaybe\tabc\tabc"
        val r = RuleIo.decode(line).single()

        assertEquals("不是数字/未知枚举都不该让导入崩掉", 5, r.intervalMinutes)
        assertNull(r.maxDepth)
        assertEquals(Mode.MOVE, r.mode)
        assertEquals(RuleEntity.DEFAULT_EXTENSIONS, r.extensions)
        assertEquals("", r.suffix)
        assertTrue(r.contentRename)
        assertFalse(r.dstSubdirApp)
        assertEquals(0, r.minAgeDays)
        assertEquals(0, r.minSizeKb)
    }

    @Test
    fun `超出范围的数值在导入时被夹到允许区间`() {
        val line = "规则\t/sdcard/DCIM\t/sdcard/Pictures\ttrue\t-5\tCOPY\t99\tjpg\t{app}\ttrue\ttrue\t9999\t999999"
        val r = RuleIo.decode(line).single()

        assertEquals(30, r.intervalMinutes)
        assertNull("深度小于等于 0 一律当不限制", r.maxDepth)
        assertEquals(365, r.minAgeDays)
        assertEquals(100_000, r.minSizeKb)
    }
}
