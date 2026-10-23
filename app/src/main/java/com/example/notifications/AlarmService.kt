package com.example.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.DayFlowDatabase
import com.example.data.ReminderRepository
import com.example.data.UserPreferencesRepository
import com.example.ui.AlarmActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AlarmService : Service() {

    companion object {
        const val CHANNEL_ID = "dayflow_ringing_alarms_channel"
        const val NOTIFICATION_ID = 9001

        const val ACTION_START_ALARM = "com.example.dayflow.ACTION_START_ALARM"
        const val ACTION_STOP_ALARM = "com.example.dayflow.ACTION_STOP_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.example.dayflow.ACTION_SNOOZE_ALARM"

        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DESCRIPTION = "extra_description"
        const val EXTRA_SOUND_URI = "extra_sound_uri"
        const val EXTRA_VIBRATE = "extra_vibrate"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"

        @Volatile
        var isRinging: Boolean = false
            private set

        @Volatile
        var activeReminderId: Long = 0L
            private set

        @Volatile
        var activeTitle: String = ""
            private set

        @Volatile
        var activeDescription: String = ""
            private set

        @Volatile
        var activeSnoozeMinutes: Int = 10
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mediaPlayer: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_ALARM

        when (action) {
            ACTION_START_ALARM -> {
                val reminderId = intent?.getLongExtra(EXTRA_REMINDER_ID, 0L) ?: 0L
                val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Task Alarm" }
                val description = intent?.getStringExtra(EXTRA_DESCRIPTION).orEmpty()
                val soundUriStr = intent?.getStringExtra(EXTRA_SOUND_URI).orEmpty()
                val vibrate = intent?.getBooleanExtra(EXTRA_VIBRATE, true) ?: true
                val snoozeMinutes = intent?.getIntExtra(EXTRA_SNOOZE_MINUTES, 10) ?: 10

                activeReminderId = reminderId
                activeTitle = title
                activeDescription = description
                activeSnoozeMinutes = snoozeMinutes
                isRinging = true

                acquireWakeLock()
                startForegroundNotification(reminderId, title, description, snoozeMinutes)
                startAlarmAudio(soundUriStr)
                if (vibrate) {
                    startVibration()
                }
            }

            ACTION_STOP_ALARM -> {
                stopAlarmAndSelf()
            }

            ACTION_SNOOZE_ALARM -> {
                val reminderId = intent?.getLongExtra(EXTRA_REMINDER_ID, activeReminderId) ?: activeReminderId
                val snoozeMinutes = intent?.getIntExtra(EXTRA_SNOOZE_MINUTES, activeSnoozeMinutes) ?: activeSnoozeMinutes

                serviceScope.launch {
                    try {
                        val db = DayFlowDatabase.getInstance(applicationContext)
                        val scheduler = ReminderScheduler(applicationContext)
                        val prefs = UserPreferencesRepository(applicationContext)
                        val repo = ReminderRepository(db.reminderDao(), scheduler, prefs, db.taskDao())
                        if (reminderId > 0L) {
                            repo.snoozeReminder(reminderId, snoozeMinutes)
                        }
                    } catch (_: Exception) {}
                }
                stopAlarmAndSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "DayFlow:AlarmServiceWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // 10 minutes max safe timeout
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun startForegroundNotification(
        reminderId: Long,
        title: String,
        description: String,
        snoozeMinutes: Int
    ) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ringing Task Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                this.description = "Full-screen audible ringing alarms for scheduled tasks"
                setSound(null, null) // Audio is managed directly by AlarmService for exact looping
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                setBypassDnd(true)
            }
            nm.createNotificationChannel(channel)
        }

        // Full Screen Intent to AlarmActivity
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_DESCRIPTION, description)
            putExtra(EXTRA_SNOOZE_MINUTES, snoozeMinutes)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            reminderId.toInt() + 80000,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss action
        val dismissIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            reminderId.toInt() + 81000,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze action
        val snoozeIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_SNOOZE_ALARM
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_SNOOZE_MINUTES, snoozeMinutes)
        }
        val snoozePendingIntent = PendingIntent.getService(
            this,
            reminderId.toInt() + 82000,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⏰ $title")
            .setContentText(if (description.isNotBlank()) description else "Alarm ringing. Tap or slide to dismiss.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(R.drawable.ic_launcher_foreground, "Snooze ($snoozeMinutes m)", snoozePendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Dismiss", dismissPendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startAlarmAudio(soundUriStr: String) {
        stopAudio()
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val soundUri = if (soundUriStr.isNotBlank() && soundUriStr != "system") {
                Uri.parse(soundUriStr)
            } else {
                RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(applicationContext, soundUri)
                isLooping = true
                setVolume(1.0f, 1.0f)
                prepare()
                start()
            }
        } catch (_: Exception) {
            // Fallback to RingtoneManager if MediaPlayer fails to decode custom URI
            try {
                val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ringtone = RingtoneManager.getRingtone(applicationContext, fallbackUri).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        isLooping = true
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    }
                    play()
                }
            } catch (_: Exception) {}
        }
    }

    private fun startVibration() {
        try {
            val pattern = longArrayOf(0, 800, 400, 800, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 1)
            }
        } catch (_: Exception) {}
    }

    private fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
    }

    private fun stopAudio() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null

        try {
            ringtone?.stop()
        } catch (_: Exception) {}
        ringtone = null
    }

    private fun stopAlarmAndSelf() {
        isRinging = false
        activeReminderId = 0L
        activeTitle = ""
        activeDescription = ""
        stopAudio()
        stopVibration()
        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        isRinging = false
        stopAudio()
        stopVibration()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }
}
