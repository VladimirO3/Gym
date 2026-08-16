package com.business.gym.data.model

data class NewsItem(
    val id: String = "",
    val url: String = "",
    val type: String = "image",
    val title: String = "",
    val content: String = "",
    val timestamp: Long = 0,
    val userReaction: String? = null,
    val reactions: Map<String, Int> = emptyMap()
)
