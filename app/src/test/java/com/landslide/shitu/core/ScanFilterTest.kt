package com.landslide.shitu.core

import com.landslide.shitu.shizuku.RemoteFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanFilterTest {
    private val now = 1_800_000_000_000L
    private val filter = ScanFilter(extensions = setOf("jpg", "png"), stableSeconds = 30)

    private fun f(name: String, mtime: Long, dir: Boolean = false, link: Boolean = false) =
        RemoteFile("/src/$name", name, 100, mtime, dir, link)

    @Test
    fun `扩展名在白名单内且已稳定则通过`() {
        assertTrue(filter.accept(f("a.JPG", now - 60_000), now))
    }

    @Test
    fun `扩展名不在白名单则拒绝`() {
        assertFalse(filter.accept(f("a.mp4", now - 60_000), now))
    }

    @Test
    fun `未过稳定期则拒绝`() {
        assertFalse(filter.accept(f("a.jpg", now - 10_000), now))
    }

    @Test
    fun `目录与软链拒绝`() {
        assertFalse(filter.accept(f("d", now - 60_000, dir = true), now))
        assertFalse(filter.accept(f("l.jpg", now - 60_000, link = true), now))
    }

    @Test
    fun `带点的白名单写法和空项被忽略`() {
        val f2 = ScanFilter(extensions = setOf(".jpg", " ", "png"), stableSeconds = 0)
        assertFalse(f2.accept(f("a.jpeg", now), now))
        assertTrue(f2.accept(f("a.jpg", now), now))
    }
}
