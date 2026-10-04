package com.landslide.shitu.shizuku

import org.junit.Assert.assertEquals
import org.junit.Test

class ShizukuStateMachineTest {

    @Test
    fun `未安装优先于其他状态`() {
        assertEquals(
            ShizukuState.NOT_INSTALLED,
            derive(installed = false, running = true, permitted = true, bindFailures = 0, binderAlive = true),
        )
    }

    @Test
    fun `已安装未运行`() {
        assertEquals(
            ShizukuState.NOT_RUNNING,
            derive(true, running = false, permitted = true, bindFailures = 0, binderAlive = true),
        )
    }

    @Test
    fun `未授权`() {
        assertEquals(
            ShizukuState.NO_PERMISSION,
            derive(true, true, permitted = false, bindFailures = 0, binderAlive = true),
        )
    }

    @Test
    fun `绑定失败 5 次进入 BIND_FAILED`() {
        assertEquals(
            ShizukuState.BIND_FAILED,
            derive(true, true, true, bindFailures = 5, binderAlive = true),
        )
    }

    @Test
    fun `binder 断开回落到 NOT_RUNNING`() {
        assertEquals(ShizukuState.NOT_RUNNING, derive(true, true, true, 0, binderAlive = false))
    }

    @Test
    fun `全部就绪为 READY`() {
        assertEquals(ShizukuState.READY, derive(true, true, true, 0, true))
    }

    @Test
    fun `每个状态都有中文说明`() {
        ShizukuState.entries.forEach { assertEquals(true, it.display().isNotBlank()) }
    }
}
