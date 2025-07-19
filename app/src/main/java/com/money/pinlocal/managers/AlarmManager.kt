// File: managers/AlarmManager.kt (Complete Updated Version)
package com.money.pinlocal.managers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.money.pinlocal.R
import com.money.pinlocal.MainActivity
import kotlinx.coroutines.*

class AlarmManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {

    companion object {
        private const val CHANNEL_ID_NORMAL = "pinlocal_alerts"
        private const val CHANNEL_ID_ALARM = "pinlocal_alarm_alerts"
        private const val NOTIFICATION_ID_ALERT = 1001
        private const val ALARM_DURATION_MS = 3000L // 3 seconds
    }

    private var alarmPlayer: MediaPlayer? = null
    private var alarmJob: Job? = null
    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Normal notification channel
            val normalChannel = NotificationChannel(
                CHANNEL_ID_NORMAL,
                "Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Normal alert notifications"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
            }

            // High priority alarm channel that overrides silent mode
            val alarmChannel = NotificationChannel(
                CHANNEL_ID_ALARM,
                "Emergency Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority alerts that override silent mode"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setBypassDnd(true) // Override Do Not Disturb

                // Set custom alarm sound
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()

                setSound(alarmUri, audioAttributes)
            }

            notificationManager.createNotificationChannel(normalChannel)
            notificationManager.createNotificationChannel(alarmChannel)
        }
    }

    // Main method to trigger alert - checks user preference
    fun triggerAlert(title: String, message: String, category: String = "SAFETY") {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"
        val shouldOverrideSilent = preferencesManager.getAlarmOverrideSilent()

        if (shouldOverrideSilent) {
            triggerHighPriorityAlarm(title, message, category, isSpanish)
        } else {
            triggerNormalNotification(title, message, category, isSpanish)
        }
    }

    // Force high priority alarm (for emergency situations)
    fun triggerEmergencyAlarm(title: String, message: String, category: String = "SAFETY") {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"
        triggerHighPriorityAlarm(title, message, category, isSpanish)
    }

    // Force normal notification (for non-emergency situations)
    fun triggerSilentNotification(title: String, message: String, category: String = "SAFETY") {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"
        triggerNormalNotification(title, message, category, isSpanish)
    }

    private fun triggerHighPriorityAlarm(title: String, message: String, category: String, isSpanish: Boolean) {
        // Force volume to maximum for emergency
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

        // Play more intense alarm
        playAlarmSound()

        // Stronger vibration pattern
        triggerIntenseVibration()

        // Show high priority notification
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_alerts", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALARM)
            .setSmallIcon(getNotificationIcon(category))
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pendingIntent, true)
            .addAction(
                R.drawable.ic_notifications,
                if (isSpanish) "Ver Alertas" else "View Alerts",
                pendingIntent
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID_ALERT, notification)

        // Auto-stop alarm after longer duration and restore volume
        alarmJob = CoroutineScope(Dispatchers.Main).launch {
            delay(5000L) // 5 seconds instead of 3
            stopAlarmSound()
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
        }
    }

    private fun triggerIntenseVibration() {
        try {
            val vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)
            vibrator?.let { v ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // More intense vibration pattern - longer bursts
                    val pattern = longArrayOf(0, 1000, 300, 1000, 300, 1000, 300, 1000)
                    val effect = VibrationEffect.createWaveform(pattern, -1)
                    v.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(longArrayOf(0, 1000, 300, 1000, 300, 1000, 300, 1000), -1)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AlarmManager", "Error triggering intense vibration: ${e.message}")
        }
    }
    private fun triggerNormalNotification(title: String, message: String, category: String, isSpanish: Boolean) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_alerts", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_NORMAL)
            .setSmallIcon(getNotificationIcon(category))
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_notifications,
                if (isSpanish) "Ver Alertas" else "View Alerts",
                pendingIntent
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID_ALERT, notification)
    }

    private fun playAlarmSound() {
        try {
            stopAlarmSound() // Stop any existing alarm

            // Try to get the most urgent/alarming sound available
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE) // Often more urgent than notification
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            alarmPlayer = MediaPlayer().apply {
                setDataSource(context, alarmUri)

                // Configure to override silent mode
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM) // This overrides silent mode
                        .build()
                )

                isLooping = true
                setVolume(1.0f, 1.0f) // Maximum volume
                prepareAsync()

                setOnPreparedListener { player ->
                    player.start()
                }

                setOnErrorListener { _, _, _ ->
                    playFallbackSound()
                    true
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AlarmManager", "Error playing alarm sound: ${e.message}")
            playFallbackSound()
        }
    }
    private fun playFallbackSound() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // Force volume to maximum for alarm
            val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

            // Play system notification sound
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, notificationUri)
            ringtone?.play()

            // Restore original volume after delay
            CoroutineScope(Dispatchers.Main).launch {
                delay(ALARM_DURATION_MS)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
            }
        } catch (e: Exception) {
            android.util.Log.e("AlarmManager", "Error playing fallback sound: ${e.message}")
        }
    }

    private fun getNotificationIcon(category: String): Int {
        return when (category.uppercase()) {
            "SAFETY" -> R.drawable.ic_report_marker
            "FUN" -> R.drawable.ic_fun_marker
            "LOST" -> R.drawable.ic_lost_marker
            "MULTIPLE" -> R.drawable.ic_notifications
            else -> R.drawable.ic_notifications
        }
    }

    fun stopAlarmSound() {
        try {
            alarmJob?.cancel()
            alarmPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
            alarmPlayer = null
        } catch (e: Exception) {
            android.util.Log.e("AlarmManager", "Error stopping alarm: ${e.message}")
        }
    }

    fun cleanup() {
        stopAlarmSound()
        alarmJob?.cancel()
    }

    // Utility methods for checking user preferences
    fun isAlarmOverrideEnabled(): Boolean {
        return preferencesManager.getAlarmOverrideSilent()
    }

    fun setAlarmOverride(enabled: Boolean) {
        preferencesManager.saveAlarmOverrideSilent(enabled)
    }
}