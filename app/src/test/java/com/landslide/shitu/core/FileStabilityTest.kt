package com.landslide.shitu.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileStabilityTest {

    @Test
    fun `第一次看到不算稳定`() {
        val fs = FileStability(minConfirmMillis = 3_000)
        assertFalse(fs.settled("/a.png", size = 100, mtime = 1_000, now = 0))
    }

    @Test
    fun `大小与 mtime 都没变且间隔够了就算稳定`() {
        val fs = FileStability(minConfirmMillis = 3_000)
        fs.settled("/a.png", 100, 1_000, 0)
        assertFalse(fs.settled("/a.png", 100, 1_000, 1_000)) // 间隔不够
        assertTrue(fs.settled("/a.png", 100, 1_000, 3_000))
    }

    @Test
    fun `还在长大的文件不算稳定`() {
        val fs = FileStability(minConfirmMillis = 3_000)
        fs.settled("/a.png", 100, 1_000, 0)
        assertFalse(fs.settled("/a.png", 500, 2_000, 5_000)) // 变大且 mtime 变了
        assertTrue(fs.settled("/a.png", 500, 2_000, 9_000)) // 停下来了
    }

    @Test
    fun `mtime 变了就不算稳定`() {
        val fs = FileStability(minConfirmMillis = 3_000)
        fs.settled("/a.png", 100, 1_000, 0)
        assertFalse(fs.settled("/a.png", 100, 2_000, 9_000))
    }

    @Test
    fun `不同文件互不影响`() {
        val fs = FileStability(minConfirmMillis = 3_000)
        fs.settled("/a.png", 100, 1_000, 0)
        assertFalse(fs.settled("/b.png", 100, 1_000, 5_000))
        assertTrue(fs.settled("/a.png", 100, 1_000, 5_000))
    }

    @Test
    fun `记录数有上限不会无限膨胀`() {
        val fs = FileStability(minConfirmMillis = 1_000, maxTracked = 10)
        repeat(50) { fs.settled("/f$it.png", 1, 1, 0) }
        assertTrue(fs.trackedCount() <= 10)
    }

    @Test
    fun `forget 之后要重新确认`() {
        val fs = FileStability(minConfirmMillis = 1_000)
        fs.settled("/a.png", 100, 1_000, 0)
        fs.forget("/a.png")
        assertFalse(fs.settled("/a.png", 100, 1_000, 5_000))
        assertEquals(1, fs.trackedCount())
    }
}
