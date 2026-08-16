package com.business.gym.data.api

import com.business.gym.util.TokenManager
import okhttp3.*
import android.util.Log

class ChatManager(private val tokenManager: TokenManager) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    fun connect(callback: ChatCallback) {
        val token = tokenManager.getToken()
        val request = Request.Builder()
            .url("ws://5.35.98.149:5557/chat")
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
