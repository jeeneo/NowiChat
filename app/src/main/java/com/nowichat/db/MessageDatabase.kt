package com.nowichat.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.nowichat.models.Message

class MessageDatabase(context: Context) : SQLiteOpenHelper(context, "messages.db", null, 1) {
    
    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL("""
            CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                text TEXT,
                sender_id TEXT,
                receiver_id TEXT,
                timestamp INTEGER,
                is_read INTEGER,
                reactions TEXT,
                is_edited INTEGER,
                encrypted_content TEXT,
                iv TEXT
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}
    
    fun insertMessage(message: Message) {
        val db = writableDatabase
        db.execSQL("""
            INSERT INTO messages (text, sender_id, receiver_id, timestamp, is_read, reactions, is_edited, encrypted_content, iv) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, arrayOf(
            message.text,
            message.senderId,
            message.receiverId, 
            message.timestamp,
            if(message.isRead) 1 else 0,
            message.reactions.joinToString(","),
            if(message.isEdited) 1 else 0,
            message.encryptedContent,
            message.iv
        ))
        db.close()
    }

    fun getMessagesForChat(senderId: String, receiverId: String): List<Message> {
        val messages = mutableListOf<Message>()
        val db = readableDatabase
        
        val cursor = db.rawQuery("""
            SELECT * FROM messages 
            WHERE (sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?)
            ORDER BY timestamp ASC
        """, arrayOf(senderId, receiverId, receiverId, senderId))

        if (cursor.moveToFirst()) {
            do {
                messages.add(Message(
                    id = cursor.getLong(0),
                    text = cursor.getString(1),
                    senderId = cursor.getString(2),
                    receiverId = cursor.getString(3),
                    timestamp = cursor.getLong(4),
                    isRead = cursor.getInt(5) == 1,
                    reactions = cursor.getString(6).split(",").filter { it.isNotEmpty() },
                    isEdited = cursor.getInt(7) == 1,
                    encryptedContent = cursor.getString(8),
                    iv = cursor.getString(9)
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return messages
    }
}
