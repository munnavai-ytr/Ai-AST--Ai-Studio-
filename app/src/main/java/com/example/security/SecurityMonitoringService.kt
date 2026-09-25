package com.example.security

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.devicesecurity.DeviceSecurityPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that runs owner-controlled security monitoring:
 * 1. AccessibilityTamperWatcher: monitors accessibility service state
 * 2. AppInstallWatcher: monitors newly installed applications
 */
class SecurityMonitoringService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var accessibilityWatcher: AccessibilityTamperWatcher? = null
    private var appInstallWatcher: AppInstallWatcher? = null
    private var periodicPackageCheckJob: Job? = null
    private var lastKnownInstalledPackages: Set<String> = emptySet()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SecurityMonitoringService onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_SERVICE) {
            stopMonitoringService()
            return START_NOT_STICKY
        }

        startForegroundService()
        startWatchers()

        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = createNotification("Monitoring accessibility integrity & app installations")

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                0
            )
            _isServiceRunning.value = true
            _serviceStatusMessage.value = "Security monitoring active"
            DeviceSecurityPreferences.setSecurityMonitoringEnabled(this, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting security foreground service: ${e.message}")
        }
    }

    private fun startWatchers() {
        // 1. Accessibility Tamper Watcher
        if (accessibilityWatcher == null) {
            accessibilityWatcher = AccessibilityTamperWatcher(applicationContext).apply {
                startWatching(serviceScope)
            }
        }

        // 2. App Install Dynamic Receiver
        if (appInstallWatcher == null) {
            try {
                val receiver = AppInstallWatcher()
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REPLACED)
                    addDataScheme("package")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    registerReceiver(receiver, filter)
                }
                appInstallWatcher = receiver
            } catch (e: Exception) {
                Log.e(TAG, "Error registering AppInstallWatcher receiver: ${e.message}")
            }
        }

        // 3. Fallback Periodic Package Diff Checker (Every 5 minutes)
        initPackageSnapshot()
        periodicPackageCheckJob?.cancel()
        periodicPackageCheckJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(5 * 60 * 1000L)
                checkNewPackagesDiff()
            }
        }
    }

    private fun initPackageSnapshot() {
        try {
            val pm = packageManager
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            lastKnownInstalledPackages = installed.map { it.packageName }.toSet()
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing initial package snapshot", e)
        }
    }

    private fun checkNewPackagesDiff() {
        try {
            val pm = packageManager
            val currentInstalled = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .map { it.packageName }.toSet()

            val newlyAdded = currentInstalled - lastKnownInstalledPackages
            for (pkg in newlyAdded) {
                if (pkg != packageName) {
                    Log.i(TAG, "Periodic check detected new package: $pkg")
                    AppInstallWatcher.logAppInstallEvent(applicationContext, pkg, "PACKAGE_ADDED")
                }
            }
            lastKnownInstalledPackages = currentInstalled
        } catch (e: Exception) {
            Log.e(TAG, "Error checking package diff", e)
        }
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

        val stopIntent = Intent(this, SecurityMonitoringService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mimi Security Monitoring Active")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Open App", openPendingIntent)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mimi Security Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors accessibility service status and app installs for device security alerts"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun stopMonitoringService() {
        periodicPackageCheckJob?.cancel()
        periodicPackageCheckJob = null

        accessibilityWatcher?.stopWatching()
        accessibilityWatcher = null

        appInstallWatcher?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver: ${e.message}")
            }
            appInstallWatcher = null
        }

        serviceScope.cancel()
        _isServiceRunning.value = false
        _serviceStatusMessage.value = "Security monitoring inactive"
        DeviceSecurityPreferences.setSecurityMonitoringEnabled(this, false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopMonitoringService()
        super.onDestroy()
    }

    companion object {
        const val TAG = "SecurityMonitoringSvc"
        const val CHANNEL_ID = "security_monitoring_channel"
        const val NOTIFICATION_ID = 3001
        const val ACTION_START_SERVICE = "com.example.security.START_MONITORING"
        const val ACTION_STOP_SERVICE = "com.example.security.STOP_MONITORING"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _serviceStatusMessage = MutableStateFlow("Security monitoring inactive")
        val serviceStatusMessage: StateFlow<String> = _serviceStatusMessage.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, SecurityMonitoringService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SecurityMonitoringService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
