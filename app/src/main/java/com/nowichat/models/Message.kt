package com.nowichat.models

data class Message(
    val id: Long = 0,
    val text: String? = null,
    val senderId: String,
    val receiverId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val reactions: List<String> = emptyList(),
    val isEdited: Boolean = false,
    val encryptedContent: String? = null,
    val iv: String? = null
)
