package com.nowichat.chat

import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.nowichat.R
import com.nowichat.models.ChatMessage
import com.nowichat.models.FileType
import com.nowichat.utils.FileHelper

class MediaMessageViewHolder(itemView: View, private val isSent: Boolean) : RecyclerView.ViewHolder(itemView) {
    private val mediaPreview: ImageView = itemView.findViewById(R.id.mediaPreview)
    private val playButton: ImageView = itemView.findViewById(R.id.playButton)
    
    fun bind(message: ChatMessage) {
        message.fileUri?.let { uri ->
            FileHelper.loadMediaPreview(itemView.context, uri, mediaPreview)
            playButton.visibility = if (message.fileType == FileType.VIDEO) View.VISIBLE else View.GONE
        }
    }
}
