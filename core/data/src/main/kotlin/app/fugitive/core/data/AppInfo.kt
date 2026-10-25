package app.fugitive.core.data

/** Build/app facts provided by the app module (keeps library modules free of BuildConfig). */
data class AppInfo(
    val versionName: String,
    val versionCode: Int,
    val privacyPolicyUrl: String,
    val supportEmail: String,
    val onlineConfigured: Boolean,
)
