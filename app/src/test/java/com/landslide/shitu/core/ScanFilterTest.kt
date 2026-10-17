package com.landslide.shitu.core

import com.landslide.shitu.shizuku.RemoteFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanFilterTest {
    private val now = 1_800_000_000_000L
    private val dayMs = 86_400_000L
    private val filter = ScanFilter(extensions = setOf("jpg", "png"), stableSeconds = 30)

    private fun f(
        name: String,
        mtime: Long,
        dir: Boolean = false,
        link: Boolean = false,
        size: Long = 100,
    ) = RemoteFile("/src/$name", name, size, mtime, dir, link)

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

    // ---------- 0.12.5：只搬最近 N 天内修改的文件 ----------

    private fun ageFilter(days: Int) =
        ScanFilter(extensions = setOf("jpg"), stableSeconds = 0, maxAgeDays = days)

    @Test
    fun `修改时间正好 N 天时仍匹配`() {
        assertTrue(ageFilter(7).matches(f("a.jpg", now - 7 * dayMs), now))
    }

    @Test
    fun `超过 N 天 1 毫秒就不匹配`() {
        assertFalse(ageFilter(7).matches(f("a.jpg", now - 7 * dayMs - 1), now))
    }

    @Test
    fun `天数设为 0 表示不限修改时间`() {
        assertTrue(ageFilter(0).matches(f("老图.jpg", now - 3650 * dayMs), now))
        // 未来时间差是负数，同样不算过期
        assertTrue(ageFilter(0).matches(f("未来.jpg", now + dayMs), now))
    }

    // ---------- 0.12.5：只搬大于 N KB 的文件 ----------

    private fun sizeFilter(kb: Int) =
        ScanFilter(extensions = setOf("jpg"), stableSeconds = 0, minSizeKb = kb)

    @Test
    fun `文件大小正好 N KB 时匹配`() {
        assertTrue(sizeFilter(100).matches(f("a.jpg", now, size = 100 * 1024L), now))
    }

    @Test
    fun `比 N KB 少 1 字节就不匹配`() {
        assertFalse(sizeFilter(100).matches(f("a.jpg", now, size = 100 * 1024L - 1), now))
    }

    @Test
    fun `大小设为 0 表示不限`() {
        assertTrue(sizeFilter(0).matches(f("一字节.jpg", now, size = 1), now))
        assertTrue(sizeFilter(0).matches(f("空文件.jpg", now, size = 0), now))
    }

    @Test
    fun `目录和软链在开启天数与大小限制后仍然被排除`() {
        val flt = ScanFilter(setOf("jpg"), stableSeconds = 0, maxAgeDays = 7, minSizeKb = 10)
        assertFalse(flt.matches(f("d", now, dir = true, size = 20 * 1024L), now))
        assertFalse(flt.matches(f("l.jpg", now, link = true, size = 20 * 1024L), now))
    }

    @Test
    fun `天数与大小两个条件都满足才匹配`() {
        val flt = ScanFilter(setOf("jpg"), stableSeconds = 0, maxAgeDays = 7, minSizeKb = 10)
        assertTrue(flt.matches(f("ok.jpg", now - dayMs, size = 10 * 1024L), now))
        assertFalse("超过天数", flt.matches(f("old.jpg", now - 8 * dayMs, size = 20 * 1024L), now))
        assertFalse("小于下限", flt.matches(f("tiny.jpg", now - dayMs, size = 10 * 1024L - 1), now))
    }

    @Test
    fun `matches 不看稳定期但 accept 还看`() {
        val flt = ScanFilter(setOf("jpg"), stableSeconds = 30, maxAgeDays = 7, minSizeKb = 10)
        val fresh = f("a.jpg", now - 5_000, size = 20 * 1024L)
        assertTrue("matches 只负责天数/大小等条件", flt.matches(fresh, now))
        assertFalse("accept 仍要求过稳定期", flt.accept(fresh, now))
    }

    @Test
    fun `扩展名大小写与白名单写法不受新条件影响`() {
        val flt = ScanFilter(setOf(".JPG", "png"), stableSeconds = 0, maxAgeDays = 1, minSizeKb = 1)
        assertTrue(flt.matches(f("a.Jpg", now - dayMs, size = 1024L), now))
    }
}
