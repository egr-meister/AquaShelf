package com.egrmeister.aquashelf.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [WaterEntryEntity::class, ContainerPresetEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entries(): WaterEntryDao
    abstract fun presets(): PresetDao

    companion object {
        const val NAME = "aquashelf.db"

        /**
         * Explicit migrations only. For schema changes: bump [version], add a `Migration(n, n + 1)`
         * here and commit the new exported schema from app/schemas. No destructive fallback is
         * configured, so a missing migration fails loudly instead of wiping user records.
         */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
