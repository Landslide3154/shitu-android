package com.landslide.shitu.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    /**
     * 与 GitHub `releases/latest` 的真实响应同构（字段有裁剪，转义、嵌套与字段顺序保持原样）。
     * body 里的 `\r\n`、`\"` 在原始字符串中保留成反斜杠序列，正是线上 JSON 的样子。
     */
    private val latestResponse = """
        {
          "url": "https://api.github.com/repos/Landslide3154/shitu-android/releases/123456",
          "assets_url": "https://api.github.com/repos/Landslide3154/shitu-android/releases/123456/assets",
          "html_url": "https://github.com/Landslide3154/shitu-android/releases/tag/v0.13.0",
          "id": 123456,
          "author": {"login": "Landslide3154", "id": 1, "type": "User"},
          "node_id": "RE_kwDOAbCdEf4GHiJk",
          "tag_name": "v0.13.0",
          "target_commitish": "main",
          "name": "v0.13.0",
          "draft": false,
          "prerelease": false,
          "created_at": "2026-10-17T09:00:00Z",
          "published_at": "2026-10-17T10:00:00Z",
          "assets": [
            {
              "url": "https://api.github.com/repos/Landslide3154/shitu-android/releases/assets/1",
              "id": 1,
              "name": "shitu-0.13.0.apk",
              "label": "",
              "content_type": "application/vnd.android.package-archive",
              "state": "uploaded",
              "size": 12345678,
              "download_count": 3,
              "created_at": "2026-10-17T09:50:00Z",
              "updated_at": "2026-10-17T09:50:00Z",
              "browser_download_url": "https://github.com/Landslide3154/shitu-android/releases/download/v0.13.0/shitu-0.13.0.apk"
            },
            {
              "url": "https://api.github.com/repos/Landslide3154/shitu-android/releases/assets/2",
              "id": 2,
              "name": "source.zip",
              "content_type": "application/zip",
              "state": "uploaded",
              "size": 654321,
              "download_count": 0,
              "browser_download_url": "https://github.com/Landslide3154/shitu-android/releases/download/v0.13.0/source.zip"
            }
          ],
          "tarball_url": "https://api.github.com/repos/Landslide3154/shitu-android/tarball/v0.13.0",
          "zipball_url": "https://api.github.com/repos/Landslide3154/shitu-android/zipball/v0.13.0",
          "body": "## 更新\r\n- 修了 \"引号\" 问题"
        }
    """.trimIndent()

    @Test
    fun `解析真实 GitHub 最新版本响应`() {
        val info = UpdateChecker.parseRelease(latestResponse) ?: error("应当解析成功")

        assertEquals("v0.13.0", info.tagName)
        assertEquals("0.13.0", info.version)
        assertEquals("v0.13.0", info.title)
        assertEquals("## 更新\r\n- 修了 \"引号\" 问题", info.notes)
        assertEquals(
            "https://github.com/Landslide3154/shitu-android/releases/tag/v0.13.0",
            info.pageUrl,
        )
        assertEquals(
            "https://github.com/Landslide3154/shitu-android/releases/download/v0.13.0/shitu-0.13.0.apk",
            info.apkUrl,
        )
        assertEquals("2026-10-17T10:00:00Z", info.publishedAt)
    }

    @Test
    fun `name 为空或缺失时标题回退到 tag`() {
        val emptyName = UpdateChecker.parseRelease("""{"tag_name":"v0.13.0","name":""}""")
        assertEquals("v0.13.0", emptyName?.title)

        val nullName = UpdateChecker.parseRelease("""{"tag_name":"v0.13.0","name":null}""")
        assertEquals("v0.13.0", nullName?.title)
    }

    @Test
    fun `notes 中的转义引号换行反斜杠与制表符都能还原`() {
        val json = """{"tag_name":"v0.13.0","body":"第一行\n第二行 \"引号\" C:\\path\t结束"}"""
        val info = UpdateChecker.parseRelease(json) ?: error("应当解析成功")

        assertEquals("第一行\n第二行 \"引号\" C:\\path\t结束", info.notes)
    }

    @Test
    fun `notes 中的 unicode 转义与 emoji 不乱码`() {
        val json = """{"tag_name":"v0.13.0","body":"\u4e2d\u6587 \ud83c\udf89"}"""
        val info = UpdateChecker.parseRelease(json) ?: error("应当解析成功")

        assertEquals("中文 🎉", info.notes)
    }

    @Test
    fun `body 里出现类似字段的文本不会干扰解析`() {
        val json = """
            {"tag_name":"v0.13.0",
             "body":"说明里写了 \"tag_name\": \"v9.9.9\"，别被它带偏",
             "assets":[{"browser_download_url":"https://example.com/shitu-0.13.0.apk"}]}
        """.trimIndent()
        val info = UpdateChecker.parseRelease(json) ?: error("应当解析成功")

        assertEquals("v0.13.0", info.tagName)
        assertEquals("https://example.com/shitu-0.13.0.apk", info.apkUrl)
    }

    @Test
    fun `notes 截断到 4000 字符`() {
        val body = "更".repeat(5_000)
        val json = """{"tag_name":"v1.0.0","body":"$body"}"""
        val info = UpdateChecker.parseRelease(json) ?: error("应当解析成功")

        assertEquals(4_000, info.notes.length)
        assertTrue(info.notes.all { it == '更' })
    }

    @Test
    fun `畸形 JSON 或缺少 tag_name 返回 null 而不是抛异常`() {
        assertNull(UpdateChecker.parseRelease(""))
        assertNull(UpdateChecker.parseRelease("不是 JSON"))
        assertNull(UpdateChecker.parseRelease("""{"assets":[]}"""))
        assertNull(UpdateChecker.parseRelease("""{"tag_name":""}"""))
        assertNull(UpdateChecker.parseRelease("""{"tag_name":123}"""))
        assertNull(UpdateChecker.parseRelease("""{"tag_name":"v1.0.0","assets":[}"""))
    }

    @Test
    fun `多个附件时选中 apk 且优先带版本号的那个`() {
        val json = """
            {
              "tag_name": "v0.13.0",
              "assets": [
                {"name": "shitu-debug.apk", "browser_download_url": "https://example.com/shitu-debug.apk"},
                {"name": "source.zip", "browser_download_url": "https://example.com/source.zip"},
                {"name": "shitu-0.13.0.apk", "browser_download_url": "https://example.com/shitu-0.13.0.apk"}
              ]
            }
        """.trimIndent()

        assertEquals("https://example.com/shitu-0.13.0.apk", UpdateChecker.parseRelease(json)?.apkUrl)
    }

    @Test
    fun `没有 apk 附件时 apkUrl 为 null`() {
        val json = """
            {
              "tag_name": "v0.13.0",
              "assets": [
                {"name": "source.zip", "browser_download_url": "https://example.com/source.zip"},
                {"name": "checksums.txt", "browser_download_url": "https://example.com/checksums.txt"}
              ]
            }
        """.trimIndent()

        assertNull(UpdateChecker.parseRelease(json)?.apkUrl)
    }

    @Test
    fun `assets 缺失时 apkUrl 为 null`() {
        assertNull(UpdateChecker.parseRelease("""{"tag_name":"v0.13.0"}""")?.apkUrl)
    }

    @Test
    fun `补丁号变大算更新`() {
        assertTrue(UpdateChecker.isNewer("0.12.3", "0.12.4"))
        assertTrue(UpdateChecker.isNewer("0.12.3", "0.12.10"))
    }

    @Test
    fun `次版本变大且带 v 前缀算更新`() {
        assertTrue(UpdateChecker.isNewer("0.12.3", "v0.13.0"))
        assertTrue(UpdateChecker.isNewer("v0.12.3", "V0.13.0"))
    }

    @Test
    fun `段数不同时缺的段按 0 比较`() {
        assertFalse(UpdateChecker.isNewer("0.13", "0.13.0"))
        assertFalse(UpdateChecker.isNewer("0.13.0", "0.13"))
        assertTrue(UpdateChecker.isNewer("0.13", "0.13.1"))
        assertTrue(UpdateChecker.isNewer("1.0", "1.0.1"))
        assertTrue(UpdateChecker.isNewer("0.12.3", "0.12.3.1"))
        assertFalse(UpdateChecker.isNewer("1.0.0.0", "1.0"))
    }

    @Test
    fun `相同版本与降级都不算更新`() {
        assertFalse(UpdateChecker.isNewer("0.12.3", "0.12.3"))
        assertFalse(UpdateChecker.isNewer("0.13.0", "0.12.9"))
        assertFalse(UpdateChecker.isNewer("v0.13.0", "v0.12.3"))
        assertFalse(UpdateChecker.isNewer("1.0", "0.99.99"))
    }

    @Test
    fun `非法版本字符串返回 false 且不抛异常`() {
        assertFalse(UpdateChecker.isNewer("", "0.13.0"))
        assertFalse(UpdateChecker.isNewer("0.12.3", ""))
        assertFalse(UpdateChecker.isNewer("abc", "0.13.0"))
        assertFalse(UpdateChecker.isNewer("0.12.3", "1.x"))
        assertFalse(UpdateChecker.isNewer("1..2", "1.2.3"))
        assertFalse(UpdateChecker.isNewer("v", "0.13.0"))
        assertFalse(UpdateChecker.isNewer("0.12.3", "0.12.3.0.0"))
    }

    @Test
    fun `忽略预发布与构建后缀`() {
        assertTrue(UpdateChecker.isNewer("0.12.3", "0.13.0-beta"))
        assertTrue(UpdateChecker.isNewer("0.12.3", "v0.13.0-rc.1"))
        assertTrue(UpdateChecker.isNewer("0.12.3", "0.13.0+build.7"))
        assertFalse(UpdateChecker.isNewer("0.13.0", "0.13.0-beta"))
    }

    @Test
    fun `自动检查节流：从未查过或超过间隔才查`() {
        assertTrue(UpdateChecker.shouldAutoCheck(now = 1000L, lastCheckAt = 0L, intervalMs = 100L))
        assertFalse(UpdateChecker.shouldAutoCheck(now = 1099L, lastCheckAt = 1000L, intervalMs = 100L))
        assertTrue(UpdateChecker.shouldAutoCheck(now = 1100L, lastCheckAt = 1000L, intervalMs = 100L))
    }

    @Test
    fun `同一版本只提醒一次，已是最新不提醒`() {
        assertTrue(UpdateChecker.shouldNotify("0.20.8", "0.20.9", null))
        assertTrue(UpdateChecker.shouldNotify("0.20.8", "0.20.9", "0.20.8"))
        assertFalse(UpdateChecker.shouldNotify("0.20.8", "0.20.9", "0.20.9"))
        assertFalse(UpdateChecker.shouldNotify("0.20.9", "0.20.9", null))
        assertFalse(UpdateChecker.shouldNotify("0.20.9", "0.20.8", null))
    }
}
