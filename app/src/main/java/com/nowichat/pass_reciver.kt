package com.nowichat

import android.app.Activity
import android.app.Dialog
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaActionSound
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatEditText
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.addTextChangedListener
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.nowichat.db.device_db
import com.nowichat.db.device_db.Companion.device_list
import com.nowichat.db.mac_db
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec


@RequiresApi(Build.VERSION_CODES.O)
fun very (pass: String): Boolean{
    val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val digest = MessageDigest.getInstance("SHA256")
    if (ks.getKey(pass, null) == null && Base64.getEncoder().withoutPadding().encodeToString(digest.digest(pass.toByteArray())) != pass){
        return false
    }
    return true
}

fun delet_all(context: Activity){
    val mk = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    var pref = EncryptedSharedPreferences.create(context, "as", mk, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    pref.edit().clear().commit()

    pref = EncryptedSharedPreferences.create(context, "ap", mk, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    pref.edit().clear().commit()
    pref.edit().putBoolean("block", true).commit()

    val db_device = device_db(context)
    db_device.all()
    val db_mac = mac_db(context)
    db_mac.all()
    context.finishAffinity()
}

@RequiresApi(Build.VERSION_CODES.O)
fun dialog_pass(context: Activity){
    val mk = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    val pref = EncryptedSharedPreferences.create(context, "ap", mk, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)

    val dialog = Dialog(context)
    val view = LayoutInflater.from(context).inflate(R.layout.password, null)

    val total_opor = view.findViewById<ConstraintLayout>(R.id.opor_total)
    val opor = view.findViewById<TextView>(R.id.opor)
    opor.text = pref.getInt("opor", 3).toString()
    val input_pass = view.findViewById<AppCompatEditText>(R.id.input_pass)
    val very_pass = view.findViewById<ConstraintLayout>(R.id.very_pass)

        total_opor.visibility = View.VISIBLE
        very_pass.visibility = View.INVISIBLE

        input_pass.addTextChangedListener {pass ->
            if (pass?.toList()?.size == pref.getInt("size", 0)){
                if (very(pass.toString())){
                    var final_message = ""
                    val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

                    for ((user, message, iv) in history){
                        val c = Cipher.getInstance("AES/GCM/NoPadding")
                        c.init(Cipher.DECRYPT_MODE, ks.getKey(pass.toString(), null), GCMParameterSpec(128, Base64.getDecoder().decode(iv)))

                        final_message += "$user: ${String(c.doFinal(Base64.getDecoder().decode(message)))}\n\n"
                    }
                    val resolver = context.contentResolver

                    val file = ContentValues().apply {
                        put(MediaStore.Files.FileColumns.DISPLAY_NAME, "History.txt")
                        put(MediaStore.Files.FileColumns.MIME_TYPE, "text/plain")
                        put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }

                    val uri = resolver.insert(MediaStore.Files.getContentUri("external"), file)

                    resolver.openOutputStream(uri!!)?.write(final_message.toByteArray())

                    history.clear()
                    dialog.dismiss()
                    Toast.makeText(context, "Stored conversation", Toast.LENGTH_SHORT).show()

                }else {
                    input_pass.setText("")
                    opor.text = (opor.text.toString().toInt() - 1).toString()
                    pref.edit().putInt("opor", opor.text.toString().toInt()).apply()

                    if (opor.text.toString().toInt() == 0){
                        Toast.makeText(context, "You have exhausted all your opportunities", Toast.LENGTH_SHORT).show()
                        delet_all(context)
                        dialog.dismiss()
                    }
                }
            }
    }
    dialog.setContentView(view)
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    dialog.show()
}
