package com.example.gravity1

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.util.*

class BlockerService : AccessibilityService() {

    companion object {
        var isBlocked = true
        var blacklistedPackages = mutableSetOf<String>()
        var startHour = 9
        var endHour = 17

        fun refreshSettings(context: Context) {
            val prefs = context.getSharedPreferences("FocusPrefs", Context.MODE_PRIVATE)
            startHour = prefs.getInt("start_hour", 9)
            endHour = prefs.getInt("end_hour", 17)

            val appsString = prefs.getString("blocked_apps", "") ?: ""
            blacklistedPackages.clear()
            if (appsString.isNotEmpty()) {
                val splitApps = appsString.split(",").map { it.trim() }
                blacklistedPackages.addAll(splitApps)
            }
            Log.d("FocusShield", "Configuración actualizada. Apps: $blacklistedPackages")
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 1. Cargar configuración inmediatamente
        refreshSettings(this)

        // 2. ACTIVAR MODO INMORTAL DE FORMA SEGURA
        try {
            startPersistentNotification()
        } catch (e: Exception) {
            Log.e("FocusShield", "Error al iniciar servicio en primer plano", e)
        }
    }

    private fun startPersistentNotification() {
        val channelId = "FocusShieldChannel"
        val channelName = "Focus Shield Service"

        // Crear canal silencioso
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_MIN // Mínima importancia (Discreción total)
            ).apply {
                description = "Mantiene el bloqueo activo"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        // Notificación "Pegajosa" (Ongoing) -> CLAVE PARA QUE NO LO MATEN
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Focus Shield")
            .setContentText("Protección Activa")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true) // NO SE PUEDE BORRAR DESLIZANDO
            .build()

        startForeground(1, notification)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isBlocked) return
        val packageName = event.packageName?.toString() ?: return

        // Evitar bloquearse a sí mismo o la UI del sistema
        if (packageName == this.packageName || packageName == "com.android.systemui") return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

            val className = event.className?.toString() ?: ""

            // Solo actuamos si estamos en horario de bloqueo
            if (isInBlockingSchedule()) {

                // --- REGLA 1: AUTO-DEFENSA (Bloqueo Quirúrgico de Ajustes) ---
                // Si intenta entrar a configurar notificaciones (para matar la app) -> BLOQUEAR
                // Si intenta entrar a la info de la app (para forzar cierre) -> BLOQUEAR
                if (packageName == "com.android.settings" &&
                    (className.contains("AppNotificationSettingsActivity") ||
                            className.contains("Notification") ||
                            className.contains("InstalledAppDetails"))) {

                    Log.d("FocusShield", "🛡️ Intento de sabotaje detectado. Bloqueando.")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    Toast.makeText(this, "🛡️ Ajustes protegidos por Focus Shield", Toast.LENGTH_SHORT).show()
                    return
                }

                // --- REGLA 2: BLOQUEO DE APPS LISTA NEGRA ---
                if (blacklistedPackages.contains(packageName)) {
                    Log.d("FocusShield", "🚫 Bloqueando: $packageName")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
        }
    }

    private fun isInBlockingSchedule(): Boolean {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        return if (startHour < endHour) {
            hour in startHour until endHour
        } else {
            hour >= startHour || hour < endHour
        }
    }

    override fun onInterrupt() { }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}
