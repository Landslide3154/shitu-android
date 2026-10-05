package com.landslide.shitu.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 按内容生成的文件名主体：长度、字符集、稳定性。 */
class ContentNameTest {

    private fun digest(s: String) = s.toByteArray(Charsets.UTF_8)

    @Test
    fun `固定 15 位，且只用约定的字符集`() {
        val name = ContentName.fromDigest(digest("hello"))
        assertEquals(ContentName.LENGTH, name.length)
        name.forEach { c ->
            assertTrue("出现了不该有的字符：$c", ContentName.ALPHABET.contains(c))
        }
    }

    @Test
    fun `字符集里没有容易看错的 0 O 1 l I`() {
        assertEquals(57, ContentName.ALPHABET.length)
        listOf('0', 'O', '1', 'l', 'I').forEach { bad ->
            assertTrue("字符集不该包含 $bad", !ContentName.ALPHABET.contains(bad))
        }
        // 去重后长度不变，说明没有重复字符
        assertEquals(ContentName.ALPHABET.length, ContentName.ALPHABET.toSet().size)
    }

    @Test
    fun `同一个内容永远得到同一个名字`() {
        val a = ContentName.fromDigest(digest("same-content"))
        val b = ContentName.fromDigest(digest("same-content"))
        assertEquals(a, b)
    }

    @Test
    fun `不同内容得到不同名字`() {
        val a = ContentName.fromDigest(digest("content-a"))
        val b = ContentName.fromDigest(digest("content-b"))
        assertNotEquals(a, b)
    }

    @Test
    fun `短哈希会补足位数，不会变短`() {
        val name = ContentName.fromDigest(byteArrayOf(0x01, 0x02))
        assertEquals(ContentName.LENGTH, name.length)
    }

    @Test
    fun `空哈希返回空串（调用方回退原名）`() {
        assertEquals("", ContentName.fromDigest(ByteArray(0)))
    }

    @Test
    fun `与扩展名、后缀拼起来是合法文件名`() {
        val base = ContentName.fromDigest(digest("x"))
        val policy = NamePolicy()
        val full = policy.withSourceSuffix(base + ".jpg", "起点读书")
        assertTrue(full.startsWith(base))
        assertTrue(full.endsWith("_起点读书.jpg"))
        assertEquals(".jpg", policy.extensionOf(base + ".jpg"))
    }
}
