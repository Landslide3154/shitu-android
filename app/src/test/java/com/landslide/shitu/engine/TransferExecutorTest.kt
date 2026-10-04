package com.landslide.shitu.engine

import com.landslide.shitu.core.NamePolicy
import com.landslide.shitu.data.db.ItemStatus
import com.landslide.shitu.data.db.Mode
import com.landslide.shitu.shizuku.RemoteFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferExecutorTest {

    private val policy = NamePolicy()

    private fun src(name: String, size: Long = 100, mtime: Long = 1_000) =
        RemoteFile("/src/$name", name, size, mtime, false, false)

    private fun executor(b: FakeFileBridge) = TransferExecutor(b, policy)

    @Test
    fun `移动成功后源消失目标存在`() = runBlocking {
        val bridge = FakeFileBridge().apply { put("/src/a.png") }
        val item = executor(bridge).transfer(
            src("a.png"), "/src/a.png", "/dst", "起点", Mode.MOVE, 1, 2_000,
        )
        assertEquals(ItemStatus.MOVED, item.status)
        assertFalse(bridge.exists("/src/a.png"))
        assertTrue(bridge.exists("/dst/a_起点.png"))
    }

    @Test
    fun `移动失败时源保持不动`() = runBlocking {
        val bridge = FakeFileBridge().apply { put("/src/a.png"); failMove = true }
        val item = executor(bridge).transfer(
            src("a.png"), "/src/a.png", "/dst", "起点", Mode.MOVE, 1, 2_000,
        )
        assertEquals(ItemStatus.FAILED, item.status)
        assertTrue(bridge.exists("/src/a.png"))
        assertFalse(bridge.exists("/dst/a_起点.png"))
    }

    @Test
    fun `复制模式源保留`() = runBlocking {
        val bridge = FakeFileBridge().apply { put("/src/a.png") }
        val item = executor(bridge).transfer(
            src("a.png"), "/src/a.png", "/dst", "起点", Mode.COPY, 1, 2_000,
        )
        assertEquals(ItemStatus.COPIED, item.status)
        assertTrue(bridge.exists("/src/a.png"))
        assertTrue(bridge.exists("/dst/a_起点.png"))
    }

    @Test
    fun `目标已有同内容文件时移动模式删源不重复搬`() = runBlocking {
        val bridge = FakeFileBridge().apply {
            put("/src/a.png", size = 100, mtime = 1_000)
            put("/dst/a_起点.png", size = 100, mtime = 1_000)
        }
        val item = executor(bridge).transfer(
            src("a.png", size = 100, mtime = 1_000), "/src/a.png", "/dst", "起点", Mode.MOVE, 1, 2_000,
        )
        assertEquals(ItemStatus.MOVED, item.status)
        assertEquals("目标已存在同内容文件", item.lastError)
        assertFalse(bridge.exists("/src/a.png"))
        assertTrue(bridge.exists("/dst/a_起点.png"))
    }

    @Test
    fun `目标已有同内容文件时复制模式跳过`() = runBlocking {
        val bridge = FakeFileBridge().apply {
            put("/src/a.png", size = 100, mtime = 1_000)
            put("/dst/a_起点.png", size = 100, mtime = 1_000)
        }
        val item = executor(bridge).transfer(
            src("a.png", size = 100, mtime = 1_000), "/src/a.png", "/dst", "起点", Mode.COPY, 1, 2_000,
        )
        assertEquals(ItemStatus.SKIPPED, item.status)
        assertTrue(bridge.exists("/src/a.png"))
    }

    @Test
    fun `目标已有不同内容时改名而不是覆盖`() = runBlocking {
        val bridge = FakeFileBridge().apply {
            put("/src/a.png", size = 100, mtime = 1_000)
            put("/dst/a_起点.png", size = 999, mtime = 5_000)
        }
        val item = executor(bridge).transfer(
            src("a.png"), "/src/a.png", "/dst", "起点", Mode.MOVE, 1, 2_000,
        )
        assertEquals(ItemStatus.MOVED, item.status)
        assertNotNull(item.dstPath)
        assertTrue(item.dstPath!! != "/dst/a_起点.png")
        // 原有文件零改动
        assertEquals(999L, bridge.files["/dst/a_起点.png"]!!.size)
    }

    @Test
    fun `不追加来源后缀时用原名`() = runBlocking {
        val bridge = FakeFileBridge().apply { put("/src/a.png") }
        val item = executor(bridge).transfer(
            src("a.png"), "/src/a.png", "/dst", null, Mode.MOVE, 1, 2_000,
        )
        assertEquals(ItemStatus.MOVED, item.status)
        assertTrue(bridge.exists("/dst/a.png"))
    }

    @Test
    fun `自定义固定后缀`() = runBlocking {
        val bridge = FakeFileBridge().apply { put("/src/a.png") }
        val item = executor(bridge).transfer(
            src("a.png"), "/src/a.png", "/dst", "_拾图", Mode.MOVE, 1, 2_000,
        )
        assertEquals(ItemStatus.MOVED, item.status)
        assertTrue(bridge.exists("/dst/a_拾图.png"))
    }

    @Test
    fun `校验失败时移动模式会回滚且源仍在`() = runBlocking {
        val bridge = object : FakeFileBridge() {
            override suspend fun stat(path: String): RemoteFile? {
                val real = super.stat(path)
                // 目标"变小了"：模拟校验不通过
                return if (path.startsWith("/dst/") && real != null) real.copy(size = real.size - 1) else real
            }
        }
        bridge.put("/src/a.png")
        val item = executor(bridge).transfer(
            src("a.png"), "/src/a.png", "/dst", "起点", Mode.MOVE, 1, 2_000,
        )
        assertEquals(ItemStatus.FAILED, item.status)
        assertEquals("搬运后校验失败（大小不一致）", item.lastError)
        assertTrue(bridge.exists("/src/a.png"))
    }
}
