package com.phonecontroller.app

/** A parsed IPv4/UDP/DNS query read off the VPN's tun interface. */
data class DnsQuery(
    val sourceIp: ByteArray,
    val sourcePort: Int,
    val destIp: ByteArray,
    val destPort: Int,
    val dnsId: Int,
    val domain: String,
    /** The full DNS message (header + question section) as received. */
    val rawDnsMessage: ByteArray
)

/**
 * Minimal, read-only-enough IPv4 + UDP + DNS (de)serialization -- just
 * enough to recognize a port-53 query, pull the queried name out of it, and
 * wrap a DNS response (either synthesized or from a real upstream server)
 * back into a valid IPv4/UDP packet addressed back to the querying app.
 *
 * Only IPv4 is handled; IPv6 DNS queries pass through [parseDnsQuery] as
 * null and are simply dropped by the caller (fine for v1 -- see the VPN
 * routing scope in DomainBlockVpnService, which only ever routes IPv4
 * traffic into the tun in the first place).
 */
object DnsPacketUtil {

    fun parseDnsQuery(packet: ByteArray, length: Int): DnsQuery? {
        if (length < 20) return null
        val versionAndIhl = packet[0].toInt() and 0xFF
        if (versionAndIhl shr 4 != 4) return null
        val ihl = (versionAndIhl and 0x0F) * 4
        if (ihl < 20 || length < ihl + 8) return null

        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return null // UDP only

        val sourceIp = packet.copyOfRange(12, 16)
        val destIp = packet.copyOfRange(16, 20)

        val udpStart = ihl
        val sourcePort = readUInt16(packet, udpStart)
        val destPort = readUInt16(packet, udpStart + 2)
        if (destPort != 53) return null

        val udpLength = readUInt16(packet, udpStart + 4)
        val dnsStart = udpStart + 8
        val dnsLength = udpLength - 8
        if (dnsLength <= 12 || dnsStart + dnsLength > length) return null

        val dnsMessage = packet.copyOfRange(dnsStart, dnsStart + dnsLength)
        val dnsId = readUInt16(dnsMessage, 0)
        val questionCount = readUInt16(dnsMessage, 4)
        if (questionCount < 1) return null

        val domain = try {
            parseDomainName(dnsMessage, 12)
        } catch (e: Exception) {
            null
        } ?: return null

        return DnsQuery(sourceIp, sourcePort, destIp, destPort, dnsId, domain, dnsMessage)
    }

    /** Builds a DNS reply (RCODE=3, NXDOMAIN, no answers) wrapped in IPv4/UDP, addressed back to the querying app. */
    fun buildNxDomainResponse(query: DnsQuery): ByteArray {
        val header = byteArrayOf(
            (query.dnsId shr 8).toByte(), query.dnsId.toByte(),
            0x81.toByte(), 0x83.toByte(), // QR=1 RD=1 | RA=1 RCODE=3 (NXDOMAIN)
            0, 1, // QDCOUNT=1
            0, 0, // ANCOUNT=0
            0, 0, // NSCOUNT=0
            0, 0  // ARCOUNT=0
        )
        val question = query.rawDnsMessage.copyOfRange(12, query.rawDnsMessage.size)
        return wrapInIpUdp(query, header + question)
    }

    /** Builds a reply carrying a real upstream DNS response verbatim, addressed back to the querying app. */
    fun buildForwardedResponse(query: DnsQuery, upstreamDnsResponse: ByteArray): ByteArray =
        wrapInIpUdp(query, upstreamDnsResponse)

    private fun parseDomainName(message: ByteArray, startOffset: Int): String? {
        val labels = mutableListOf<String>()
        var offset = startOffset
        while (offset < message.size) {
            val labelLength = message[offset].toInt() and 0xFF
            if (labelLength == 0) break
            if (offset + 1 + labelLength > message.size) return null
            labels.add(String(message, offset + 1, labelLength, Charsets.US_ASCII))
            offset += 1 + labelLength
        }
        return if (labels.isEmpty()) null else labels.joinToString(".")
    }

    private fun wrapInIpUdp(query: DnsQuery, dnsMessage: ByteArray): ByteArray {
        val udpLength = 8 + dnsMessage.size
        val totalLength = 20 + udpLength
        val packet = ByteArray(totalLength)

        packet[0] = 0x45 // IPv4, 20-byte header
        packet[1] = 0
        writeUInt16(packet, 2, totalLength)
        packet[4] = 0; packet[5] = 0
        packet[6] = 0x40; packet[7] = 0 // don't fragment
        packet[8] = 64 // TTL
        packet[9] = 17 // UDP
        packet[10] = 0; packet[11] = 0 // checksum, filled in below
        // Reply "from" the address that was queried, "to" the original querying app.
        System.arraycopy(query.destIp, 0, packet, 12, 4)
        System.arraycopy(query.sourceIp, 0, packet, 16, 4)
        writeUInt16(packet, 10, ipv4HeaderChecksum(packet))

        writeUInt16(packet, 20, query.destPort) // reply "from" port 53
        writeUInt16(packet, 22, query.sourcePort)
        writeUInt16(packet, 24, udpLength)
        packet[26] = 0; packet[27] = 0 // UDP checksum: 0 = not computed, valid for IPv4

        System.arraycopy(dnsMessage, 0, packet, 28, dnsMessage.size)
        return packet
    }

    private fun ipv4HeaderChecksum(header: ByteArray): Int {
        var sum = 0
        var i = 0
        while (i < 20) {
            sum += readUInt16(header, i)
            i += 2
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }

    private fun readUInt16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun writeUInt16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value shr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }
}
