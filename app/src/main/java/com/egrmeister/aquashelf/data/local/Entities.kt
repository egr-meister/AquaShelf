package com.egrmeister.aquashelf.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.egrmeister.aquashelf.domain.entries.ContainerType
import com.egrmeister.aquashelf.domain.entries.WaterEntry
import com.egrmeister.aquashelf.domain.presets.Preset
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One saved water record. Dates and times are stored as ISO strings
 * (yyyy-MM-dd / HH:mm:ss), which sort correctly as text and survive time-zone changes.
 */
@Entity(
    tableName = "water_entries",
    indices = [
        Index(value = ["loggedLocalDate", "loggedLocalTime"]),
        Index(value = ["loggedLocalDate", "createdAt"]),
        Index(value = ["createdAt"]),
    ],
)
data class WaterEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMl: Int,
    val containerType: String,
    val presetNameSnapshot: String?,
    /** Absolute instant, epoch milliseconds. */
    val timestamp: Long,
    val loggedLocalDate: String,
    val loggedLocalTime: String,
    val zoneId: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "container_presets",
    indices = [Index(value = ["displayOrder"])],
)
data class ContainerPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val containerType: String,
    val amountMl: Int,
    val displayOrder: Int,
)

private val LOCAL_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)

fun WaterEntryEntity.toDomain(): WaterEntry = WaterEntry(
    id = id,
    amountMl = amountMl,
    type = ContainerType.fromStorage(containerType),
    presetNameSnapshot = presetNameSnapshot,
    timestamp = Instant.ofEpochMilli(timestamp),
    localDate = LocalDate.parse(loggedLocalDate),
    localTime = LocalTime.parse(loggedLocalTime),
    zoneId = zoneId,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun WaterEntry.toEntity(): WaterEntryEntity = WaterEntryEntity(
    id = id,
    amountMl = amountMl,
    containerType = type.name,
    presetNameSnapshot = presetNameSnapshot,
    timestamp = timestamp.toEpochMilli(),
    loggedLocalDate = localDate.toString(),
    loggedLocalTime = localTime.withNano(0).format(LOCAL_TIME),
    zoneId = zoneId,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

fun ContainerPresetEntity.toDomain(): Preset = Preset(
    id = id,
    name = name,
    type = ContainerType.fromStorage(containerType),
    amountMl = amountMl,
    displayOrder = displayOrder,
)

fun Preset.toEntity(): ContainerPresetEntity = ContainerPresetEntity(
    id = id,
    name = name,
    containerType = type.name,
    amountMl = amountMl,
    displayOrder = displayOrder,
)
