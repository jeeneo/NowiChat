package com.nowichat

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatEditText
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.google.android.material.imageview.ShapeableImageView
import com.nowichat.db.device_db
import com.nowichat.recy_device.device_adapter
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.AlgorithmParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import com.nowichat.db.device_db.Companion.device_list
import com.nowichat.db.mac_db
import com.nowichat.recy_device.device_data
import java.net.URI

var d = false

class MainActivity : AppCompatActivity() {
    private lateinit var adapter : device_adapter
    private lateinit var broadcast : BroadcastReceiver
    private val permisos_list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT, 
            Manifest.permission.ACCESS_FINE_LOCATION,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.POST_NOTIFICATIONS
            } else {
                Manifest.permission.ACCESS_COARSE_LOCATION
            }
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    @SuppressLint("MissingInflatedId", "SetTextI18n", "WrongViewCast")
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        device_list.clear()
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES

        fun permisos (): Boolean{
            permisos_list.map {permiso ->
                if (ActivityCompat.checkSelfPermission(this, permiso) == PackageManager.PERMISSION_DENIED){
                    return false
                }
            }
            return true
        }

        if (!permisos()){
            ActivityCompat.requestPermissions(this, permisos_list, 100)
        }

        adapter = device_adapter(device_list)
        val recy = findViewById<RecyclerView>(R.id.recy)
        recy.adapter = adapter
        recy.layoutManager = LinearLayoutManager(this)

        val back = findViewById<ConstraintLayout>(R.id.back)
        back.visibility = View.INVISIBLE
        val delete_all = findViewById<ConstraintLayout>(R.id.delete_all)


        val search_device = findViewById<ConstraintLayout>(R.id.search_device)
        val save_device = findViewById<ConstraintLayout>(R.id.save)

        val add_mac = findViewById<ConstraintLayout>(R.id.add_mac)
        val mode_search = findViewById<ConstraintLayout>(R.id.search_mode)

        val mk = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        var pref = EncryptedSharedPreferences.create(this, "ap", mk, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)

        if (pref.getBoolean("block", false)){
            Toast.makeText(this, "You need to clear the cache to use NowiChat", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS))
            finishAffinity()
        }

        if (!pref.getBoolean("from", false)){
            val alertdialog_donacion = AlertDialog.Builder(this)

                .setTitle("Do you want to contribute ideas or donate money to the NowiPass project?")
                .setPositiveButton("Ideas"){_, _ ->
                    pref.edit().putBoolean("from", true).apply()

                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.google.com/forms/d/e/1FAIpQLScYWzcI8esljOk2NViS1O2yVN3I7_4UaNauJen0fSb3lUyTgw/viewform?usp=dialog")))
                }
                .setNegativeButton("Donate"){_, _ ->
                    pref.edit().putBoolean("from", true).apply()

                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/cuadratico")))
                }
                .setNeutralButton("No"){_, _ ->
                    pref.edit().putBoolean("from", true).apply()
                }

            alertdialog_donacion.setCancelable(false)
            alertdialog_donacion.show()
        }
        val db_mac = mac_db(this)
        db_mac.extraccion()


        delete_all.setOnClickListener {
            val alert = AlertDialog.Builder(this)

            alert.setTitle("You want to delete all your information from NowiChat")
            alert.setPositiveButton("Yes"){_, _ -> delet_all(this)}

            alert.show()
        }
        val bluetooth_a = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.adapter
        } else {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }

        back.setOnClickListener {
            if (bluetooth_a.isDiscovering){
                bluetooth_a.cancelDiscovery()
                unregisterReceiver(broadcast)
            }
            recreate()
        }


        fun invisible(){
            back.visibility = View.VISIBLE
            search_device.visibility = View.INVISIBLE
            save_device.visibility = View.INVISIBLE
        }

        val deviceFilter = findViewById<EditText>(R.id.device_filter)
        
        search_device.setOnClickListener {
            d = false
            invisible()
            deviceFilter.visibility = View.VISIBLE

            deviceFilter.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    adapter.filter(s.toString())
                }
            })

            try {
                // Request Bluetooth visibility for 300 seconds
                val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                }
                startActivity(discoverableIntent)

                broadcast = object: BroadcastReceiver(){
                    override fun onReceive(p0: Context?, intent: Intent?) {
                        try {
                            when(intent?.action){
                                BluetoothDevice.ACTION_FOUND -> {
                                    if (ActivityCompat.checkSelfPermission(applicationContext, permisos_list[1]) == PackageManager.PERMISSION_GRANTED) {
                                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                                        } else {
                                            @Suppress("DEPRECATION")
                                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                                        }
                                        
                                        // Update UI on main thread
                                        runOnUiThread {
                                            device_list.add(device_data(device?.name ?: "Unknown", device?.address ?: ""))
                                            adapter.notifyDataSetChanged() // Use this instead of custom upgrade()
                                        }
                                    }
                                }

                                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                                    try {
                                        unregisterReceiver(broadcast)
                                    } catch(e: Exception) {
                                        Log.e("BluetoothError", "Error unregistering receiver", e)
                                    }
                                    bluetooth_a.cancelDiscovery()
                                    runOnUiThread {
                                        Toast.makeText(applicationContext, "Search complete", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } catch(e: Exception) {
                            Log.e("BluetoothError", "Error in broadcast receiver", e)
                        }
                    }
                }

                if (!bluetooth_a.isDiscovering){
                    bluetooth_a.startDiscovery()
                }

                val intent = IntentFilter(BluetoothDevice.ACTION_FOUND).apply {
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED) 
                }
                registerReceiver(broadcast, intent)

                Toast.makeText(this, "Search started", Toast.LENGTH_SHORT).show()

            } catch(e: Exception) {
                Log.e("BluetoothError", "Error starting device search", e)
                Toast.makeText(this, "Error starting search: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        save_device.setOnClickListener {

            val dialog = Dialog(this)
            val view = LayoutInflater.from(this).inflate(R.layout.password, null)

            val total_opor = view.findViewById<ConstraintLayout>(R.id.opor_total)
            total_opor.visibility = View.INVISIBLE
            val opor = view.findViewById<TextView>(R.id.opor)
            opor.text = pref.getInt("opor", 3).toString()
            val input_pass = view.findViewById<AppCompatEditText>(R.id.input_pass)
            val very_pass = view.findViewById<ConstraintLayout>(R.id.very_pass)

            Log.e("error", pref.getBoolean("pass", false).toString())
            if (pref.getBoolean("pass", false)){
                total_opor.visibility = View.VISIBLE
                very_pass.visibility = View.INVISIBLE

                input_pass.addTextChangedListener { editable ->
                    val pass = editable.toString()
                    if (pass.length == pref.getInt("size", 0)){
                        if (very(pass)){
                            val db = device_db(this)

                            if (db.extraccion()){
                                invisible()
                                pref.edit().putInt("long", device_list.size).apply()
                                d = true
                                val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                                for ((_, mac, position, iv) in device_list){
                                    val c = Cipher.getInstance("AES/GCM/NoPadding")
                                    c.init(Cipher.DECRYPT_MODE, ks.getKey(pass, null), GCMParameterSpec(128, Base64.getDecoder().decode(iv)))
                                    device_list[position].mac = String(c.doFinal(Base64.getDecoder().decode(mac)))
                                    device_list[position].iv = ""
                                    adapter.notifyDataSetChanged()
                                }
                            }else {
                                Toast.makeText(this, "The database is empty", Toast.LENGTH_SHORT).show()
                            }
                            dialog.dismiss()
                        }
                        else
                        {
                            input_pass.setText("")
                            opor.text = (opor.text.toString().toInt() - 1).toString()
                            pref.edit().putInt("opor", opor.text.toString().toInt()).apply()

                            if (opor.text.toString().toInt() == 0){
                                Toast.makeText(this, "You have exhausted all your opportunities", Toast.LENGTH_SHORT).show()
                                delet_all(this)
                                dialog.dismiss()
                            }
                        }
                    }
                }
            }
            else
            {
                Toast.makeText(this, "Write the password you want", Toast.LENGTH_SHORT).show()
            }

            very_pass.setOnClickListener {
                if (input_pass.text.toString().toList().size >= 8){
                    val kgs = KeyGenParameterSpec.Builder(input_pass.text.toString(), KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                    val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                    kg.init(kgs as AlgorithmParameterSpec)
                    kg.generateKey()
                    pref.edit().putBoolean("pass", true).apply()
                    Log.e("error", pref.getBoolean("pass", false).toString())
                    pref.edit().putInt("size", input_pass.text.toString().toList().size).apply()
                    pref.edit().putString("key", input_pass.text.toString()).apply()

                    Toast.makeText(this, "Your key has been generated", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }else {
                    Toast.makeText(this, "You are missing ${8 - input_pass.text.toString().toList().size} characters", Toast.LENGTH_SHORT).show()
                }
            }
            dialog.setContentView(view)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.show()
        }

        add_mac.setOnClickListener {
            val dialog = Dialog(this)
            val view =  LayoutInflater.from(this).inflate(R.layout.device_add, null)

            val input_mac = view.findViewById<EditText>(R.id.input_mac)
            val add = view.findViewById<ConstraintLayout>(R.id.add_device)

            add.setOnClickListener {
                if (comprobacion(input_mac.text.toString().trim())){
                    dialog.dismiss()
                    val intent_chat = Intent(this, chatActivity::class.java).apply {
                        putExtra("pro", true)
                        putExtra("mac", input_mac.text.toString().trim())
                    }
                    startActivity(intent_chat)

                }else {
                    Toast.makeText(this, "The mac address is not valid", Toast.LENGTH_SHORT).show()
                }
            }

            dialog.setContentView(view)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.show()
        }

        mode_search.setOnClickListener {
            val intent_chat = Intent(this, chatActivity::class.java).apply {
                putExtra("pro", false)
            }
            startActivity(intent_chat)
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100 && ActivityCompat.checkSelfPermission(this, permisos_list[1]) == PackageManager.PERMISSION_GRANTED){
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val a = bluetoothManager.adapter
            if (!a.isEnabled) {
                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }

            val manage_notify = this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val canal = NotificationChannel("Noti_cha", "noti", NotificationManager.IMPORTANCE_HIGH)

            manage_notify.createNotificationChannel(canal)
        }
    }

    public override fun recreate() {
        super.recreate()
        findViewById<EditText>(R.id.device_filter).visibility = View.GONE
    }

}