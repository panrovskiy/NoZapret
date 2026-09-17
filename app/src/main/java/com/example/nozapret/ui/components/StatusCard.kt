package com.example.nozapret.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nozapret.R
import com.example.nozapret.ui.theme.MotionConstants

@Composable
fun StatusCard(
    vpnActive: Boolean,
    backendActive: Boolean,
    networkType: String,
    dnsServer: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatusItem(
                icon = Icons.Rounded.VpnLock,
                label = stringResource(R.string.label_vpn),
                value = if (vpnActive) stringResource(R.string.status_active) else stringResource(R.string.status_inactive),
                active = vpnActive
            )
            StatusItem(
                icon = Icons.Rounded.SettingsInputComponent,
                label = stringResource(R.string.label_backend),
                value = if (backendActive) stringResource(R.string.status_running) else stringResource(R.string.status_stopped),
                active = backendActive
            )
            StatusItem(
                icon = Icons.Rounded.Wifi,
                label = stringResource(R.string.label_network),
                value = networkType,
                active = true
            )
            StatusItem(
                icon = Icons.Rounded.Dns,
                label = stringResource(R.string.label_dns_short),
                value = dnsServer,
                active = true
            )
        }
    }
}

@Composable
private fun StatusItem(
    icon: ImageVector,
    label: String,
    value: String,
    active: Boolean
) {
    val color by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        animationSpec = tween(MotionConstants.DurationNormal),
        label = "ItemColor"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        AnimatedContent(
            targetState = value,
            transitionSpec = {
                fadeIn(tween(MotionConstants.DurationFast)) togetherWith fadeOut(tween(MotionConstants.DurationFast))
            },
            label = "ValueTransition"
        ) { targetValue ->
            Text(
                targetValue,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (active) FontWeight.Bold else null,
                color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
            )
        }
    }
}
