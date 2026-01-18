package com.example.gravity1

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var tvChallenge: TextView
    private lateinit var etResponse: EditText
    private lateinit var btnUnlock: Button
    private lateinit var layoutTimer: LinearLayout
    private lateinit var tvTimer: TextView
    private lateinit var btnRegret: Button

    private var currentChallenge: Int = 0
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvChallenge = findViewById(R.id.tvChallenge)
        etResponse = findViewById(R.id.etResponse)
        btnUnlock = findViewById(R.id.btnUnlock)
        layoutTimer = findViewById(R.id.layoutTimer)
        tvTimer = findViewById(R.id.tvTimer)
        btnRegret = findViewById(R.id.btnRegret)

        generateChallenge()

        btnUnlock.setOnClickListener {
            val response = etResponse.text.toString().toIntOrNull()
            if (validateResponse(response)) {
                startUnlockSequence()
            } else {
                Toast.makeText(this, "Clave incorrecta", Toast.LENGTH_SHORT).show()
                generateChallenge()
            }
        }

        btnRegret.setOnClickListener {
            cancelUnlockSequence()
        }
    }

    override fun onResume() {
        super.onResume()
        // Verificar optimización de batería solo cuando la actividad vuelve al primer plano
        checkBatteryOptimization()
        // Verificar y solicitar privilegios de administrador del dispositivo
        checkDeviceAdmin()
    }

    // Función para pedir permiso de "No matar la app" - solo se ejecuta si es necesario
    private fun checkBatteryOptimization() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager

        // Solo pedir si NO está en la lista blanca de batería
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val prefs = getSharedPreferences("FocusPrefs", Context.MODE_PRIVATE)
            val batteryPromptShown = prefs.getBoolean("battery_prompt_shown", false)

            // No volver a mostrar si el usuario ya lo rechazó previamente
            if (!batteryPromptShown) {
                val intent = Intent()
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                try {
                    startActivity(intent)
                    // Marcar que ya mostramos el prompt
                    prefs.edit().putBoolean("battery_prompt_shown", true).apply()
                } catch (e: Exception) {
                    // En algunos teléfonos este intent puede fallar, abrimos la configuración general
                    try {
                        intent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                        startActivity(intent)
                        prefs.edit().putBoolean("battery_prompt_shown", true).apply()
                    } catch (ex: Exception) {
                        Log.e("FocusShield", "Error abriendo configuración de batería: ${ex.message}")
                    }
                }
            }
        }
    }

    private fun generateChallenge() {
        currentChallenge = Random().nextInt(900000) + 100000
        tvChallenge.text = "Código de Desafío: $currentChallenge"
        etResponse.text.clear()
    }

    private fun validateResponse(response: Int?): Boolean {
        if (response == null) return false
        val expected = (currentChallenge * 7 + 1337) % 1000000
        return response == expected
    }

    private fun startUnlockSequence() {
        btnUnlock.visibility = View.GONE
        layoutTimer.visibility = View.VISIBLE

        countDownTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                tvTimer.text = String.format("%02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                unlockSystem()
            }
        }.start()
    }

    private fun cancelUnlockSequence() {
        countDownTimer?.cancel()
        layoutTimer.visibility = View.GONE
        btnUnlock.visibility = View.VISIBLE
        generateChallenge()
        Toast.makeText(this, "Desbloqueo cancelado. ¡Mantente enfocado!", Toast.LENGTH_LONG).show()
    }

    /**
     * Verifica si la app tiene privilegios de administrador del dispositivo.
     * Si no los tiene, solicita al usuario que los active.
     */
    private fun checkDeviceAdmin() {
        val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(this, FocusDeviceAdminReceiver::class.java)

        // Si ya es administrador, no hacer nada
        if (devicePolicyManager.isAdminActive(componentName)) {
            return
        }

        // Verificar si ya se solicitó antes
        val prefs = getSharedPreferences("FocusPrefs", Context.MODE_PRIVATE)
        val adminPromptShown = prefs.getBoolean("admin_prompt_shown", false)

        if (!adminPromptShown) {
            // Crear el intent para solicitar privilegios de administrador
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            intent.putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Focus Shield necesita privilegios de administrador para protegerse contra " +
                        "desinstalación accidental durante tus períodos de concentración. " +
                        "Esto ayuda a mantener tu compromiso con la productividad."
            )

            try {
                startActivity(intent)
                // Marcar que ya mostramos el prompt
                prefs.edit().putBoolean("admin_prompt_shown", true).apply()
            } catch (e: Exception) {
                Log.e("FocusShield", "Error solicitando privilegios de admin: ${e.message}")
                Toast.makeText(
                    this,
                    "No se pudo solicitar protección contra desinstalación",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun unlockSystem() {
        BlockerService.isBlocked = false
        Toast.makeText(this, "Sistema Desbloqueado. Configura tus bloqueos.", Toast.LENGTH_LONG).show()

        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
        finish()
    }
}