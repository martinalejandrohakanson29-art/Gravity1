package com.example.gravity1

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var etBlockedApps: EditText

    // Definimos el "lanzador" que esperará la respuesta de la lista de apps
    private val getAppsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Obtenemos el texto que nos mandó AppPickerActivity
            val newApps = result.data?.getStringExtra("selected_apps") ?: ""

            if (newApps.isNotEmpty()) {
                val currentText = etBlockedApps.text.toString().trim()

                // Lógica inteligente para sumar texto:
                if (currentText.isEmpty()) {
                    etBlockedApps.setText(newApps)
                } else {
                    // Si ya había texto, agregamos una coma si falta y luego las nuevas
                    val separator = if (currentText.endsWith(",")) " " else ", "
                    etBlockedApps.setText("$currentText$separator$newApps")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val etStartHour = findViewById<EditText>(R.id.etStartHour)
        val etEndHour = findViewById<EditText>(R.id.etEndHour)
        etBlockedApps = findViewById(R.id.etBlockedApps) // Inicializamos la variable global
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnSelectApps = findViewById<Button>(R.id.btnSelectApps)

        // Cargar datos guardados
        val prefs = getSharedPreferences("FocusPrefs", Context.MODE_PRIVATE)
        etStartHour.setText(prefs.getInt("start_hour", 9).toString())
        etEndHour.setText(prefs.getInt("end_hour", 17).toString())
        etBlockedApps.setText(prefs.getString("blocked_apps", ""))

        // Acción del Botón "Elegir de la lista"
        btnSelectApps.setOnClickListener {
            val intent = Intent(this, AppPickerActivity::class.java)
            // Usamos el lanzador en lugar de startActivity normal
            getAppsLauncher.launch(intent)
        }

        // Acción del Botón Guardar
        btnSave.setOnClickListener {
            val start = etStartHour.text.toString().toIntOrNull() ?: 9
            val end = etEndHour.text.toString().toIntOrNull() ?: 17
            val apps = etBlockedApps.text.toString().trim()

            // Guardar la configuración de forma síncrona
            prefs.edit().apply {
                putInt("start_hour", start)
                putInt("end_hour", end)
                putString("blocked_apps", apps)
                commit() // Usar commit() en lugar de apply() para garantizar que se guarde inmediatamente
            }

            // Actualizar servicio y reactivar bloqueo inmediatamente
            BlockerService.refreshSettings(this)
            BlockerService.isBlocked = true

            // Verificar que se guardó correctamente
            val saved = prefs.getString("blocked_apps", "")
            android.util.Log.d("FocusShield", "Configuración guardada: '$saved'")
            android.util.Log.d("FocusShield", "Apps bloqueadas: ${BlockerService.blacklistedPackages}")
            android.util.Log.d("FocusShield", "Sitios bloqueados: ${BlockerService.bannedSites}")

            Toast.makeText(this, "Configuración Guardada y BLOQUEO ACTIVADO\nSitios: ${BlockerService.bannedSites.size}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}