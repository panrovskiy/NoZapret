package com.example.nozapret.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.nozapret.ui.theme.MotionConstants
import kotlinx.coroutines.delay

@Composable
fun FadeEntrance(
    index: Int = 0,
    delayUnit: Int = 50,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay((index * delayUnit).toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(MotionConstants.DurationEmphasis)) + 
                slideInVertically(tween(MotionConstants.DurationEmphasis)) { 20 },
        modifier = modifier
    ) {
        content()
    }
}
