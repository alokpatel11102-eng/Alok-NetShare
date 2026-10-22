package com.alok.netshare.tunnel

import org.json.JSONObject

object RelayProtocol {
    const val DEFAULT_RELAY_URL = "wss://netshare-relay.example.com/tunnel"
    const val PROTOCOL_VERSION = "1.0"

    const val TYPE_REGISTER_SENDER = "REGISTER_SENDER"
    const val TYPE_SENDER_REGISTERED = "SENDER_REGISTERED"
    const val TYPE_CONNECT_RECEIVER = "CONNECT_RECEIVER"
    const val TYPE_RECEIVER_AUTHORIZED = "RECEIVER_AUTHORIZED"
    const val TYPE_DATA_PACKET = "DATA_PACKET"
    const val TYPE_DISCONNECT = "DISCONNECT"

    data class QrPayload(
        val sessionId: String,
        val pin: String,
        val relayUrl: String,
        val senderPublicKey: String,
        val expiresAt: Long
    ) {
        fun toJson(): String {
            val json = JSONObject()
            json.put("v", PROTOCOL_VERSION)
            json.put("sid", sessionId)
            json.put("pin", pin)
            json.put("relay", relayUrl)
            json.put("pubkey", senderPublicKey)
            json.put("exp", expiresAt)
            return json.toString()
        }
    }

    data class ConnectedDevice(
        val deviceId: String,
        val deviceName: String,
        val connectedAt: Long,
        var bytesUploaded: Long = 0L,
        var bytesDownloaded: Long = 0L,
        var isAuthorized: Boolean = true
    )
}