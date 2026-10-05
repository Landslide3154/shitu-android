package com.landslide.shitu.engine

import com.landslide.shitu.core.ContentName
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

    @Test
    fun `开了按内容命名_目标名变成内容名加后缀`() = runBlocking {
        val bridge = FakeFileBridge()
        bridge.put("/src/a.png", size = 1234, mtime = 1_000)
        val engine = RuleEngine(
            bridge, NamePolicy(), settings,
            sourceAppLabel = { _, _ -> "起点读书" },
            now = { 2_000_000 },
        )
        val result = engine.runOnce(rule().copy(contentRename = true), LoopGuard(60_000, 99))
        assertEquals(1, result.moved)

        val base = com.landslide.shitu.core.ContentName
            .fromDigest("size=1234;mtime=1000".toByteArray(Charsets.UTF_8))
        assertTrue(
            "目标应该是 ${base}_起点读书.png，实际：" + bridge.files.keys,
            bridge.exists("/dst/${base}_起点读书.png"),
        )
        assertFalse(bridge.exists("/src/a.png"))
    }

    @Test
    fun `按内容命名时同一张图再出现不会存第二份`() = runBlocking {
        val bridge = FakeFileBridge()
        bridge.put("/src/a.png", size = 1234, mtime = 1_000)
        val engine = RuleEngine(
            bridge, NamePolicy(), settings,
            sourceAppLabel = { _, _ -> "起点读书" },
            now = { 2_000_000 },
        )
        val r = rule(mode = Mode.COPY).copy(contentRename = true)
        assertEquals(1, engine.runOnce(r, LoopGuard(60_000, 99)).moved)

        // 源文件还在（复制模式），再跑一轮：同名同大小 → 认定同一份 → 跳过，不再多存一份
        val second = engine.runOnce(r, LoopGuard(60_000, 99))
        assertEquals(0, second.moved)
        assertEquals(1, second.skipped)
        assertEquals(1, bridge.files.keys.count { it.startsWith("/dst/") })
    }

    @Test
    fun `关着按内容命名时还是用原来的文件名`() = runBlocking {
        val bridge = FakeFileBridge()
        bridge.put("/src/a.png", size = 1234, mtime = 1_000)
        val engine = RuleEngine(
            bridge, NamePolicy(), settings,
            sourceAppLabel = { _, _ -> "起点读书" },
            now = { 2_000_000 },
        )
        engine.runOnce(rule().copy(contentRename = false), LoopGuard(60_000, 99))
        assertTrue(bridge.exists("/dst/a_起点读书.png"))
    }

    // ---------- 「已经复制过的内容」账本 ----------

    private class FakeLedger : ContentLedger {
        val fingerprints = HashSet<String>()

        override suspend fun seen(fingerprint: String): Boolean = fingerprint in fingerprints

        override suspend fun remember(fingerprint: String, ruleId: Long, dstPath: String?, now: Long) {
            fingerprints += fingerprint
        }

        override suspend fun count(): Int = fingerprints.size

        override suspend fun clear(): Int {
            val n = fingerprints.size
            fingerprints.clear()
            return n
        }
    }

    private fun fingerprintOf(size: Long, mtime: Long) =
        ContentName.fromDigest("size=$size;mtime=$mtime".toByteArray(Charsets.UTF_8))

    /** 模拟"用户把中转站里的副本手工移走了" */
    private fun clearDir(bridge: FakeFileBridge, dir: String) {
        bridge.files.keys.filter { it.startsWith("$dir/") }.forEach { bridge.files.remove(it) }
    }

    @Test
    fun `复制过的内容被你移走之后也不会再复制一份`() = runBlocking {
        val bridge = FakeFileBridge()
        bridge.put("/src/a.png", size = 100, mtime = 1_000)
        val ledger = FakeLedger()
        val engine = RuleEngine(bridge, NamePolicy(), settings, now = { 2_000_000 }, ledger = ledger)
        val r = rule(mode = Mode.COPY)

        assertEquals(1, engine.runOnce(r, LoopGuard(60_000, 99)).moved)
        assertEquals(1, ledger.count())

        // 用户把复制出来的那份移走/改到别的目录了 —— 目标目录里已经找不到它
        clearDir(bridge, "/dst")

        val second = engine.runOnce(r, LoopGuard(60_000, 99))
        assertEquals(0, second.moved)
        assertEquals(1, second.skipped)
        assertFalse("不该再复制一份进来", bridge.exists("/dst/a_t.png"))
        // 源文件照旧留着（复制模式不删源）
        assertTrue(bridge.exists("/src/a.png"))
    }

    @Test
    fun `换一条复制规则、换一个源目录，同一份内容也不再复制`() = runBlocking {
        val bridge = FakeFileBridge()
        bridge.put("/srcA/a.png", size = 100, mtime = 1_000)
        bridge.put("/srcB/b.png", size = 100, mtime = 1_000) // 同一个文件被放到另一个源目录
        val ledger = FakeLedger()
        val engine = RuleEngine(bridge, NamePolicy(), settings, now = { 2_000_000 }, ledger = ledger)

        val ruleA = rule(mode = Mode.COPY).copy(id = 1, name = "A", srcPath = "/srcA", dstPath = "/dstA")
        val ruleB = rule(mode = Mode.COPY).copy(id = 2, name = "B", srcPath = "/srcB", dstPath = "/dstB")

        assertEquals(1, engine.runOnce(ruleA, LoopGuard(60_000, 99)).moved)

        val second = engine.runOnce(ruleB, LoopGuard(60_000, 99))
        assertEquals(0, second.moved)
        assertEquals(1, second.skipped)
        assertFalse(bridge.exists("/dstB/b_B.png"))
    }

    @Test
    fun `移动模式完全不受复制记录影响`() = runBlocking {
        val bridge = FakeFileBridge()
        bridge.put("/src/a.png", size = 100, mtime = 1_000)
        val ledger = FakeLedger()
        // 账本里已经有这份内容的指纹
        ledger.fingerprints += fingerprintOf(100, 1_000)
        val engine = RuleEngine(bridge, NamePolicy(), settings, now = { 2_000_000 }, ledger = ledger)

        val result = engine.runOnce(rule(), LoopGuard(60_000, 99))
        assertEquals(1, result.moved)
        assertFalse(bridge.exists("/src/a.png"))
    }

    @Test
    fun `复制记录只管复制模式_移动过的内容不进账本`() = runBlocking {
        val bridge = FakeFileBridge()
        bridge.put("/src/a.png", size = 100, mtime = 1_000)
        val ledger = FakeLedger()
        val engine = RuleEngine(bridge, NamePolicy(), settings, now = { 2_000_000 }, ledger = ledger)

        engine.runOnce(rule(), LoopGuard(60_000, 99))
        assertEquals(0, ledger.count())
    }

    @Test
    fun `清空账本之后同一份内容会重新复制一次`() = runBlocking {
        val bridge = FakeFileBridge()
        bridge.put("/src/a.png", size = 100, mtime = 1_000)
        val ledger = FakeLedger()
        val engine = RuleEngine(bridge, NamePolicy(), settings, now = { 2_000_000 }, ledger = ledger)
        val r = rule(mode = Mode.COPY)

        engine.runOnce(r, LoopGuard(60_000, 99))
        clearDir(bridge, "/dst")
        assertEquals(0, engine.runOnce(r, LoopGuard(60_000, 99)).moved)

        assertEquals(1, ledger.clear())
        assertEquals(1, engine.runOnce(r, LoopGuard(60_000, 99)).moved)
    }

    @Test
    fun `没有账本时复制模式维持老行为`() = runBlocking {
        val bridge = FakeFileBridge()
        bridge.put("/src/a.png", size = 100, mtime = 1_000)
        val engine = RuleEngine(bridge, NamePolicy(), settings, now = { 2_000_000 })
        val r = rule(mode = Mode.COPY)

        assertEquals(1, engine.runOnce(r, LoopGuard(60_000, 99)).moved)
        clearDir(bridge, "/dst")
        // 老口径只看目标目录：副本被移走后就会再复制一份
        assertEquals(1, engine.runOnce(r, LoopGuard(60_000, 99)).moved)
    }
}
