package com.phonecontroller.app

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Screen { Dashboard, AppLimits, BlockedSites }

class MainActivity : ComponentActivity() {

    private lateinit var updateInstaller: UpdateInstaller

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateInstaller = UpdateInstaller(this)
        updateInstaller.register()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var screen by remember { mutableStateOf(Screen.Dashboard) }
                    when (screen) {
                        Screen.Dashboard -> DashboardScreen(
                            updateInstaller,
                            onManageLimits = { screen = Screen.AppLimits },
                            onManageBlockedSites = { screen = Screen.BlockedSites }
                        )
                        Screen.AppLimits -> AppLimitsScreen(onBack = { screen = Screen.Dashboard })
                        Screen.BlockedSites -> BlockedSitesScreen(onBack = { screen = Screen.Dashboard })
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        updateInstaller.unregister()
        super.onDestroy()
    }
}

@Composable
fun DashboardScreen(
    updateInstaller: UpdateInstaller,
    onManageLimits: () -> Unit,
    onManageBlockedSites: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var hasUsageAccess by remember { mutableStateOf(UsageStatsHelper.hasUsageAccess(context)) }
    var accessibilityEnabled by remember { mutableStateOf(AppBlockerAccessibilityService.isEnabled(context)) }
    var canInstallUpdates by remember { mutableStateOf(updateInstaller.canInstallUpdates()) }
    var usage by remember { mutableStateOf(listOf<AppUsageInfo>()) }
    var websiteBlockingActive by remember { mutableStateOf(DomainBlockVpnService.isRunning) }

    var checkingForUpdate by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloadStarted by remember { mutableStateOf(false) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            context.startForegroundService(Intent(context, DomainBlockVpnService::class.java))
            websiteBlockingActive = true
        }
    }

    // Re-check permissions and reload stats every time the user comes back
    // to this screen (e.g. after visiting Settings).
    LifecycleResumeEffect(Unit) {
        hasUsageAccess = UsageStatsHelper.hasUsageAccess(context)
        accessibilityEnabled = AppBlockerAccessibilityService.isEnabled(context)
        canInstallUpdates = updateInstaller.canInstallUpdates()
        usage = UsageStatsHelper.getTodayUsage(context)
        websiteBlockingActive = DomainBlockVpnService.isRunning
        onPauseOrDispose { }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Phone Controller", style = MaterialTheme.typography.headlineSmall)

            PermissionCard(
                title = "Usage access",
                granted = hasUsageAccess,
                description = "Needed to read today's per-app screen time.",
                onRequest = {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            )

            PermissionCard(
                title = "Accessibility service",
                granted = accessibilityEnabled,
                description = "Needed to detect the foreground app and enforce blocks.",
                onRequest = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )

            Button(
                onClick = onManageLimits,
                enabled = accessibilityEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (accessibilityEnabled) "Manage app limits" else "Enable accessibility service first")
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "Website blocking", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "A local VPN that blocks DNS lookups for sites on your list, across every app. Needs Private DNS off in system settings to be fully effective.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = if (websiteBlockingActive) "Active" else "Off",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (websiteBlockingActive) {
                                context.startService(
                                    Intent(context, DomainBlockVpnService::class.java)
                                        .setAction(DomainBlockVpnService.ACTION_STOP)
                                )
                                websiteBlockingActive = false
                            } else {
                                val consentIntent = VpnService.prepare(context)
                                if (consentIntent != null) {
                                    vpnPermissionLauncher.launch(consentIntent)
                                } else {
                                    context.startForegroundService(Intent(context, DomainBlockVpnService::class.java))
                                    websiteBlockingActive = true
                                }
                            }
                        }) {
                            Text(if (websiteBlockingActive) "Stop" else "Start")
                        }
                        Button(onClick = onManageBlockedSites) {
                            Text("Manage blocked sites")
                        }
                    }
                }
            }

            PermissionCard(
                title = "Install updates",
                granted = canInstallUpdates,
                description = "Needed so this app can install new versions it downloads itself.",
                onRequest = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "App version", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Installed: ${UpdateChecker.currentVersionName(context)}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    when {
                        downloadStarted -> Text(
                            text = "Downloading v${availableUpdate?.versionName} -- you'll get an install prompt when it's done.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        availableUpdate != null -> {
                            Text(
                                text = "Update available: v${availableUpdate!!.versionName}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(
                                enabled = canInstallUpdates,
                                onClick = {
                                    updateInstaller.downloadAndInstall(availableUpdate!!)
                                    downloadStarted = true
                                }
                            ) {
                                Text("Download & install")
                            }
                            if (!canInstallUpdates) {
                                Text(
                                    text = "Grant \"Install updates\" above first.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        else -> Button(
                            enabled = !checkingForUpdate,
                            onClick = {
                                checkingForUpdate = true
                                coroutineScope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        UpdateChecker.checkForUpdate(context)
                                    }
                                    availableUpdate = result
                                    checkingForUpdate = false
                                }
                            }
                        ) {
                            Text(if (checkingForUpdate) "Checking..." else "Check for updates")
                        }
                    }
                }
            }

            Text(text = "Today's screen time", style = MaterialTheme.typography.titleMedium)

            if (!hasUsageAccess) {
                Text(
                    text = "Grant usage access above to see per-app usage.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else if (usage.isEmpty()) {
                Text(text = "No usage recorded yet today.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(usage) { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = app.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = UsageStatsHelper.formatDuration(app.foregroundMillis),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    granted: Boolean,
    description: String,
    onRequest: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(text = description, style = MaterialTheme.typography.bodySmall)
            Text(
                text = if (granted) "Granted" else "Not granted",
                style = MaterialTheme.typography.bodySmall
            )
            if (!granted) {
                Button(onClick = onRequest) {
                    Text("Open settings")
                }
            }
        }
    }
}
