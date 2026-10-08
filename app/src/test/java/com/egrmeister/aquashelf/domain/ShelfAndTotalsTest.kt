package com.egrmeister.aquashelf.domain

import com.egrmeister.aquashelf.domain.entries.ContainerType
import com.egrmeister.aquashelf.domain.entries.DayTotals
import com.egrmeister.aquashelf.domain.entries.ShelfOrder
import com.egrmeister.aquashelf.domain.entries.WaterEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

fun entry(
    id: Long,
    amount: Int,
    type: ContainerType,
    time: LocalTime,
    createdSecond: Long = id,
    date: LocalDate = LocalDate.of(2026, 10, 8),
    preset: String? = null,
) = WaterEntry(
    id = id,
    amountMl = amount,
    type = type,
    presetNameSnapshot = preset,
    timestamp = Instant.EPOCH,
    localDate = date,
    localTime = time,
    zoneId = "UTC",
    createdAt = Instant.ofEpochSecond(1_000 + createdSecond),
    updatedAt = Instant.ofEpochSecond(1_000 + createdSecond),
)

class ShelfAndTotalsTest {

    @Test
    fun mixedContainers_countBottlesAsBottles_andUseSnapshotAmounts() {
        val day = listOf(
            entry(1, 200, ContainerType.GLASS, LocalTime.of(8, 0)),
            entry(2, 200, ContainerType.GLASS, LocalTime.of(10, 0)),
            entry(3, 500, ContainerType.BOTTLE, LocalTime.of(12, 0)),
        )
        val t = DayTotals.of(day)
        assertEquals("2 glasses · 1 bottle", t.breakdown)
        assertEquals("900 mL recorded", t.volumeLabel)
        assertEquals(3, t.entries)
        assertEquals("3 entries", t.entriesLabel)
        assertEquals(1, t.bottles)
    }

    @Test
    fun customEntries_areCountedSeparately_andTotalsUseLong() {
        val many = (1L..3_000L).map { entry(it, 5_000, ContainerType.CUSTOM, LocalTime.of(12, 0)) }
        val t = DayTotals.of(many + entry(9_999, 150, ContainerType.GLASS, LocalTime.NOON))
        assertEquals(15_000_150L, t.volumeMl)
        assertEquals("1 glass · 3000 custom", t.breakdown)
        assertEquals("15,000,150 mL recorded", t.volumeLabel)
        assertEquals("", DayTotals.EMPTY.breakdown)
    }

    @Test
    fun shelf_isChronologicalByLoggedTime_thenCreationOrder() {
        val late = entry(1, 250, ContainerType.GLASS, LocalTime.of(18, 0), createdSecond = 1)
        val early = entry(2, 250, ContainerType.GLASS, LocalTime.of(7, 0), createdSecond = 2)
        val tieB = entry(4, 500, ContainerType.BOTTLE, LocalTime.of(12, 0), createdSecond = 4)
        val tieA = entry(3, 150, ContainerType.GLASS, LocalTime.of(12, 0), createdSecond = 3)
        assertEquals(listOf(2L, 3L, 4L, 1L), ShelfOrder.arrange(listOf(late, early, tieB, tieA)).map { it.id })
        assertEquals(early.id, ShelfOrder.arrange(listOf(late, early)).first().id)
        assertEquals(tieB.id, ShelfOrder.latestLogged(listOf(late, early, tieB, tieA))?.id)
    }

    @Test
    fun shelves_fillLeftToRight_neverDropEntries() {
        val entries = (1L..11L).map { entry(it, 100, ContainerType.GLASS, LocalTime.of(6, 0).plusMinutes(it)) }
        val rows = ShelfOrder.shelves(entries.shuffled(), perShelf = 4)
        assertEquals(listOf(4, 4, 3), rows.map { it.size })
        assertEquals((1L..11L).toList(), rows.flatten().map { it.id })
    }

    @Test
    fun widerScreens_holdMoreObjectsPerShelf_withReadableMinimum() {
        assertEquals(4, ShelfOrder.perShelf(340f))
        assertTrue(ShelfOrder.perShelf(900f) > ShelfOrder.perShelf(340f))
        assertEquals(3, ShelfOrder.perShelf(120f))
        assertEquals(12, ShelfOrder.perShelf(5_000f))
    }

    @Test
    fun editedTime_reordersEntryOnItsShelf() {
        val a = entry(1, 250, ContainerType.GLASS, LocalTime.of(9, 0))
        val b = entry(2, 250, ContainerType.GLASS, LocalTime.of(10, 0))
        val movedA = a.copy(localTime = LocalTime.of(11, 0))
        assertEquals(listOf(2L, 1L), ShelfOrder.arrange(listOf(movedA, b)).map { it.id })
    }
}
