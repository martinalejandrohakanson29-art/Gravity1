package com.example.gravity1

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

class AppPickerActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var btnConfirm: Button
    private lateinit var adapter: AppsAdapter
    private val appList = mutableListOf<AppInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)

        listView = findViewById(R.id.lvApps)
        btnConfirm = findViewById(R.id.btnConfirmSelection)

        loadInstalledApps()

        btnConfirm.setOnClickListener {
            returnSelectedApps()
        }
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        // Obtener todas las apps instaladas
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)

        // Lista temporal para ordenar
        val tempAppList = mutableListOf<AppInfo>()

        for (packageInfo in packages) {
            // 1. Verificamos si la app se puede "abrir" (tiene interfaz)
            if (pm.getLaunchIntentForPackage(packageInfo.packageName) != null) {

                // Excluir nuestra propia app para no bloquearnos a nosotros mismos
                if (packageInfo.packageName == packageName) continue

                // CORRECCIÓN DEL ERROR AQUÍ:
                // Guardamos la info en una variable y verificamos que no sea nula (null)
                val appInfo = packageInfo.applicationInfo

                if (appInfo != null) {
                    val appName = appInfo.loadLabel(pm).toString()
                    val icon = appInfo.loadIcon(pm)

                    tempAppList.add(AppInfo(appName, packageInfo.packageName, icon))
                }
            }
        }

        // Ordenar alfabéticamente por nombre
        tempAppList.sortBy { it.name.lowercase() }

        appList.clear()
        appList.addAll(tempAppList)

        adapter = AppsAdapter(this, appList)
        listView.adapter = adapter
    }

    private fun returnSelectedApps() {
        // Filtrar solo las que tienen el checkbox marcado
        val selectedPackages = appList.filter { it.isSelected }.map { it.packageName }

        // Convertir la lista a un texto separado por comas
        val resultString = selectedPackages.joinToString(", ")

        // Devolver el resultado a SettingsActivity
        val resultIntent = Intent()
        resultIntent.putExtra("selected_apps", resultString)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}