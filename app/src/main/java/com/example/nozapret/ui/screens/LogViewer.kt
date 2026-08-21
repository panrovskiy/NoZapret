package com.example.nozapret.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nozapret.R

@Composable
fun LogViewer(
    logLines: List<String>,
    onClearLogs: () -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    
    val msgLogsCopied = stringResource(R.string.msg_logs_copied)

    LaunchedEffect(logLines.size, Unit) {
        if (logLines.isNotEmpty()) {
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            val isAtBottom = (lastVisibleItemIndex != null) && (lastVisibleItemIndex >= (logLines.size - 5))
            if (isAtBottom || lastVisibleItemIndex == null) {
                listState.scrollToItem(logLines.size - 1)
            }
        }
    }

    fun copyLogsToClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("NoZapret Logs", logLines.joinToString("\n"))
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, msgLogsCopied, Toast.LENGTH_SHORT).show()
    }

    fun shareLogs() {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, logLines.joinToString("\n"))
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, null)
        context.startActivity(shareIntent)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.title_native_logs),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Row {
                IconButton(onClick = { copyLogsToClipboard() }) {
                    Icon(Icons.Rounded.ContentCopy, stringResource(R.string.btn_copy_logs))
                }
                IconButton(onClick = { shareLogs() }) {
                    Icon(Icons.Rounded.Share, stringResource(R.string.btn_share_logs))
                }
                IconButton(onClick = onClearLogs) { 
                    Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) 
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 12.dp, start = 12.dp, end = 12.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(logLines) { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall.copy(
                            lineHeight = 16.sp,
                            letterSpacing = 0.sp
                        ),
                        fontFamily = FontFamily.Monospace,
                        color = when {
                            line.contains(" E/") || line.contains("Error") -> MaterialTheme.colorScheme.error
                            line.contains(" W/") || line.contains("Warning") -> MaterialTheme.colorScheme.tertiary
                            line.contains(" D/") -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}
