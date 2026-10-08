package com.egrmeister.aquashelf.domain.entries

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Kind of container shown on the shelf. Custom entries use a neutral vessel icon. */
enum class ContainerType(val label: String, val singular: String, val plural: String) {
    GLASS("Glass", "glass", "glasses"),
    BOTTLE("Bottle", "bottle", "bottles"),
    CUSTOM("Custom", "custom", "custom");

    fun countLabel(count: Int): String = "$count ${if (count == 1) singular else plural}"

    companion object {
        fun fromStorage(raw: String): ContainerType = ContainerType.entries.firstOrNull { it.name == raw } ?: CUSTOM
    }
}

/**
 * One saved water record. Amount, type and preset name are snapshots taken at logging time,
 * so later preset edits never change history. [localDate]/[localTime]/[zoneId] are the
 * attribution used for grouping, so travelling does not silently move entries between days.
 */
data class WaterEntry(
    val id: Long,
    val amountMl: Int,
    val type: ContainerType,
    val presetNameSnapshot: String?,
    val timestamp: Instant,
    val localDate: LocalDate,
    val localTime: LocalTime,
    val zoneId: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Technical input constraints — not consumption guidance. */
object Limits {
    const val AMOUNT_MIN = 1
    const val AMOUNT_MAX = 5_000
    const val PRESET_NAME_MAX = 24
    const val MAX_PRESETS = 8
}
