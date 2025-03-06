package com.nowichat

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64


@RequiresApi(Build.VERSION_CODES.O)
fun very (pass: String): Boolean{
    val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val digest = MessageDigest.getInstance("SHA256")
    if (ks.getKey(pass, null) == null && Base64.getEncoder().withoutPadding().encodeToString(digest.digest(pass.toByteArray())) != pass){
        return false
    }
    return true
}
