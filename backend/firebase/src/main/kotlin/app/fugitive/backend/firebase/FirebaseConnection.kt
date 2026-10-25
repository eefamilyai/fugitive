package app.fugitive.backend.firebase

import app.fugitive.core.data.backend.LobbyConnection
import app.fugitive.core.data.backend.ServerClock
import app.fugitive.model.ChatMessage
import app.fugitive.model.GameAction
import app.fugitive.model.GameConfig
import app.fugitive.model.GameEvent
import app.fugitive.model.GameState
import app.fugitive.model.LobbyCode
import app.fugitive.model.LobbyInfo
import app.fugitive.model.LobbyPhase
import app.fugitive.model.LobbySnapshot
import app.fugitive.model.LocationFix
import app.fugitive.model.MapMarker
import app.fugitive.model.Member
import app.fugitive.model.PlayerId
import app.fugitive.model.Role
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.KSerializer

internal class FirebaseConnection(
    private val backend: FirebaseBackend,
    private val db: FirebaseDatabase,
    private val ref: DatabaseReference,
    override val code: LobbyCode,
    override val selfId: PlayerId,
    scope: CoroutineScope,
) : LobbyConnection {

    // ── Clock & presence ────────────────────────────────────────────────
    private val offset = MutableStateFlow(0L)
    override val clock: ServerClock = object : ServerClock {
        override fun now() = System.currentTimeMillis() + offset.value
        override val offsetMs: StateFlow<Long> = offset.asStateFlow()
    }
    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val selfRef = ref.child("members").child(selfId)
    private val offsetListener = db.getReference(".info/serverTimeOffset").addValueEventListener(simpleListener { s ->
        offset.value = (s.value as? Number)?.toLong() ?: 0L
    })
    private val connectedListener = db.getReference(".info/connected").addValueEventListener(simpleListener { s ->
        val isConnected = s.value as? Boolean ?: false
        _connected.value = isConnected
        if (isConnected && !closed) {
            // onDisconnect handlers belong to a connection, so re-arm them after every reconnect.
            selfRef.child("online").onDisconnect().setValue(false)
            selfRef.child("lastActive").onDisconnect().setValue(ServerValue.TIMESTAMP)
            markOnline()
        }
    })

    /**
     * Presence write. Security rules reject it if our member record no longer
     * exists, so a kicked player can't resurrect themselves.
     */
    private fun markOnline() {
        if (closed) return
        selfRef.updateChildren(mapOf("online" to true, "lastActive" to ServerValue.TIMESTAMP))
    }

    /** Heartbeat: keeps lastActive fresh and repairs presence after silent reconnects. */
    private val heartbeat = scope.launch {
        while (!closed) {
            kotlinx.coroutines.delay(HEARTBEAT_MS)
            if (_connected.value) markOnline()
        }
    }

    @Volatile private var closed = false

    // ── Streams ─────────────────────────────────────────────────────────
    private fun valueFlow(r: Query): Flow<DataSnapshot> = callbackFlow {
        val l = r.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { trySend(snapshot) }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        })
        awaitClose { r.removeEventListener(l) }
    }

    /** Keeps a live map of a node's children, emitting a copy whenever any child changes. */
    private fun <T> childMapFlow(r: Query, serializer: KSerializer<T>): Flow<Map<String, T>> = callbackFlow {
        val map = LinkedHashMap<String, T>()
        var loaded = false
        val l = r.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(s: DataSnapshot, previousChildName: String?) { put(s) }
            override fun onChildChanged(s: DataSnapshot, previousChildName: String?) { put(s) }
            override fun onChildRemoved(s: DataSnapshot) { map.remove(s.key); emit() }
            override fun onChildMoved(s: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
            private fun put(s: DataSnapshot) {
                val key = s.key ?: return
                val v = FireJson.decode(serializer, s.value)
                if (v == null) map.remove(key) else map[key] = v
                if (loaded) emit()
            }
            private fun emit() { trySend(LinkedHashMap(map)) }
        })
        // Child listeners don't signal "initial load done"; a single value read does.
        r.get().addOnCompleteListener { loaded = true; trySend(LinkedHashMap(map)) }
        awaitClose { r.removeEventListener(l) }
    }.conflate()

    private val infoFlow = valueFlow(ref.child("info")).map { FireJson.decode(LobbyInfo.serializer(), it.value) }
    private val configFlow = valueFlow(ref.child("config")).map { FireJson.decode(GameConfig.serializer(), it.value) ?: GameConfig() }
    private val membersFlow = valueFlow(ref.child("members")).map { snap ->
        snap.children.mapNotNull { c ->
            FireJson.decode(Member.serializer(), c.value)?.takeIf { it.name.isNotBlank() }?.copy(id = c.key ?: "")
        }.associateBy { it.id }
    }
    private val rosterFlow = valueFlow(ref.child("roster")).map { snap ->
        snap.children.mapNotNull { c -> (c.value as? String)?.let { (c.key ?: "") to Role(it) } }.toMap()
    }
    private val gameFlow = valueFlow(ref.child("game")).map { FireJson.decode(GameState.serializer(), it.value) }

    override val lobby: Flow<LobbySnapshot?> = combine(infoFlow, configFlow, membersFlow, rosterFlow, gameFlow) { info, config, members, roster, game ->
        info?.let { LobbySnapshot(it.copy(code = code), config, members, roster, game) }
    }.distinctUntilChanged().shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    override val locations: Flow<Map<PlayerId, LocationFix>> =
        childMapFlow(ref.child("locations"), LocationFix.serializer()).shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    override val events: Flow<List<GameEvent>> =
        childMapFlow(ref.child("events").limitToLast(200), GameEvent.serializer())
            .map { m -> m.map { (k, v) -> v.copy(id = k) }.sortedBy { it.at } }
            .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    override val actions: Flow<List<GameAction>> =
        childMapFlow(ref.child("actions"), GameAction.serializer()).map { m -> m.map { (k, v) -> v.copy(id = k) } }

    override val markers: Flow<List<MapMarker>> =
        childMapFlow(ref.child("markers").limitToLast(100), MapMarker.serializer())
            .map { m -> m.map { (k, v) -> v.copy(id = k) }.filter { it.expiresAt > clock.now() } }

    override val chat: Flow<List<ChatMessage>> =
        childMapFlow(ref.child("chat").limitToLast(150), ChatMessage.serializer()).map { m -> m.map { (k, v) -> v.copy(id = k) } }

    override suspend fun trails(): Map<PlayerId, List<LocationFix>> {
        val snap = ref.child("trails").get().await()
        return snap.children.associate { p ->
            (p.key ?: "") to p.children.mapNotNull { FireJson.decode(LocationFix.serializer(), it.value) }.sortedBy { it.time }
        }
    }

    // ── Player commands ─────────────────────────────────────────────────
    private var lastSelf: Member? = null

    override suspend fun updateSelf(transform: (Member) -> Member) {
        val current = selfRef.get().await()
        val me = FireJson.decode(Member.serializer(), current.value)?.takeIf { it.name.isNotBlank() } ?: return
        val updated = transform(me.copy(id = selfId))
        lastSelf = updated
        val plain = FireJson.encode(Member.serializer(), updated) as Map<*, *>
        // Only write fields that changed, so concurrent presence updates aren't clobbered.
        val old = FireJson.encode(Member.serializer(), me) as Map<*, *>
        val diff = plain.filter { (k, v) -> old[k] != v }.mapKeys { it.key.toString() }
        if (diff.isNotEmpty()) selfRef.updateChildren(diff).await()
    }

    override suspend fun publishLocation(fix: LocationFix) {
        ref.child("locations").child(selfId).setValue(FireJson.encode(LocationFix.serializer(), fix))
    }

    override suspend fun appendTrail(fix: LocationFix) {
        ref.child("trails").child(selfId).push().setValue(FireJson.encode(LocationFix.serializer(), fix))
    }

    override suspend fun submitAction(action: GameAction) {
        val r = ref.child("actions").push()
        r.setValue(FireJson.encode(GameAction.serializer(), action.copy(id = ""))).await()
    }

    override suspend fun sendChat(message: ChatMessage) {
        ref.child("chat").push().setValue(FireJson.encode(ChatMessage.serializer(), message)).await()
    }

    override suspend fun placeMarker(marker: MapMarker) {
        ref.child("markers").push().setValue(FireJson.encode(MapMarker.serializer(), marker)).await()
    }

    override suspend fun leave() {
        val snap = ref.get().await()
        val info = FireJson.decode(LobbyInfo.serializer(), snap.child("info").value)
        val others = snap.child("members").children.filter { it.key != selfId && it.child("name").value != null }
        if (info?.hostId == selfId) {
            val next = others.filter { it.child("online").value == true }.minByOrNull { (it.child("joinedAt").value as? Long) ?: Long.MAX_VALUE }
            if (next == null) {
                // Last one out turns off the lights.
                selfRef.child("online").onDisconnect().cancel()
                ref.removeValue().await()
                return
            }
            ref.child("info").child("hostId").setValue(next.key).await()
        }
        selfRef.child("online").onDisconnect().cancel()
        selfRef.child("lastActive").onDisconnect().cancel()
        ref.updateChildren(
            mapOf("members/$selfId" to null, "locations/$selfId" to null),
        ).await()
    }

    // ── Host commands ───────────────────────────────────────────────────
    override suspend fun updateConfig(config: GameConfig) {
        ref.child("config").setValue(FireJson.encode(GameConfig.serializer(), config)).await()
    }

    override suspend fun setRoster(roster: Map<PlayerId, Role>) {
        ref.child("roster").setValue(roster.mapValues { it.value.id }.ifEmpty { null }).await()
    }

    override suspend fun setLocked(locked: Boolean) {
        ref.child("info").child("locked").setValue(locked).await()
    }

    override suspend fun kick(id: PlayerId) {
        ref.updateChildren(mapOf("members/$id" to null, "roster/$id" to null, "locations/$id" to null)).await()
    }

    override suspend fun transferHost(id: PlayerId) {
        ref.child("info").child("hostId").setValue(id).await()
    }

    override suspend fun startGame(game: GameState, events: List<GameEvent>) {
        ref.updateChildren(mapOf("events" to null, "actions" to null, "trails" to null, "markers" to null)).await()
        val updates = HashMap<String, Any?>()
        updates["game"] = FireJson.encode(GameState.serializer(), game)
        updates["info/phase"] = LobbyPhase.PLAYING.name
        updates["info/round"] = game.round
        events.forEach { e -> updates["events/${ref.child("events").push().key}"] = FireJson.encode(GameEvent.serializer(), e.copy(id = "")) }
        ref.updateChildren(updates).await()
    }

    override suspend fun commitTick(game: GameState, events: List<GameEvent>, consumedActions: List<String>, phase: LobbyPhase?) {
        val updates = HashMap<String, Any?>()
        updates["game"] = FireJson.encode(GameState.serializer(), game)
        events.forEach { e -> updates["events/${ref.child("events").push().key}"] = FireJson.encode(GameEvent.serializer(), e.copy(id = "")) }
        consumedActions.forEach { updates["actions/$it"] = null }
        if (phase != null) updates["info/phase"] = phase.name
        ref.updateChildren(updates).await()
    }

    override suspend fun resetToLobby() {
        ref.updateChildren(
            mapOf(
                "info/phase" to LobbyPhase.LOBBY.name, "game" to null, "events" to null, "actions" to null,
                "trails" to null, "markers" to null,
            ),
        ).await()
        // Everyone must re-confirm they're ready.
        val members = ref.child("members").get().await()
        val ready = members.children.mapNotNull { it.key }.associate { "members/$it/ready" to false }
        if (ready.isNotEmpty()) runCatching { ref.updateChildren(ready).await() }
    }

    override suspend fun claimHost(expectedHost: PlayerId): Boolean =
        runTransaction(ref.child("info").child("hostId")) { current ->
            if (current.value == expectedHost) selfId else null
        }

    override fun close() {
        closed = true
        heartbeat.cancel()
        db.getReference(".info/serverTimeOffset").removeEventListener(offsetListener)
        db.getReference(".info/connected").removeEventListener(connectedListener)
    }

    private fun simpleListener(block: (DataSnapshot) -> Unit) = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) = block(snapshot)
        override fun onCancelled(error: DatabaseError) {}
    }

    companion object {
        const val HEARTBEAT_MS = 15_000L
        /** A member whose heartbeat is older than this is considered gone. */
        const val STALE_MS = 45_000L
    }
}
