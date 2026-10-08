package com.egrmeister.aquashelf

import com.egrmeister.aquashelf.domain.entries.EntryStore
import com.egrmeister.aquashelf.domain.entries.WaterEntry
import com.egrmeister.aquashelf.domain.presets.Preset
import com.egrmeister.aquashelf.domain.presets.PresetStore
import com.egrmeister.aquashelf.domain.time.TimeSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Controllable clock and zone. */
class FakeTime(var instant: Instant, var zoneId: ZoneId = ZoneId.of("Europe/Minsk")) : TimeSource {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zoneId
    fun advanceSeconds(s: Long) {
        instant = instant.plusSeconds(s)
    }
}

/** In-memory EntryStore with Room-like semantics (auto IDs, row counts, serialized transactions). */
class FakeEntryStore : EntryStore {
    private val mutex = Mutex()
    private var nextId = 1L
    val rows = linkedMapOf<Long, WaterEntry>()

    override suspend fun <R> inTransaction(block: suspend () -> R): R = mutex.withLock { block() }
    override suspend fun insert(entry: WaterEntry): Long {
        val id = nextId++
        rows[id] = entry.copy(id = id)
        return id
    }
    override suspend fun update(entry: WaterEntry): Int = if (rows.containsKey(entry.id)) { rows[entry.id] = entry; 1 } else 0
    override suspend fun deleteById(id: Long): Int = if (rows.remove(id) != null) 1 else 0
    override suspend fun byId(id: Long): WaterEntry? = rows[id]
    override suspend fun latestCreatedOn(date: LocalDate): WaterEntry? =
        rows.values.filter { it.localDate == date }.maxWithOrNull(compareBy({ it.createdAt }, { it.id }))
    override suspend fun latestCreatedAt(): Instant? = rows.values.maxOfOrNull { it.createdAt }
    override suspend fun deleteAll() {
        rows.clear()
    }

    fun on(date: LocalDate): List<WaterEntry> = rows.values.filter { it.localDate == date }
}

class FakePresetStore : PresetStore {
    private val mutex = Mutex()
    private var nextId = 1L
    val rows = linkedMapOf<Long, Preset>()

    override suspend fun <R> inTransaction(block: suspend () -> R): R = mutex.withLock { block() }
    override suspend fun all(): List<Preset> = rows.values.sortedWith(compareBy({ it.displayOrder }, { it.id }))
    override suspend fun insert(preset: Preset): Long {
        val id = nextId++
        rows[id] = preset.copy(id = id)
        return id
    }
    override suspend fun update(preset: Preset): Int = if (rows.containsKey(preset.id)) { rows[preset.id] = preset; 1 } else 0
    override suspend fun deleteById(id: Long): Int = if (rows.remove(id) != null) 1 else 0
    override suspend fun setOrder(id: Long, order: Int): Int {
        val p = rows[id] ?: return 0
        rows[id] = p.copy(displayOrder = order)
        return 1
    }
    override suspend fun deleteAll() {
        rows.clear()
    }
}
