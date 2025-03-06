package com.nowichat.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class mac_db(context: Context): SQLiteOpenHelper(context, "db_mac", null, 1) {

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL("CREATE TABLE db_mac (id PRIMARY KEY, mac TEXT)")
    }

    override fun onUpgrade(p0: SQLiteDatabase?, p1: Int, p2: Int) {}


    fun put(position: Int, mac: String){
        val db = this.writableDatabase

        db.execSQL("INSERT INTO db_mac (id, mac) VALUES (?, ?)", arrayOf(position, mac))

        db.close()
    }
    fun update (position: Int, mac: String){

        val db = this.writableDatabase

        db.execSQL("UPDATE db_mac SET mac = ? WHERE id = ?", arrayOf(mac, position))

        db.close()

    }

    fun delete(position: Int){
        val db = this.writableDatabase

        db.execSQL("DELETE FROM db_mac WHERE id = ?", arrayOf(position))

        db.close()
    }


    fun all(){
        val db = this.writableDatabase

        db.execSQL("DELETE FROM db_mac")

        db.close()
    }

    fun extraccion(){
        macs_list.clear()
        val db = this.readableDatabase

        val query = db.rawQuery("SELECT * FROM db_mac", null)

        fun añadir (){
            macs_list.add(query.getString(1))
        }

        if (query.moveToFirst()){
            añadir()
            while(query.moveToNext()){
                añadir()
            }
        }
    }

    companion object {
        val macs_list = mutableListOf<String>()
    }
}