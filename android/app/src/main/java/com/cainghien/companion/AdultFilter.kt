package com.cainghien.companion

/**
 * Bộ lọc gói tin cho đường hầm VPN chống nội dung 18+.
 *
 * Chỉ đọc gói tin IPv4/UDP gửi tới cổng 53, ngoài ra trả về null để mạch chính bỏ qua.
 * Không import class Android nào cả, nhờ vậy toàn bộ logic chạy được trên JVM trong unit test.
 */
object AdultFilter {
    const val DNS_PORT = 53
    const val MAX_ANSWER = 1472

    class Query(
        val payload: ByteArray,
        val clientIp: Int,
        val dnsIp: Int,
        val clientPort: Int,
    )

    fun parseQuery(packet: ByteArray, length: Int): Query? {
        if (length < 20) return null
        if ((packet[0].toInt() shr 4) != 4) return null
        val header = (packet[0].toInt() and 0x0F) * 4
        if (header < 20 || length < header + 20) return null
        if (packet[9].toInt() != 17) return null
        if ((u16(packet, 6) and 0x3FFF) != 0) return null
        if (u16(packet, header + 2) != DNS_PORT) return null
        val udp = u16(packet, header + 4)
        if (udp < 8) return null
        val size = minOf(udp - 8, length - header - 8)
        if (size < 12) return null
        val payload = packet.copyOfRange(header + 8, header + 8 + size)
        return Query(payload, u32(packet, 12), u32(packet, 16), u16(packet, header))
    }

    /** Tên miền trong câu hỏi, đã bỏ dấu chấm cuối và viết thường. Null khi packet lạ. */
    fun questionName(payload: ByteArray): String? {
        if (payload.size < 13) return null
        if ((u16(payload, 2) and 0x8000) != 0) return null
        if (u16(payload, 4) == 0) return null
        val name = StringBuilder()
        var index = 12
        while (true) {
            if (index >= payload.size) return null
            val size = payload[index].toInt() and 0xFF
            if (size == 0) break
            if ((size and 0xC0) != 0) return null
            if (index + size >= payload.size) return null
            if (name.isNotEmpty()) name.append('.')
            name.append(payload.copyOfRange(index + 1, index + 1 + size).toString(Charsets.US_ASCII).lowercase())
            index += size + 1
        }
        return if (name.isEmpty()) null else name.toString()
    }

    /** So theo từng nhánh tên, nên notpornhub.com không dính pornhub.com. */
    fun blockedBy(name: String?, domains: Set<String>): Boolean {
        if (name.isNullOrEmpty()) return false
        var start = 0
        while (true) {
            if (domains.contains(name.substring(start))) return true
            val dot = name.indexOf('.', start)
            if (dot < 0) return false
            start = dot + 1
        }
    }

    fun nxdomain(query: Query): ByteArray? = respond(query, 3)

    fun servfail(query: Query): ByteArray? = respond(query, 2)

    fun replyPacket(query: Query, payload: ByteArray): ByteArray? {
        if (payload.size > MAX_ANSWER) return null
        val total = 20 + 8 + payload.size
        val packet = ByteArray(total)
        packet[0] = 0x45
        put16(packet, 2, total)
        put16(packet, 6, 0x4000)
        packet[8] = 64
        packet[9] = 17
        put32(packet, 12, query.dnsIp)
        put32(packet, 16, query.clientIp)
        put16(packet, 20, DNS_PORT)
        put16(packet, 22, query.clientPort)
        put16(packet, 24, 8 + payload.size)
        payload.copyInto(packet, 28)
        put16(packet, 10, checksum(packet, 0, 20))
        return packet
    }

    /** Cộng bù một trên các word 16 bit, dùng cho tổng kiểm tra của IP header. */
    fun checksum(data: ByteArray, from: Int, until: Int): Int {
        var sum = 0
        var index = from
        while (index + 1 < until) {
            sum += u16(data, index)
            index += 2
        }
        if (index < until) sum += (data[index].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    private fun respond(query: Query, code: Int): ByteArray? {
        val payload = query.payload
        if (payload.size < 12) return null
        val questions = u16(payload, 4)
        if (questions == 0) return null
        var index = 12
        repeat(questions) {
            val end = skipName(payload, index) ?: return null
            index = end + 4
            if (index > payload.size) return null
        }
        val answer = payload.copyOf(index)
        put16(answer, 2, 0x8000 or (u16(payload, 2) and 0x0100) or code)
        put16(answer, 6, 0)
        put16(answer, 8, 0)
        put16(answer, 10, 0)
        return answer
    }

    private fun skipName(data: ByteArray, from: Int): Int? {
        var index = from
        while (index < data.size) {
            val size = data[index].toInt() and 0xFF
            if (size == 0) return index + 1
            if ((size and 0xC0) != 0) return index + 2
            index += size + 1
        }
        return null
    }

    private fun u16(data: ByteArray, at: Int): Int =
        ((data[at].toInt() and 0xFF) shl 8) or (data[at + 1].toInt() and 0xFF)

    private fun u32(data: ByteArray, at: Int): Int =
        ((data[at].toInt() and 0xFF) shl 24) or ((data[at + 1].toInt() and 0xFF) shl 16) or
            ((data[at + 2].toInt() and 0xFF) shl 8) or (data[at + 3].toInt() and 0xFF)

    private fun put16(data: ByteArray, at: Int, value: Int) {
        data[at] = ((value shr 8) and 0xFF).toByte()
        data[at + 1] = (value and 0xFF).toByte()
    }

    private fun put32(data: ByteArray, at: Int, value: Int) {
        data[at] = ((value shr 24) and 0xFF).toByte()
        data[at + 1] = ((value shr 16) and 0xFF).toByte()
        data[at + 2] = ((value shr 8) and 0xFF).toByte()
        data[at + 3] = (value and 0xFF).toByte()
    }
}
