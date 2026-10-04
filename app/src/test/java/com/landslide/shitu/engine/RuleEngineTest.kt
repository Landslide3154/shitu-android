package com.landslide.shitu.engine

import com.landslide.shitu.core.LoopGuard
import com.landslide.shitu.core.NamePolicy
import com.landslide.shitu.data.Settings
import com.landslide.shitu.data.db.Mode
import com.landslide.shitu.data.db.RuleEntity
import com.landslide.shitu.data.db.RuleState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `目标目录在源目录里时不会反复搬同一个文件`() = runBlocking {
        val bridge = FakeFileBridge()
        bridge.put("/src/photo.png", size = 10, mtime = 1_000)
        bridge.put("/src/dst/photo_标签.png", size = 10, mtime = 1_000)
        val rule = RuleEntity(
            id = 1, name = "t", srcPath = "/src", dstPath = "/src/dst",
            extensions = "png", mode = Mode.MOVE, createdAt = 0, updatedAt = 0,
        )
        val engine = RuleEngine(bridge, NamePolicy(), settings, now = { 2_000_000 })
        val result = engine.runOnce(rule, LoopGuard(60_000, 99))

        assertEquals(1, result.moved)
        // 已经在目标目录里的文件不能再被当成源（否则名字会被一轮轮加后缀）
        assertTrue(bridge.exists("/src/dst/photo_标签.png"))
        assertFalse(bridge.exists("/src/photo.png"))
    }

    @Test
    fun `写完就搬：大小没变就不等满稳定期`() = runBlocking {
        val bridge = FakeFileBridge()
        // 距 now 只有 1 秒，按「稳定期 30 秒」的老口径搬不走
        bridge.put("/src/new.png", size = 10, mtime = 1_999_000)
        var t = 2_000_000L
        val cfg = settings.copy(stableSec = 30, settleDetect = true, settleGapSec = 3)
        val engine = RuleEngine(
            bridge, NamePolicy(), cfg, now = { t },
            stability = com.landslide.shitu.core.FileStability(minConfirmMillis = 3_000),
        )
        // 第一次只是记下观察
        assertEquals(0, engine.runOnce(rule(), LoopGuard(60_000, 99)).moved)
        // 4 秒后 size/mtime 都没变 → 判定写完，立刻搬
        t += 4_000
        assertEquals(1, engine.runOnce(rule(), LoopGuard(60_000, 99)).moved)
        assertFalse(bridge.exists("/src/new.png"))
    }

    @Test
    fun `关掉写完就搬时仍按稳定期等待`() = runBlocking {
        val bridge = FakeFileBridge()
        bridge.put("/src/new.png", size = 10, mtime = 1_999_000)
        var t = 2_000_000L
        val cfg = settings.copy(stableSec = 30, settleDetect = false)
        val engine = RuleEngine(
            bridge, NamePolicy(), cfg, now = { t },
            stability = com.landslide.shitu.core.FileStability(minConfirmMillis = 3_000),
        )
        assertEquals(0, engine.runOnce(rule(), LoopGuard(60_000, 99)).moved)
        t += 4_000
        assertEquals(0, engine.runOnce(rule(), LoopGuard(60_000, 99)).moved)
        assertTrue(bridge.exists("/src/new.png"))
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
