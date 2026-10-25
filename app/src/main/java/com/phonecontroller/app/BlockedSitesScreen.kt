package com.phonecontroller.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
fun BlockedSitesScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var text by remember { mutableStateOf("") }
    var savedConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        text = DomainBlocklistStore.toEditableText(context)
    }

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
                Text(text = "Blocked websites", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text("Back") }
            }
            Text(
                text = "One domain per line, e.g. facebook.com -- also blocks its subdomains (m.facebook.com, etc). Applies while website blocking is turned on.",
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                label = { Text("Blocked domains") }
            )

            Button(
                onClick = {
                    DomainBlocklistStore.saveFromText(context, text)
                    text = DomainBlocklistStore.toEditableText(context)
                    savedConfirmation = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }

            if (savedConfirmation) {
                Text(text = "Saved.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
