package com.egrmeister.aquashelf.domain

import com.egrmeister.aquashelf.FakePresetStore
import com.egrmeister.aquashelf.domain.entries.ContainerType
import com.egrmeister.aquashelf.domain.presets.PresetError
import com.egrmeister.aquashelf.domain.presets.PresetResult
import com.egrmeister.aquashelf.domain.presets.PresetService
import com.egrmeister.aquashelf.domain.presets.StarterPresets
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetsTest {
    private val store = FakePresetStore()
    private val service = PresetService(store)

    private fun errors(r: PresetResult) = (r as PresetResult.Rejected).errors

    @Test
    fun starterPresets_areSeededOnce() = runTest {
        assertTrue(service.seedIfEmpty())
        assertEquals(listOf("Small glass" to 150, "Glass" to 250, "Bottle" to 500), store.all().map { it.name to it.amountMl })
        assertFalse(service.seedIfEmpty())
        assertEquals(3, store.all().size)
        assertEquals(StarterPresets.ALL.size, store.all().size)
    }

    @Test
    fun validation_rejectsBlankLongDuplicateAndBadAmounts() = runTest {
        service.add("Glass", ContainerType.GLASS, "250")
        assertEquals(listOf(PresetError.NAME_BLANK), errors(service.add("   ", ContainerType.GLASS, "100")))
        assertEquals(listOf(PresetError.NAME_TOO_LONG), errors(service.add("x".repeat(25), ContainerType.GLASS, "100")))
        assertEquals(listOf(PresetError.NAME_DUPLICATE), errors(service.add("  gLaSs ", ContainerType.BOTTLE, "100")))
        assertEquals(listOf(PresetError.AMOUNT_INVALID), errors(service.add("Jug", ContainerType.GLASS, "0")))
        assertEquals(listOf(PresetError.AMOUNT_INVALID), errors(service.add("Jug", ContainerType.GLASS, "5001")))
        assertEquals(listOf(PresetError.AMOUNT_INVALID), errors(service.add("Jug", ContainerType.GLASS, "12.5")))
        assertEquals(listOf(PresetError.TYPE_INVALID), errors(service.add("Jug", ContainerType.CUSTOM, "100")))
        val ok = service.add("  ${"y".repeat(24)}  ", ContainerType.BOTTLE, "5000") as PresetResult.Saved
        assertEquals("y".repeat(24), ok.preset.name)
    }

    @Test
    fun editing_canKeepItsOwnName_butNotTakeAnother() = runTest {
        val a = (service.add("Glass", ContainerType.GLASS, "250") as PresetResult.Saved).preset
        service.add("Bottle", ContainerType.BOTTLE, "500")
        assertTrue(service.update(a.id, "glass", ContainerType.GLASS, "300") is PresetResult.Saved)
        assertEquals(listOf(PresetError.NAME_DUPLICATE), errors(service.update(a.id, "BOTTLE", ContainerType.GLASS, "300")))
    }

    @Test
    fun atMostEightPresets() = runTest {
        repeat(8) { service.add("P$it", ContainerType.GLASS, "100") }
        assertEquals(listOf(PresetError.LIMIT_REACHED), errors(service.add("Ninth", ContainerType.GLASS, "100")))
        assertEquals(8, store.all().size)
    }

    @Test
    fun moveUpAndDown_reordersAndRenumbers() = runTest {
        service.seedIfEmpty()
        val ids = store.all().map { it.id }
        assertTrue(service.move(ids[2], up = true))
        assertEquals(listOf(ids[0], ids[2], ids[1]), store.all().map { it.id })
        assertFalse("first cannot move up", service.move(ids[0], up = true))
        assertTrue(service.move(ids[0], up = false))
        assertEquals(listOf(ids[2], ids[0], ids[1]), store.all().map { it.id })
        assertEquals(listOf(0, 1, 2), store.all().map { it.displayOrder })
    }

    @Test
    fun deletingAllPresets_leavesEmptyList_andIsNotReseeded() = runTest {
        service.seedIfEmpty()
        store.all().forEach { service.delete(it.id) }
        assertTrue(store.all().isEmpty())
        // Seeding is gated by a first-launch flag in the app; reset explicitly restores starters.
        service.resetToStarters()
        assertEquals(3, store.all().size)
    }
}
