package com.alok.netshare.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.alok.netshare.MainActivity
import com.alok.netshare.R
import com.alok.netshare.tunnel.RelayProtocol
import com.alok.netshare.tunnel.TunnelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

class NetShareVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var tunnelJob: Job? = null
    private val TAG = "NetShareVpnService"

    companion object {
        const val ACTION_CONNECT = "com.alok.netshare.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.alok.netshare.vpn.DISCONNECT"
        const val EXTRA_SESSION_ID = "SESSION_ID"
        const val EXTRA_PIN = "PIN"
        const val EXTRA_RELAY_URL = "RELAY_URL"
        const val NOTIFICATION_ID = 4040
        const val CHANNEL_ID = "netshare_vpn_channel"

        var isRunning: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: ""
                val pin = intent.getStringExtra(EXTRA_PIN) ?: ""
                val relayUrl = intent.getStringExtra(EXTRA_RELAY_URL) ?: RelayProtocol.DEFAULT_RELAY_URL
                startVpnTunnel(sessionId, pin, relayUrl)
            }
            ACTION_DISCONNECT -> {
                stopVpnTunnel()
            }
        }
        return START_NOT_STICKY
    }

    private fun startVpnTunnel(sessionId: String, pin: String, relayUrl: String) {
        if (isRunning) return
        startForeground(NOTIFICATION_ID, buildForegroundNotification("Connecting to Alok NetShare..."))

        try {
            val builder = Builder()
                .setSession("AlokNetShareVpn")
                .addAddress("10.8.0.2", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .setMtu(1400)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.addDisallowedApplication(packageName)
            }

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN TUN interface.")
                stopSelf()
                return
            }

            isRunning = true

            tunnelJob = serviceScope.launch {
                runTunnelLoop(sessionId, pin, relayUrl)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN: ${e.message}", e)
            stopVpnTunnel()
        }
    }

    private suspend fun runTunnelLoop(sessionId: String, pin: String, relayUrl: String) {
        val descriptor = vpnInterface?.fileDescriptor ?: return
        val inputStream = FileInputStream(descriptor)
        val outputStream = FileOutputStream(descriptor)
        val packetBuffer = ByteBuffer.allocate(32767)

        TunnelManager.connectReceiver(relayUrl, sessionId, pin, onPacketReceived = { packetData ->
            try {
                outputStream.write(packetData)
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to TUN interface: ${e.message}")
            }
        })

        while (serviceScope.isActive && isRunning) {
            try {
                val length = inputStream.read(packetBuffer.array())
                if (length > 0) {
                    val outgoingPacket = ByteArray(length)
                    System.arraycopy(packetBuffer.array(), 0, outgoingPacket, 0, length)
                    TunnelManager.sendPacket(outgoingPacket)
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "TUN packet read error: ${e.message}")
                }
                break
            }
        }
    }

    private fun stopVpnTunnel() {
        isRunning = false
        tunnelJob?.cancel()
        TunnelManager.disconnect()

        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface: ${e.message}")
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}