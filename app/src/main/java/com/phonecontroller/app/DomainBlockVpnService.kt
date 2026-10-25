package com.phonecontroller.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

/**
 * A local, DNS-only VPN: routes just DNS queries (not general traffic) into
 * this process, answers blocked domains with NXDOMAIN, and forwards
 * everything else to a real upstream resolver untouched. All other network
 * traffic bypasses the VPN entirely and goes straight out, since only the
 * fake DNS server's /32 address is routed here (see [startVpn]) -- so this
 * never has to implement general IP packet forwarding.
 *
 * Known limitations (documented in the UI too): this only sees plain
 * port-53 DNS, so it's blind to a system "Private DNS" setting left on, or
 * a browser's own built-in secure DNS pointed at a specific provider. Both
 * need to be off/automatic for blocking to actually apply.
 */
class DomainBlockVpnService : VpnService() {

    companion object {
        const val ACTION_STOP = "com.phonecontroller.app.action.STOP_VPN"
        private const val TUN_ADDRESS = "10.0.0.2"
        private const val FAKE_DNS_SERVER = "10.0.0.1"
        private const val UPSTREAM_DNS = "8.8.8.8"
        private const val NOTIFICATION_CHANNEL_ID = "vpn_status"
        private const val NOTIFICATION_ID = 42

        @Volatile
        var isRunning = false
            private set
    }

    private var tunInterface: ParcelFileDescriptor? = null
    private var readerThread: Thread? = null
    @Volatile private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
        } else {
            startVpn()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        // User revoked VPN consent from system Settings directly.
        stopVpn()
        super.onRevoke()
    }

    private fun startVpn() {
        if (running) return

        val establishedInterface = Builder()
            .addAddress(TUN_ADDRESS, 32)
            .addDnsServer(FAKE_DNS_SERVER)
            .addRoute(FAKE_DNS_SERVER, 32)
            .setSession("Phone Controller website blocking")
            .setMtu(1500)
            .establish() ?: return

        tunInterface = establishedInterface
        running = true
        isRunning = true
        startForeground(NOTIFICATION_ID, buildNotification())
        readerThread = thread(name = "dns-vpn-reader") { runReadLoop(establishedInterface) }
    }

    private fun stopVpn() {
        running = false
        isRunning = false
        readerThread?.interrupt()
        readerThread = null
        tunInterface?.close()
        tunInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun runReadLoop(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buffer = ByteArray(32767)

        while (running) {
            val length = try {
                input.read(buffer)
            } catch (e: Exception) {
                if (running) continue else break
            }
            if (length <= 0) continue

            val query = DnsPacketUtil.parseDnsQuery(buffer, length) ?: continue
            val querySnapshot = query.copy(rawDnsMessage = query.rawDnsMessage.copyOf())
            thread(name = "dns-query") { handleQuery(querySnapshot, output) }
        }
    }

    private fun handleQuery(query: DnsQuery, output: FileOutputStream) {
        val responsePacket = if (DomainBlocklistStore.isBlocked(this, query.domain)) {
            DnsPacketUtil.buildNxDomainResponse(query)
        } else {
            val upstreamResponse = forwardToUpstream(query) ?: return
            DnsPacketUtil.buildForwardedResponse(query, upstreamResponse)
        }
        synchronized(output) {
            try {
                output.write(responsePacket)
            } catch (e: Exception) {
                // tun was closed meanwhile (VPN stopped); nothing to do.
            }
        }
    }

    private fun forwardToUpstream(query: DnsQuery): ByteArray? {
        return try {
            DatagramSocket().use { socket ->
                protect(socket) // bypass the VPN for this socket, or we'd loop forever
                socket.soTimeout = 5000
                socket.send(DatagramPacket(query.rawDnsMessage, query.rawDnsMessage.size, InetAddress.getByName(UPSTREAM_DNS), 53))
                val responseBuffer = ByteArray(512)
                val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(responsePacket)
                responseBuffer.copyOf(responsePacket.length)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID, "Website blocking", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, DomainBlockVpnService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Phone Controller")
            .setContentText("Website blocking is active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stopPendingIntent).build())
            .build()
    }
}
