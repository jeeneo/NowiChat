package com.nowichat.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.nowichat.recy_device.device_data

class device_db(context: Context): SQLiteOpenHelper(context, "db_device", null, 1) {
    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL("CREATE TABLE db_device (id INTEGER PRIMARY KEY, name TEXT, mac TEXT, iv TEXT)")
    }

    override fun onUpgrade(p0: SQLiteDatabase?, p1: Int, p2: Int) {}


    fun put (position: Int, name: String, mac: String, iv: String){
        val db = this.writableDatabase

        db.execSQL("INSERT INTO db_device (id, name, mac, iv) VALUES (?, ?, ?, ?)", arrayOf(position, name, mac, iv))

        db.close()
    }

    fun edit(position: Int, name: String, mac: String, iv: String){
        val db = this.writableDatabase

        db.execSQL("UPDATE db_device SET name = ?, mac = ?, iv = ? WHERE id = ?", arrayOf(name, mac, iv, position))

        db.close()
    }

    fun delete(position: Int){
        val db = this.writableDatabase

        db.execSQL("DELETE FROM db_device WHERE id = ?", arrayOf(position))

        for (posi in 0..device_list.size){
            db.execSQL("UPDATE db_device SET id = ? WHERE id = ?", arrayOf(posi, device_list[posi].position))
            device_list[posi].position = posi
        }

        db.close()
    }

    fun all(){
        val db = this.writableDatabase

        db.execSQL("DELETE FROM db_device")

        db.close()
    }
    fun extraccion(): Boolean{

        val db = this.readableDatabase

        val consulta = db.rawQuery("SELECT * FROM db_device", null)

        fun añadir(){
            device_list.add(device_data(consulta.getString(1), consulta.getString(2), consulta.getInt(0), consulta.getString(3)))
        }

        if (consulta.moveToFirst()){
            añadir()
            while(consulta.moveToNext()){
                añadir()
            }
            return true
        }else {
            return false
        }
    }

    companion object {
        val device_list = mutableListOf<device_data>()
    }
}