package com.example.gravity1

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Receptor de administración del dispositivo para proteger la app contra desinstalación.
 *
 * IMPORTANTE: Device Admin API está deprecado para apps de consumidor.
 * Solo funciona en dispositivos que aún lo soporten.
 * Para publicar en Google Play, necesitarías usar Work Profiles o ser una app MDM empresarial.
 */
class FocusDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(
            context,
            "🛡️ Focus Shield ahora está protegido contra desinstalación",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(
            context,
            "⚠️ Protección de Focus Shield desactivada",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // Mensaje que aparece cuando el usuario intenta desactivar el administrador
        return "¿Estás seguro de que quieres desactivar la protección de Focus Shield? " +
                "Esto permitirá desinstalar la aplicación."
    }

    companion object {
        /**
         * Verifica si la app tiene privilegios de administrador activos
         */
        fun isAdminActive(context: Context): Boolean {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                as android.app.admin.DevicePolicyManager
            val componentName = android.content.ComponentName(context, FocusDeviceAdminReceiver::class.java)
            return devicePolicyManager.isAdminActive(componentName)
        }
    }
}
