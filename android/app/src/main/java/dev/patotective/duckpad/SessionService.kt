package dev.patotective.duckpad

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log

/**
 * Acompaña a la sesión del mando. Al quitar la app de recientes Android mata el proceso salvo que tenga un servicio
 * en primer plano; sin él moriría con el PC conectado, Android retiraría los servicios en caliente y Windows borraría
 * el mando. Así llega onTaskRemoved, se corta el enlace y el servicio se para cuando el servidor ya está cerrado.
 */
class SessionService : Service(), BlePad.Listener {

    override fun onCreate() {
        super.onCreate()
        BlePad.addListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_END) {
            BlePad.endSession()
            if (BlePad.idle) stopSelf()
            return START_NOT_STICKY
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.session_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val end = PendingIntent.getService(this, 1,
            Intent(this, SessionService::class.java).setAction(ACTION_END), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.session_notification))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.session_end), end).build())
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i("DuckPadBLE", "app quitada de recientes: fin de sesión")
        BlePad.endSession()
    }

    override fun onBleChanged() {
        if (!BlePad.sessionOpen && BlePad.idle) stopSelf()
    }

    override fun onDestroy() {
        BlePad.removeListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private companion object {
        const val ACTION_END = "dev.patotective.duckpad.END_SESSION"
        const val CHANNEL = "session"
        const val NOTIFICATION_ID = 1
    }
}
