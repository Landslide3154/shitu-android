package com.landslide.shitu.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter fun modeToString(v: Mode) = v.name
    @TypeConverter fun stringToMode(s: String) = Mode.valueOf(s)

    @TypeConverter fun ruleStateToString(v: RuleState) = v.name
    @TypeConverter fun stringToRuleState(s: String) = RuleState.valueOf(s)

    @TypeConverter fun itemStatusToString(v: ItemStatus) = v.name
    @TypeConverter fun stringToItemStatus(s: String) = ItemStatus.valueOf(s)

    @TypeConverter fun logResultToString(v: LogResult) = v.name
    @TypeConverter fun stringToLogResult(s: String) = LogResult.valueOf(s)
}

@Database(
    entities = [RuleEntity::class, ItemEntity::class, LogEntity::class],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun itemDao(): ItemDao
    abstract fun logDao(): LogDao

    companion object {
        const val NAME = "shitu.db"

        /**
         * 2 → 3：规则表加「按内容命名」开关。
         * 老规则一律写成 0（关闭）——升级后不会有人被悄悄改名；新规则由 Kotlin 默认值打开。
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rules ADD COLUMN contentRename INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
