package com.nowichat.chat

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nowichat.R
import com.nowichat.models.ChatMessage
import com.nowichat.utils.FileHelper

class FileMessageViewHolder(itemView: View, private val isSent: Boolean) : RecyclerView.ViewHolder(itemView) {
    private val fileName: TextView = itemView.findViewById(R.id.fileName)
    private val fileSize: TextView = itemView.findViewById(R.id.fileSize)
    
    fun bind(message: ChatMessage) {
        fileName.text = message.fileName ?: "Unknown file"
        message.fileUri?.let { uri ->
            fileSize.text = FileHelper.getFileSize(itemView.context, uri)
        }
    }
}
