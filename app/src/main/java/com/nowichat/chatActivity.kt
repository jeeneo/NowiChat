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
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.nowichat.chat.ChatAdapter
import com.nowichat.db.device_db
import com.nowichat.db.mac_db
import com.nowichat.history_data
import com.nowichat.models.ChatMessage
import com.nowichat.databinding.ActivityChatBinding
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

val history = mutableListOf<history_data>()
private const val BLUETOOTH_PERMISSION_REQUEST = 101 // Define the constant

private fun dialog_pass(context: Context) {
    Toast.makeText(context, "Saving chat history...", Toast.LENGTH_SHORT).show()
    // TODO: Implement actual chat saving functionality
}

class chatActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatBinding
    private lateinit var socket: BluetoothSocket
    private lateinit var scope: Job
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var adapter: BluetoothAdapter
    private lateinit var device_d: TextView
    private lateinit var mac: String
    private val simetric = mutableListOf<pass_data>()
    private var where = true
    private val uuid = "00001101-0000-1000-8000-00805F9B34FB"
    private val ks = KeyStore.getInstance("AndroidKeyStore").apply { 
        load(null) 
    }

    private fun error(message: String, details: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        Log.e("error", details)
        finish()
    }

    private fun visible(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        scope.start()
    }

    private fun sendExitMessage() {
        try {
            if (socket.isConnected) {
                var c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, ks.getKey("key", null), GCMParameterSpec(128, 
                    Base64.decode(simetric[0].iv, Base64.NO_WRAP)))
                val sime = SecretKeySpec(c.doFinal(
                    Base64.decode(simetric[0].pass, Base64.NO_WRAP)), "AES")

                c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.ENCRYPT_MODE, sime)

                socket.outputStream.write(c.iv + c.doFinal("__EXIT__".toByteArray()))
            }
        } catch (e: Exception) {
            Log.e("error", "Error sending exit message: ${e.message}")
        }
    }

    private fun cleanupAndExit() {
        runOnUiThread {
            Toast.makeText(applicationContext, "Chat ended", Toast.LENGTH_SHORT).show()
        }
        scope.cancel()
        try {
            socket.close()
        } catch (e: Exception) {
            Log.e("error", "Error closing socket: ${e.message}")
        }
        finish()
    }

    @SuppressLint("MissingInflatedId")
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Add exit button handler
        binding.exitButton.setOnClickListener {
            sendExitMessage()
            cleanupAndExit()
        }

        // Add window insets handling
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val navigationInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.interfaz.setPadding(0, 0, 0, imeInsets.bottom + navigationInsets.bottom)
            windowInsets
        }

        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
        adapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.adapter
        } else {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val proposito = intent.extras!!.getBoolean("pro", false)

        device_d = findViewById<TextView>(R.id.device_d)
        mac = intent.extras?.getString("mac", "").orEmpty()
        binding.result.visibility = View.GONE
        binding.interfaz.visibility = View.INVISIBLE
        val message = findViewById<EditText>(R.id.message)
        val send = findViewById<ConstraintLayout>(R.id.send)
        val fondo = findViewById<View>(R.id.fondo)
        val saveButton = findViewById<FloatingActionButton>(R.id.save_chat)
        val messagesRecyclerView = findViewById<RecyclerView>(R.id.messagesRecyclerView)

        val mk = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val pref_ap = EncryptedSharedPreferences.create(this, "ap", mk, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        val pref_as = EncryptedSharedPreferences.create(this, "as", mk, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)

        if (!pref_ap.getBoolean("pass", false)){
            saveButton.visibility = View.INVISIBLE
        }
        saveButton.setOnClickListener {
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

        binding.fondo?.setOnClickListener {
            binding.result.text?.toString()?.takeIf { it.isNotEmpty() }?.let { text ->
                val manage_clip = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("message", text)
                manage_clip.setPrimaryClip(clip)
            }
        }

        val simetric = mutableListOf<pass_data>()
        val asimetric = mutableListOf<pass_data>()

        fun sim(){
            val kg_s = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES)
            kg_s.init(256)
            val clave = kg_s.generateKey().encoded

            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, ks.getKey("key", null))

            simetric.add(pass_data(Base64.encodeToString(c.doFinal(clave), Base64.NO_WRAP), Base64.encodeToString(c.iv, Base64.NO_WRAP)))
        }

        fun asi(){
            val kg_a = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA)
            kg_a.initialize(2048)
            val cl = kg_a.generateKeyPair()
            val claves = cl

            var c = Cipher.getInstance("AES/GCM/NoPadding")

            c.init(Cipher.ENCRYPT_MODE, ks.getKey("key", null))
            asimetric.add(pass_data(Base64.encodeToString(c.doFinal(claves.public.encoded), Base64.NO_WRAP), Base64.encodeToString(c.iv, Base64.NO_WRAP)))

            c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, ks.getKey("key", null))
            asimetric.add(pass_data(Base64.encodeToString(c.doFinal(claves.private.encoded), Base64.NO_WRAP), Base64.encodeToString(c.iv, Base64.NO_WRAP)))

            socket.outputStream.write(claves.public.encoded)
        }

        fun asimetric_global(){
            val kgs = KeyGenParameterSpec.Builder("key", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).apply {
                setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            }
                .build()

            val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            kg.init(kgs as AlgorithmParameterSpec) // Specify AlgorithmParameterSpec
            kg.generateKey()

            sim()
            asi()
        }
        scope = CoroutineScope(Dispatchers.IO).launch(start = CoroutineStart.LAZY) {
            try {
                val vibration_manage = applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                val vibration = VibrationEffect.createWaveform(longArrayOf(0, 2, 10, 50, 0), -1)
                vibration_manage.vibrate(vibration)

                withContext(Dispatchers.Main) {
                    binding.result.text = "Sharing keys..."
                }
                asimetric_global()
                while (simetric.size != 2) {
                    if (socket.inputStream.available() > 0) {
                        val buffer = ByteArray(socket.inputStream.available())
                        socket.inputStream.read(buffer)

                        if (buffer.size > 256) {
                            val public_user = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA).generatePublic(X509EncodedKeySpec(buffer))
                            var c = Cipher.getInstance("AES/GCM/NoPadding")
                            c.init(Cipher.DECRYPT_MODE, ks.getKey("key", null), GCMParameterSpec(128, Base64.decode(simetric[0].iv, Base64.NO_WRAP)))
                            val simetri = c.doFinal(Base64.decode(simetric[0].pass, Base64.NO_WRAP))

                            c = Cipher.getInstance("RSA/ECB/PKCS1Padding")
                            c.init(Cipher.ENCRYPT_MODE, public_user)

                            socket.outputStream.write(c.doFinal(simetri))
                        } else {
                            var c = Cipher.getInstance("AES/GCM/NoPadding")
                            c.init(Cipher.DECRYPT_MODE, ks.getKey("key", null), GCMParameterSpec(128, Base64.decode(asimetric[1].iv, Base64.NO_WRAP)))
                            val private_k = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA).generatePrivate(PKCS8EncodedKeySpec(c.doFinal(Base64.decode(asimetric[1].pass, Base64.NO_WRAP))))

                            c = Cipher.getInstance("RSA/ECB/PKCS1Padding")
                            c.init(Cipher.DECRYPT_MODE, private_k)
                            val sime_user = c.doFinal(buffer)

                            c = Cipher.getInstance("AES/GCM/NoPadding")
                            c.init(Cipher.ENCRYPT_MODE, ks.getKey("key", null))
                            simetric.add(pass_data(Base64.encodeToString(c.doFinal(sime_user), Base64.NO_WRAP), Base64.encodeToString(c.iv, Base64.NO_WRAP)))

                        }
                    }
                }
                asimetric.clear()
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Shared keys", Toast.LENGTH_SHORT).show()
                    binding.interfaz.visibility = View.VISIBLE
                    binding.result.text = ""
                }
                while (true) {
                    try {
                        if (socket.inputStream.available() > 0) {

                            val buffer = ByteArray(socket.inputStream.available())
                            socket.inputStream.read(buffer)
                            var c = Cipher.getInstance("AES/GCM/NoPadding")
                            c.init(Cipher.DECRYPT_MODE, ks.getKey("key", null), GCMParameterSpec(128, Base64.decode(simetric[1].iv, Base64.NO_WRAP)))
                            val sime = SecretKeySpec(c.doFinal(Base64.decode(simetric[1].pass, Base64.NO_WRAP)), "AES")

                            c = Cipher.getInstance("AES/GCM/NoPadding")
                            c.init(Cipher.DECRYPT_MODE, sime, GCMParameterSpec(128, buffer.copyOfRange(0, 12)))
                            val recept = c.doFinal(buffer.copyOfRange(12, buffer.size))

                            if (pref_ap.getBoolean("pass", false)) {
                                c = Cipher.getInstance("AES/GCM/NoPadding")
                                c.init(Cipher.ENCRYPT_MODE, ks.getKey(pref_as.getString("key", null), null))

                                history.add(history_data("The other user", Base64.encodeToString(c.doFinal(recept), Base64.NO_WRAP), Base64.encodeToString(c.iv, Base64.NO_WRAP)))
                            }

                            if (!where && ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                                val notificacion = NotificationCompat.Builder(applicationContext, "Noti_cha")
                                    .setSmallIcon(R.mipmap.logo_nowi_chat_round)
                                    .setContentTitle("You have a new message")
                                    .setContentText(String(recept))
                                    .setPriority(NotificationManager.IMPORTANCE_HIGH)
                                    .build()

                                NotificationManagerCompat.from(applicationContext).notify(1, notificacion)
                            }
                            withContext(Dispatchers.Main) {
                                // Use proper context reference
                                val messageText = String(recept)
                                chatAdapter.addMessage(ChatMessage(messageText, false))
                                messagesRecyclerView.layoutManager = LinearLayoutManager(this@chatActivity).apply {
                                    stackFromEnd = true 
                                }
                                messagesRecyclerView.adapter = chatAdapter
                            }
                            
                            val messageText = String(recept)
                            if (messageText == "__EXIT__") {
                                withContext(Dispatchers.Main) {
                                    cleanupAndExit()
                                }
                                break
                            }
                        }
                    } catch (erro: Exception) {
                        withContext(Dispatchers.Main) {
                            error("Connection has been lost", erro.toString())
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error("Connection error", e.toString())
                }
            }
        }

        chatAdapter = ChatAdapter()
        messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true 
            }
            adapter = chatAdapter
        }

        send.setOnClickListener {
            val messageText = message.text.toString()
            if (messageText.isNotEmpty()) {
                try {
                    var c = Cipher.getInstance("AES/GCM/NoPadding")
                    c.init(Cipher.DECRYPT_MODE, ks.getKey("key", null), GCMParameterSpec(128, Base64.decode(simetric[0].iv, Base64.NO_WRAP)))
                    val sime = SecretKeySpec(c.doFinal(Base64.decode(simetric[0].pass, Base64.NO_WRAP)), "AES")

                    c = Cipher.getInstance("AES/GCM/NoPadding")
                    c.init(Cipher.ENCRYPT_MODE, sime)

                    socket.outputStream.write(c.iv + c.doFinal(messageText.toByteArray()))
                    chatAdapter.addMessage(ChatMessage(messageText, true))
                    message.setText("")
                    messagesRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)

                } catch (erro: Exception) {
                    error("Connection has been lost", erro.toString())
                }
            }
        }

        binding.deviceI.text = adapter.name ?: adapter.address ?: "this device"
        binding.deviceD.text = "(waiting...)"

        binding.interfaz.post {
            if (proposito) {
                conection()
            } else {
                search()
            }
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        }

    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        sendExitMessage()
        cleanupAndExit()
    }

    private fun search() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 
                    BLUETOOTH_PERMISSION_REQUEST)
                return
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.BLUETOOTH),
                    BLUETOOTH_PERMISSION_REQUEST)
                return
            }
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    binding.result.text = "Waiting for connection..."
                }

                socket = withContext(Dispatchers.IO) {
                    adapter.listenUsingRfcommWithServiceRecord("chat", UUID.fromString(uuid))
                        .accept(30000) // 30 second timeout
                }

                if (ActivityCompat.checkSelfPermission(this@chatActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    val remoteDevice = socket.remoteDevice
                    withContext(Dispatchers.Main) {
                        binding.deviceD.text = remoteDevice.name ?: remoteDevice.address
                        visible("A device has connected to you")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error("Device not found or connection timeout", e.toString())
                }
            }
        }
    }

    private fun conection() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    withContext(Dispatchers.Main) {
                        binding.result.text = "Connecting..."
                    }

                    val device = adapter.getRemoteDevice(mac)
                    socket = device.createRfcommSocketToServiceRecord(UUID.fromString(uuid))
                    
                    withContext(Dispatchers.IO) {
                        socket.connect()
                    }

                    val deviceName = intent.extras?.getString("name") 
                        ?: device.name 
                        ?: device.address

                    withContext(Dispatchers.Main) {
                        binding.deviceD.text = deviceName
                        visible("You have connected to $deviceName")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        error("Could not connect to device", e.toString())
                    }
                }
            }
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
}