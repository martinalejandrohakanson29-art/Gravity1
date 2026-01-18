package com.example.gravity1

import android.accessibilityservice.AccessibilityService
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.util.*

class BlockerService : AccessibilityService() {

    private var lastActionTime = 0L // Para evitar bucles de bloqueo infinitos
    private val watchdogHandler = Handler(Looper.getMainLooper())
    private var watchdogRunnable: Runnable? = null

    companion object {
        var isBlocked = true
        var blacklistedPackages = mutableSetOf<String>()
        var startHour = 9
        var endHour = 17

        @Volatile
        var serviceInstance: BlockerService? = null

        private val bannedKeywords = listOf("porn", "xxx", "sexo", "hentai", "xvideos")
        // Esta lista ahora se llena desde SharedPreferences
        var bannedSites = mutableListOf<String>()

        // Lista de navegadores soportados
        private val supportedBrowsers = setOf(
            "com.android.chrome",           // Google Chrome
            "org.mozilla.firefox",          // Firefox
            "com.brave.browser",            // Brave
            "com.microsoft.emmx",           // Microsoft Edge
            "com.opera.browser",            // Opera
            "com.opera.mini.native",        // Opera Mini
            "com.duckduckgo.mobile.android",// DuckDuckGo
            "org.mozilla.focus",            // Firefox Focus
            "com.kiwibrowser.browser",      // Kiwi Browser
            "com.sec.android.app.sbrowser"  // Samsung Internet
        )

        fun isServiceRunning(): Boolean {
            return serviceInstance != null
        }

        fun refreshSettings(context: Context) {
            val prefs = context.getSharedPreferences("FocusPrefs", Context.MODE_PRIVATE)
            startHour = prefs.getInt("start_hour", 9)
            endHour = prefs.getInt("end_hour", 17)
            val appsString = prefs.getString("blocked_apps", "") ?: ""

            Log.d("FocusShield", "refreshSettings llamado. String original: '$appsString'")

            blacklistedPackages.clear()
            bannedSites.clear()

            if (appsString.isNotEmpty()) {
                val allBlockedItems = appsString.split(",").map { it.trim() }
                Log.d("FocusShield", "Items después de split: $allBlockedItems")

                for (item in allBlockedItems) {
                    if (item.isEmpty()) continue // Saltar items vacíos

                    // Clasificar correctamente: packages de Android tienen formato "com.company.app"
                    // Sitios web son como "facebook.com", "instagram.com"
                    val dotCount = item.count { it == '.' }
                    Log.d("FocusShield", "Procesando '$item' - puntos: $dotCount")

                    when {
                        // Paquete de Android: tiene al menos 2 puntos (ej: com.instagram.android)
                        dotCount >= 2 -> {
                            blacklistedPackages.add(item)
                            Log.d("FocusShield", "  -> Agregado como APP")
                        }
                        // Sitio web: tiene 1 punto (ej: facebook.com) o es un dominio
                        dotCount == 1 -> {
                            bannedSites.add(item)
                            Log.d("FocusShield", "  -> Agregado como SITIO WEB")
                        }
                        // Sin punto: probablemente un identificador, lo tratamos como app
                        else -> {
                            blacklistedPackages.add(item)
                            Log.d("FocusShield", "  -> Agregado como APP (sin puntos)")
                        }
                    }
                }
            }
            Log.d("FocusShield", "=== CONFIGURACIÓN FINAL ===")
            Log.d("FocusShield", "Apps bloqueadas (${blacklistedPackages.size}): $blacklistedPackages")
            Log.d("FocusShield", "Sitios bloqueados (${bannedSites.size}): $bannedSites")
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInstance = this
        refreshSettings(this)
        startPersistentNotification()
        startWatchdog()
        Log.d("FocusShield", "=== SERVICIO CONECTADO Y ACTIVO ===")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceInstance = null
        stopWatchdog()
        Log.d("FocusShield", "=== SERVICIO DESTRUIDO ===")
        // Programar reinicio
        scheduleRestart()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d("FocusShield", "=== SERVICIO DESVINCULADO ===")
        serviceInstance = null
        scheduleRestart()
        return super.onUnbind(intent)
    }

    private fun startWatchdog() {
        watchdogRunnable = object : Runnable {
            override fun run() {
                Log.d("FocusShield", "Watchdog: servicio activo")
                // Mantener la notificación actualizada para evitar que el sistema mate el servicio
                startPersistentNotification()
                watchdogHandler.postDelayed(this, 30000) // Cada 30 segundos
            }
        }
        watchdogHandler.postDelayed(watchdogRunnable!!, 30000)
    }

    private fun stopWatchdog() {
        watchdogRunnable?.let { watchdogHandler.removeCallbacks(it) }
    }

    private fun scheduleRestart() {
        try {
            val restartIntent = Intent(applicationContext, BlockerService::class.java)
            val pendingIntent = PendingIntent.getService(
                applicationContext,
                42,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 1000,
                pendingIntent
            )
            Log.d("FocusShield", "Reinicio programado en 1 segundo")
        } catch (e: Exception) {
            Log.e("FocusShield", "Error programando reinicio: ${e.message}")
        }
    }

    private fun startPersistentNotification() {
        val channelId = "FocusShieldChannel"
        val channelName = "Focus Shield Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_MIN  // Minima visibilidad, no aparece en barra de estado
            ).apply {
                description = "Mantiene el bloqueo activo"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Focus Shield")
            .setContentText("Proteccion activa")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)  // Minima prioridad visual
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(1, notification)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val currentPackage = event.packageName?.toString() ?: "N/A"

        // Recargar configuración periódicamente (cada vez que cambia de ventana)
        // Esto asegura que siempre tenga la configuración más reciente
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            refreshSettings(this)
        }

        // PROTECCION SIEMPRE ACTIVA (independiente del horario y estado de bloqueo)
        // Evitar que cierren la app desde recientes o la desinstalen
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            if (System.currentTimeMillis() - lastActionTime >= 1500) {
                // Detectar si el usuario está en la pantalla de recientes intentando cerrar la app
                if (isRecentAppsScreenWithOurApp(currentPackage, event)) {
                    lastActionTime = System.currentTimeMillis()
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(this, "Focus Shield no puede cerrarse", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                // Detectar intentos de acceso a configuraciones y desinstalación
                if (isSettingsOrUninstallScreen(currentPackage, event)) {
                    irAlHome()
                    return
                }
            }
        }

        // BLOQUEO DE APPS Y SITIOS (solo durante horario de bloqueo)
        if (!isBlocked) return
        if (!isInBlockingSchedule()) return
        if (System.currentTimeMillis() - lastActionTime < 1500) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            if (blacklistedPackages.contains(currentPackage)) {
                irAlHome()
                return
            }

            // Verificar si es algún navegador soportado
            if (supportedBrowsers.contains(currentPackage)) {
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    findUrlAndBlock(rootNode)
                    rootNode.recycle() // Liberar recursos
                }
            }
        }
    }

    private fun findUrlAndBlock(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val text = node.text?.toString()?.lowercase(Locale.ROOT) ?: ""
        val viewId = node.viewIdResourceName ?: ""
        var isBanned = false

        if (text.isNotEmpty()) {
            // Regla 1: Detección precisa en la barra de URL
            // Diferentes navegadores usan diferentes IDs para la barra de URL
            if (viewId.contains("url_bar") || // Chrome
                viewId.contains("url_field") || // Algunos navegadores
                viewId.contains("mozac_browser_toolbar_url_view") || // Firefox
                viewId.contains("search_src_text") || // Algunos navegadores
                viewId.contains("addressbarEdit")) { // Edge, otros

                // Log para debugging
                Log.d("FocusShield", "URL detectada: '$text' | ViewID: $viewId")
                Log.d("FocusShield", "Sitios bloqueados (${bannedSites.size}): $bannedSites")

                // Verificar si el texto de la URL contiene algún dominio bloqueado
                for (site in bannedSites) {
                    Log.d("FocusShield", "Comparando URL '$text' con sitio bloqueado '$site'")
                    if (isUrlBlocked(text, site)) {
                        Log.d("FocusShield", "¡¡¡ URL BLOQUEADA !!! '$text' coincide con '$site'")
                        isBanned = true
                        break
                    } else {
                        Log.d("FocusShield", "NO coincide")
                    }
                }

                // Verificar palabras clave solo en la URL, no en contenido de página
                if (!isBanned) {
                    for (keyword in bannedKeywords) {
                        if (text.contains(keyword)) {
                            isBanned = true
                            break
                        }
                    }
                }
            }
        }

        if (isBanned) {
            if (node.isEditable) {
                val args = Bundle()
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }
            irAlHome(fromBrowser = true)
            return true // Detiene la recursión
        }

        // Iterar sobre los hijos y reciclar nodos
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val found = findUrlAndBlock(child)
                child.recycle() // Liberar recursos del hijo
                if (found) {
                    return true // Si un hijo encontró algo, detenemos la búsqueda.
                }
            }
        }

        return false
    }

    /**
     * Verifica si una URL debe ser bloqueada comparando con un sitio baneado.
     * Soporta diferentes formatos de dominio (con o sin www, con subdominios, etc.)
     */
    private fun isUrlBlocked(url: String, bannedSite: String): Boolean {
        val normalizedUrl = url.lowercase(Locale.ROOT)
        val normalizedBanned = bannedSite.lowercase(Locale.ROOT)

        // Caso 1: El sitio baneado aparece como dominio exacto
        // Ejemplo: "facebook.com" bloquea "facebook.com", "www.facebook.com", "m.facebook.com"
        if (normalizedUrl.contains("://$normalizedBanned") ||
            normalizedUrl.contains("://www.$normalizedBanned") ||
            normalizedUrl.contains(".$normalizedBanned/") ||
            normalizedUrl.contains(".$normalizedBanned?")) {
            return true
        }

        // Caso 2: El sitio baneado es un subdominio
        // Ejemplo: "m.facebook.com" debe aparecer exacto
        if (normalizedUrl.contains(normalizedBanned)) {
            // Verificar que sea parte del dominio y no solo una subcadena
            val index = normalizedUrl.indexOf(normalizedBanned)
            if (index > 0) {
                val charBefore = normalizedUrl[index - 1]
                // Solo bloquear si está precedido por :// o por un punto (dominio válido)
                if (charBefore == '/' || charBefore == '.') {
                    // Verificar que después venga /, ?, # o fin de string
                    val afterIndex = index + normalizedBanned.length
                    if (afterIndex >= normalizedUrl.length) return true
                    val charAfter = normalizedUrl[afterIndex]
                    if (charAfter == '/' || charAfter == '?' || charAfter == '#' || charAfter == ':') {
                        return true
                    }
                }
            }
        }

        // Caso 3: URL sin protocolo (usuario escribiendo en la barra)
        // Ejemplo: solo "facebook.com" o "facebook"
        if (!normalizedUrl.contains("://")) {
            // Si la URL no tiene protocolo, es más flexible
            if (normalizedUrl == normalizedBanned ||
                normalizedUrl.startsWith("$normalizedBanned/") ||
                normalizedUrl.startsWith("$normalizedBanned?") ||
                normalizedUrl.startsWith("www.$normalizedBanned")) {
                return true
            }

            // Permitir bloquear "facebook" si el usuario solo escribe eso
            val bannedBase = normalizedBanned.substringBefore('.')
            if (normalizedUrl == bannedBase || normalizedUrl.startsWith("$bannedBase/")) {
                return true
            }
        }

        return false
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d("FocusShield", "=== TASK REMOVED - Programando reinicio ===")
        scheduleRestart()
        super.onTaskRemoved(rootIntent)
    }

    private fun irAlHome(fromBrowser: Boolean = false) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastActionTime < 2000) return
        lastActionTime = currentTime

        if (fromBrowser) {
            // Para navegadores: cerrar la pestaña actual con múltiples backs
            // y luego ir al home para asegurar que no quede la página bloqueada
            performGlobalAction(GLOBAL_ACTION_BACK)
            Handler(Looper.getMainLooper()).postDelayed({
                performGlobalAction(GLOBAL_ACTION_BACK)
            }, 100)
            Handler(Looper.getMainLooper()).postDelayed({
                performGlobalAction(GLOBAL_ACTION_BACK)
            }, 200)
            Handler(Looper.getMainLooper()).postDelayed({
                performGlobalAction(GLOBAL_ACTION_HOME)
            }, 350)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
            performGlobalAction(GLOBAL_ACTION_HOME)
        }

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, "🛡️ Contenido bloqueado por Focus Shield", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Detecta si el usuario está en la pantalla de apps recientes y nuestra app es visible
     * (para evitar que la cierre deslizando)
     */
    private fun isRecentAppsScreenWithOurApp(packageName: String, event: AccessibilityEvent): Boolean {
        // Launchers y pantallas de recientes de diferentes fabricantes
        val recentAppsPackages = setOf(
            "com.android.systemui",              // Android stock recents
            "com.android.launcher3",             // AOSP Launcher
            "com.google.android.apps.nexuslauncher", // Pixel Launcher
            "com.miui.home",                     // Xiaomi MIUI
            "com.huawei.android.launcher",       // Huawei
            "com.sec.android.app.launcher",      // Samsung
            "com.oppo.launcher",                 // OPPO
            "com.vivo.launcher",                 // Vivo
            "com.oneplus.launcher",              // OnePlus
            "com.teslacoilsw.launcher",          // Nova Launcher
            "com.microsoft.launcher"             // Microsoft Launcher
        )

        if (!recentAppsPackages.contains(packageName)) {
            return false
        }

        // Buscar si nuestra app aparece en la pantalla
        val rootNode = rootInActiveWindow ?: return false
        val allText = StringBuilder()
        extractAllText(rootNode, allText)
        val screenContent = allText.toString().lowercase(Locale.ROOT)
        rootNode.recycle()

        // Si estamos en recientes Y nuestra app es visible, bloquear
        val ourAppIndicators = listOf("focus shield", "gravity1", "com.example.gravity1")
        for (indicator in ourAppIndicators) {
            if (screenContent.contains(indicator)) {
                Log.d("FocusShield", "Detectado intento de cerrar app desde recientes")
                return true
            }
        }

        return false
    }

    /**
     * Detecta si el usuario está intentando acceder a pantallas peligrosas
     * (configuraciones, desinstalación, administración de apps, etc.)
     */
    private fun isSettingsOrUninstallScreen(packageName: String, event: AccessibilityEvent): Boolean {
        // Lista de paquetes relacionados con configuración del sistema
        val settingsPackages = setOf(
            "com.android.settings",           // Configuración estándar de Android
            "com.miui.securitycenter",        // Xiaomi Security Center
            "com.android.packageinstaller",   // Instalador de paquetes (desinstalación)
            "com.google.android.packageinstaller", // Google Package Installer
            "com.samsung.android.sm",         // Samsung Device Care
            "com.huawei.systemmanager",       // Huawei System Manager
            "com.oppo.safe",                  // OPPO Security
            "com.coloros.safecenter",         // ColorOS Safe Center
            "com.vivo.securedaemonservice",   // Vivo Security
            "com.android.vending"             // Google Play Store (desinstalación)
        )

        if (!settingsPackages.contains(packageName)) {
            return false
        }

        // Obtener el texto visible en la pantalla desde el evento
        val screenText = event.text.toString().lowercase(Locale.ROOT)
        val contentDesc = event.contentDescription?.toString()?.lowercase(Locale.ROOT) ?: ""
        var combinedText = "$screenText $contentDesc"

        // También escanear el árbol de accesibilidad para obtener todo el texto visible
        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            val allText = StringBuilder()
            extractAllText(rootNode, allText)
            combinedText += " " + allText.toString().lowercase(Locale.ROOT)
            rootNode.recycle()
        }

        Log.d("FocusShield", "Escaneando pantalla de settings: $combinedText")

        // Palabras clave que indican intento de manipular la app
        val dangerousKeywords = listOf(
            "focus shield",
            "gravity1",
            "com.example.gravity1",
            "desinstalar",
            "uninstall",
            "detener",
            "stop",
            "forzar detención",
            "force stop",
            "borrar datos",
            "clear data",
            "deshabilitar",
            "disable",
            "permisos",
            "permissions",
            "administrador",
            "device admin",
            "admin del dispositivo"
        )

        // Si encuentra alguna palabra peligrosa, bloquear
        for (keyword in dangerousKeywords) {
            if (combinedText.contains(keyword)) {
                Log.d("FocusShield", "Bloqueando intento de manipulación: $keyword detectado en $packageName")
                return true
            }
        }

        // Bloquear acceso a la pantalla de servicios de accesibilidad
        // (donde se puede desactivar el servicio)
        if (packageName == "com.android.settings") {
            val accessibilityScreenIndicators = listOf(
                "servicios de accesibilidad",
                "accessibility services",
                "accessibility service",
                "apps descargadas",
                "downloaded apps",
                "installed services"
            )
            for (indicator in accessibilityScreenIndicators) {
                if (combinedText.contains(indicator)) {
                    Log.d("FocusShield", "Bloqueando acceso a pantalla de accesibilidad")
                    return true
                }
            }
        }

        return false
    }

    /**
     * Extrae recursivamente todo el texto visible de un nodo de accesibilidad
     */
    private fun extractAllText(node: AccessibilityNodeInfo?, builder: StringBuilder) {
        if (node == null) return

        node.text?.let { builder.append(it).append(" ") }
        node.contentDescription?.let { builder.append(it).append(" ") }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractAllText(child, builder)
                child.recycle()
            }
        }
    }

    private fun isInBlockingSchedule(): Boolean {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        return if (startHour < endHour) hour in startHour until endHour else hour >= startHour || hour < endHour
    }

    override fun onInterrupt() {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startPersistentNotification()
        return START_STICKY
    }
}
