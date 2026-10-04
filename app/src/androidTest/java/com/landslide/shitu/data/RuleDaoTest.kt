package com.landslide.shitu.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.landslide.shitu.data.db.AppDatabase
import com.landslide.shitu.data.db.ItemEntity
import com.landslide.shitu.data.db.ItemStatus
import com.landslide.shitu.data.db.LogEntity
import com.landslide.shitu.data.db.LogResult
import com.landslide.shitu.data.db.Mode
import com.landslide.shitu.data.db.RuleEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room 三表的真机测试（对应实现计划任务 4；规格 §7）。
 * 放在 androidTest：用真机上的 SQLite，比 Robolectric 更接近实际。
 */
@RunWith(AndroidJUnit4::class)
class RuleDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndQueryEnabledRules() = runBlocking {
        val id = db.ruleDao().insert(
            RuleEntity(
                name = "r1", srcPath = "/sdcard/Android/data/com.a/files",
                dstPath = "/sdcard/Pictures/拾图", mode = Mode.MOVE, intervalMinutes = 5,
                extensions = "jpg,png", createdAt = 1, updatedAt = 1,
            ),
        )
        assertEquals(1, db.ruleDao().enabledRules().size)
        assertEquals("r1", db.ruleDao().byId(id)!!.name)
        assertNotNull(db.ruleDao().all())
    }

    @Test
    fun itemUniquePerRuleAndSrcPath() = runBlocking {
        val dao = db.itemDao()
        val e = ItemEntity(
            ruleId = 1, srcPath = "/a/x.png", srcSize = 10, srcMtime = 100,
            dstPath = null, status = ItemStatus.PENDING,
        )
        dao.upsert(e)
        dao.upsert(e.copy(id = 0))
        assertEquals(1, dao.countForRule(1))
    }

    @Test
    fun pruneLogsBeforeTimestamp() = runBlocking {
        val dao = db.logDao()
        repeat(10) {
            dao.insert(
                LogEntity(
                    ts = it.toLong(), ruleId = 1, srcPath = null, dstPath = null,
                    result = LogResult.INFO, durationMs = 0, message = null,
                ),
            )
        }
        dao.pruneBefore(5)
        assertEquals(5, dao.count())
    }

    @Test
    fun filteredLogsByResultAndRule() = runBlocking {
        val dao = db.logDao()
        dao.insert(LogEntity(ts = 1, ruleId = 1, srcPath = "/a", dstPath = "/b", result = LogResult.MOVED, durationMs = 1, message = null))
        dao.insert(LogEntity(ts = 2, ruleId = 1, srcPath = "/c", dstPath = null, result = LogResult.FAILED, durationMs = 1, message = "x"))
        dao.insert(LogEntity(ts = 3, ruleId = 2, srcPath = "/d", dstPath = null, result = LogResult.INFO, durationMs = 1, message = null))

        assertEquals(1, dao.filtered(10, null, "MOVED").size)
        assertEquals(2, dao.filtered(10, 1, null).size)
        assertEquals(3, dao.filtered(10, null, null).size)
    }

    @Test
    fun recentDoneForRuleUsesStatusFilter() = runBlocking {
        val dao = db.itemDao()
        dao.upsert(ItemEntity(ruleId = 7, srcPath = "/a", srcSize = 1, srcMtime = 1, dstPath = "/d/a", status = ItemStatus.MOVED, processedAt = 10))
        dao.upsert(ItemEntity(ruleId = 7, srcPath = "/b", srcSize = 1, srcMtime = 1, dstPath = null, status = ItemStatus.FAILED, processedAt = 20))
        assertEquals(1, dao.recentDoneForRule(7, 50).size)
    }
}
