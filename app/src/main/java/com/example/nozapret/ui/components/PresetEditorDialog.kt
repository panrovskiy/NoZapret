package com.example.nozapret.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nozapret.R
import com.example.nozapret.ui.getLocalizedPresetName
import com.example.nozapret.ui.getPresetIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetEditorDialog(
    presetName: String,
    domains: List<String>,
    onAddDomain: (String) -> Result<Unit>,
    onRemoveDomain: (String) -> Unit,
    onEditDomain: (String, String) -> Result<Unit>,
    onClearDomains: () -> Unit,
    onRestoreDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var newDomainInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var editingDomain by remember { mutableStateOf<String?>(null) }
    var editInput by remember { mutableStateOf("") }

    val filteredDomains = remember(domains, searchQuery) {
        if (searchQuery.isBlank()) domains
        else domains.filter { it.contains(searchQuery.trim(), ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(getPresetIcon(presetName), null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(
                            text = getLocalizedPresetName(presetName),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.preset_sites_count, domains.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newDomainInput,
                        onValueChange = {
                            newDomainInput = it
                            errorMessage = null
                        },
                        label = { Text(stringResource(R.string.label_add_domain)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                    IconButton(
                        onClick = {
                            if (newDomainInput.isNotBlank()) {
                                val res = onAddDomain(newDomainInput)
                                if (res.isSuccess) {
                                    newDomainInput = ""
                                    errorMessage = null
                                } else {
                                    errorMessage = res.exceptionOrNull()?.message ?: context.getString(R.string.error_invalid_domain)
                                }
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Rounded.Add, stringResource(R.string.btn_add))
                    }
                }

                errorMessage?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (domains.size > 5) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.label_search)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Clear, null)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (filteredDomains.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (domains.isEmpty()) stringResource(R.string.preset_empty_list) else stringResource(R.string.preset_no_matches),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredDomains, key = { it }) { domain ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = domain,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    trailingContent = {
                                        Row {
                                            IconButton(onClick = {
                                                editingDomain = domain
                                                editInput = domain
                                            }) {
                                                Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = { onRemoveDomain(domain) }) {
                                                Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_done))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRestoreDefaults) {
                    Text(stringResource(R.string.btn_restore_defaults))
                }
                TextButton(
                    onClick = onClearDomains,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.btn_clear_all))
                }
            }
        }
    )

    editingDomain?.let { oldDom ->
        AlertDialog(
            onDismissRequest = { editingDomain = null },
            title = { Text(stringResource(R.string.title_edit_domain)) },
            text = {
                OutlinedTextField(
                    value = editInput,
                    onValueChange = { editInput = it },
                    label = { Text(stringResource(R.string.label_domain)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val res = onEditDomain(oldDom, editInput)
                    if (res.isSuccess) {
                        editingDomain = null
                    } else {
                        Toast.makeText(context, res.exceptionOrNull()?.message ?: context.getString(R.string.error_invalid_domain), Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text(stringResource(R.string.btn_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingDomain = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}
