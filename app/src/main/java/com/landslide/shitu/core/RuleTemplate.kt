package com.landslide.shitu.core

import com.landslide.shitu.data.db.Mode
import com.landslide.shitu.data.db.RuleEntity

/**
 * 新建规则的模板：把最常见的几种用法先填好，用户少点几步。
 *
 * 选了模板之后仍然会进编辑页 —— 什么都没保存，改完点「保存并开始搬运」才生效。
 */
data class RuleTemplate(
    val key: String,
    val title: String,
    val detail: String,
    val name: String,
    val srcPath: String,
    val dstPath: String = RuleEntity.DEFAULT_DST,
    val includeSubdirs: Boolean = true,
    val mode: Mode = Mode.MOVE,
    val suffix: String = RuleEntity.DEFAULT_SUFFIX,
    val intervalMinutes: Int = 5,
) {
    fun toEntity(now: Long): RuleEntity = RuleEntity(
        name = name,
        srcPath = srcPath,
        includeSubdirs = includeSubdirs,
        dstPath = dstPath,
        mode = mode,
        intervalMinutes = intervalMinutes,
        suffix = suffix,
        createdAt = now,
        updatedAt = now,
    )

    companion object {
        /** 「某个 App 的图片目录」模板里预填的示例路径（README 里就是这个例子）。 */
        const val EXAMPLE_APP_DIR = "/sdcard/Android/data/com.qidian.QDReader/files"

        val ALL = listOf(
            RuleTemplate(
                key = "app-data",
                title = "某个 App 的图片目录",
                detail = "预填了示例路径，点「选择」换成你要清的那个 App；找不到就用 /sdcard/DCIM 那种公共目录",
                name = "App 图片",
                srcPath = EXAMPLE_APP_DIR,
            ),
            RuleTemplate(
                key = "dcim-by-app",
                title = "相册图片按来源分类",
                detail = "把 /sdcard/DCIM 这一层里的图片加上来源 App 名收进 /sdcard/DCIM/杂图（子目录不动）",
                name = "相册整理",
                srcPath = "/sdcard/DCIM",
                includeSubdirs = false,
            ),
            RuleTemplate(
                key = "download",
                title = "下载目录里的图片",
                detail = "先按「复制」模式试一阵，确认没问题再改成「移动」",
                name = "下载图片",
                srcPath = "/sdcard/Download",
                mode = Mode.COPY,
            ),
        )
    }
}
