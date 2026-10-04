package com.landslide.shitu.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoServiceTest {

    @Test
    fun `目标存在且源位置为空则撤回成功`() = runBlocking {
        val b = FakeFileBridge()
        b.put("/dst/a_起点.png", size = 100, mtime = 1_000)
        val r = UndoService(b).undo("/src/a.png", "/dst/a_起点.png", 100)
        assertTrue(r.ok)
        assertTrue(b.exists("/src/a.png"))
        assertFalse(b.exists("/dst/a_起点.png"))
    }

    @Test
    fun `目标已被改动则跳过`() = runBlocking {
        val b = FakeFileBridge()
        b.put("/dst/a.png", size = 999, mtime = 1_000)
        val r = UndoService(b).undo("/src/a.png", "/dst/a.png", 100)
        assertFalse(r.ok)
        assertTrue(r.reason!!.contains("已被改动"))
    }

    @Test
    fun `源位置已被占用则跳过`() = runBlocking {
        val b = FakeFileBridge()
        b.put("/dst/a.png", size = 100, mtime = 1_000)
        b.put("/src/a.png", size = 1, mtime = 1)
        val r = UndoService(b).undo("/src/a.png", "/dst/a.png", 100)
        assertFalse(r.ok)
        assertTrue(r.reason!!.contains("源位置已被新文件占用"))
    }

    @Test
    fun `目标已不存在则跳过并说明原因`() = runBlocking {
        val b = FakeFileBridge()
        val r = UndoService(b).undo("/src/a.png", "/dst/gone.png", 100)
        assertFalse(r.ok)
        assertEquals("目标已不存在：/dst/gone.png", r.reason)
    }
}
