package com.nowichat.models

data class ChatMessage(
    val text: String,
    val isFromCurrentUser: Boolean
)
