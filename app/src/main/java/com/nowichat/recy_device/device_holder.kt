package com.nowichat.recy_device

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.text.Layout
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.nowichat.R
import com.nowichat.chatActivity
import com.nowichat.d
import com.nowichat.db.device_db
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import com.nowichat.db.device_db.Companion.device_list
import com.nowichat.db.mac_db
import java.security.MessageDigest

class device_holder(view: View): RecyclerView.ViewHolder(view) {

    val fondo = view.findViewById<View>(R.id.fondo)
    val device_n = view.findViewById<TextView>(R.id.device_name)
    val device_m = view.findViewById<TextView>(R.id.device_mac)

    @RequiresApi(Build.VERSION_CODES.O)
    fun device(data : device_data){
        device_n.text = data.name
        device_m.text = data.mac
        val context = device_n.context

        fondo.setOnClickListener {
            val intent = Intent(context, chatActivity::class.java).apply {
                putExtra("pro", true)
                putExtra("name", device_n.text.toString())
                putExtra("mac", device_m.text.toString())
            }
            context.startActivity(intent)
        }

        fondo.setOnLongClickListener {
            if (d){
                val dialog = Dialog(context)
                val view = LayoutInflater.from(context).inflate(R.layout.manage_device_recy, null)

                val n_name = view.findViewById<EditText>(R.id.name)
                n_name.setText(device_n.text.toString())
                val n_mac = view.findViewById<EditText>(R.id.mac)
                n_mac.setText(device_m.text.toString())

                val delete = view.findViewById<ConstraintLayout>(R.id.delete)
                val edit = view.findViewById<ConstraintLayout>(R.id.edit)

                val db = device_db(context)
                val mac_db = mac_db(context)
                val mk = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                val pref = EncryptedSharedPreferences.create(context, "as", mk, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)

                delete.setOnClickListener {
                    db.delete(data.position)
                    mac_db.delete(data.position)
                    dialog.dismiss()
                    pref.edit().putInt("long", device_list.size).apply()
                    dialog.dismiss()
                }
                edit.setOnClickListener {
                    val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                    val c = Cipher.getInstance("AES/GCM/NoPadding")
                    c.init(Cipher.ENCRYPT_MODE, ks.getKey(pref.getString("key", ""), null))

                    db.edit(data.position, n_name.text.toString(), Base64.getEncoder().withoutPadding().encodeToString(c.doFinal(n_mac.text.toString().toByteArray())), Base64.getEncoder().withoutPadding().encodeToString(c.iv))
                    device_list[data.position].name = n_name.text.toString()
                    device_list[data.position].mac = n_mac.text.toString()

                    mac_db.update(data.position, Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA256").digest(n_mac.text.toString().toByteArray())))
                    dialog.dismiss()
                }

                dialog.setContentView(view)
                dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                dialog.show()
            }
            true
        }
    }
}