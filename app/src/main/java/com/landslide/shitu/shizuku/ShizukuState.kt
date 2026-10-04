package com.landslide.shitu.shizuku

/**
 * Shizuku 状态机（规格 §6.7）：
 * NOT_INSTALLED → NOT_RUNNING → NO_PERMISSION → READY
 * 绑定连续失败 5 次进入 BIND_FAILED；binder 断开回落到 NOT_RUNNING。
 */
enum class ShizukuState { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, BIND_FAILED, READY }

fun derive(
    installed: Boolean,
    running: Boolean,
    permitted: Boolean,
    bindFailures: Int,
    binderAlive: Boolean,
): ShizukuState = when {
    !installed -> ShizukuState.NOT_INSTALLED
    !running -> ShizukuState.NOT_RUNNING
    !permitted -> ShizukuState.NO_PERMISSION
    bindFailures >= 5 -> ShizukuState.BIND_FAILED
    !binderAlive -> ShizukuState.NOT_RUNNING
    else -> ShizukuState.READY
}

fun ShizukuState.display(): String = when (this) {
    ShizukuState.NOT_INSTALLED -> "未安装 Shizuku"
    ShizukuState.NOT_RUNNING -> "未就绪（Shizuku 未运行或未绑定）"
    ShizukuState.NO_PERMISSION -> "未授权（请在 Shizuku 中授权拾图）"
    ShizukuState.BIND_FAILED -> "绑定失败（可重试或跑自检）"
    ShizukuState.READY -> "就绪"
}
