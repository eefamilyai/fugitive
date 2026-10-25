package app.fugitive.core.data.session

import app.fugitive.model.LocationFix
import app.fugitive.model.PlayerId

/**
 * Short rolling history of every player's fixes as received by this device.
 * Answers "where was X at time T?" for ping-style visibility and draws trails.
 */
class LocationHistory(private val retentionMs: Long = 35 * 60_000L, private val maxPerPlayer: Int = 2400) {
    private val data = HashMap<PlayerId, ArrayDeque<LocationFix>>()

    @Synchronized
    fun record(id: PlayerId, fix: LocationFix) {
        val q = data.getOrPut(id) { ArrayDeque() }
        val last = q.lastOrNull()
        if (last != null && fix.time <= last.time) return
        q.addLast(fix)
        val cutoff = fix.time - retentionMs
        while (q.size > maxPerPlayer || (q.firstOrNull()?.time ?: Long.MAX_VALUE) < cutoff) q.removeFirst()
    }

    @Synchronized
    fun recordAll(fixes: Map<PlayerId, LocationFix>) = fixes.forEach { (id, f) -> record(id, f) }

    /** Latest fix at or before [time]. */
    @Synchronized
    fun at(id: PlayerId, time: Long): LocationFix? {
        val q = data[id] ?: return null
        var result: LocationFix? = null
        for (f in q) { if (f.time <= time) result = f else break }
        return result
    }

    @Synchronized
    fun since(id: PlayerId, from: Long, until: Long = Long.MAX_VALUE): List<LocationFix> =
        data[id]?.filter { it.time in from..until }.orEmpty()

    @Synchronized
    fun clear() = data.clear()
}
