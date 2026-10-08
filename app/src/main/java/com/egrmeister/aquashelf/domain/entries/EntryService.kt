package com.egrmeister.aquashelf.domain.entries

import com.egrmeister.aquashelf.domain.time.TimeSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/** Persistence port for entries; Room in the app, an in-memory fake in unit tests. */
interface EntryStore {
    suspend fun <R> inTransaction(block: suspend () -> R): R
    suspend fun insert(entry: WaterEntry): Long
    suspend fun update(entry: WaterEntry): Int
    suspend fun deleteById(id: Long): Int
    suspend fun byId(id: Long): WaterEntry?
    /** Most recently CREATED entry attributed to [date] (creation order, not drinking time). */
    suspend fun latestCreatedOn(date: LocalDate): WaterEntry?
    suspend fun latestCreatedAt(): Instant?
    suspend fun deleteAll()
}

sealed interface EditResult {
    data class Updated(val before: WaterEntry, val after: WaterEntry) : EditResult
    data object NotFound : EditResult
}

/**
 * All entry mutations. Every write runs in a transaction; deletions report whether THIS call
 * removed the row, so simultaneous actions can never remove the same entry twice.
 */
class EntryService(private val store: EntryStore, private val time: TimeSource) {

    /** Quick logging: one entry at the current local date and time with a snapshot of the preset. */
    suspend fun logNow(amountMl: Int, type: ContainerType, presetName: String?): WaterEntry {
        val at = time.now().atZone(time.zone())
        return addManual(amountMl, type, presetName, at)
    }

    suspend fun addManual(amountMl: Int, type: ContainerType, presetName: String?, at: ZonedDateTime): WaterEntry {
        require(amountMl in Limits.AMOUNT_MIN..Limits.AMOUNT_MAX) { "amount out of range" }
        val now = time.now()
        val draft = WaterEntry(
            id = 0,
            amountMl = amountMl,
            type = type,
            presetNameSnapshot = presetName?.takeIf { type != ContainerType.CUSTOM },
            timestamp = at.toInstant(),
            localDate = at.toLocalDate(),
            localTime = at.toLocalTime().withNano(0),
            zoneId = at.zone.id,
            createdAt = now,
            updatedAt = now,
        )
        return store.inTransaction {
            val id = store.insert(draft)
            draft.copy(id = id)
        }
    }

    /**
     * Changes amount, type, date and time. The preset-name snapshot is kept unless the entry is
     * explicitly converted to a Custom record. Moving to another date updates both shelves because
     * shelves are derived from the stored local date.
     */
    suspend fun edit(id: Long, amountMl: Int, type: ContainerType, at: ZonedDateTime): EditResult {
        require(amountMl in Limits.AMOUNT_MIN..Limits.AMOUNT_MAX) { "amount out of range" }
        return store.inTransaction {
            val before = store.byId(id) ?: return@inTransaction EditResult.NotFound
            val after = before.copy(
                amountMl = amountMl,
                type = type,
                presetNameSnapshot = if (type == ContainerType.CUSTOM) null else before.presetNameSnapshot,
                timestamp = at.toInstant(),
                localDate = at.toLocalDate(),
                localTime = at.toLocalTime().withNano(0),
                zoneId = at.zone.id,
                updatedAt = time.now(),
            )
            if (store.update(after) == 1) EditResult.Updated(before, after) else EditResult.NotFound
        }
    }

    /**
     * Removes the most recently logged entry on [date] (creation order). When [expectedId] is given,
     * nothing is removed unless that entry is still the latest — a repeated tap cannot remove two.
     */
    suspend fun undoLast(date: LocalDate, expectedId: Long? = null): WaterEntry? = store.inTransaction {
        val latest = store.latestCreatedOn(date) ?: return@inTransaction null
        if (expectedId != null && latest.id != expectedId) return@inTransaction null
        if (store.deleteById(latest.id) == 1) latest else null
    }

    /** Deletes one specific entry. Returns it only if this call removed it. */
    suspend fun delete(id: Long): WaterEntry? = store.inTransaction {
        val existing = store.byId(id) ?: return@inTransaction null
        if (store.deleteById(id) == 1) existing else null
    }

    suspend fun latestLoggedAt(): Instant? = store.latestCreatedAt()
}

/**
 * Prevents one activation (e.g. a double-delivered tap) from being processed twice:
 * - while an action with the same key is in flight, repeats are ignored;
 * - repeats of the same key within [windowMs] are ignored.
 * Taps after the window are intentional, separate entries.
 */
class ActionGuard(private val clockMs: () -> Long, private val windowMs: Long = 400) {
    private val lastAccepted = HashMap<String, Long>()
    private val inFlight = HashSet<String>()

    @Synchronized
    fun tryAcquire(key: String): Boolean {
        if (key in inFlight) return false
        val now = clockMs()
        val last = lastAccepted[key]
        if (last != null && now - last < windowMs) return false
        inFlight += key
        lastAccepted[key] = now
        return true
    }

    @Synchronized
    fun release(key: String) {
        inFlight -= key
    }
}
