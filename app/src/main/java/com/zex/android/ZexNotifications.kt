package com.zex.android

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val CHANNEL_ID = "zex_updates"
private const val DAILY_NOTIFICATION_ID = 20
private const val UPDATE_NOTIFICATION_ID = 21
private const val INSTALLED_NOTIFICATION_ID = 22

object ZexNotifications {
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "ZEX reminders and updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Daily ZEX reminders and new app version alerts"
                }
            )
        }
    }

    fun areAllowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            (Build.VERSION.SDK_INT < 24 || context.getSystemService(NotificationManager::class.java).areNotificationsEnabled())

    fun show(context: Context, id: Int, title: String, message: String, openRelease: Boolean = false) {
        createChannel(context)
        if (!areAllowed(context)) return
        val intent = if (openRelease) {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/syedtayyabdev01/zex/releases/latest"))
        } else {
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
        val pending = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL_ID) else Notification.Builder(context)
        val notification = builder
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }
}

class DailyNotificationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        if (!ZexNotifications.areAllowed(applicationContext)) return Result.success()

        val release = fetchLatestRelease()
        if (release != null && isNewer(release, BuildConfig.VERSION_NAME)) {
            val preferences = applicationContext.getSharedPreferences("zex_notifications", Context.MODE_PRIVATE)
            if (preferences.getString("last_update_notice", null) != release) {
                ZexNotifications.show(
                    applicationContext,
                    UPDATE_NOTIFICATION_ID,
                    "ZEX update available",
                    "Version $release is ready. Tap to download it from GitHub.",
                    openRelease = true
                )
                preferences.edit().putString("last_update_notice", release).apply()
                return Result.success()
            }
        }

        ZexNotifications.show(applicationContext, DAILY_NOTIFICATION_ID, "Your ZEX is ready", "Come back to ZEX and pick up where you left off.")
        return Result.success()
    }

    private fun fetchLatestRelease(): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("https://api.github.com/repos/syedtayyabdev01/zex/releases/latest").openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 5_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "ZEX-Android")
            }
            if (connection.responseCode !in 200..299) return null
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = release.optString("tag_name").trim()
            val version = tag.removePrefix("v").removePrefix("V")
            if (version.isBlank()) null else version
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun isNewer(candidate: String, installed: String): Boolean {
        val newParts = Regex("\\d+").findAll(candidate).mapNotNull { it.value.toIntOrNull() }.toList()
        val installedParts = Regex("\\d+").findAll(installed).mapNotNull { it.value.toIntOrNull() }.toList()
        if (newParts.isEmpty() || installedParts.isEmpty()) return false
        for (index in 0 until maxOf(newParts.size, installedParts.size)) {
            val comparison = (newParts.getOrElse(index) { 0 }).compareTo(installedParts.getOrElse(index) { 0 })
            if (comparison != 0) return comparison > 0
        }
        return false
    }
}

class AppUpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ZexNotifications.show(context, INSTALLED_NOTIFICATION_ID, "ZEX updated", "ZEX Android ${BuildConfig.VERSION_NAME} is installed and ready.")
        }
    }
}
