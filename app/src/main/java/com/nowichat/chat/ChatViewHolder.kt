package com.nowichat.chat

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nowichat.R
import com.nowichat.models.ChatMessage

class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val messageText: TextView = itemView.findViewById(R.id.messageText)
    
    fun bind(message: ChatMessage) {
        messageText.text = message.text
    }
}
