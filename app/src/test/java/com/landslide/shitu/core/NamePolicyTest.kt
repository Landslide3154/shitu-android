package com.landslide.shitu.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NamePolicyTest {
    private val policy = NamePolicy()

    @Test
    fun `清洗非法字符`() {
        assertEquals("a_b_c_d_e_f_g_h_i.jpg", policy.sanitize("a:b*c?d\"e<f>g|h/i.jpg"))
    }

    @Test
    fun `中文与 emoji 保留`() {
        assertEquals("封面🎉.png", policy.sanitize("封面🎉.png"))
    }

    @Test
    fun `去掉控制字符与结尾的点`() {
        assertEquals("x_y", policy.sanitize("x\u0001y."))
    }

    @Test
    fun `追加来源 App 后缀`() {
        assertEquals("bookLastImage_起点读书.png", policy.withSourceSuffix("bookLastImage.png", "起点读书"))
    }

    @Test
    fun `超过 180 字节时截断且保留扩展名`() {
        val long = "图".repeat(200) + ".png"
        val out = policy.truncate(long, maxBytes = 180)
        assertTrue(out.endsWith(".png"))
        assertTrue(out.toByteArray(Charsets.UTF_8).size <= 180)
    }

    @Test
    fun `目标不存在时用原名后缀`() {
        val name = policy.resolve("bookLastImage.png", "起点读书", exists = { false })
        assertEquals("bookLastImage_起点读书.png", name)
    }

    @Test
    fun `目标已存在时加时间戳`() {
        val name = policy.resolve(
            "a.png", "起点", exists = { it == "a_起点.png" },
            timestamp = "20261004-101500",
        )
        assertEquals("a_起点_20261004-101500.png", name)
    }

    @Test
    fun `仍然冲突时加序号`() {
        val existing = setOf("a_起点.png", "a_起点_20261004-101500.png", "a_起点_1.png")
        val name = policy.resolve("a.png", "起点", exists = { it in existing }, timestamp = "20261004-101500")
        assertEquals("a_起点_2.png", name)
    }

    @Test
    fun `URL 派生的超长含冒号文件名被清洗并截断`() {
        val raw = "https:readx-her-" + "x".repeat(200) + ".png"
        val out = policy.resolve(raw, "起点读书", exists = { false })
        assertTrue(out.none { it == ':' })
        assertTrue(out.toByteArray(Charsets.UTF_8).size <= 180)
        assertTrue(out.endsWith(".png"))
    }

    @Test
    fun `不追加来源后缀时用原名并保持冲突链路`() {
        assertEquals("a.png", policy.resolve("a.png", null, exists = { false }))
        assertEquals(
            "a_20261004-101500.png",
            policy.resolve("a.png", null, exists = { it == "a.png" }, timestamp = "20261004-101500"),
        )
        assertEquals(
            "a_2.png",
            policy.resolve("a.png", null, exists = { it in setOf("a.png", "a_1.png") }, timestamp = ""),
        )
    }

    @Test
    fun `候选名序列按 原名标签 到 时间戳 到 序号 排列`() {
        val seq = policy.candidates("a.png", "起点", "T").take(4).toList()
        assertEquals(listOf("a_起点.png", "a_起点_T.png", "a_起点_1.png", "a_起点_2.png"), seq)
    }
}
