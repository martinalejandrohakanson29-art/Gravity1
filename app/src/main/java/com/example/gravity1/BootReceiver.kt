package com.example.gravity1

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("FocusShield", "BootReceiver: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                // Verificar si el servicio de accesibilidad está habilitado
                if (isAccessibilityServiceEnabled(context)) {
                    Log.d("FocusShield", "Servicio de accesibilidad habilitado, iniciando servicio")
                    startBlockerService(context)
                } else {
                    Log.d("FocusShield", "Servicio de accesibilidad NO habilitado")
                }
            }
        }
    }

    private fun startBlockerService(context: Context) {
        try {
            val serviceIntent = Intent(context, BlockerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d("FocusShield", "Servicio iniciado desde BootReceiver")
        } catch (e: Exception) {
            Log.e("FocusShield", "Error iniciando servicio: ${e.message}")
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

        for (service in enabledServices) {
            if (service.id.contains(context.packageName)) {
                return true
            }
        }
        return false
    }
}
