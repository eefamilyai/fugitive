package app.fugitive.core.data.backend

/** All transports contributed through DI. */
class BackendRegistry(backends: List<GameBackend>) {
    val all: List<GameBackend> = backends.distinctBy { it.id }

    fun get(id: String): GameBackend? = all.firstOrNull { it.id == id }

    /** The preferred configured online backend, if any. */
    val online: GameBackend? get() = all.firstOrNull { it.isOnline && it.isConfigured() }

    val practice: GameBackend? get() = all.firstOrNull { !it.isOnline }

    companion object {
        const val LOCAL = "local"
        const val FIREBASE = "firebase"
    }
}

class SystemServerClock : ServerClock {
    override fun now(): Long = System.currentTimeMillis()
    override val offsetMs = kotlinx.coroutines.flow.MutableStateFlow(0L)
}
