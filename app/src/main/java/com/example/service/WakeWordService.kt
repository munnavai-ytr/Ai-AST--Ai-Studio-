package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.voice.VoiceProfileRepository
import com.example.voice.WakeWordDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class WakeWordService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeWordDetector: WakeWordDetector? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "WakeWordService onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_SERVICE) {
            stopForegroundService()
            return START_NOT_STICKY
        }

        startForegroundService()
        initDetector()

        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = createNotification("Listening for 'Hey Mimi' (Voice profile active)")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    0
                }
                try {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        foregroundServiceType
                    )
                } catch (_: SecurityException) {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            WakeWordStateManager.setServiceRunning(true, "Listening for 'Hey Mimi'...")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service: ${e.message}")
        }
    }

    private fun initDetector() {
        if (wakeWordDetector != null) return

        wakeWordDetector = WakeWordDetector(
            context = applicationContext,
            profileRepository = VoiceProfileRepository.getInstance(applicationContext),
            onWakeWordDetected = { matchResult ->
                vibrateDevice()
                launchAssistantOnWake()
                updateNotification("Hey Mimi detected! Opening assistant...")
            },
            onOtherVoiceIgnored = { similarity, explanation ->
                updateNotification("Ignored other voice (${similarity.toInt()}% match)")
            }
        )

        // Register hooks with SpeechRecognitionManager to coordinate with manual mic
        com.example.voice.SpeechRecognitionManager.registerWakeWordHooks(
            onPause = {
                wakeWordDetector?.pauseListeningForManual()
                updateNotification("Listening paused (Manual microphone active)")
            },
            onResume = {
                wakeWordDetector?.resumeListeningFromManual()
                updateNotification("Listening for 'Hey Mimi' (Voice profile active)")
            }
        )

        wakeWordDetector?.startListening(serviceScope)
    }

    private fun launchAssistantOnWake() {
        try {
            if (Settings.canDrawOverlays(this)) {
                Log.d(TAG, "Launching FloatingUIService overlay window on wake word")
                FloatingUIService.start(this)
            } else {
                Log.d(TAG, "Overlay permission not granted; launching MainActivity")
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_WAKE_TRIGGERED, true)
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch assistant on wake: ${e.message}")
        }
    }

    private fun vibrateDevice() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(150)
            }
        } catch (_: Exception) {}
    }

    private fun createNotification(statusText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, WakeWordService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mimi Assistant Background Listener")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Open App", openPendingIntent)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hey Mimi Wake-Word Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors for 'Hey Mimi' wake-word in background using enrolled voice profile"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundService() {
        com.example.voice.SpeechRecognitionManager.unregisterWakeWordHooks()
        wakeWordDetector?.stopListening()
        wakeWordDetector = null
        serviceScope.cancel()
        WakeWordStateManager.setServiceRunning(false, "Service stopped")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        com.example.voice.SpeechRecognitionManager.unregisterWakeWordHooks()
        wakeWordDetector?.stopListening()
        wakeWordDetector = null
        serviceScope.cancel()
        WakeWordStateManager.setServiceRunning(false)
        super.onDestroy()
    }

    companion object {
        const val TAG = "WakeWordService"
        const val CHANNEL_ID = "mimi_wake_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_START_SERVICE = "com.example.service.START_WAKE_WORD"
        const val ACTION_STOP_SERVICE = "com.example.service.STOP_WAKE_WORD"
        const val EXTRA_WAKE_TRIGGERED = "extra_wake_triggered"

        fun start(context: Context) {
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
