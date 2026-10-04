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
    val addSourceAppSuffix: Boolean = true,
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
        const val DEFAULT_DST = "/sdcard/Pictures/拾图"
        const val PICKER_ROOT = "/sdcard/Android/data"
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
