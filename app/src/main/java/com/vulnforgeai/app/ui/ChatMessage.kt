package com.vulnforgeai.app.ui

data class ChatMessage(
    val sender: String,
    val text: String,
    val isUser: Boolean
)