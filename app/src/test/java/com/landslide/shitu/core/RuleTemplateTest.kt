package com.landslide.shitu.core

import com.landslide.shitu.data.db.Mode
import com.landslide.shitu.data.db.RuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 自建模板：字段搬运 + 存取格式（存 DataStore 的那段文本）。 */
class RuleTemplateTest {

    private fun rule(
        name: String = "起点",
        srcPath: String = "/sdcard/DCIM/QDReader",
        dstPath: String = "/sdcard/DCIM",
        includeSubdirs: Boolean = true,
        maxDepth: Int? = null,
        mode: Mode = Mode.MOVE,
        intervalMinutes: Int = 5,
        suffix: String = "{qidian}",
    ) = RuleEntity(
        id = 7,
        name = name,
        srcPath = srcPath,
        includeSubdirs = includeSubdirs,
        maxDepth = maxDepth,
        dstPath = dstPath,
        mode = mode,
        intervalMinutes = intervalMinutes,
        suffix = suffix,
        createdAt = 1,
        updatedAt = 2,
    )

    @Test
    fun `fromRule 带走全部设置，只换名字和 id`() {
        val t = RuleTemplate.fromRule(rule(maxDepth = 3, mode = Mode.COPY, intervalMinutes = 15), "我的模板")
        assertEquals("我的模板", t.name)
        assertEquals("/sdcard/DCIM/QDReader", t.srcPath)
        assertEquals("/sdcard/DCIM", t.dstPath)
        assertTrue(t.includeSubdirs)
        assertEquals(3, t.maxDepth)
        assertEquals(Mode.COPY, t.mode)
        assertEquals(15, t.intervalMinutes)
        assertEquals("{qidian}", t.suffix)
        assertTrue(t.id.isNotBlank())
    }

    @Test
    fun `toEntity 生成一条未保存的新规则`() {
        val t = RuleTemplate.fromRule(rule(), "模板")
        val e = t.toEntity(now = 1000L)
        assertEquals(0L, e.id)
        assertEquals(1000L, e.createdAt)
        assertEquals(1000L, e.updatedAt)
        assertEquals(t.name, e.name)
        assertEquals(t.srcPath, e.srcPath)
        assertEquals(t.dstPath, e.dstPath)
        assertEquals(t.mode, e.mode)
        assertEquals(t.suffix, e.suffix)
    }

    @Test
    fun `存取一圈，中文空格加号等号都不丢`() {
        val list = listOf(
            RuleTemplate.fromRule(rule(name = "起点 + 备份=1"), "通勤 图片/a+b=c"),
            RuleTemplate.fromRule(
                rule(name = "相册", mode = Mode.COPY, includeSubdirs = false, maxDepth = 2, suffix = ""),
                "只搬本层",
            ),
        )
        val back = RuleTemplate.decode(RuleTemplate.encode(list))
        assertEquals(list.size, back.size)
        assertEquals(list[0].name, back[0].name)
        assertEquals(list[0].srcPath, back[0].srcPath)
        assertEquals(list[1].name, back[1].name)
        assertEquals(Mode.COPY, back[1].mode)
        assertEquals(false, back[1].includeSubdirs)
        assertEquals(2, back[1].maxDepth)
        assertEquals("", back[1].suffix) // 留空 = 不改文件名，必须能存下来
    }

    @Test
    fun `模板名字里有换行也不会串行`() {
        val t = RuleTemplate.fromRule(rule(), "第一行\n第二行")
        val back = RuleTemplate.decode(RuleTemplate.encode(listOf(t)))
        assertEquals(1, back.size)
        assertEquals("第一行\n第二行", back[0].name)
    }

    @Test
    fun `空值和坏数据都不炸`() {
        assertTrue(RuleTemplate.decode(null).isEmpty())
        assertTrue(RuleTemplate.decode("").isEmpty())
        assertTrue(RuleTemplate.decode("   ").isEmpty())
        // 缺 name/src 的段落直接丢掉，不影响别的段落
        val text = "name=好的\nsrc=/sdcard/A\n\nname=缺源目录\n\nsrc=/sdcard/B"
        val back = RuleTemplate.decode(text)
        assertEquals(1, back.size)
        assertEquals("好的", back[0].name)
        // 认不出的 mode 退回 MOVE，认不出的深度当作不限
        val odd = RuleTemplate.decode("name=x\nsrc=/sdcard/A\nmode=FLY\ndepth=abc")
        assertEquals(1, odd.size)
        assertEquals(Mode.MOVE, odd[0].mode)
        assertNull(odd[0].maxDepth)
    }

    @Test
    fun `detail 说清会怎么搬`() {
        val move = RuleTemplate.fromRule(rule(intervalMinutes = 5), "t")
        assertTrue(move.detail().contains("/sdcard/DCIM/QDReader → /sdcard/DCIM"))
        assertTrue(move.detail().contains("移动"))
        assertTrue(move.detail().contains("间隔 5 分钟"))
        val copyTop = RuleTemplate.fromRule(rule(mode = Mode.COPY, includeSubdirs = false), "t")
        assertTrue(copyTop.detail().contains("复制"))
        assertTrue(copyTop.detail().contains("只看这一层"))
    }
}
