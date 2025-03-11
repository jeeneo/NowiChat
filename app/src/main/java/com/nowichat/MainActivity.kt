package com.nowichat

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.security.crypto.MasterKey
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.drawable.ColorDrawable
import android.hardware.camera2.params.ColorSpaceTransform
import android.net.Uri
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.Adapter
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.SpinnerAdapter
import android.widget.TextView
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatEditText
import androidx.constraintlayout.helper.widget.Carousel
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.security.crypto.EncryptedSharedPreferences
import com.google.android.material.imageview.ShapeableImageView
import com.nowichat.db.device_db
import com.nowichat.recy_device.device_adapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import com.nowichat.db.device_db.Companion.device_list
import com.nowichat.db.mac_db
import com.nowichat.recy_device.device_data
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import java.net.URI

var d = false
var upgrade = false
class MainActivity : AppCompatActivity() {
    private lateinit var adapter : device_adapter
    private lateinit var broadcast : BroadcastReceiver
    private val permisos_list = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
    private fun delet_all(){
        val mk = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        var pref = EncryptedSharedPreferences.create(this, "as", mk, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        pref.edit().clear().commit()

        pref = EncryptedSharedPreferences.create(this, "ap", mk, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        pref.edit().clear().commit()
        pref.edit().putBoolean("block", true).commit()

        val db_device = device_db(this)
        db_device.all()
        val db_mac = mac_db(this)
        db_mac.all()
        finishAffinity()
    }
    @SuppressLint("MissingInflatedId", "SetTextI18n", "WrongViewCast")
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        device_list.clear()
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES

        val scope = CoroutineScope(Dispatchers.IO).launch (start = CoroutineStart.LAZY){
            while (true){
                if (upgrade){
                    withContext(Dispatchers.Main) {
                        adapter.upgrade()
                    }
                    upgrade = false
                    delay(1000)
                }
            }
        }
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
                    pref.edit().putBoolean("from", true).commit()

                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.google.com/forms/d/e/1FAIpQLScYWzcI8esljOk2NViS1O2yVN3I7_4UaNauJen0fSb3lUyTgw/viewform?usp=dialog")))
                }
                .setNegativeButton("Donate"){_, _ ->
                    pref.edit().putBoolean("from", true).commit()

                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/cuadratico")))
                }
                .setNeutralButton("No"){_, _ ->
                    pref.edit().putBoolean("from", true).commit()
                }

            alertdialog_donacion.setCancelable(false)
            alertdialog_donacion.show()
        }
        val db_mac = mac_db(this)
        db_mac.extraccion()


        delete_all.setOnClickListener {
            val alert = AlertDialog.Builder(this)

            alert.setTitle("You want to delete all your information from NowiChat")
            alert.setPositiveButton("Yes"){_, _ -> delet_all()}

            alert.show()
        }
        val bluetooth_a = BluetoothAdapter.getDefaultAdapter()

        back.setOnClickListener {
            if (scope.isActive){
                scope.cancel()
            }
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

        search_device.setOnClickListener {
            d = false
            invisible()


            broadcast = object: BroadcastReceiver(){
                override fun onReceive(p0: Context?, intent: Intent?) {

                    when(intent?.action){

                        BluetoothDevice.ACTION_FOUND -> {
                            if (ActivityCompat.checkSelfPermission(applicationContext, permisos_list[1]) == PackageManager.PERMISSION_GRANTED) {
                                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                                device_list.add(device_data(device?.name.toString(), device?.address.toString()))
                                adapter.upgrade()
                            }
                        }

                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                            unregisterReceiver(broadcast)
                            bluetooth_a.cancelDiscovery()
                            Toast.makeText(applicationContext, "There are no more devices", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

            }

            Toast.makeText(this, "The search has begun", Toast.LENGTH_SHORT).show()

            if (!bluetooth_a.isDiscovering){
                bluetooth_a.startDiscovery()
            }

            val intent = IntentFilter(BluetoothDevice.ACTION_FOUND).apply {
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            registerReceiver(broadcast, intent)
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

                input_pass.addTextChangedListener {pass ->
                    if (pass?.toList()?.size == pref.getInt("size", 0)){
                        if (very(pass.toString())){
                            val db = device_db(this)

                            if (db.extraccion()){
                                invisible()
                                scope.start()
                                pref.edit().putInt("long", device_list.size).apply()
                                d = true
                                val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

                                for ((name, mac, position, iv) in device_list){
                                    val c = Cipher.getInstance("AES/GCM/NoPadding")
                                    c.init(Cipher.DECRYPT_MODE, ks.getKey(pass.toString(), null), GCMParameterSpec(128, Base64.getDecoder().decode(iv)))

                                    device_list[position].mac = String(c.doFinal(Base64.getDecoder().decode(mac)))
                                    device_list[position].iv = ""

                                    adapter.upgrade()
                                }
                            }else {
                                Toast.makeText(this, "The database is empty", Toast.LENGTH_SHORT).show()
                            }
                            dialog.dismiss()

                        }else {
                            input_pass.setText("")
                            opor.text = (opor.text.toString().toInt() - 1).toString()
                            pref.edit().putInt("opor", opor.text.toString().toInt()).apply()

                            if (opor.text.toString().toInt() == 0){
                                Toast.makeText(this, "You have exhausted all your opportunities", Toast.LENGTH_SHORT).show()
                                delet_all()
                                dialog.dismiss()
                            }
                        }
                    }
                }
            }else {
                Toast.makeText(this, "Write the password you want", Toast.LENGTH_SHORT).show()
            }

            very_pass.setOnClickListener {
                if (input_pass.text.toString().toList().size >= 8){
                    val kgs = KeyGenParameterSpec.Builder(input_pass.text.toString(), KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                    val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                    kg.init(kgs)
                    kg.generateKey()
                    pref.edit().putBoolean("pass", true).commit()
                    Log.e("error", pref.getBoolean("pass", false).toString())
                    pref.edit().putInt("size", input_pass.text.toString().toList().size).commit()
                    pref = EncryptedSharedPreferences.create(this, "as", mk, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
                    pref.edit().putString("key", input_pass.text.toString()).commit()

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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray, deviceId: Int) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)

        if (requestCode == 100 && ActivityCompat.checkSelfPermission(this, permisos_list[1]) == PackageManager.PERMISSION_GRANTED){
            val a = BluetoothAdapter.getDefaultAdapter()
            if (!a.isEnabled) {
                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        }
    }


}