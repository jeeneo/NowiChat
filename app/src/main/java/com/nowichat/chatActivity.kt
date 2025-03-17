package com.nowichat

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.material.textfield.TextInputEditText
import com.nowichat.db.device_db
import com.nowichat.db.mac_db
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import kotlin.random.Random
import com.nowichat.db.mac_db.Companion.macs_list
import java.security.KeyFactory
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

val history = mutableListOf<history_data>()
const val uuid = "00000000-0000-1000-8000-00805F9B34FB"
class chatActivity : AppCompatActivity() {
    private lateinit var socket: BluetoothSocket
    private lateinit var scope: Job
    private var where = true
    private val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    @SuppressLint("MissingInflatedId")
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)

        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
        val adapter = BluetoothAdapter.getDefaultAdapter()

        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val proposito = intent.extras!!.getBoolean("pro", false)
        val mac = intent.extras?.getString("mac", "").orEmpty()

        val device_i = findViewById<TextView>(R.id.device_i)
        device_i.text = adapter.name
        val device_d = findViewById<TextView>(R.id.device_d)

        val result = findViewById<TextView>(R.id.result)
        val interfaz = findViewById<ConstraintLayout>(R.id.interfaz)
        interfaz.visibility = View.INVISIBLE
        val message = findViewById<EditText>(R.id.message)
        val send = findViewById<ConstraintLayout>(R.id.send)
        val fondo = findViewById<ConstraintLayout>(R.id.fondo)
        val save_chat = findViewById<ConstraintLayout>(R.id.save_chat)

        val mk = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val pref_ap = EncryptedSharedPreferences.create(this, "ap", mk, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        val pref_as = EncryptedSharedPreferences.create(this, "as", mk, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)

        if (!pref_ap.getBoolean("pass", false)){
            save_chat.visibility = View.INVISIBLE
        }
        save_chat.setOnClickListener {
            val chat_dialog = AlertDialog.Builder(this)

            chat_dialog.setTitle("You want to save the conversation in a text file")
            chat_dialog.setPositiveButton ("Yes"){_, _ -> dialog_pass(this)}
            chat_dialog.setNegativeButton ("Do not show again"){_, _ -> pref_ap.edit().putBoolean("show", false).commit() }

            if (history.isEmpty()){
                Toast.makeText(this, "There are no registered messages.", Toast.LENGTH_SHORT).show()
            }else {
                if (pref_ap.getBoolean("show", true)) {
                    chat_dialog.show()
                }else {
                    dialog_pass(this)
                }
            }
        }

        fondo.setOnClickListener {
            if (result.text.toString().isNotEmpty()) {
                val manage_clip = this.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("message", result.text.toString())
                manage_clip.setPrimaryClip(clip)
            }
        }

        fun visible (texto: String){
            Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
            scope.start()
        }
        fun error(texto: String, e: String){
            Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
            Log.e("error", e)
            finish()
        }

        val simetric = mutableListOf<pass_data>()
        val asimetric = mutableListOf<pass_data>()

        fun sim(){
            val kg_s = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES)
            kg_s.init(256)
            val clave = kg_s.generateKey().encoded

            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, ks.getKey("key", null))

            simetric.add(pass_data(Base64.getEncoder().withoutPadding().encodeToString(c.doFinal(clave)), Base64.getEncoder().withoutPadding().encodeToString(c.iv)))
        }

        fun asi(){
            val kg_a = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA)
            kg_a.initialize(2048)
            val cl = kg_a.generateKeyPair()
            val claves = cl

            var c = Cipher.getInstance("AES/GCM/NoPadding")

            c.init(Cipher.ENCRYPT_MODE, ks.getKey("key", null))
            asimetric.add(pass_data(Base64.getEncoder().withoutPadding().encodeToString(c.doFinal(claves.public.encoded)), Base64.getEncoder().withoutPadding().encodeToString(c.iv)))

            c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, ks.getKey("key", null))
            asimetric.add(pass_data(Base64.getEncoder().withoutPadding().encodeToString(c.doFinal(claves.private.encoded)), Base64.getEncoder().withoutPadding().encodeToString(c.iv)))

            socket.outputStream.write(claves.public.encoded)
        }

        fun asimetric_global(){
            val kgs = KeyGenParameterSpec.Builder("key", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).apply {
                setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            }
                .build()

            val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            kg.init(kgs)
            kg.generateKey()

            sim()
            asi()
        }
        scope = CoroutineScope(Dispatchers.IO).launch (start = CoroutineStart.LAZY){
            val vibration_manage = applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val vibration = VibrationEffect.createWaveform(longArrayOf(0, 2, 10, 50, 0), -1)
            vibration_manage.vibrate(vibration)

            withContext(Dispatchers.Main){
                result.text = "Sharing keys..."
            }
            asimetric_global()
            while (simetric.size != 2){
                if (socket.inputStream.available() > 0){
                    val buffer = ByteArray(socket.inputStream.available())
                    socket.inputStream.read(buffer)

                    if (buffer.size > 256){
                        val public_user = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA).generatePublic(X509EncodedKeySpec(buffer))
                        var c = Cipher.getInstance("AES/GCM/NoPadding")
                        c.init(Cipher.DECRYPT_MODE, ks.getKey("key", null), GCMParameterSpec(128, Base64.getDecoder().decode(simetric[0].iv)))
                        val simetri = c.doFinal(Base64.getDecoder().decode(simetric[0].pass))

                        c = Cipher.getInstance("RSA/ECB/PKCS1Padding")
                        c.init(Cipher.ENCRYPT_MODE, public_user)

                        socket.outputStream.write(c.doFinal(simetri))
                    }else {
                        var c = Cipher.getInstance("AES/GCM/NoPadding")
                        c.init(Cipher.DECRYPT_MODE, ks.getKey("key", null), GCMParameterSpec(128, Base64.getDecoder().decode(asimetric[1].iv)))
                        val private_k = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA).generatePrivate(PKCS8EncodedKeySpec(c.doFinal(Base64.getDecoder().decode(asimetric[1].pass))))

                        c = Cipher.getInstance("RSA/ECB/PKCS1Padding")
                        c.init(Cipher.DECRYPT_MODE, private_k)
                        val sime_user = c.doFinal(buffer)

                        c = Cipher.getInstance("AES/GCM/NoPadding")
                        c.init(Cipher.ENCRYPT_MODE, ks.getKey("key", null))
                        simetric.add(pass_data(Base64.getEncoder().withoutPadding().encodeToString(c.doFinal(sime_user)), Base64.getEncoder().withoutPadding().encodeToString(c.iv)))

                    }
                }
            }
            asimetric.clear()
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, "Shared keys", Toast.LENGTH_SHORT).show()
                interfaz.visibility = View.VISIBLE
                result.text = ""
            }
            while(true){
                try {
                    if (socket.inputStream.available() > 0) {

                        val buffer = ByteArray(socket.inputStream.available())
                        socket.inputStream.read(buffer)
                        var c = Cipher.getInstance("AES/GCM/NoPadding")
                        c.init(Cipher.DECRYPT_MODE, ks.getKey("key", null), GCMParameterSpec(128, Base64.getDecoder().decode(simetric[1].iv)))
                        val sime = SecretKeySpec(c.doFinal(Base64.getDecoder().decode(simetric[1].pass)), "AES")

                        c = Cipher.getInstance("AES/GCM/NoPadding")
                        c.init(Cipher.DECRYPT_MODE, sime, GCMParameterSpec(128, buffer.copyOfRange(0, 12)))
                        val recept = c.doFinal(buffer.copyOfRange(12, buffer.size))

                        if (pref_ap.getBoolean("pass", false)){
                            c = Cipher.getInstance("AES/GCM/NoPadding")
                            c.init(Cipher.ENCRYPT_MODE, ks.getKey(pref_as.getString("key", null), null))

                            history.add(history_data("The other user", Base64.getEncoder().withoutPadding().encodeToString(c.doFinal(recept)), Base64.getEncoder().withoutPadding().encodeToString(c.iv)))
                        }

                        if (!where && ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED){
                            val notificacion = NotificationCompat.Builder(applicationContext, "Noti_cha")
                                .setSmallIcon(R.mipmap.logo_nowi_chat_round)
                                .setContentTitle("You have a new message")
                                .setContentText(String(recept))
                                .setPriority(NotificationManager.IMPORTANCE_HIGH)
                                .build()

                            NotificationManagerCompat.from(applicationContext).notify(1, notificacion)
                        }
                        withContext(Dispatchers.Main) {
                            result.text = String(recept)
                        }
                    }
                }catch (erro: Exception){
                    Log.e("error", erro.toString())
                }
                delay(500)
            }
        }


        fun conection(){


            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {

                try {

                    val device = adapter.getRemoteDevice(mac)
                    socket = device.createRfcommSocketToServiceRecord(UUID.fromString(uuid))
                    socket.connect()

                    device_d.text = device.name
                    visible("You have connected to")

                    val mac_hash = Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA256").digest(mac.toByteArray()))

                    if (pref_ap.getBoolean("pass", false) && !macs_list.contains(mac_hash)){
                        val dialog = AlertDialog.Builder(this).apply {

                            setTitle("Do you want to save this device")
                            setPositiveButton("Save"){_, _ ->
                                val long = pref_ap.getInt("long", 0)

                                val db_device = device_db(applicationContext)
                                val db_mac = mac_db(applicationContext)

                                val c = Cipher.getInstance("AES/GCM/NoPadding")
                                c.init(Cipher.ENCRYPT_MODE, ks.getKey(pref_as.getString("key", ""), null))

                                db_device.put(long, device_d.text.toString(), Base64.getEncoder().withoutPadding().encodeToString(c.doFinal(mac.toByteArray())), Base64.getEncoder().withoutPadding().encodeToString(c.iv))
                                db_mac.put(long, mac_hash)
                                db_mac.extraccion()
                            }

                            setNegativeButton("No"){_, _ ->}

                        }
                        dialog.show()
                    }

                } catch (erro: Exception) {
                    error("You could not connect to the device", erro.toString())
                }
            }
        }

        fun search(){
            try {
                socket = adapter.listenUsingRfcommWithServiceRecord("chat", UUID.fromString(uuid)).accept(10000)
                visible("A device has connected to you")
            }catch (erro: Exception){
                error("The device has not been", erro.toString())
            }
        }

        send.setOnClickListener {
            try {
                var c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, ks.getKey("key", null), GCMParameterSpec(128, Base64.getDecoder().decode(simetric[0].iv)))
                val sime = SecretKeySpec(c.doFinal(Base64.getDecoder().decode(simetric[0].pass)), "AES")

                c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.ENCRYPT_MODE, sime)

                socket.outputStream.write(c.iv + c.doFinal(message.text.toString().toByteArray()))

                if (pref_ap.getBoolean("pass", false)) {
                    c = Cipher.getInstance("AES/GCM/NoPadding")
                    c.init(Cipher.ENCRYPT_MODE, ks.getKey(pref_as.getString("key", null), null))
                    history.add(history_data("you", Base64.getEncoder().withoutPadding().encodeToString(c.doFinal(message.text.toString().toByteArray())), Base64.getEncoder().withoutPadding().encodeToString(c.iv)))
                }
                message.setText("")

            }catch (erro: Exception){
                error("Connection has been lost", erro.toString())
            }
        }

        interfaz.post{
            if (proposito){
                conection()
            }else {
                search()
            }
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        }




        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        if (socket.isConnected) {
            socket.close()
        }
        ks.deleteEntry("key")
        history.clear()
    }

    override fun onPause() {
        super.onPause()
        where = false
    }

    override fun onStart() {
        super.onStart()
        where = true
    }
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBackPressed() {
        super.onBackPressed()
        finish()

    }
}