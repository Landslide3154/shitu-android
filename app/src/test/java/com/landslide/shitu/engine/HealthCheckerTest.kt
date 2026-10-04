package com.landslide.shitu.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthCheckerTest {

    private fun readyBridge() = FakeFileBridge().apply {
        putDir("/tmp/src")
        putDir("/sdcard/Pictures")
    }

    @Test
    fun `全绿时 6 项都通过`() = runBlocking {
        val report = HealthChecker(readyBridge(), "/tmp/src", "/sdcard/Pictures").run()
        assertEquals(6, report.size)
        val failed = report.filter { !it.passed }
        assertTrue("失败项：$failed", failed.isEmpty())
    }

    @Test
    fun `源目录不可写时报第 4 项失败`() = runBlocking {
        val b = object : FakeFileBridge() {
            override suspend fun move(src: String, dst: String) = false
        }
        b.putDir("/tmp/src")
        b.putDir("/sdcard/Pictures")
        val report = HealthChecker(b, "/tmp/src", "/sdcard/Pictures").run()
        assertTrue(report.any { !it.passed })
        assertFalse(report.first { it.name.startsWith("4.") }.passed)
    }

    @Test
    fun `源目录不存在时第 2 项失败`() = runBlocking {
        val b = readyBridge()
        val report = HealthChecker(b, "/tmp/nope", "/sdcard/Pictures").run()
        assertFalse(report.first { it.name.startsWith("2.") }.passed)
    }

    @Test
    fun `桥不可调用时第 1 项失败`() = runBlocking {
        val b = object : FakeFileBridge() {
            override suspend fun describeEnvironment() = throw IllegalStateException("not bound")
        }
        b.putDir("/tmp/src")
        b.putDir("/sdcard/Pictures")
        val report = HealthChecker(b, "/tmp/src", "/sdcard/Pictures").run()
        assertFalse(report.first { it.name.startsWith("1.") }.passed)
    }

    @Test
    fun `自检不留临时文件`() = runBlocking {
        val b = readyBridge()
        HealthChecker(b, "/tmp/src", "/sdcard/Pictures").run()
        assertTrue(b.files.keys.none { it.contains("_shitu_") })
    }
}
