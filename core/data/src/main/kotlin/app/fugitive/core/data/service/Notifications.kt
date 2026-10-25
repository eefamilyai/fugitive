package app.fugitive.core.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.fugitive.core.location.LocationPermissions

/** Notification channels and builders shared by the tracking service and alerts. */
object Notifications {
    const val CHANNEL_STATUS = "game_status"
    const val CHANNEL_ALERTS = "game_alerts"
    const val ID_TRACKING = 1001
    private var alertId = 2000

    /** The activity to open from notifications; set by the app module. */
    var launchIntentFactory: (Context) -> Intent? = { it.packageManager.getLaunchIntentForPackage(it.packageName) }
    /** Small icon resource; set by the app module. */
    var smallIcon: Int = android.R.drawable.ic_menu_mylocation

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, "Game in progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows the running game and keeps location sharing alive"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Game alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Catches, zone warnings and nearby hunters"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 120, 250)
            },
        )
    }

    fun contentIntent(context: Context): PendingIntent? = launchIntentFactory(context)?.let {
        it.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun tracking(context: Context, title: String, text: String) =
        NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(contentIntent(context))
            .build()

    fun alert(context: Context, title: String, text: String) {
        if (!LocationPermissions.hasNotifications(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(alertId++, n) }
    }
}
