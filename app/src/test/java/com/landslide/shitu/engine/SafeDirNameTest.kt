package com.landslide.shitu.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SafeDirNameTest {

    @Test
    fun `路径非法字符全部替换成下划线`() {
        assertEquals(
            "a_b_c_d_e_f_g_h_i_j",
            RuleEngine.safeDirName("a/b\\c:d*e?f\"g<h>i|j"),
        )
    }

    @Test
    fun `中文和中间的空格原样保留`() {
        assertEquals("起点 读书", RuleEngine.safeDirName("起点 读书"))
    }

    @Test
    fun `首尾空白被去掉`() {
        assertEquals("微信", RuleEngine.safeDirName("  微信 \t\n"))
    }

    @Test
    fun `空串退回 app`() {
        assertEquals("app", RuleEngine.safeDirName(""))
    }

    @Test
    fun `全是空白也退回 app`() {
        assertEquals("app", RuleEngine.safeDirName("   \t\n  "))
    }

    @Test
    fun `超过 40 个字符被截断`() {
        assertEquals("图".repeat(40), RuleEngine.safeDirName("图".repeat(41)))
    }

    @Test
    fun `正好 40 个字符不被截断`() {
        val name = "a".repeat(40)
        assertEquals(name, RuleEngine.safeDirName(name))
    }

    @Test
    fun `先去掉首尾空白再截断`() {
        // 前面 5 个空格要去掉，剩下的 45 个字符再截到 40，不能只留 35 个
        assertEquals("a".repeat(40), RuleEngine.safeDirName("     " + "a".repeat(45)))
    }

    @Test
    fun `替换后的名字里不再有非法字符`() {
        val out = RuleEngine.safeDirName("微信:封面/第一张?*")
        assertEquals("微信_封面_第一张__", out)
        assertFalse(out.any { it in "/\\:*?\"<>|" })
    }
}
