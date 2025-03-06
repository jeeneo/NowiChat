package com.nowichat

import android.util.Log


fun comprobacion(mac: String): Boolean {
    val funcion = Regex("^([0-9A-F]{2}){5}([0-9A-F]{2})")

    if (funcion.matches(mac.split(":").joinToString(""))) {
        return true
    }
    return false
}