package com.phonecontroller.app

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat

/**
 * Downloads an update APK via the system DownloadManager and hands it to the
 * package installer once the download finishes. One instance per Activity --
 * call [register] in onCreate and [unregister] in onDestroy.
 */
class UpdateInstaller(private val context: Context) {

    private var pendingDownloadId: Long = -1L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val finishedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (finishedId == -1L || finishedId != pendingDownloadId) return

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val apkUri = downloadManager.getUriForDownloadedFile(finishedId) ?: return
            promptInstall(apkUri)
        }
    }

    fun register() {
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    fun unregister() {
        context.unregisterReceiver(receiver)
    }

    fun canInstallUpdates(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun downloadAndInstall(update: UpdateInfo) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
            .setTitle("Phone Controller update")
            .setDescription("Downloading version ${update.versionName}")
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "phone-controller-${update.versionName}.apk"
            )
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        pendingDownloadId = downloadManager.enqueue(request)
    }

    private fun promptInstall(apkUri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}
