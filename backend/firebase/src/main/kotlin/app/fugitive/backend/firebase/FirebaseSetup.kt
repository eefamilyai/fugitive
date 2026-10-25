package app.fugitive.backend.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

/**
 * How the Firebase backend is configured.
 * @param configured google-services.json was present at build time.
 * @param emulatorHost when non-empty (debug builds), talk to the Firebase Local Emulator Suite.
 */
data class FirebaseConfig(val configured: Boolean, val emulatorHost: String = "") {
    val usable: Boolean get() = configured || emulatorHost.isNotBlank()
}

internal object FirebaseSetup {
    private var initialized = false
    private const val DEMO_PROJECT = "demo-fugitive"

    @Synchronized
    fun ensure(context: Context, config: FirebaseConfig) {
        if (initialized || !config.usable) return
        if (FirebaseApp.getApps(context).isEmpty()) {
            // No google-services.json: emulator-only "demo-" project (needs no real Firebase account).
            val options = FirebaseOptions.Builder()
                .setProjectId(DEMO_PROJECT)
                .setApplicationId("1:000000000000:android:0000000000000000")
                .setApiKey("demo-api-key-not-real")
                .setDatabaseUrl("http://${config.emulatorHost}:9000?ns=$DEMO_PROJECT-default-rtdb")
                .build()
            FirebaseApp.initializeApp(context, options)
        }
        if (config.emulatorHost.isNotBlank()) {
            FirebaseDatabase.getInstance().useEmulator(config.emulatorHost, 9000)
            FirebaseAuth.getInstance().useEmulator(config.emulatorHost, 9099)
        }
        initialized = true
    }
}
