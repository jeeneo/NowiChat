package com.nowichat.models

data class ChatMessage(
    val text: String,
    val isFromCurrentUser: Boolean,
    val fileUri: String? = null,
    val fileType: FileType = FileType.TEXT,
    val fileName: String? = null
) {
    companion object {
        private const val SEPARATOR = "|:|"
        
        fun createFileMessage(text: String, isFromCurrentUser: Boolean, fileUri: String?, fileType: FileType, fileName: String?): ChatMessage {
            return ChatMessage(text, isFromCurrentUser, fileUri, fileType, fileName)
        }

        fun serialize(message: ChatMessage): ByteArray {
            val parts = listOf(
                message.text,
                message.fileType.name,
                message.fileName ?: "",
                message.fileUri ?: ""
            )
            return parts.joinToString(SEPARATOR).toByteArray()
        }

        fun deserialize(bytes: ByteArray): ChatMessage {
            val parts = String(bytes).split(SEPARATOR)
            return ChatMessage(
                text = parts[0],
                isFromCurrentUser = false,
                fileUri = parts[3].takeIf { it.isNotEmpty() },
                fileType = FileType.valueOf(parts[1]),
                fileName = parts[2].takeIf { it.isNotEmpty() }
            )
        }
    }
}

enum class FileType {
    TEXT,
    IMAGE,
    VIDEO,
    FILE
}
