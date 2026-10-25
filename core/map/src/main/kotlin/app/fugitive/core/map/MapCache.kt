package app.fugitive.core.map

import android.content.Context
import org.maplibre.android.offline.OfflineManager

/** Tile cache tuning: a bigger ambient cache means the play area stays sharp even when signal drops. */
object MapCache {
    private const val AMBIENT_CACHE_BYTES = 350L * 1024 * 1024
    @Volatile private var configured = false

    fun ensureConfigured(context: Context) {
        if (configured) return
        configured = true
        runCatching {
            OfflineManager.getInstance(context).setMaximumAmbientCacheSize(
                AMBIENT_CACHE_BYTES,
                object : OfflineManager.FileSourceCallback {
                    override fun onSuccess() {}
                    override fun onError(message: String) {}
                },
            )
        }
    }
}
