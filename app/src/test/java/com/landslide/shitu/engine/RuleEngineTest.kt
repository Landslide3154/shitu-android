package com.landslide.shitu.engine

import com.landslide.shitu.core.LoopGuard
import com.landslide.shitu.core.NamePolicy
import com.landslide.shitu.data.Settings
import com.landslide.shitu.data.db.Mode
import com.landslide.shitu.data.db.RuleEntity
import com.landslide.shitu.data.db.RuleState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    private val settings = Settings(
        scanBudgetSec = 20, maxPerRun = 3, maxPerMinute = 120, maxRunSec = 60,
        stableSec = 30, loopWindowMin = 30, loopThreshold = 3, failThreshold = 5,
        lowBatteryPause = false, lowBatteryPct = 15, logKeepDays = 30,
        logKeepCount = 50_000, fastDrain = false,
    )

    private fun rule(ext: String = "png", mode: Mode = Mode.MOVE) = RuleEntity(
        id = 1, name = "t", srcPath = "/src", dstPath = "/dst",
        extensions = ext, mode = mode, createdAt = 0, updatedAt = 0,
    )

    @Test
    fun `只搬白名单扩展名且遵守单轮上限`() = runBlocking {
        val bridge = FakeFileBridge()
        // mtime 足够老（now=2_000_000，稳定期 30 秒）
        (1..10).forEach { bridge.put("/src/f$it.png", size = 10, mtime = 1_000) }
        bridge.put("/src/skip.mp4", size = 10, mtime = 1_000)

        val engine = RuleEngine(bridge, NamePolicy(), settings, now = { 2_000_000 })
        val result = engine.runOnce(rule(), LoopGuard(60_000, 99))

        assertEquals(3, result.moved) // 单轮上限 3
        assertTrue(result.scanned >= 10) // 扫描到了全部候选
        assertTrue(bridge.exists("/src/skip.mp4")) // 非白名单未动
        val remainPng = bridge.files.keys.count { it.startsWith("/src/") && it.endsWith(".png") }
        assertEquals(7, remainPng)
    }

    @Test
    fun `未过稳定期的文件不搬`() = runBlocking {
        val bridge = FakeFileBridge()
        bridge.put("/src/new.png", size = 10, mtime = 1_995_000) // 距 now 仅 5 秒
        val engine = RuleEngine(bridge, NamePolicy(), settings, now = { 2_000_000 })
        assertEquals(0, engine.runOnce(rule(), LoopGuard(60_000, 99)).moved)
        assertTrue(bridge.exists("/src/new.png"))
    }

    @Test
    fun `同名反复出现触发重复抑制`() = runBlocking {
        val bridge = FakeFileBridge()
        val engine = RuleEngine(bridge, NamePolicy(), settings, now = { 2_000_000 })
        val guard = LoopGuard(30 * 60_000, 3)
        repeat(3) {
            bridge.put("/src/a.png", size = 10, mtime = 1_000)
            engine.runOnce(rule(ext = "png"), guard)
        }
        assertEquals(RuleState.PAUSED_LOOP, engine.lastState)
    }

    @Test
    fun `源目录不可读时记错误而不是抛异常`() = runBlocking {
        val bridge = FakeFileBridge().apply { listThrows = true }
        val engine = RuleEngine(bridge, NamePolicy(), settings, now = { 2_000_000 })
        val result = engine.runOnce(rule(), LoopGuard(60_000, 99))
        assertEquals(0, result.moved)
        assertTrue(result.error != null)
    }

    @Test
    fun `记账回调收到 Item 与日志`() = runBlocking {
        val bridge = FakeFileBridge()
        bridge.put("/src/a.png", size = 10, mtime = 1_000)
        val items = ArrayList<com.landslide.shitu.data.db.ItemEntity>()
        val logs = ArrayList<com.landslide.shitu.data.db.LogResult>()
        val recorder = object : RuleEngine.Recorder {
            override suspend fun onItem(item: com.landslide.shitu.data.db.ItemEntity) {
                items += item
            }

            override suspend fun onLog(
                result: com.landslide.shitu.data.db.LogResult,
                ruleId: Long?,
                srcPath: String?,
                dstPath: String?,
                durationMs: Long,
                message: String?,
            ) {
                logs += result
            }
        }
        val engine = RuleEngine(bridge, NamePolicy(), settings, now = { 2_000_000 })
        engine.runOnce(rule(), LoopGuard(60_000, 99), recorder)
        assertEquals(1, items.size)
        assertEquals(1, logs.size)
        assertEquals(com.landslide.shitu.data.db.LogResult.MOVED, logs.first())
    }
}
