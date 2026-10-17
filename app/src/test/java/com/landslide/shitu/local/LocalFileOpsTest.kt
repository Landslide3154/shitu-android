package com.landslide.shitu.local

import com.landslide.shitu.shizuku.RemoteFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * 本地通道的文件语义（纯 JVM，只碰临时目录）。
 *
 * 这些用例钉的其实是 ShituUserService 的那几条铁律：绝不覆盖、不先删后搬、
 * 游标分页不重不漏、隐藏目录不在这一层过滤。本地实现不依赖 shell/Binder，所以能直接跑。
 *
 * 注意：RemoteFile 是 @Parcelize 的 data class，这里只构造/读属性、不调 parcel 方法，
 * 与项目里已有的 FakeFileBridge 单测一样，在 JVM 下可用。
 */
class LocalFileOpsTest {

    private lateinit var root: Path

    @Before
    fun setUp() {
        root = Files.createTempDirectory("shitu-local-fileops-")
    }

    @After
    fun tearDown() {
        // 清理失败不该把已经得出的结论判红：临时目录系统会回收
        runCatching { root.toFile().deleteRecursively() }
    }

    // ---------- 造数据与工具 ----------

    private fun file(rel: String, content: String = "x"): File {
        val f = root.resolve(rel).toFile()
        f.parentFile?.mkdirs()
        f.writeText(content)
        return f
    }

    private fun dir(rel: String): File = root.resolve(rel).toFile().apply { mkdirs() }

    private fun listAll(
        recursive: Boolean = true,
        maxDepth: Int = 0,
        maxCount: Int = 500,
        after: String? = null,
    ): List<RemoteFile> = LocalFileOps.list(root.toString(), recursive, maxDepth, maxCount, after)

    private fun relPath(path: String): String =
        File(path).relativeTo(root.toFile()).invariantSeparatorsPath

    private fun rel(f: RemoteFile): String = relPath(f.path)

    /** 从 after 开始一页页翻到底，返回所有绝对路径（用来验证「接着列」不重不漏）。 */
    private fun pageThrough(pageSize: Int, afterPath: String? = null): List<String> {
        val all = ArrayList<String>()
        var after = afterPath
        var pages = 0
        while (true) {
            val page = listAll(maxCount = pageSize, after = after)
            if (page.isEmpty()) break
            all += page.map { it.path }
            after = page.last().path
            if (page.size < pageSize) break
            check(++pages < 1000) { "分页没有收敛，游标没有推进" }
        }
        return all
    }

    /**
     * root/
     *   a.txt
     *   b.txt
     *   dir1/c.txt
     *   dir1/sub/d.txt
     *   dir2/e.txt
     */
    private fun buildTree() {
        file("a.txt", "a")
        file("b.txt", "bb")
        file("dir1/c.txt", "ccc")
        file("dir1/sub/d.txt", "dddd")
        file("dir2/e.txt", "eeeee")
    }

    // ---------- list ----------

    @Test
    fun `list 递归列出整棵树`() {
        buildTree()
        assertEquals(
            setOf(
                "a.txt", "b.txt",
                "dir1", "dir1/c.txt", "dir1/sub", "dir1/sub/d.txt",
                "dir2", "dir2/e.txt",
            ),
            listAll().map { rel(it) }.toSet(),
        )
    }

    @Test
    fun `list 非递归只列一层`() {
        buildTree()
        assertEquals(
            listOf("a.txt", "b.txt", "dir1", "dir2"),
            listAll(recursive = false).map { rel(it) },
        )
    }

    @Test
    fun `list 的 maxDepth 只限制下钻，不吞掉当前层的条目`() {
        buildTree()
        val two = listAll(maxDepth = 2).map { rel(it) }.toSet()
        assertTrue(two.contains("dir1/c.txt"))
        assertTrue("这一层的目录本身仍要列出", two.contains("dir1/sub"))
        assertFalse("不再往下钻，深层文件不该出现", two.contains("dir1/sub/d.txt"))
    }

    @Test
    fun `list 的 after 游标分页不重不漏`() {
        buildTree()
        val full = listAll().map { it.path }
        for (size in listOf(1, 2, 3, 5)) {
            val paged = pageThrough(size)
            assertEquals("页大小 $size 时内容或顺序不一致", full, paged)
            assertEquals("页大小 $size 时出现了重复", paged.size, paged.distinct().size)
        }
    }

    @Test
    fun `after 停在目录上时这个目录的子树不会丢`() {
        buildTree()
        // 页大小 3 时第一页正好停在 dir1（目录）上，续页必须还能进到它里面
        val first = listAll(maxCount = 3)
        assertEquals("dir1", rel(first.last()))

        val rest = pageThrough(pageSize = 3, afterPath = first.last().path).map { relPath(it) }
        assertTrue("续页漏了 dir1 的直接子文件", rest.contains("dir1/c.txt"))
        assertTrue("续页漏了 dir1 的深层文件", rest.contains("dir1/sub/d.txt"))
    }

    @Test
    fun `list 用最后一项当 after 时返回空`() {
        buildTree()
        val last = listAll().last()
        assertTrue(listAll(after = last.path).isEmpty())
    }

    @Test
    fun `list 不过滤点开头的隐藏目录`() {
        file(".hidden/inside.txt")
        file(".dotfile")
        val names = listAll().map { rel(it) }.toSet()
        // 过滤隐藏目录是 UI 的事（选目录时不想看到 .DataStorage），引擎要看到真实存在的目录
        assertTrue(names.contains(".hidden"))
        assertTrue(names.contains(".hidden/inside.txt"))
        assertTrue(names.contains(".dotfile"))
    }

    @Test
    fun `list 根目录不存在时返回空`() {
        assertTrue(
            LocalFileOps.list(root.resolve("nope").toString(), true, 0, 500, null).isEmpty(),
        )
    }

    // ---------- move ----------

    @Test
    fun `move 成功时源消失、目标出现且内容一致`() {
        val src = file("src/a.txt", "hello")
        val dst = root.resolve("dst/deep/b.txt").toFile()

        assertTrue(LocalFileOps.move(src.path, dst.path))

        assertFalse("搬完源不该还在", src.exists())
        assertTrue("目标的父目录要被自动建出来", dst.exists())
        assertEquals("hello", dst.readText())
    }

    @Test
    fun `move 绝不覆盖已存在的目标`() {
        val src = file("a.txt", "new")
        val dst = file("b.txt", "old")

        assertFalse(LocalFileOps.move(src.path, dst.path))

        assertTrue("失败时源必须原地不动", src.exists())
        assertEquals("new", src.readText())
        assertEquals("old", dst.readText()) // 目标一个字节都不该变
    }

    @Test
    fun `move 源不存在时返回 false 且不造出目标`() {
        val dst = root.resolve("b.txt").toFile()
        assertFalse(LocalFileOps.move(root.resolve("a.txt").toString(), dst.path))
        assertFalse(dst.exists())
    }

    // ---------- copy ----------

    @Test
    fun `copy 成功后源还在、目标大小一致`() {
        val src = file("a.txt", "hello")
        val dst = root.resolve("out/b.txt").toFile()

        assertTrue(LocalFileOps.copy(src.path, dst.path))

        assertTrue("复制不该动源", src.exists())
        assertEquals(src.length(), dst.length())
        assertEquals("hello", dst.readText())
    }

    @Test
    fun `copy 目标已存在时返回 false 且保留原目标`() {
        val src = file("a.txt", "new")
        val dst = file("b.txt", "old")

        assertFalse(LocalFileOps.copy(src.path, dst.path))

        assertEquals("old", dst.readText())
        assertEquals("new", src.readText())
    }

    @Test
    fun `copy 源不存在或源是目录时返回 false`() {
        val dst = root.resolve("dst.txt").toFile()
        assertFalse(LocalFileOps.copy(root.resolve("nope.txt").toString(), dst.path))
        assertFalse(LocalFileOps.copy(dir("folder").path, dst.path))
        assertFalse(dst.exists())
    }

    // ---------- 基础操作 ----------

    @Test
    fun `mkdirs 能建多层目录，exists 能查到`() {
        val d = root.resolve("a/b/c").toFile()
        LocalFileOps.mkdirs(d.path)
        assertTrue(d.isDirectory)
        assertTrue(LocalFileOps.exists(d.path))
    }

    @Test
    fun `exists 对不存在的路径返回 false`() {
        assertFalse(LocalFileOps.exists(root.resolve("nope/also-nope").toString()))
    }

    @Test
    fun `stat 给出大小、mtime 与类型`() {
        val f = file("a.txt", "12345")
        f.setLastModified(1_700_000_000_000L)

        val s = LocalFileOps.stat(f.path) ?: error("stat 不该返回 null")

        assertEquals("a.txt", s.name)
        assertEquals(f.absolutePath, s.path)
        assertEquals(5L, s.size)
        assertEquals(f.lastModified(), s.mtimeMillis)
        assertTrue("mtime 不该是 0", s.mtimeMillis > 0)
        assertFalse(s.isDirectory)
        assertFalse(s.isSymlink)
    }

    @Test
    fun `stat 目录时大小是 0 且标记为目录`() {
        val d = dir("folder")
        val s = LocalFileOps.stat(d.path) ?: error("stat 不该返回 null")
        assertTrue(s.isDirectory)
        assertEquals(0L, s.size)
    }

    @Test
    fun `stat 不存在的路径返回 null`() {
        assertNull(LocalFileOps.stat(root.resolve("nope").toString()))
    }

    @Test
    fun `delete 删掉文件返回 true，再删返回 false`() {
        val f = file("a.txt")
        assertTrue(LocalFileOps.delete(f.path))
        assertFalse(f.exists())
        assertFalse(LocalFileOps.delete(f.path))
    }

    @Test
    fun `delete 不递归删除非空目录`() {
        file("d/a.txt")
        // 与 File.delete 一致：目录里有东西就删不掉，调用方别指望它递归
        assertFalse(LocalFileOps.delete(root.resolve("d").toString()))
        assertTrue(root.resolve("d/a.txt").toFile().exists())
    }

    // ---------- contentName ----------

    @Test
    fun `contentName 对同一内容稳定`() {
        val a = file("x/a.jpg", "same-bytes")
        val b = file("y/b.jpg", "same-bytes")

        val na = LocalFileOps.contentName(a.path)
        val nb = LocalFileOps.contentName(b.path)

        assertEquals("按内容命名必须是 15 位", 15, na.length)
        assertEquals("同样的内容必须得到同样的名字", na, nb)
    }

    @Test
    fun `contentName 对不同内容不同`() {
        val a = file("a.jpg", "content-a")
        val b = file("b.jpg", "content-b")
        assertNotEquals(
            LocalFileOps.contentName(a.path),
            LocalFileOps.contentName(b.path),
        )
    }

    @Test
    fun `contentName 目录或不存在时返回空串`() {
        assertEquals("", LocalFileOps.contentName(dir("folder").path))
        assertEquals("", LocalFileOps.contentName(root.resolve("nope.jpg").toString()))
    }
}
