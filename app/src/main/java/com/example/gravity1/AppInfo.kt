package com.example.gravity1

import android.graphics.drawable.Drawable

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    var isSelected: Boolean = false
)