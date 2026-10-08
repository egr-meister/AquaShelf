package com.egrmeister.aquashelf.domain.presets

import com.egrmeister.aquashelf.domain.entries.ContainerType
import com.egrmeister.aquashelf.domain.entries.Limits

/** A quick-log button. Editing a preset only affects future entries (entries store snapshots). */
data class Preset(
    val id: Long,
    val name: String,
    val type: ContainerType,
    val amountMl: Int,
    val displayOrder: Int,
)

object StarterPresets {
    val ALL: List<Preset> = listOf(
        Preset(id = 0, name = "Small glass", type = ContainerType.GLASS, amountMl = 150, displayOrder = 0),
        Preset(id = 0, name = "Glass", type = ContainerType.GLASS, amountMl = 250, displayOrder = 1),
        Preset(id = 0, name = "Bottle", type = ContainerType.BOTTLE, amountMl = 500, displayOrder = 2),
    )
}

enum class PresetError(val message: String) {
    NAME_BLANK("Enter a name."),
    NAME_TOO_LONG("Use at most 24 characters."),
    NAME_DUPLICATE("A preset with this name already exists."),
    AMOUNT_INVALID("Amount must be a whole number from 1 to 5,000 mL."),
    TYPE_INVALID("Presets are a Glass or a Bottle."),
    LIMIT_REACHED("You can keep up to 8 presets."),
    NOT_FOUND("This preset no longer exists."),
}

sealed interface PresetCheck {
    data class Valid(val name: String, val type: ContainerType, val amountMl: Int) : PresetCheck
    data class Invalid(val errors: List<PresetError>) : PresetCheck
}

object PresetValidator {
    fun check(
        name: String,
        type: ContainerType,
        amountText: String,
        existing: List<Preset>,
        editingId: Long?,
    ): PresetCheck {
        val errors = mutableListOf<PresetError>()
        val trimmed = name.trim()
        when {
            trimmed.isEmpty() -> errors += PresetError.NAME_BLANK
            trimmed.length > Limits.PRESET_NAME_MAX -> errors += PresetError.NAME_TOO_LONG
            existing.any { it.id != editingId && it.name.trim().equals(trimmed, ignoreCase = true) } ->
                errors += PresetError.NAME_DUPLICATE
        }
        if (type == ContainerType.CUSTOM) errors += PresetError.TYPE_INVALID
        val raw = amountText.trim().replace(",", "")
        val amount = raw.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toLongOrNull()
        if (amount == null || amount < Limits.AMOUNT_MIN || amount > Limits.AMOUNT_MAX) errors += PresetError.AMOUNT_INVALID
        if (editingId == null && existing.size >= Limits.MAX_PRESETS) errors += PresetError.LIMIT_REACHED
        return if (errors.isEmpty()) PresetCheck.Valid(trimmed, type, amount!!.toInt()) else PresetCheck.Invalid(errors)
    }

    /** Swaps [id] with its neighbour and returns the renumbered list (0..n-1), or null if it can't move. */
    fun moved(presets: List<Preset>, id: Long, up: Boolean): List<Preset>? {
        val ordered = presets.sortedWith(compareBy({ it.displayOrder }, { it.id })).toMutableList()
        val index = ordered.indexOfFirst { it.id == id }
        if (index < 0) return null
        val target = if (up) index - 1 else index + 1
        if (target !in ordered.indices) return null
        val tmp = ordered[index]
        ordered[index] = ordered[target]
        ordered[target] = tmp
        return ordered.mapIndexed { i, p -> p.copy(displayOrder = i) }
    }
}

interface PresetStore {
    suspend fun <R> inTransaction(block: suspend () -> R): R
    suspend fun all(): List<Preset>
    suspend fun insert(preset: Preset): Long
    suspend fun update(preset: Preset): Int
    suspend fun deleteById(id: Long): Int
    suspend fun setOrder(id: Long, order: Int): Int
    suspend fun deleteAll()
}

sealed interface PresetResult {
    data class Saved(val preset: Preset) : PresetResult
    data class Rejected(val errors: List<PresetError>) : PresetResult
}

class PresetService(private val store: PresetStore) {

    suspend fun add(name: String, type: ContainerType, amountText: String): PresetResult = store.inTransaction {
        val existing = store.all()
        when (val c = PresetValidator.check(name, type, amountText, existing, editingId = null)) {
            is PresetCheck.Invalid -> PresetResult.Rejected(c.errors)
            is PresetCheck.Valid -> {
                val order = (existing.maxOfOrNull { it.displayOrder } ?: -1) + 1
                val draft = Preset(0, c.name, c.type, c.amountMl, order)
                PresetResult.Saved(draft.copy(id = store.insert(draft)))
            }
        }
    }

    suspend fun update(id: Long, name: String, type: ContainerType, amountText: String): PresetResult = store.inTransaction {
        val existing = store.all()
        val current = existing.firstOrNull { it.id == id }
            ?: return@inTransaction PresetResult.Rejected(listOf(PresetError.NOT_FOUND))
        when (val c = PresetValidator.check(name, type, amountText, existing, editingId = id)) {
            is PresetCheck.Invalid -> PresetResult.Rejected(c.errors)
            is PresetCheck.Valid -> {
                val updated = current.copy(name = c.name, type = c.type, amountMl = c.amountMl)
                store.update(updated)
                PresetResult.Saved(updated)
            }
        }
    }

    /** Earlier entries keep their own snapshots, so deleting a preset never deletes history. */
    suspend fun delete(id: Long): Boolean = store.inTransaction {
        val removed = store.deleteById(id) == 1
        if (removed) {
            store.all().sortedWith(compareBy({ it.displayOrder }, { it.id }))
                .forEachIndexed { i, p -> if (p.displayOrder != i) store.setOrder(p.id, i) }
        }
        removed
    }

    suspend fun move(id: Long, up: Boolean): Boolean = store.inTransaction {
        val reordered = PresetValidator.moved(store.all(), id, up) ?: return@inTransaction false
        reordered.forEach { store.setOrder(it.id, it.displayOrder) }
        true
    }

    suspend fun resetToStarters() = store.inTransaction {
        store.deleteAll()
        StarterPresets.ALL.forEach { store.insert(it) }
    }

    /** Seeds the three starter presets exactly once, on first launch. */
    suspend fun seedIfEmpty(): Boolean = store.inTransaction {
        if (store.all().isNotEmpty()) return@inTransaction false
        StarterPresets.ALL.forEach { store.insert(it) }
        true
    }
}
