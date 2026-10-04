package com.landslide.shitu.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

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
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun itemDao(): ItemDao
    abstract fun logDao(): LogDao

    companion object {
        const val NAME = "shitu.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
