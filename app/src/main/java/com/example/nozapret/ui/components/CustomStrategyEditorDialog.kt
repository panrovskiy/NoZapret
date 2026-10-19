package com.example.nozapret.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nozapret.R
import com.example.nozapret.core.Config
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomStrategyEditorDialog(
    strategyToEdit: Config.CustomStrategy? = null,
    existingStrategies: List<Config.CustomStrategy> = emptyList(),
    onSave: (Config.CustomStrategy) -> Result<Unit>,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(strategyToEdit?.name ?: "") }
    var description by remember { mutableStateOf(strategyToEdit?.description ?: "") }
    var backend by remember { mutableStateOf(strategyToEdit?.backend ?: "ByeDPI") }
    var userArgs by remember { mutableStateOf(strategyToEdit?.userArgs ?: "") }
    var fakeSni by remember { mutableStateOf(strategyToEdit?.fakeSni ?: "") }
    var customDns by remember { mutableStateOf(strategyToEdit?.customDns ?: "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isEditing = strategyToEdit != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isEditing) Icons.Rounded.Edit else Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    if (isEditing) stringResource(R.string.title_edit_custom_strategy)
                    else stringResource(R.string.title_create_custom_strategy),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.label_strategy_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.label_strategy_description)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = userArgs,
                    onValueChange = { userArgs = it },
                    label = { Text(stringResource(R.string.label_custom_arguments)) },
                    placeholder = { Text("--tlsrec 1+s --disorder 1 --split 2 --wait-send") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                OutlinedTextField(
                    value = fakeSni,
                    onValueChange = { fakeSni = it },
                    label = { Text(stringResource(R.string.label_fake_sni_override)) },
                    placeholder = { Text("www.google.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = customDns,
                    onValueChange = {
                        customDns = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.label_custom_dns)) },
                    placeholder = { Text(stringResource(R.string.placeholder_custom_dns)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    if (trimmedName.isEmpty()) {
                        errorMessage = "Strategy name cannot be empty"
                        return@Button
                    }

                    if (existingStrategies.any { it.id != strategyToEdit?.id && it.name.equals(trimmedName, ignoreCase = true) }) {
                        errorMessage = "A strategy with this name already exists"
                        return@Button
                    }

                    val trimmedDns = customDns.trim()
                    if (trimmedDns.isNotEmpty()) {
                        val dnsValidation = Config.validateIpAddress(trimmedDns)
                        if (dnsValidation.isFailure) {
                            errorMessage = "Invalid Custom DNS IP address format"
                            return@Button
                        }
                    }

                    val strategy = Config.CustomStrategy(
                        id = strategyToEdit?.id ?: UUID.randomUUID().toString(),
                        name = trimmedName,
                        description = description.trim(),
                        backend = backend,
                        userArgs = userArgs.trim(),
                        fakeSni = fakeSni.trim().ifEmpty { null },
                        customDns = trimmedDns.ifEmpty { null },
                        category = "User Custom",
                        createdAt = strategyToEdit?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )

                    val result = onSave(strategy)
                    if (result.isSuccess) {
                        onDismiss()
                    } else {
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to save strategy"
                    }
                }
            ) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
