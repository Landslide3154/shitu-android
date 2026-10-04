package com.landslide.shitu.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirProbeTest {

    private class FakeStat {
        val mtimes = HashMap<String, Long>()
        suspend fun of(path: String): Long? = mtimes[path]
    }

    @Test
    fun `首次见到的目录算变化`() = runBlocking {
        val s = FakeStat().apply { mtimes["/src"] = 100 }
        assertEquals(listOf("/src"), DirProbe().changed(listOf("/src")) { s.of(it) })
    }

    @Test
    fun `mtime 没变就不算变化`() = runBlocking {
        val s = FakeStat().apply { mtimes["/src"] = 100 }
        val probe = DirProbe()
        probe.changed(listOf("/src")) { s.of(it) }
        assertTrue(probe.changed(listOf("/src")) { s.of(it) }.isEmpty())
    }

    @Test
    fun `mtime 变了就算变化`() = runBlocking {
        val s = FakeStat().apply { mtimes["/src"] = 100 }
        val probe = DirProbe()
        probe.changed(listOf("/src")) { s.of(it) }
        s.mtimes["/src"] = 200
        assertEquals(listOf("/src"), probe.changed(listOf("/src")) { s.of(it) })
    }

    @Test
    fun `只报变化的那几个目录`() = runBlocking {
        val s = FakeStat().apply {
            mtimes["/a"] = 1
            mtimes["/b"] = 1
        }
        val probe = DirProbe()
        probe.changed(listOf("/a", "/b")) { s.of(it) }
        s.mtimes["/b"] = 2
        assertEquals(listOf("/b"), probe.changed(listOf("/a", "/b")) { s.of(it) })
    }

    @Test
    fun `目录不存在时忽略不计`() = runBlocking {
        val s = FakeStat()
        assertTrue(DirProbe().changed(listOf("/gone")) { s.of(it) }.isEmpty())
    }

    @Test
    fun `sync 之后不再报变化`() = runBlocking {
        val s = FakeStat().apply { mtimes["/src"] = 100 }
        val probe = DirProbe()
        probe.sync(listOf("/src")) { s.of(it) }
        assertTrue(probe.changed(listOf("/src")) { s.of(it) }.isEmpty())
    }
}
