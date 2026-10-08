package com.egrmeister.aquashelf.domain

import com.egrmeister.aquashelf.FakeEntryStore
import com.egrmeister.aquashelf.FakePresetStore
import com.egrmeister.aquashelf.FakeTime
import com.egrmeister.aquashelf.domain.entries.ActionGuard
import com.egrmeister.aquashelf.domain.entries.ContainerType
import com.egrmeister.aquashelf.domain.entries.EditResult
import com.egrmeister.aquashelf.domain.entries.EntryService
import com.egrmeister.aquashelf.domain.presets.PresetResult
import com.egrmeister.aquashelf.domain.presets.PresetService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class EntriesTest {
    private val zone = ZoneId.of("Europe/Minsk")
    private val time = FakeTime(LocalDateTime.of(2026, 10, 8, 14, 30).atZone(zone).toInstant(), zone)
    private val store = FakeEntryStore()
    private val service = EntryService(store, time)
    private val today = LocalDate.of(2026, 10, 8)

    private fun at(date: LocalDate, h: Int, m: Int) = LocalDateTime.of(date, LocalTime.of(h, m)).atZone(zone)

    @Test
    fun logNow_createsOneEntryAtCurrentLocalDateAndTime() = runTest {
        val e = service.logNow(250, ContainerType.GLASS, "Glass")
        assertEquals(today, e.localDate)
        assertEquals(LocalTime.of(14, 30), e.localTime)
        assertEquals("Europe/Minsk", e.zoneId)
        assertEquals("Glass", e.presetNameSnapshot)
        assertEquals(1, store.rows.size)
    }

    @Test
    fun actionGuard_blocksDuplicateActivation_butAllowsIntentionalRepeats() {
        var clock = 0L
        val guard = ActionGuard(clockMs = { clock })
        assertTrue(guard.tryAcquire("log-preset-1"))
        assertFalse("same key while in flight", guard.tryAcquire("log-preset-1"))
        guard.release("log-preset-1")
        clock += 100
        assertFalse("double-delivered tap inside window", guard.tryAcquire("log-preset-1"))
        assertTrue("different preset is a separate action", guard.tryAcquire("log-preset-2"))
        clock += 1_000
        assertTrue("intentional separate entry later", guard.tryAcquire("log-preset-1"))
    }

    @Test
    fun separateIntentionalLogs_createSeparateEntries() = runTest {
        service.logNow(500, ContainerType.BOTTLE, "Bottle")
        time.advanceSeconds(5)
        service.logNow(500, ContainerType.BOTTLE, "Bottle")
        assertEquals(2, store.on(today).size)
    }

    @Test
    fun presetChanges_doNotAlterEarlierEntrySnapshots() = runTest {
        val presets = PresetService(FakePresetStore())
        val saved = presets.add("Glass", ContainerType.GLASS, "250") as PresetResult.Saved
        val p = saved.preset
        val first = service.logNow(p.amountMl, p.type, p.name)

        presets.update(p.id, "Big mug", ContainerType.BOTTLE, "400")
        presets.delete(p.id)

        val stored = store.byId(first.id)!!
        assertEquals(250, stored.amountMl)
        assertEquals(ContainerType.GLASS, stored.type)
        assertEquals("Glass", stored.presetNameSnapshot)
    }

    @Test
    fun undo_removesLatestByCreationOrder_notByDrinkingTime() = runTest {
        val a = service.addManual(200, ContainerType.GLASS, null, at(today, 9, 0))
        time.advanceSeconds(60)
        val b = service.addManual(300, ContainerType.GLASS, null, at(today, 12, 0))
        time.advanceSeconds(60)
        // Backdated entry logged LAST, with the EARLIEST drinking time.
        val c = service.addManual(150, ContainerType.CUSTOM, null, at(today, 7, 0))

        assertEquals(c.id, service.undoLast(today)?.id)
        assertEquals(b.id, service.undoLast(today)?.id)
        assertEquals(a.id, service.undoLast(today)?.id)
        assertNull("empty shelf: nothing to undo", service.undoLast(today))
    }

    @Test
    fun undo_withExpectedId_cannotRemoveTwoOnRepeatedTap() = runTest {
        service.addManual(200, ContainerType.GLASS, null, at(today, 9, 0))
        time.advanceSeconds(1)
        val latest = service.addManual(300, ContainerType.GLASS, null, at(today, 10, 0))
        val results = listOf(
            async { service.undoLast(today, expectedId = latest.id) },
            async { service.undoLast(today, expectedId = latest.id) },
        ).awaitAll()
        assertEquals(1, results.count { it != null })
        assertEquals(1, store.on(today).size)
    }

    @Test
    fun undo_onlyAffectsDisplayedDay() = runTest {
        val yesterday = today.minusDays(1)
        val old = service.addManual(250, ContainerType.GLASS, null, at(yesterday, 20, 0))
        time.advanceSeconds(1)
        service.addManual(500, ContainerType.BOTTLE, null, at(today, 8, 0))
        assertEquals(old.id, service.undoLast(yesterday)?.id)
        assertEquals(1, store.on(today).size)
    }

    @Test
    fun delete_removesThatSpecificEntryOnlyOnce() = runTest {
        val a = service.addManual(200, ContainerType.GLASS, null, at(today, 9, 0))
        val b = service.addManual(300, ContainerType.GLASS, null, at(today, 10, 0))
        val results = listOf(async { service.delete(a.id) }, async { service.delete(a.id) }).awaitAll()
        assertEquals(1, results.count { it != null })
        assertNull(store.byId(a.id))
        assertNotNull(store.byId(b.id))
    }

    @Test
    fun edit_movingToAnotherDate_updatesBothShelves() = runTest {
        val e = service.addManual(250, ContainerType.GLASS, "Glass", at(today, 9, 0))
        val target = today.minusDays(2)
        val r = service.edit(e.id, 330, ContainerType.GLASS, at(target, 18, 45)) as EditResult.Updated
        assertEquals(target, r.after.localDate)
        assertTrue(store.on(today).isEmpty())
        assertEquals(listOf(e.id), store.on(target).map { it.id })
        assertEquals("Glass", r.after.presetNameSnapshot)
        assertEquals(e.createdAt, r.after.createdAt)
    }

    @Test
    fun edit_convertingToCustom_dropsPresetSnapshot() = runTest {
        val e = service.addManual(250, ContainerType.GLASS, "Glass", at(today, 9, 0))
        val r = service.edit(e.id, 250, ContainerType.CUSTOM, at(today, 9, 0)) as EditResult.Updated
        assertNull(r.after.presetNameSnapshot)
        assertEquals(EditResult.NotFound, service.edit(999, 100, ContainerType.GLASS, at(today, 9, 0)))
    }

    @Test
    fun latestLoggedAt_usesCreationTime() = runTest {
        assertNull(service.latestLoggedAt())
        service.addManual(100, ContainerType.GLASS, null, at(today.minusDays(3), 9, 0))
        assertEquals(time.now(), service.latestLoggedAt())
        assertEquals(Instant.parse("2026-10-08T11:30:00Z"), service.latestLoggedAt())
    }
}
