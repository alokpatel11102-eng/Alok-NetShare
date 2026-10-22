package com.alok.netshare.tunnel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.alok.netshare.data.DataUsageTracker
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class SenderRelayService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private val connectedClients = ConcurrentHashMap<String, RelayProtocol.ConnectedDevice>()

    companion object {
        const val ACTION_START_SHARING = "com.alok.netshare.sender.START"
        const val ACTION_STOP_SHARING = "com.alok.netshare.sender.STOP"
        const val ACTION_DISCONNECT_CLIENT = "com.alok.netshare.sender.DISCONNECT_CLIENT"
        const val EXTRA_SESSION_ID = "SESSION_ID"
        const val EXTRA_PIN = "PIN"
        const val EXTRA_CLIENT_ID = "CLIENT_ID"
        const val EXTRA_RELAY_URL = "RELAY_URL"
        const val EXTRA_DATA_LIMIT_MB = "DATA_LIMIT_MB"

        var isSharing: Boolean = false
            private set
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SHARING -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: ""
                val pin = intent.getStringExtra(EXTRA_PIN) ?: ""
                val relayUrl = intent.getStringExtra(EXTRA_RELAY_URL) ?: RelayProtocol.DEFAULT_RELAY_URL
                val limitMb = intent.getLongExtra(EXTRA_DATA_LIMIT_MB, 0L)
                startSharing(sessionId, pin, relayUrl, limitMb)
            }
            ACTION_STOP_SHARING -> {
                stopSharing()
            }
        }
        return START_NOT_STICKY
    }

    private fun startSharing(sessionId: String, pin: String, relayUrl: String, limitMb: Long) {
        if (isSharing) return
        isSharing = true

        val request = Request.Builder()
            .url(relayUrl)
            .addHeader("X-NetShare-Role", "SENDER")
            .addHeader("X-NetShare-Session", sessionId)
            .addHeader("X-NetShare-PIN", pin)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val regMsg = JSONObject().apply {
                    put("type", RelayProtocol.TYPE_REGISTER_SENDER)
                    put("sessionId", sessionId)
                    put("pin", pin)
                    put("limitMb", limitMb)
                }
                webSocket.send(regMsg.toString())
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val packetData = bytes.toByteArray()
                DataUsageTracker.recordBytesReceived(packetData.size.toLong())
            }
        })
    }

    private fun stopSharing() {
        isSharing = false
        webSocket?.close(1000, "Sender closed session")
        webSocket = null
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}