package com.example.nozapret.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.nozapret.MainViewModel
import com.example.nozapret.R
import com.example.nozapret.ui.getLocalizedStrategyName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestDetailsView(
    strategyName: String, 
    results: Map<String, MainViewModel.TlsTestResult>,
    onBack: () -> Unit,
    onApply: (String) -> Unit,
    onUseAsCustom: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                getLocalizedStrategyName(strategyName), 
                style = MaterialTheme.typography.headlineSmall, 
                fontWeight = FontWeight.ExtraBold
            )
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) { Icon(Icons.Rounded.Close, null) }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onApply(strategyName) },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(stringResource(R.string.btn_apply_and_run))
            }
            OutlinedButton(
                onClick = { onUseAsCustom(strategyName) },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(stringResource(R.string.btn_use_in_custom))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val sortedSites = results.keys.sortedWith(compareByDescending<String> { results[it]?.success == true }.thenBy { it })
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(sortedSites) { site ->
                val result = results[site] ?: MainViewModel.TlsTestResult(false)
                val success = result.success
                val ping = result.ping
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (success)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                    ),
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(
                        1.dp, 
                        if (success) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) 
                        else MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                    )
                ) {
                    ListItem(
                        headlineContent = { 
                            Text(
                                site, 
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            ) 
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (success && ping != null) {
                                    val pingColor = when {
                                        ping < 200 -> Color(0xFF4CAF50) // Green
                                        ping < 500 -> Color(0xFFFFC107) // Yellow
                                        else -> Color(0xFFF44336) // Red
                                    }
                                    Text(
                                        "${ping}ms",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = pingColor
                                    )
                                } else if (!success) {
                                    Text(
                                        "Timeout",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                
                                Icon(
                                    if (success) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                                    null,
                                    tint = if (success) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        },
                        supportingContent = if (success && (result.protocol != null || result.cipherSuite != null)) {
                            {
                                Column(modifier = Modifier.padding(top = 4.dp)) {
                                    result.protocol?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                        )
                                    }
                                    result.cipherSuite?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically, 
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Text(
                                            "HTTP:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                        Icon(
                                            if (result.httpSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                                            null,
                                            modifier = Modifier.size(12.dp),
                                            tint = if (result.httpSuccess) Color(0xFF4CAF50).copy(alpha = 0.8f) else MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                        )
                                        if (!result.httpSuccess && result.httpError != null) {
                                            Text(
                                                result.httpError,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        } else null,
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
            if (sortedSites.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.no_results_yet), 
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}
