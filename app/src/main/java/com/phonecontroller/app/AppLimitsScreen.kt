package com.phonecontroller.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun AppLimitsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var apps by remember { mutableStateOf(listOf<InstalledApp>()) }
    var limits by remember { mutableStateOf(mapOf<String, Int>()) }
    var editingApp by remember { mutableStateOf<InstalledApp?>(null) }

    LaunchedEffect(Unit) {
        apps = InstalledAppsHelper.getLaunchableApps(context)
    }

    fun refreshLimits() {
        limits = AppLimitsStore.getAllLimits(context)
    }
    LaunchedEffect(Unit) { refreshLimits() }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "App limits", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text("Back") }
            }
            Text(
                text = "Pick apps and set a daily time limit. Once reached today, opening that app sends him back to the home screen.",
                style = MaterialTheme.typography.bodySmall
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(apps) { app ->
                    val limit = limits[app.packageName]
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = app.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = if (limit != null) "Limit: ${limit} min/day" else "No limit",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Button(onClick = { editingApp = app }) {
                                Text(if (limit != null) "Edit" else "Set limit")
                            }
                        }
                    }
                }
            }
        }
    }

    editingApp?.let { app ->
        SetLimitDialog(
            app = app,
            currentLimit = limits[app.packageName],
            onDismiss = { editingApp = null },
            onSave = { minutes ->
                AppLimitsStore.setLimitMinutes(context, app.packageName, minutes)
                refreshLimits()
                editingApp = null
            },
            onRemove = {
                AppLimitsStore.removeLimit(context, app.packageName)
                refreshLimits()
                editingApp = null
            }
        )
    }
}

@Composable
private fun SetLimitDialog(
    app: InstalledApp,
    currentLimit: Int?,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    onRemove: () -> Unit
) {
    var text by remember { mutableStateOf(currentLimit?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input -> text = input.filter { it.isDigit() } },
                label = { Text("Daily limit (minutes)") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { text.toIntOrNull()?.let { onSave(it) } },
                enabled = text.toIntOrNull()?.let { it > 0 } == true
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (currentLimit != null) {
                    TextButton(onClick = onRemove) { Text("Remove limit") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
