package com.egrmeister.aquashelf.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterEntryDao {
    @Query(
        "SELECT * FROM water_entries WHERE loggedLocalDate = :date " +
            "ORDER BY loggedLocalTime ASC, createdAt ASC, id ASC",
    )
    fun observeDay(date: String): Flow<List<WaterEntryEntity>>

    @Query(
        "SELECT * FROM water_entries WHERE loggedLocalDate BETWEEN :from AND :to " +
            "ORDER BY loggedLocalDate ASC, loggedLocalTime ASC, createdAt ASC, id ASC",
    )
    fun observeRange(from: String, to: String): Flow<List<WaterEntryEntity>>

    @Query("SELECT * FROM water_entries WHERE id = :id")
    suspend fun byId(id: Long): WaterEntryEntity?

    @Query("SELECT * FROM water_entries WHERE loggedLocalDate = :date ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun latestCreatedOn(date: String): WaterEntryEntity?

    @Query("SELECT MAX(createdAt) FROM water_entries")
    suspend fun latestCreatedAt(): Long?

    @Insert
    suspend fun insert(entry: WaterEntryEntity): Long

    @Update
    suspend fun update(entry: WaterEntryEntity): Int

    @Query("DELETE FROM water_entries WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM water_entries")
    suspend fun deleteAll(): Int
}

@Dao
interface PresetDao {
    @Query("SELECT * FROM container_presets ORDER BY displayOrder ASC, id ASC")
    fun observeAll(): Flow<List<ContainerPresetEntity>>

    @Query("SELECT * FROM container_presets ORDER BY displayOrder ASC, id ASC")
    suspend fun all(): List<ContainerPresetEntity>

    @Insert
    suspend fun insert(preset: ContainerPresetEntity): Long

    @Update
    suspend fun update(preset: ContainerPresetEntity): Int

    @Query("DELETE FROM container_presets WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("UPDATE container_presets SET displayOrder = :order WHERE id = :id")
    suspend fun setOrder(id: Long, order: Int): Int

    @Query("DELETE FROM container_presets")
    suspend fun deleteAll(): Int
}
