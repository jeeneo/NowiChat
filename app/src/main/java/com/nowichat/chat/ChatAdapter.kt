package com.nowichat.chat

import android.view.View
import android.widget.TextView
import com.nowichat.models.FileType
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nowichat.R
import com.nowichat.models.ChatMessage

class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val messages = mutableListOf<ChatMessage>()
    
    companion object {
        private const val VIEW_TYPE_SENT_TEXT = 1
        private const val VIEW_TYPE_RECEIVED_TEXT = 2
        private const val VIEW_TYPE_SENT_MEDIA = 3
        private const val VIEW_TYPE_RECEIVED_MEDIA = 4
        private const val VIEW_TYPE_SENT_FILE = 5
        private const val VIEW_TYPE_RECEIVED_FILE = 6
    }

    fun addMessage(message: ChatMessage) {
        // Add message on main thread to prevent ANR
        val position = messages.size
        messages.add(message) 
        notifyItemInserted(position)
    }

    fun removeLastMessage() {
        if (messages.isNotEmpty()) {
            val position = messages.size - 1
            messages.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SENT_TEXT -> createTextViewHolder(parent, true)
            VIEW_TYPE_RECEIVED_TEXT -> createTextViewHolder(parent, false)
            VIEW_TYPE_SENT_MEDIA -> createMediaViewHolder(parent, true)
            VIEW_TYPE_RECEIVED_MEDIA -> createMediaViewHolder(parent, false)
            VIEW_TYPE_SENT_FILE -> createFileViewHolder(parent, true)
            else -> createFileViewHolder(parent, false)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is SentMessageViewHolder -> holder.bind(message)
            is ReceivedMessageViewHolder -> holder.bind(message)
            is MediaMessageViewHolder -> holder.bind(message)
            is FileMessageViewHolder -> holder.bind(message)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return when {
            message.isFromCurrentUser && message.fileType == FileType.TEXT -> VIEW_TYPE_SENT_TEXT
            !message.isFromCurrentUser && message.fileType == FileType.TEXT -> VIEW_TYPE_RECEIVED_TEXT
            message.isFromCurrentUser && message.fileType == FileType.IMAGE -> VIEW_TYPE_SENT_MEDIA
            !message.isFromCurrentUser && message.fileType == FileType.IMAGE -> VIEW_TYPE_RECEIVED_MEDIA
            message.isFromCurrentUser && message.fileType == FileType.VIDEO -> VIEW_TYPE_SENT_MEDIA
            !message.isFromCurrentUser && message.fileType == FileType.VIDEO -> VIEW_TYPE_RECEIVED_MEDIA
            message.isFromCurrentUser -> VIEW_TYPE_SENT_FILE
            else -> VIEW_TYPE_RECEIVED_FILE
        }
    }

    private fun createTextViewHolder(parent: ViewGroup, isSent: Boolean): RecyclerView.ViewHolder {
        val layout = if (isSent) R.layout.chat_bubble_sent else R.layout.chat_bubble_received
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return if (isSent) SentMessageViewHolder(view) else ReceivedMessageViewHolder(view)
    }

    private fun createMediaViewHolder(parent: ViewGroup, isSent: Boolean): RecyclerView.ViewHolder {
        val layout = if (isSent) R.layout.chat_media_sent else R.layout.chat_media_received
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return MediaMessageViewHolder(view, isSent)
    }

    private fun createFileViewHolder(parent: ViewGroup, isSent: Boolean): RecyclerView.ViewHolder {
        val layout = if (isSent) R.layout.chat_file_sent else R.layout.chat_file_received
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return FileMessageViewHolder(view, isSent)
    }

    override fun getItemCount() = messages.size

    class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        fun bind(message: ChatMessage) {
            messageText.text = message.text
        }
    }

    class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        fun bind(message: ChatMessage) {
            messageText.text = message.text
        }
    }
}
