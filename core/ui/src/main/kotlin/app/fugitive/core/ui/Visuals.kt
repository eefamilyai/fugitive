package app.fugitive.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Coronavirus
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.FrontHand
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.fugitive.core.designsystem.theme.FugitiveColors
import app.fugitive.engine.role.RoleCatalog
import app.fugitive.engine.rules.EventTypes
import app.fugitive.model.Role

/** Maps engine icon keys and ids to Compose visuals. Unknown keys get a sensible default. */
object Visuals {
    fun roleColor(role: Role): Color = Color(RoleCatalog.info(role).color)

    fun roleIcon(role: Role): ImageVector = when (role) {
        Role.HUNTER -> Icons.Rounded.GpsFixed
        Role.RUNNER -> Icons.AutoMirrored.Rounded.DirectionsRun
        Role.SPECTATOR -> Icons.Rounded.Visibility
        else -> Icons.Rounded.Person
    }

    fun icon(key: String): ImageVector = when (key) {
        "classic" -> Icons.AutoMirrored.Rounded.DirectionsRun
        "virus" -> Icons.Rounded.Coronavirus
        "helicopter" -> Icons.Rounded.Flight
        "target" -> Icons.Rounded.TrackChanges
        "timer" -> Icons.Rounded.Timer
        "groups" -> Icons.Rounded.Groups
        "visibility" -> Icons.Rounded.Visibility
        "back_hand" -> Icons.Rounded.FrontHand
        "radar" -> Icons.Rounded.Radar
        "tune" -> Icons.Rounded.Tune
        "bolt" -> Icons.Rounded.Bolt
        "shield" -> Icons.Rounded.Shield
        "cloak" -> Icons.Rounded.VisibilityOff
        "sonar" -> Icons.Rounded.Sensors
        "hunter" -> Icons.Rounded.GpsFixed
        "runner" -> Icons.AutoMirrored.Rounded.DirectionsRun
        else -> Icons.Rounded.Tune
    }

    fun eventIcon(type: String): ImageVector = when (type) {
        EventTypes.CAUGHT, EventTypes.CATCH_CLAIMED, EventTypes.CATCH_DISPUTED -> Icons.Rounded.FrontHand
        EventTypes.INFECTED -> Icons.Rounded.Coronavirus
        EventTypes.ELIMINATED, EventTypes.OUTSIDE_ZONE -> Icons.Rounded.Warning
        EventTypes.ZONE_WARNING, EventTypes.ZONE_SHRINKING, EventTypes.ZONE_FINAL, EventTypes.BACK_IN_ZONE -> Icons.Rounded.Radar
        EventTypes.RELEASED -> Icons.Rounded.Whatshot
        EventTypes.ESCAPED, EventTypes.OBJECTIVE -> Icons.Rounded.Flight
        EventTypes.ABILITY -> Icons.Rounded.Bolt
        EventTypes.REVEALED -> Icons.Rounded.MyLocation
        EventTypes.DISCONNECTED -> Icons.Rounded.WifiOff
        EventTypes.RECONNECTED -> Icons.Rounded.GpsNotFixed
        EventTypes.LEFT -> Icons.AutoMirrored.Rounded.ExitToApp
        EventTypes.JOINED -> Icons.Rounded.PersonAdd
        EventTypes.TIME_WARNING -> Icons.Rounded.AccessTime
        EventTypes.GAME_OVER -> Icons.Rounded.EmojiEvents
        EventTypes.GAME_STARTED -> Icons.Rounded.Flag
        EventTypes.TARGET -> Icons.Rounded.TrackChanges
        EventTypes.WRONG_CODE -> Icons.Rounded.LocationOff
        EventTypes.INFO -> Icons.Rounded.Info
        else -> Icons.Rounded.Campaign
    }

    fun eventColor(type: String, importance: Int): Color = when (type) {
        EventTypes.CAUGHT, EventTypes.ELIMINATED, EventTypes.OUTSIDE_ZONE, EventTypes.CATCH_CLAIMED -> FugitiveColors.Hunter
        EventTypes.INFECTED -> Color(0xFF84CC16)
        EventTypes.ZONE_WARNING, EventTypes.ZONE_SHRINKING, EventTypes.ZONE_FINAL -> FugitiveColors.Zone
        EventTypes.ESCAPED, EventTypes.BACK_IN_ZONE, EventTypes.RECONNECTED -> FugitiveColors.Success
        EventTypes.RELEASED, EventTypes.TIME_WARNING -> FugitiveColors.Warning
        EventTypes.ABILITY, EventTypes.REVEALED -> FugitiveColors.Runner
        EventTypes.GAME_OVER -> FugitiveColors.Signal
        else -> if (importance >= 2) FugitiveColors.Warning else FugitiveColors.Mist
    }
}
