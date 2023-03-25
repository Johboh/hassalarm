package com.fjun.hassalarm.history

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.fjun.hassalarm.Constants

@Database(
    entities = [Publish::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration (from = 2, to = 3)]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun publishDao(): PublishDao

    companion object {
        // Singleton prevents multiple instances of database opening at the
        // same time.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        @JvmStatic
        fun getDatabase(context: Context): AppDatabase {
            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    Constants.PUBLISH_HISTORY_DB_NAME
                ).build()
                INSTANCE = instance
                // return instance
                instance
            }
        }
    }
}