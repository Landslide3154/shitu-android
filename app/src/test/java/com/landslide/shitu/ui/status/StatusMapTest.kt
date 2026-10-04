package com.landslide.shitu.ui.status

import com.landslide.shitu.data.db.LogResult
import com.landslide.shitu.data.db.Mode
import com.landslide.shitu.data.db.RuleEntity
import com.landslide.shitu.data.db.RuleState
import com.landslide.shitu.shizuku.ShizukuState
import com.landslide.shitu.ui.theme.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 状态映射的纯逻辑单测：界面颜色/文案都从这里来，映射错了整屏都会骗人。 */
class StatusMapTest {

    private fun rule(
        enabled: Boolean = true,
        state: RuleState = RuleState.IDLE,
        pauseReason: String? = null,
    ) = RuleEntity(
        id = 1,
        name = "测试",
        srcPath = "/sdcard/DCIM/QDReader",
        dstPath = "/sdcard/DCIM",
        mode = Mode.MOVE,
        enabled = enabled,
        state = state,
        pauseReason = pauseReason,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun `shizuku 状态映射到颜色语义`() {
        assertEquals(Tone.OK, shizukuTone(ShizukuState.READY))
        assertEquals(Tone.ERROR, shizukuTone(ShizukuState.NOT_INSTALLED))
        assertEquals(Tone.WARN, shizukuTone(ShizukuState.NOT_RUNNING))
        assertEquals(Tone.WARN, shizukuTone(ShizukuState.NO_PERMISSION))
        assertEquals(Tone.WARN, shizukuTone(ShizukuState.BIND_FAILED))
    }

    @Test
    fun `就绪时不给按钮，其余状态都给一个动作`() {
        assertNull(shizukuActionLabel(ShizukuState.READY))
        ShizukuState.entries.filter { it != ShizukuState.READY }.forEach { s ->
            assertNotNull("状态 $s 应该有下一步动作", shizukuActionLabel(s))
        }
    }

    @Test
    fun `每个 shizuku 状态都有标题和指引`() {
        ShizukuState.entries.forEach { s ->
            assertNotNull(shizukuTitle(s))
            assertNotNull(shizukuAdvice(s))
            assertEquals(true, shizukuTitle(s).isNotBlank())
            assertEquals(true, shizukuAdvice(s).isNotBlank())
        }
    }

    @Test
    fun `规则状态映射：停用是灰的、运行是主题色、失败是红的、暂停是琥珀`() {
        assertEquals(Tone.IDLE, ruleTone(rule(enabled = false)))
        assertEquals(Tone.OK, ruleTone(rule()))
        assertEquals(Tone.BUSY, ruleTone(rule(state = RuleState.RUNNING)))
        assertEquals(Tone.ERROR, ruleTone(rule(state = RuleState.PAUSED_ERROR)))
        assertEquals(Tone.WARN, ruleTone(rule(state = RuleState.PAUSED_MANUAL)))
        assertEquals(Tone.WARN, ruleTone(rule(state = RuleState.PAUSED_LOOP)))
    }

    @Test
    fun `停用的规则优先显示已停止，而不是它内部的暂停原因`() {
        assertEquals("已停止", ruleStatusText(rule(enabled = false, state = RuleState.PAUSED_ERROR)))
        assertEquals(Tone.IDLE, ruleTone(rule(enabled = false, state = RuleState.PAUSED_ERROR)))
    }

    @Test
    fun `规则状态文案`() {
        assertEquals("待命", ruleStatusText(rule()))
        assertEquals("运行中", ruleStatusText(rule(state = RuleState.RUNNING)))
        assertEquals("失败暂停", ruleStatusText(rule(state = RuleState.PAUSED_ERROR)))
        assertEquals("已暂停", ruleStatusText(rule(state = RuleState.PAUSED_MANUAL)))
        assertEquals("循环暂停", ruleStatusText(rule(state = RuleState.PAUSED_LOOP)))
    }

    @Test
    fun `只有被自动暂停的启用规则能恢复`() {
        assertEquals(false, canResume(rule()))
        assertEquals(false, canResume(rule(enabled = false, state = RuleState.PAUSED_ERROR)))
        assertEquals(true, canResume(rule(state = RuleState.PAUSED_ERROR)))
        assertEquals(true, canResume(rule(state = RuleState.PAUSED_LOOP)))
    }

    @Test
    fun `日志结果映射到颜色语义`() {
        assertEquals(Tone.OK, logTone(LogResult.MOVED))
        assertEquals(Tone.OK, logTone(LogResult.COPIED))
        assertEquals(Tone.WARN, logTone(LogResult.SKIPPED))
        assertEquals(Tone.ERROR, logTone(LogResult.FAILED))
        assertEquals(Tone.BUSY, logTone(LogResult.UNDONE))
        assertEquals(Tone.IDLE, logTone(LogResult.INFO))
    }
}
