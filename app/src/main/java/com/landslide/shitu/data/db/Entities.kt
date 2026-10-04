package com.landslide.shitu.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val srcPath: String,
    val includeSubdirs: Boolean = true,
    val maxDepth: Int? = null,
    val dstPath: String,
    val mode: Mode = Mode.MOVE,
    val intervalMinutes: Int = 5,
    val extensions: String = DEFAULT_EXTENSIONS,
    /** 文件名后缀模板：空 = 不加后缀；`{app}` = 用来源 App 名；其他内容原样使用 */
    val suffix: String = DEFAULT_SUFFIX,
    val enabled: Boolean = true,
    val state: RuleState = RuleState.IDLE,
    val pauseReason: String? = null,
    val lastRunAt: Long? = null,
    val lastMoved: Int = 0,
    val lastFailed: Int = 0,
    val totalMoved: Long = 0,
    val totalFailed: Long = 0,
    val consecutiveFailures: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        const val DEFAULT_EXTENSIONS = "jpg,jpeg,png,gif,webp,bmp,heic,heif,avif"

        /** `{app}` = 用来源 App 的名字，例如 封面_{app}.png → 封面_起点读书.png */
        const val DEFAULT_SUFFIX = "{app}"
        const val DEFAULT_DST = "/sdcard/DCIM"

        /** 目录选择器打开时的起始目录（用户从存储根目录往下点） */
        const val PICKER_ROOT = "/sdcard"
    }
}

@Entity(
    tableName = "items",
    indices = [
        Index(value = ["ruleId", "srcPath"], unique = true),
        Index(value = ["ruleId", "srcSize", "srcMtime"]),
    ],
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    val srcPath: String,
    val srcSize: Long,
    val srcMtime: Long,
    val dstPath: String?,
    val status: ItemStatus,
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val processedAt: Long? = null,
)

@Entity(tableName = "logs", indices = [Index("ts"), Index("ruleId")])
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val ruleId: Long?,
    val srcPath: String?,
    val dstPath: String?,
    val result: LogResult,
    val durationMs: Long = 0,
    val message: String? = null,
)
