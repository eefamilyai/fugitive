package app.fugitive.engine.web

import app.fugitive.model.Circle
import app.fugitive.model.GameConfig
import app.fugitive.model.GameEvent
import app.fugitive.model.GameResult
import app.fugitive.model.GameState
import app.fugitive.model.LobbyInfo
import app.fugitive.model.Member
import app.fugitive.model.Objective
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** A lobby as read from the database by the web client (one object per lobby node). */
@Serializable
data class WebLobby(
    val info: LobbyInfo = LobbyInfo(),
    val config: GameConfig = GameConfig(),
    val members: Map<String, Member> = emptyMap(),
    val roster: Map<String, String> = emptyMap(),
    val game: GameState? = null,
)

@Serializable data class WebRole(val id: String, val label: String, val color: String, val icon: String)

@Serializable data class WebMode(
    val id: String, val name: String, val tagline: String, val icon: String, val accent: String,
    val rules: List<String>, val minPlayers: Int, val usesHunterSelection: Boolean,
    val roleLabels: Map<String, String>, val roles: List<String>,
)

@Serializable data class WebPreset(val id: String, val name: String, val description: String, val modeId: String, val emoji: String)
@Serializable data class WebAbility(val id: String, val name: String, val description: String, val icon: String, val roles: List<String>)
@Serializable data class WebCatalog(val modes: List<WebMode>, val presets: List<WebPreset>, val abilities: List<WebAbility>, val roles: List<WebRole>, val schemaVersion: Int)

@Serializable data class FormOption(val id: String, val label: String, val description: String)

@Serializable data class FormSetting(
    val key: String,
    /** toggle | number | choice | point */
    val type: String,
    val title: String,
    val description: String,
    val advanced: Boolean,
    val nested: Boolean,
    val visible: Boolean,
    val value: JsonElement,
    val display: String,
    val min: Long? = null,
    val max: Long? = null,
    val step: Long? = null,
    /** NONE | SECONDS | MINUTES | METERS | COUNT | PERCENT */
    val unit: String? = null,
    val specialLabels: Map<String, String> = emptyMap(),
    val suggestions: List<Long> = emptyList(),
    val options: List<FormOption> = emptyList(),
)

@Serializable data class FormCategory(val id: String, val title: String, val icon: String, val settings: List<FormSetting>)
@Serializable data class WebIssue(val message: String, val blocking: Boolean, val settingKey: String? = null)
@Serializable data class WebForm(val modeId: String, val presetId: String? = null, val categories: List<FormCategory>, val values: Map<String, JsonElement>)

@Serializable data class StartResult(val ok: Boolean, val game: GameState? = null, val events: List<GameEvent> = emptyList(), val error: String? = null)
@Serializable data class TickResult(val changed: Boolean, val game: GameState, val events: List<GameEvent>, val consumed: List<String>, val over: Boolean)

@Serializable data class WebFix(
    val lat: Double, val lng: Double, val accuracy: Float, val bearing: Float? = null,
    val speed: Float? = null, val time: Long, val battery: Int? = null,
)

@Serializable data class WebPlayer(
    val id: String, val name: String, val avatar: String, val color: Int,
    val role: String, val originalRole: String, val roleLabel: String, val status: String,
    val outReason: String? = null, val caughtBy: String? = null, val catches: Int,
    /** LIVE | SNAPSHOT | HIDDEN */
    val visibility: String, val visibilityReason: String? = null,
    val fix: WebFix? = null, val ageMs: Long? = null, val isStale: Boolean,
    val distance: Double? = null, val bearing: Double? = null, val isMe: Boolean, val online: Boolean,
    val trail: List<List<Double>> = emptyList(),
)

@Serializable data class WebZone(
    val current: Circle, val next: Circle? = null, val phase: String, val phaseEndsAt: Long? = null,
    val stageIndex: Int, val stageCount: Int,
)

@Serializable data class WebZoneStatus(val inside: Boolean, val marginMeters: Double, val outsideSince: Long? = null, val graceLeftMs: Long? = null, val applies: Boolean)
@Serializable data class WebThreat(val playerId: String, val distance: Double, val bearing: Double)
@Serializable data class WebCatchOption(val targetId: String, val allowed: Boolean, val reason: String? = null, val distance: Double? = null)
@Serializable data class WebPending(val id: String, val hunterId: String, val runnerId: String, val createdAt: Long, val expiresAt: Long, val distanceMeters: Double? = null)
@Serializable data class WebAbilityState(
    val id: String, val name: String, val icon: String, val usesLeft: Int, val readyAt: Long,
    val activeUntil: Long? = null, val blockedReason: String? = null,
)

@Serializable data class WebProjection(
    val now: Long,
    /** COUNTDOWN | HEADSTART | ACTIVE | ENDED */
    val stage: String,
    val round: Int,
    val modeId: String,
    val modeName: String,
    val myRoleLabel: String,
    val objective: String,
    val me: WebPlayer? = null,
    val isSpectating: Boolean,
    val isPrey: Boolean,
    val players: List<WebPlayer>,
    val zone: WebZone? = null,
    val zoneStatus: WebZoneStatus? = null,
    val threats: List<WebThreat>,
    val catchOptions: List<WebCatchOption>,
    val incomingCatch: WebPending? = null,
    val outgoingCatches: List<WebPending>,
    val abilities: List<WebAbilityState>,
    val objectives: List<Objective>,
    val timeLeftMs: Long? = null,
    val stageEndsInMs: Long? = null,
    val nextPingAt: Long? = null,
    val activeCounts: Map<String, Int>,
    val startAt: Long,
    val headstartEndsAt: Long,
    val endsAt: Long? = null,
    val result: GameResult? = null,
    val codeLockedMs: Long? = null,
    /** Raw values of every setting, for the UI (catch method, alert radius, …). */
    val settings: Map<String, JsonElement>,
)

@Serializable data class WebStats(val playerId: String, val survivedMs: Long, val catches: Int, val distanceMeters: Double, val topSpeedMps: Double, val won: Boolean)
