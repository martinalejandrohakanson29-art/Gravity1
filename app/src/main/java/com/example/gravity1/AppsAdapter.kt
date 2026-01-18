package com.example.gravity1

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView

class AppsAdapter(context: Context, private val apps: List<AppInfo>) :
    ArrayAdapter<AppInfo>(context, 0, apps) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        // 1. Obtener o crear la vista del renglón
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_app_select, parent, false)

        // 2. Obtener la app actual
        val app = getItem(position) ?: return view

        // 3. Conectar elementos visuales
        val tvName = view.findViewById<TextView>(R.id.tvAppName)
        val tvPackage = view.findViewById<TextView>(R.id.tvPackageName)
        val imgIcon = view.findViewById<ImageView>(R.id.imgAppIcon)
        val cbSelected = view.findViewById<CheckBox>(R.id.cbSelected)

        // 4. Poner los datos
        tvName.text = app.name
        tvPackage.text = app.packageName
        imgIcon.setImageDrawable(app.icon)

        // IMPORTANTE: Quitar el listener anterior para evitar errores al reciclar vistas
        cbSelected.setOnCheckedChangeListener(null)

        // 5. Marcar si está seleccionada
        cbSelected.isChecked = app.isSelected

        // 6. Escuchar cambios (Si el usuario toca el checkbox)
        cbSelected.setOnCheckedChangeListener { _, isChecked ->
            app.isSelected = isChecked
        }

        // Hacer que tocar todo el renglón también marque el checkbox
        view.setOnClickListener {
            cbSelected.isChecked = !cbSelected.isChecked
        }

        return view
    }
}