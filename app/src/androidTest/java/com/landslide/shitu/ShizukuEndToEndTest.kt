package com.landslide.shitu

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.landslide.shitu.core.LoopGuard
import com.landslide.shitu.core.NamePolicy
import com.landslide.shitu.data.Settings
import com.landslide.shitu.data.db.ItemStatus
import com.landslide.shitu.data.db.Mode
import com.landslide.shitu.data.db.RuleEntity
import com.landslide.shitu.engine.HealthChecker
import com.landslide.shitu.engine.RuleEngine
import com.landslide.shitu.engine.UndoService
import com.landslide.shitu.shizuku.ShizukuBridge
import com.landslide.shitu.shizuku.ShizukuState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真机端到端测试（规格 §14 集成测试 / 验收 A1、A3、A4、A6、A7）。
 * 只用自己创建的临时文件，测试结束清理干净；Shizuku 未就绪时自动跳过。
 */
@RunWith(AndroidJUnit4::class)
class ShizukuEndToEndTest {

    private companion object {
        const val SRC = "/sdcard/Download/_shitu_instr"
        const val DST = "/sdcard/Pictures/_shitu_instr"
        const val PROBE = "/dev/null"
    }

    private lateinit var ctx: Context
    private lateinit var bridge: ShizukuBridge

    private val settings = Settings(
        scanBudgetSec = 20, maxPerRun = 20, maxPerMinute = 120, maxRunSec = 60,
        stableSec = 0, loopWindowMin = 30, loopThreshold = 3, failThreshold = 5,
        lowBatteryPause = false, lowBatteryPct = 0, logKeepDays = 30,
        logKeepCount = 50_000, fastDrain = false,
    )

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        bridge = ShizukuBridge(ctx)
        if (bridge.state() != ShizukuState.READY) {
            runBlocking { bridge.bindWithRetry(2) }
        }
        assumeTrue("Shizuku 未就绪（${bridge.state()}），跳过真机用例", bridge.state() == ShizukuState.READY)
        runBlocking {
            bridge.mkdirs(SRC)
            bridge.mkdirs(DST)
        }
    }

    @After
    fun tearDown() {
        if (!::bridge.isInitialized) return
        runBlocking {
            runCatching { bridge.list(SRC, false, 1, 500, null) }.getOrDefault(emptyList())
                .forEach { bridge.delete(it.path) }
            runCatching { bridge.list(DST, false, 1, 500, null) }.getOrDefault(emptyList())
                .forEach { bridge.delete(it.path) }
        }
    }

    @Test
    fun canListOtherAppAndroidData() = runBlocking {
        val androidData = bridge.list("/sdcard/Android/data", false, 1, 50, null)
        assertTrue("应能列出别的 App 的 Android/data", androidData.isNotEmpty())
        val env = bridge.describeEnvironment()
        assertTrue(env.contains("uid=2000"))
    }

    @Test
    fun selfCheckAllGreen() = runBlocking {
        val report = HealthChecker(bridge, SRC, DST) { bridge.environmentLine() }.run()
        val failed = report.filter { !it.passed }
        assertTrue("自检失败项：$failed", failed.isEmpty())
        assertEquals(6, report.size)
    }

    @Test
    fun copyModeKeepsSourceAndAddsSuffix() = runBlocking {
        bridge.copy(PROBE, "$SRC/端到端测试.png")
        val rule = RuleEntity(
            id = 999, name = "instr", srcPath = SRC, dstPath = DST,
            mode = Mode.COPY, extensions = "png", createdAt = 0, updatedAt = 0,
        )
        val engine = RuleEngine(bridge, NamePolicy(), settings)
        val result = engine.runOnce(rule, LoopGuard(60_000, 99))

        assertEquals(1, result.moved)
        assertTrue("源文件应保留", bridge.exists("$SRC/端到端测试.png"))
        assertTrue(
            "目标应出现带来源后缀的文件",
            bridge.list(DST, false, 1, 50, null).any { it.name.startsWith("端到端测试_") && it.name.endsWith(".png") },
        )
    }

    @Test
    fun moveModeRemovesSourceAndUndoRestoresIt() = runBlocking {
        bridge.copy(PROBE, "$SRC/可撤回.png")
        val rule = RuleEntity(
            id = 998, name = "instr", srcPath = SRC, dstPath = DST,
            mode = Mode.MOVE, extensions = "png", createdAt = 0, updatedAt = 0,
        )
        val engine = RuleEngine(bridge, NamePolicy(), settings)
        val result = engine.runOnce(rule, LoopGuard(60_000, 99))
        assertEquals(1, result.moved)
        assertTrue("移动模式下源应消失", !bridge.exists("$SRC/可撤回.png"))

        val movedName = bridge.list(DST, false, 1, 50, null).first { it.name.startsWith("可撤回_") }
        val undo = UndoService(bridge).undo("$SRC/可撤回.png", movedName.path, 0)
        assertTrue("撤回应成功：${undo.reason}", undo.ok)
        assertTrue("撤回后源应回来", bridge.exists("$SRC/可撤回.png"))
        assertTrue("撤回后目标应消失", !bridge.exists(movedName.path))
    }

    @Test
    fun neverOverwritesAndSkipsIdenticalTarget() = runBlocking {
        // 先在目标放一个"同 size + 同 mtime"的文件，模拟"已经搬过了"
        bridge.copy(PROBE, "$DST/同名_起点.png")
        val existing = bridge.stat("$DST/同名_起点.png")!!
        bridge.copy(PROBE, "$SRC/同名.png")
        // /dev/null 拷贝出来的都是 0 字节，mtime 不同；改造成 mtime 一致
        val srcStat = bridge.stat("$SRC/同名.png")!!
        val executor = com.landslide.shitu.engine.TransferExecutor(bridge, NamePolicy())
        val sameMtimeTarget = existing.copy(mtimeMillis = srcStat.mtimeMillis)
        assertTrue(sameMtimeTarget.size == srcStat.size)

        // 直接验证"目标已有同内容文件"的分支：COPY 模式应记 SKIPPED
        val item = executor.transfer(
            src = srcStat.copy(mtimeMillis = existing.mtimeMillis),
            srcPath = "$SRC/同名.png",
            dstDir = DST,
            sourceApp = "起点",
            mode = Mode.COPY,
            ruleId = 1,
            now = System.currentTimeMillis(),
        )
        assertEquals(ItemStatus.SKIPPED, item.status)
        assertTrue("源不能被删", bridge.exists("$SRC/同名.png"))
        assertTrue("目标不能被覆盖", bridge.exists("$DST/同名_起点.png"))
    }
}
