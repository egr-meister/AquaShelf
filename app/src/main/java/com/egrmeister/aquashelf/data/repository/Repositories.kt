package com.egrmeister.aquashelf.data.repository

import androidx.room.withTransaction
import com.egrmeister.aquashelf.data.local.AppDatabase
import com.egrmeister.aquashelf.data.local.PreferencesStore
import com.egrmeister.aquashelf.data.local.toDomain
import com.egrmeister.aquashelf.data.local.toEntity
import com.egrmeister.aquashelf.domain.entries.EntryService
import com.egrmeister.aquashelf.domain.entries.EntryStore
import com.egrmeister.aquashelf.domain.entries.WaterEntry
import com.egrmeister.aquashelf.domain.history.DateRange
import com.egrmeister.aquashelf.domain.presets.Preset
import com.egrmeister.aquashelf.domain.presets.PresetService
import com.egrmeister.aquashelf.domain.presets.PresetStore
import com.egrmeister.aquashelf.domain.presets.StarterPresets
import com.egrmeister.aquashelf.domain.time.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/** Room-backed [EntryStore]. All Room calls are suspend/Flow, so nothing runs on the main thread. */
class RoomEntryStore(private val db: AppDatabase) : EntryStore {
    private val dao = db.entries()

    override suspend fun <R> inTransaction(block: suspend () -> R): R = db.withTransaction { block() }
    override suspend fun insert(entry: WaterEntry): Long = dao.insert(entry.toEntity().copy(id = 0))
    override suspend fun update(entry: WaterEntry): Int = dao.update(entry.toEntity())
    override suspend fun deleteById(id: Long): Int = dao.deleteById(id)
    override suspend fun byId(id: Long): WaterEntry? = dao.byId(id)?.toDomain()
    override suspend fun latestCreatedOn(date: LocalDate): WaterEntry? = dao.latestCreatedOn(date.toString())?.toDomain()
    override suspend fun latestCreatedAt(): Instant? = dao.latestCreatedAt()?.let { Instant.ofEpochMilli(it) }
    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}

class RoomPresetStore(private val db: AppDatabase) : PresetStore {
    private val dao = db.presets()

    override suspend fun <R> inTransaction(block: suspend () -> R): R = db.withTransaction { block() }
    override suspend fun all(): List<Preset> = dao.all().map { it.toDomain() }
    override suspend fun insert(preset: Preset): Long = dao.insert(preset.toEntity().copy(id = 0))
    override suspend fun update(preset: Preset): Int = dao.update(preset.toEntity())
    override suspend fun deleteById(id: Long): Int = dao.deleteById(id)
    override suspend fun setOrder(id: Long, order: Int): Int = dao.setOrder(id, order)
    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}

class WaterRepository(private val db: AppDatabase, time: TimeSource) {
    private val store = RoomEntryStore(db)
    val service = EntryService(store, time)

    fun observeDay(date: LocalDate): Flow<List<WaterEntry>> =
        db.entries().observeDay(date.toString()).map { list -> list.map { it.toDomain() } }

    fun observeRange(range: DateRange): Flow<List<WaterEntry>> =
        db.entries().observeRange(range.start.toString(), range.end.toString()).map { list -> list.map { it.toDomain() } }

    suspend fun entry(id: Long): WaterEntry? = store.byId(id)
}

class PresetRepository(private val db: AppDatabase) {
    private val store = RoomPresetStore(db)
    val service = PresetService(store)

    fun observe(): Flow<List<Preset>> = db.presets().observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun preset(id: Long): Preset? = store.all().firstOrNull { it.id == id }

    /** First launch only: three starter presets. Deleting all presets later does not bring them back. */
    suspend fun seedOnFirstLaunch(prefs: PreferencesStore) {
        if (prefs.current().starterPresetsSeeded) return
        service.seedIfEmpty()
        prefs.markStarterPresetsSeeded()
    }
}

/** Clear All Data: records and presets are reset in ONE Room transaction. */
suspend fun AppDatabase.resetAllRecords() {
    val db = this
    db.withTransaction {
        db.entries().deleteAll()
        db.presets().deleteAll()
        StarterPresets.ALL.forEach { db.presets().insert(it.toEntity().copy(id = 0)) }
    }
}
