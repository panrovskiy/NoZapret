package com.example.nozapret.services

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.example.nozapret.MainActivity

class VpnTileService : TileService() {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateTile()
        }
    }

    private var isReceiverRegistered = false

    override fun onStartListening() {
        super.onStartListening()
        if (!isReceiverRegistered) {
            val filter = IntentFilter(DpiVpnService.ACTION_VPN_STATE_CHANGED)
            // Use RECEIVER_NOT_EXPORTED for internal broadcasts to satisfy Android 13+ requirements and improve security
            ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            isReceiverRegistered = true
        }
        updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(receiver)
            } catch (_: Exception) {
                // Ignore if not registered
            }
            isReceiverRegistered = false
        }
    }

    private var lastClickTime = 0L

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    override fun onClick() {
        val currentTime = System.currentTimeMillis()
        if ((currentTime - lastClickTime) < 300) return
        lastClickTime = currentTime

        super.onClick()

        // 1. Check if VPN is prepared (authorized by user)
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("start_vpn_on_resume", true)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                startActivityAndCollapse(intent)
            }
            return
        }

        // 2. Toggle VPN state
        if (DpiVpnService.isRunning) {
            val intent = Intent(this, DpiVpnService::class.java).apply {
                action = DpiVpnService.ACTION_STOP
            }
            // startService is fine here because the service is already running in foreground
            startService(intent)
        } else {
            val intent = Intent(this, DpiVpnService::class.java).apply {
                action = DpiVpnService.ACTION_START
            }
            ContextCompat.startForegroundService(this, intent)
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.state = if (DpiVpnService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
