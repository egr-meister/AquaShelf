package com.egrmeister.aquashelf.domain.entries

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface AmountResult {
    data class Valid(val amountMl: Int) : AmountResult
    data class Invalid(val message: String) : AmountResult
}

sealed interface EntryTimeResult {
    data class Valid(val zoned: ZonedDateTime) : EntryTimeResult
    data class Invalid(val message: String) : EntryTimeResult
}

object EntryValidator {

    fun parseAmount(text: String): AmountResult {
        val trimmed = text.trim().replace(",", "")
        if (trimmed.isEmpty()) return AmountResult.Invalid("Enter an amount in milliliters.")
        if (!trimmed.all { it.isDigit() }) return AmountResult.Invalid("Use whole milliliters only.")
        val value = trimmed.toLongOrNull()
        if (value == null || value < Limits.AMOUNT_MIN || value > Limits.AMOUNT_MAX) {
            return AmountResult.Invalid("Amount must be between ${Limits.AMOUNT_MIN} and 5,000 mL.")
        }
        return AmountResult.Valid(value.toInt())
    }

    /**
     * Resolves a user-chosen local date + time in [zone].
     * - Dates after today and instants after [now] are rejected.
     * - A time inside a daylight-saving gap does not exist and is rejected with an explanation.
     * - A time in a daylight-saving overlap consistently uses the EARLIER offset.
     */
    fun resolve(date: LocalDate, time: LocalTime, zone: ZoneId, now: Instant): EntryTimeResult {
        val today = now.atZone(zone).toLocalDate()
        if (date.isAfter(today)) return EntryTimeResult.Invalid("Future dates can't be recorded. Choose today or an earlier date.")
        val local = LocalDateTime.of(date, time.withNano(0))
        if (zone.rules.getValidOffsets(local).isEmpty()) {
            return EntryTimeResult.Invalid(
                "${time.format(TIME)} doesn't exist on ${date.format(DATE)} because the clocks moved forward. " +
                    "Choose a different time.",
            )
        }
        val zoned = ZonedDateTime.ofLocal(local, zone, null) // null preference → earlier offset in an overlap
        if (zoned.toInstant().isAfter(now)) {
            return EntryTimeResult.Invalid("That time is in the future. Choose a time up to now.")
        }
        return EntryTimeResult.Valid(zoned)
    }

    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US)
}
