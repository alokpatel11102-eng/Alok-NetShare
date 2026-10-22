package com.alok.netshare

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.alok.netshare.auth.AuthManager
import com.alok.netshare.ui.screens.*
import com.alok.netshare.ui.theme.AlokNetShareTheme
import com.alok.netshare.vpn.NetShareVpnService

class MainActivity : ComponentActivity() {

    private var pendingSessionId: String = ""
    private var pendingPin: String = ""
    private var pendingRelayUrl: String = ""

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            launchVpnService()
        } else {
            Toast.makeText(this, "VPN permission is required to route internet through NetShare", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var isDarkTheme by remember { mutableStateOf(true) }
            var currentScreen by remember { mutableStateOf("home") }
            val currentUser by AuthManager.currentUser.collectAsState()

            AlokNetShareTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Scaffold(
                        bottomBar = {
                            if (currentUser != null) {
                                NavigationBar {
                                    NavigationBarItem(
                                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                        label = { Text("Home") },
                                        selected = currentScreen == "home",
                                        onClick = { currentScreen = "home" }
                                    )
                                    NavigationBarItem(
                                        icon = { Icon(Icons.Default.ArrowUpward, contentDescription = "Send") },
                                        label = { Text("Send") },
                                        selected = currentScreen == "send",
                                        onClick = { currentScreen = "send" }
                                    )
                                    NavigationBarItem(
                                        icon = { Icon(Icons.Default.ArrowDownward, contentDescription = "Receive") },
                                        label = { Text("Receive") },
                                        selected = currentScreen == "receive",
                                        onClick = { currentScreen = "receive" }
                                    )
                                    NavigationBarItem(
                                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                        label = { Text("Settings") },
                                        selected = currentScreen == "settings",
                                        onClick = { currentScreen = "settings" }
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            if (currentUser == null) {
                                LoginScreen(
                                    onLoginSuccess = { currentScreen = "home" }
                                )
                            } else {
                                when (currentScreen) {
                                    "home" -> HomeScreen(
                                        onSendClick = { currentScreen = "send" },
                                        onReceiveClick = { currentScreen = "receive" }
                                    )
                                    "send" -> SendScreen(
                                        onBack = { currentScreen = "home" }
                                    )
                                    "receive" -> ReceiveScreen(
                                        onBack = { currentScreen = "home" },
                                        onRequestConnect = { sid, pin, relay ->
                                            requestVpnConnection(sid, pin, relay)
                                        },
                                        onDisconnect = {
                                            disconnectVpn()
                                        }
                                    )
                                    "settings" -> SettingsScreen(
                                        isDarkTheme = isDarkTheme,
                                        onToggleTheme = { isDarkTheme = !isDarkTheme },
                                        onLogout = {
                                            AuthManager.logout()
                                            currentScreen = "home"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestVpnConnection(sessionId: String, pin: String, relayUrl: String) {
        pendingSessionId = sessionId
        pendingPin = pin
        pendingRelayUrl = relayUrl

        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            launchVpnService()
        }
    }

    private fun launchVpnService() {
        val intent = Intent(this, NetShareVpnService::class.java).apply {
            action = NetShareVpnService.ACTION_CONNECT
            putExtra(NetShareVpnService.EXTRA_SESSION_ID, pendingSessionId)
            putExtra(NetShareVpnService.EXTRA_PIN, pendingPin)
            putExtra(NetShareVpnService.EXTRA_RELAY_URL, pendingRelayUrl)
        }
        startService(intent)
        Toast.makeText(this, "Connecting to sender's internet tunnel...", Toast.LENGTH_SHORT).show()
    }

    private fun disconnectVpn() {
        val intent = Intent(this, NetShareVpnService::class.java).apply {
            action = NetShareVpnService.ACTION_DISCONNECT
        }
        startService(intent)
        Toast.makeText(this, "Disconnected from internet tunnel", Toast.LENGTH_SHORT).show()
    }
}