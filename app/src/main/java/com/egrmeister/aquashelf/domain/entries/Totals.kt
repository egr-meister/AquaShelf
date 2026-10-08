package com.egrmeister.aquashelf.domain.entries

/**
 * Daily totals derived from saved entries only — each entry's own amount snapshot is used,
 * never the current preset size. Volume is a Long so large histories cannot overflow.
 */
data class DayTotals(
    val volumeMl: Long,
    val glasses: Int,
    val bottles: Int,
    val custom: Int,
    val entries: Int,
) {
    /** "2 glasses · 1 bottle" — empty when there are no entries. */
    val breakdown: String
        get() = buildList {
            if (glasses > 0) add(ContainerType.GLASS.countLabel(glasses))
            if (bottles > 0) add(ContainerType.BOTTLE.countLabel(bottles))
            if (custom > 0) add(ContainerType.CUSTOM.countLabel(custom))
        }.joinToString(" · ")

    val entriesLabel: String get() = if (entries == 1) "1 entry" else "$entries entries"

    val volumeLabel: String get() = "${Fmt.ml(volumeMl)} recorded"

    companion object {
        val EMPTY = DayTotals(0, 0, 0, 0, 0)

        fun of(entries: List<WaterEntry>): DayTotals = DayTotals(
            volumeMl = entries.sumOf { it.amountMl.toLong() },
            glasses = entries.count { it.type == ContainerType.GLASS },
            bottles = entries.count { it.type == ContainerType.BOTTLE },
            custom = entries.count { it.type == ContainerType.CUSTOM },
            entries = entries.size,
        )
    }
}

/** Shelf arrangement rules. Objects are identified by entry ID, never by position. */
object ShelfOrder {
    /** Chronological by logged local time; ties broken by creation order, then ID. */
    val chronological: Comparator<WaterEntry> =
        compareBy<WaterEntry>({ it.localTime }, { it.createdAt }, { it.id })

    /** Creation order (used by Undo Last), independent of a backdated drinking time. */
    val creation: Comparator<WaterEntry> = compareBy<WaterEntry>({ it.createdAt }, { it.id })

    fun arrange(entries: List<WaterEntry>): List<WaterEntry> = entries.sortedWith(chronological)

    /** Left to right, then onto the next shelf. Never drops entries. */
    fun shelves(entries: List<WaterEntry>, perShelf: Int): List<List<WaterEntry>> =
        arrange(entries).chunked(perShelf.coerceAtLeast(1))

    fun latestLogged(entries: List<WaterEntry>): WaterEntry? = entries.maxWithOrNull(creation)

    /** More objects per shelf on wider screens; objects keep a readable minimum width. */
    fun perShelf(availableWidthDp: Float, minItemWidthDp: Float = 84f, min: Int = 3, max: Int = 12): Int {
        if (availableWidthDp <= 0f) return min
        return (availableWidthDp / minItemWidthDp).toInt().coerceIn(min, max)
    }
}
