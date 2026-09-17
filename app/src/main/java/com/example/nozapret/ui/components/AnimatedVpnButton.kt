package com.example.nozapret.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import com.example.nozapret.ui.theme.MotionConstants

enum class VpnButtonState {
    DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, ERROR
}

@Composable
fun AnimatedVpnButton(
    state: VpnButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "InfiniteBreathing")
    
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == VpnButtonState.CONNECTING) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = MotionConstants.StandardEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Breathing"
    )

    val breathingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = if (state == VpnButtonState.CONNECTED) 0.4f else 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = MotionConstants.StandardEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ConnectedGlow"
    )

    val color by animateColorAsState(
        targetValue = when (state) {
            VpnButtonState.CONNECTED -> MaterialTheme.colorScheme.primary
            VpnButtonState.CONNECTING -> MaterialTheme.colorScheme.secondary
            VpnButtonState.DISCONNECTING -> MaterialTheme.colorScheme.outline
            VpnButtonState.ERROR -> MaterialTheme.colorScheme.error
            VpnButtonState.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(MotionConstants.DurationNormal),
        label = "ButtonColor"
    )

    val contentColor by animateColorAsState(
        targetValue = when (state) {
            VpnButtonState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onPrimary
        },
        animationSpec = tween(MotionConstants.DurationNormal),
        label = "ContentColor"
    )

    Box(
        modifier = modifier
            .size(170.dp)
            .graphicsLayer {
                scaleX = if (state == VpnButtonState.CONNECTING) breathingScale else 1f
                scaleY = if (state == VpnButtonState.CONNECTING) breathingScale else 1f
            }
            .clip(CircleShape)
            .bouncingClickable(enabled = state != VpnButtonState.CONNECTING && state != VpnButtonState.DISCONNECTING) {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        // Outer Glow / Ring
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = if (state == VpnButtonState.CONNECTED) 1f + (breathingAlpha * 0.1f) else 1f
                    scaleX = s
                    scaleY = s
                },
            shape = CircleShape,
            color = color.copy(alpha = if (state == VpnButtonState.DISCONNECTED) 0.1f else 0.2f),
            border = null
        ) {}

        // Main Circle
        Surface(
            modifier = Modifier
                .size(140.dp)
                .shadow(
                    elevation = if (state == VpnButtonState.CONNECTED) 12.dp else 4.dp,
                    shape = CircleShape,
                    ambientColor = color,
                    spotColor = color
                ),
            shape = CircleShape,
            color = color,
        ) {
            Box(contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        fadeIn(tween(MotionConstants.DurationNormal)) + scaleIn() togetherWith
                                fadeOut(tween(MotionConstants.DurationNormal)) + scaleOut()
                    },
                    label = "IconTransition"
                ) { targetState ->
                    when (targetState) {
                        VpnButtonState.CONNECTING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(64.dp),
                                color = contentColor,
                                strokeWidth = 4.dp
                            )
                        }
                        VpnButtonState.CONNECTED -> {
                            Icon(
                                Icons.Rounded.Shield,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = contentColor
                            )
                        }
                        else -> {
                            Icon(
                                Icons.Rounded.PowerSettingsNew,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = contentColor
                            )
                        }
                    }
                }
            }
        }
    }
}
