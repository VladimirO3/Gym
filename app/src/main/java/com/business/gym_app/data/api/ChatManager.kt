package com.business.gym_app.data.api

import com.business.gym_app.util.TokenManager
import okhttp3.*
import android.util.Log

class ChatManager(private val tokenManager: TokenManager) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    fun connect(callback: ChatCallback) {
        val token = tokenManager.getToken()
        val wsUrl = NewsApiService.getBaseUrl()
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
            .removeSuffix("/") + "/chat/chat"
        val request = Request.Builder()
            .url(wsUrl)
            .apply {
                token?.let {
                    addHeader("Authorization", "Bearer $it")
                }
            }
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("ChatManager", "WebSocket Connected")
                callback.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("ChatManager", "Message received: $text")
                callback.onMessageReceived(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("ChatManager", "WebSocket Closing: $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("ChatManager", "WebSocket Closed")
                callback.onDisconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("ChatManager", "WebSocket Failure: ${t.message}")
                callback.onError(t)
            }
        })
    }

    fun sendMessage(message: String) {
        webSocket?.send(message)
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
    }

    interface ChatCallback {
        fun onConnected()
        fun onMessageReceived(text: String)
        fun onDisconnected()
        fun onError(t: Throwable)
    }
}
