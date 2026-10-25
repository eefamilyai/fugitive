package app.fugitive.engine.stats

import app.fugitive.engine.geo.Geo
import app.fugitive.model.GameState
import app.fugitive.model.LocationFix
import app.fugitive.model.PlayerId
import app.fugitive.model.PlayerStatus

data class PlayerStats(
    val playerId: PlayerId,
    val survivedMs: Long,
    val catches: Int,
    val distanceMeters: Double,
    val topSpeedMps: Double,
    val won: Boolean,
)

/** Post-game statistics, computed from the final state and the players' recorded trails. */
object GameStats {
    fun compute(game: GameState, trails: Map<PlayerId, List<LocationFix>>): List<PlayerStats> {
        val end = game.result?.endedAt ?: game.endsAt ?: game.startAt
        val winners = game.result?.winnerIds?.toSet() ?: emptySet()
        return game.players.values.map { p ->
            val outAt = if (p.status == PlayerStatus.ACTIVE) end else (p.statusChangedAt ?: end)
            val survived = (outAt - game.startAt).coerceAtLeast(0)
            val trail = trails[p.id].orEmpty().sortedBy { it.time }.filter { it.time in game.startAt..end }
            var top = 0.0
            for (i in 1 until trail.size) {
                val dt = (trail[i].time - trail[i - 1].time) / 1000.0
                if (dt <= 0) continue
                val v = Geo.distance(trail[i - 1].point, trail[i].point) / dt
                if (v < 12.5) top = maxOf(top, v) // ignore GPS teleports (> 45 km/h on foot)
            }
            PlayerStats(p.id, survived, p.catches, Geo.pathLength(trail), top, p.id in winners)
        }.sortedWith(compareByDescending<PlayerStats> { it.won }.thenByDescending { it.catches }.thenByDescending { it.survivedMs })
    }
}
