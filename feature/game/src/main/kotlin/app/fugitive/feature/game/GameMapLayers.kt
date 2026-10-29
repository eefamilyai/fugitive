package app.fugitive.feature.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import app.fugitive.engine.projection.GameProjection
import app.fugitive.engine.projection.LocationHistory
import app.fugitive.core.designsystem.theme.FugitiveColors
import app.fugitive.core.map.MapLayer
import app.fugitive.core.map.layers.MapPin
import app.fugitive.core.map.layers.MarkerLook
import app.fugitive.core.map.layers.PinsLayer
import app.fugitive.core.map.layers.PlayerMarker
import app.fugitive.core.map.layers.PlayersLayer
import app.fugitive.core.map.layers.RingLayer
import app.fugitive.core.map.layers.Trail
import app.fugitive.core.map.layers.TrailsLayer
import app.fugitive.core.map.layers.ZoneLayer
import app.fugitive.core.ui.Format
import app.fugitive.core.ui.Visuals
import app.fugitive.engine.settings.CoreSettings
import app.fugitive.engine.visibility.VisibilityKind
import app.fugitive.model.Circle
import app.fugitive.model.MapMarker
import app.fugitive.model.PlayerId
import app.fugitive.model.PlayerStatus

/** All overlays of the in-game map, kept stable across recompositions. */
internal class GameLayers {
    val zone = ZoneLayer()
    val trails = TrailsLayer()
    val objectives = PinsLayer("objectives")
    val catchRing = RingLayer("catch")
    val alertRing = RingLayer("alert")
    val pings = PinsLayer("pings")
    val players = PlayersLayer()
    val all: List<MapLayer> = listOf(zone, trails, objectives, alertRing, catchRing, pings, players)
}

@Composable
internal fun rememberGameLayers(): GameLayers = remember { GameLayers() }

/** Pushes the latest projection into the map layers. */
@Composable
internal fun BindGameLayers(
    layers: GameLayers,
    p: GameProjection,
    history: LocationHistory?,
    markers: List<MapMarker>,
    selected: PlayerId?,
    units: app.fugitive.core.ui.UnitSystem,
) {
    SideEffect {
        val s = p.settings
        // Zone
        val outside = p.zoneStatus?.let { it.applies && !it.inside } == true
        layers.zone.submit(p.zone?.current, p.zone?.next, FugitiveColors.Zone.toArgb(), danger = outside)

        // Players
        val glide = s[CoreSettings.LOCATION_INTERVAL_SEC] * 1000
        val markersList = p.players.mapNotNull { pp ->
            val fix = pp.fix ?: return@mapNotNull null
            val look = when {
                pp.state.status != PlayerStatus.ACTIVE -> MarkerLook.OUT
                pp.visibility.kind == VisibilityKind.SNAPSHOT -> MarkerLook.SNAPSHOT
                pp.isStale -> MarkerLook.STALE
                else -> MarkerLook.LIVE
            }
            PlayerMarker(
                id = pp.id, point = fix.point, avatar = pp.state.avatar,
                ringColor = Visuals.roleColor(pp.state.role).toArgb(),
                fillColor = FugitiveColors.player(pp.state.color).toArgb(),
                label = if (pp.isMe) "You" else pp.state.name,
                bearing = fix.bearing, accuracy = fix.accuracy, look = look, isMe = pp.isMe, selected = pp.id == selected,
                badge = when (look) {
                    MarkerLook.SNAPSHOT, MarkerLook.STALE -> Format.ageBadge(p.now - fix.time)
                    else -> null
                },
                glideMs = glide,
            )
        }
        layers.players.submit(markersList, showAccuracy = true)

        // Trails
        if (s[CoreSettings.SHOW_TRAILS] && history != null) {
            val since = p.now - s[CoreSettings.TRAIL_MINUTES] * 60_000
            layers.trails.submit(
                p.players.filter { it.visibility.kind == VisibilityKind.LIVE && it.state.status == PlayerStatus.ACTIVE }.map { pp ->
                    Trail(pp.id, history.since(pp.id, since).map { it.point }, FugitiveColors.player(pp.state.color).toArgb())
                },
            )
        } else layers.trails.submit(emptyList())

        // Objectives
        layers.objectives.submit(p.objectives.map { o ->
            MapPin(o.id, o.point, if (o.type == "extraction") "🚁" else "⭐", FugitiveColors.Warning.toArgb(), o.label, o.radius)
        })

        // Team pings
        layers.pings.submit(markers.filter { it.expiresAt > p.now }.map { m ->
            val who = p.lobby.members[m.by]?.name ?: "Teammate"
            val emoji = when (m.kind) { "spotted" -> "👀"; "going" -> "🏃"; "danger" -> "⚠️"; else -> "📍" }
            MapPin(m.id, m.point, emoji, FugitiveColors.Runner.toArgb(), "$who · ${Format.ageBadge(p.now - m.createdAt)}")
        })

        // Catch range around me (when I can catch someone and range matters)
        val me = p.me
        val myFix = p.myFix
        val method = s[CoreSettings.CATCH_METHOD]
        val rangeMatters = method == "proximity" || method == "auto" || s[CoreSettings.CATCH_REQUIRE_RANGE]
        layers.catchRing.submit(
            if (me != null && myFix != null && p.catchOptions.isNotEmpty() && rangeMatters)
                Circle(myFix.lat, myFix.lng, s[CoreSettings.CATCH_RADIUS_M].toDouble()) else null,
            Visuals.roleColor(me?.role ?: app.fugitive.model.Role.HUNTER).toArgb(), 0.10f,
        )
        // Danger radius when a hunter is near (helps judge distance)
        val alert = s[CoreSettings.PROXIMITY_ALERT_M].toDouble()
        layers.alertRing.submit(
            if (myFix != null && p.threats.isNotEmpty() && alert > 0) Circle(myFix.lat, myFix.lng, alert) else null,
            FugitiveColors.Hunter.toArgb(), 0.06f,
        )
    }
}
