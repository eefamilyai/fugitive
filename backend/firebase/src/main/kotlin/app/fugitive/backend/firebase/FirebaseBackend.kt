package app.fugitive.backend.firebase

import android.content.Context
import app.fugitive.core.data.backend.GameBackend
import app.fugitive.core.data.backend.JoinException
import app.fugitive.core.data.backend.LobbyConnection
import app.fugitive.model.GameConfig
import app.fugitive.model.LobbyCode
import app.fugitive.model.LobbyCodes
import app.fugitive.model.LobbyInfo
import app.fugitive.model.Member
import app.fugitive.model.PlayerId
import app.fugitive.model.PlayerProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Online multiplayer over Firebase Realtime Database + anonymous Firebase Auth. */
class FirebaseBackend(
    private val context: Context,
    private val config: FirebaseConfig,
    private val scope: CoroutineScope,
    private val appVersion: Int,
) : GameBackend {
    override val id = "firebase"
    override val displayName = "Online"
    override val isOnline = true
    override fun isConfigured() = config.usable

    private val db: FirebaseDatabase get() { FirebaseSetup.ensure(context, config); return FirebaseDatabase.getInstance() }
    private val auth: FirebaseAuth get() { FirebaseSetup.ensure(context, config); return FirebaseAuth.getInstance() }
    internal fun lobbyRef(code: LobbyCode): DatabaseReference = db.getReference("lobbies").child(code)

    override suspend fun signIn(): PlayerId = withTimeout(20_000) {
        auth.currentUser?.uid ?: auth.signInAnonymously().await().user?.uid ?: error("Sign-in failed")
    }

    override suspend fun createLobby(profile: PlayerProfile, config: GameConfig): LobbyCode {
        val uid = signIn()
        repeat(8) {
            val code = LobbyCodes.random()
            val info = FireJson.encode(LobbyInfo.serializer(), LobbyInfo(code = code, hostId = uid)) as Map<*, *>
            val claimed = runTransaction(lobbyRef(code).child("info")) { current ->
                if (current.value != null) null else (info + ("createdAt" to ServerValue.TIMESTAMP))
            }
            if (claimed) {
                val ref = lobbyRef(code)
                ref.child("config").setValue(FireJson.encode(GameConfig.serializer(), config)).await()
                ref.child("members").child(uid).setValue(memberValue(uid, profile, null) + ("ready" to true)).await()
                return code
            }
        }
        error("Couldn't create a lobby. Try again.")
    }

    override suspend fun joinLobby(code: LobbyCode, profile: PlayerProfile) {
        val uid = signIn()
        val ref = lobbyRef(code)
        val infoSnap = try {
            withTimeout(15_000) { ref.child("info").get().await() }
        } catch (e: Exception) {
            throw JoinException(JoinException.Reason.NETWORK, "Couldn't reach the server")
        }
        val info = FireJson.decode(LobbyInfo.serializer(), infoSnap.value)
            ?: throw JoinException(JoinException.Reason.NOT_FOUND, "No game with code $code")
        if (info.schema > LobbyInfo.SCHEMA_VERSION) throw JoinException(JoinException.Reason.INCOMPATIBLE, "That lobby needs a newer version of the app")
        val existing = runCatching { ref.child("members").child(uid).get().await() }.getOrNull()
        val already = existing?.child("name")?.value != null
        if (info.locked && !already) throw JoinException(JoinException.Reason.LOCKED, "That lobby is locked")
        val joinedAt = existing?.child("joinedAt")?.value as? Long
        try {
            ref.child("members").child(uid).updateChildren(memberValue(uid, profile, joinedAt)).await()
        } catch (e: Exception) {
            throw JoinException(JoinException.Reason.LOCKED, "Couldn't join: ${e.message}")
        }
    }

    private fun memberValue(uid: PlayerId, profile: PlayerProfile, joinedAt: Long?): Map<String, Any?> {
        val base = FireJson.encode(
            Member.serializer(),
            Member(id = uid, name = profile.name.ifBlank { "Player" }, avatar = profile.avatar, color = profile.color, online = true, appVersion = appVersion),
        ) as Map<*, *>
        return base.entries.associate { it.key.toString() to it.value } +
            mapOf("joinedAt" to (joinedAt ?: ServerValue.TIMESTAMP), "lastActive" to ServerValue.TIMESTAMP, "ready" to false)
    }

    override fun connect(code: LobbyCode, selfId: PlayerId): LobbyConnection =
        FirebaseConnection(this, db, lobbyRef(code), code, selfId, scope)
}

/** Runs a transaction; [update] returns the new value, or null to abort. Returns true if committed. */
internal suspend fun runTransaction(ref: DatabaseReference, update: (MutableData) -> Any?): Boolean =
    suspendCancellableCoroutine { cont ->
        ref.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val value = update(currentData) ?: return Transaction.abort()
                currentData.value = value
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (!cont.isActive) return
                if (error != null) cont.resumeWithException(error.toException()) else cont.resume(committed)
            }
        })
    }
