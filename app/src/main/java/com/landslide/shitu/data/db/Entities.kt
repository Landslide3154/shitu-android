package com.landslide.shitu.data.db

import androidx.room.ColumnInfo
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
    /**
     * 目标文件名改成"按内容生成"的 15 位名字（同一个文件永远同名，重复的图不会再存第二份）。
     * Kotlin 默认 true 只作用于**新建**的规则；老数据由数据库迁移写成 0（关闭），不会突然被改名。
     */
    @ColumnInfo(defaultValue = "0") val contentRename: Boolean = true,
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

/**
 * 「这份内容已经搬进来过」的账本。
 *
 * 复制模式的规则原本靠"目标目录里还有没有那份文件"判断重复，但目标目录往往只是中转站——
 * 用户把文件手工移走/改名之后这条依据就断了，于是同一份内容会被反复复制进来。
 * 这里按**内容指纹**记一笔（全局，跨规则、跨源目录）：只要复制过就永不再复制。
 *
 * 只记"复制成功"的内容；移动模式完全不受影响。
 */
@Entity(tableName = "copied")
data class CopiedEntity(
    /** 15 位内容指纹（由 ContentName 从文件内容算出，与内容文件名主体同源） */
    @PrimaryKey val fingerprint: String,
    /** 第一次复制它的规则（仅供参考） */
    val ruleId: Long,
    /** 第一次复制到的位置（仅供参考/排查） */
    val dstPath: String?,
    val firstSeenAt: Long,
)
