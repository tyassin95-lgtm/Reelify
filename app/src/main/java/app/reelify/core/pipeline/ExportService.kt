package app.reelify.core.pipeline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.reelify.MainActivity
import app.reelify.R
import app.reelify.ReelifyApp
import kotlinx.coroutines.launch

/** Foreground service (type mediaProcessing / dataSync) that keeps long exports alive. */
class ExportService : LifecycleService() {
    private val manager by lazy { (application as ReelifyApp).container.exportManager }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_CANCEL) { manager.cancel(); return START_NOT_STICKY }
        ensureChannel()
        val type = if (Build.VERSION.SDK_INT >= 35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        ServiceCompat.startForeground(this, NOTIF_ID, notification("Preparing export", 0, true), type)
        lifecycleScope.launch {
            manager.state.collect { s ->
                val nm = getSystemService(NotificationManager::class.java)
                if (s.running) nm.notify(NOTIF_ID, notification(s.message, s.progress, true))
                else {
                    ServiceCompat.stopForeground(this@ExportService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    if (s.phase == ExportPhase.DONE || s.phase == ExportPhase.FAILED) {
                        nm.notify(NOTIF_DONE_ID, notification(if (s.phase == ExportPhase.DONE) "Your reel is ready" else "Export failed: ${s.error.orEmpty()}", 100, false))
                    }
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        manager.cancel()
        stopSelf()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Video export", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification(text: String, progress: Int, ongoing: Boolean): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val cancel = PendingIntent.getService(this, 1, Intent(this, ExportService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_reelify)
            .setContentTitle("Reelify")
            .setContentText(text)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .apply { if (ongoing) { setProgress(100, progress, progress == 0); addAction(0, "Cancel", cancel) } }
            .build()
    }

    companion object {
        const val CHANNEL = "reelify_export"
        const val NOTIF_ID = 42
        const val NOTIF_DONE_ID = 43
        const val ACTION_CANCEL = "app.reelify.CANCEL_EXPORT"
    }
}
