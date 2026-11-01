package com.cainghien.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

private const val CLIENT = 0x0A010105
private const val SERVER = 0x0A711701
private const val CLIENT_PORT = 41234

class AdultFilterTest {
    private val domains = setOf("pornhub.com", "onlyfans.com")

    @Test
    fun `parse query keeps address ports and payload`() {
        val dns = query("www.PornHub.com")
        val packet = udp(dns)
        val parsed = AdultFilter.parseQuery(packet, packet.size)
        assertNotNull(parsed)
        parsed!!
        assertEquals(CLIENT, parsed.clientIp)
        assertEquals(SERVER, parsed.dnsIp)
        assertEquals(CLIENT_PORT, parsed.clientPort)
        sameBytes(dns, parsed.payload)
        assertEquals("www.pornhub.com", AdultFilter.questionName(parsed.payload))
        assertTrue(AdultFilter.blockedBy(AdultFilter.questionName(parsed.payload), domains))
    }

    @Test
    fun `parse query refuses anything that is not a dns lookup`() {
        val dns = query("example.com")
        assertNull(AdultFilter.parseQuery(udp(dns, protocol = 6), dns.size + 28))
        assertNull(AdultFilter.parseQuery(udp(dns, port = 5353), dns.size + 28))
        assertNull(AdultFilter.parseQuery(udp(dns, fragments = 1), dns.size + 28))
        assertNull(AdultFilter.parseQuery(udp(dns, fragments = 0x2000), dns.size + 28))
        assertNull(AdultFilter.parseQuery(udp(dns.copyOfRange(0, 10)), 38))
        val six = ByteArray(40 + dns.size)
        six[0] = 0x60
        dns.copyInto(six, 40)
        assertNull(AdultFilter.parseQuery(six, six.size))
        assertNull(AdultFilter.parseQuery(ByteArray(10), 10))
    }

    @Test
    fun `blocking walks name branches without matching inside a label`() {
        assertTrue(AdultFilter.blockedBy("pornhub.com", domains))
        assertTrue(AdultFilter.blockedBy("m.pornhub.com", domains))
        assertTrue(AdultFilter.blockedBy("cdn.onlyfans.com", domains))
        assertFalse(AdultFilter.blockedBy("notpornhub.com", domains))
        assertFalse(AdultFilter.blockedBy("pornhub.com.evil.example", domains))
        assertFalse(AdultFilter.blockedBy("hub.com", domains))
        assertFalse(AdultFilter.blockedBy("", domains))
        assertFalse(AdultFilter.blockedBy(null, domains))
    }

    @Test
    fun `question name reads only queries`() {
        assertEquals("example.com", AdultFilter.questionName(query("example.com")))
        val alreadyAnswered = query("example.com").also { it[2] = 0x81.toByte() }
        assertNull(AdultFilter.questionName(alreadyAnswered))
        assertNull(AdultFilter.questionName(ByteArray(8)))
    }

    @Test
    fun `nxdomain keeps the question and drops records after it`() {
        val dns = query("www.pornhub.com")
        val padded = dns + byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        val parsed = AdultFilter.parseQuery(udp(padded), padded.size + 28)!!
        val answer = AdultFilter.nxdomain(parsed)
        assertNotNull(answer)
        answer!!
        assertEquals(dns.size, answer.size)
        sameBytes(dns.copyOfRange(12, dns.size), answer.copyOfRange(12, answer.size))
        assertEquals(0x8103, bits(answer, 2))
        assertEquals(1, bits(answer, 4))
        assertEquals(0, bits(answer, 6))
        assertEquals(0, bits(answer, 8))
        assertEquals(0, bits(answer, 10))
    }

    @Test
    fun `servfail answers the same question with code two`() {
        val dns = query("example.com")
        val parsed = AdultFilter.parseQuery(udp(dns), dns.size + 28)!!
        val answer = AdultFilter.servfail(parsed)!!
        assertEquals(0x8102, bits(answer, 2))
        sameBytes(dns.copyOfRange(12, dns.size), answer.copyOfRange(12, answer.size))
    }

    @Test
    fun `reply swaps addresses and ports and carries a valid header checksum`() {
        val dns = query("example.com")
        val parsed = AdultFilter.parseQuery(udp(dns), dns.size + 28)!!
        val payload = AdultFilter.nxdomain(parsed)!!
        val packet = AdultFilter.replyPacket(parsed, payload)
        assertNotNull(packet)
        packet!!
        assertEquals(0x45, packet[0].toInt() and 0xFF)
        assertEquals(20 + 8 + payload.size, bits(packet, 2))
        assertEquals(SERVER, dword(packet, 12))
        assertEquals(CLIENT, dword(packet, 16))
        assertEquals(53, bits(packet, 20))
        assertEquals(CLIENT_PORT, bits(packet, 22))
        assertEquals(8 + payload.size, bits(packet, 24))
        assertEquals(0, bits(packet, 26))
        sameBytes(payload, packet.copyOfRange(28, packet.size))
        assertEquals(0xFFFF, headerSum(packet))
        val blank = packet.copyOf().also { put16(it, 10, 0) }
        assertEquals(bits(packet, 10), AdultFilter.checksum(blank, 0, 20))
    }

    @Test
    fun `oversized answers are refused instead of fragmented`() {
        val dns = query("example.com")
        val parsed = AdultFilter.parseQuery(udp(dns), dns.size + 28)!!
        assertNull(AdultFilter.replyPacket(parsed, ByteArray(AdultFilter.MAX_ANSWER + 1)))
        val fits = AdultFilter.replyPacket(parsed, ByteArray(AdultFilter.MAX_ANSWER))
        assertNotNull(fits)
        assertEquals(1500, fits!!.size)
    }

    private fun query(name: String, id: Int = 0x1234): ByteArray {
        val out = ByteArrayOutputStream()
        write16(out, id)
        write16(out, 0x0100)
        write16(out, 1)
        write16(out, 0)
        write16(out, 0)
        write16(out, 0)
        if (name.isNotEmpty()) {
            name.split('.').forEach { label ->
                out.write(label.length)
                out.write(label.toByteArray(Charsets.US_ASCII))
            }
        }
        out.write(0)
        write16(out, 1)
        write16(out, 1)
        return out.toByteArray()
    }

    private fun udp(payload: ByteArray, protocol: Int = 17, port: Int = 53, fragments: Int = 0): ByteArray {
        val total = 20 + 8 + payload.size
        val packet = ByteArray(total)
        packet[0] = 0x45
        put16(packet, 2, total)
        put16(packet, 6, fragments)
        packet[8] = 64
        packet[9] = protocol.toByte()
        put32(packet, 12, CLIENT)
        put32(packet, 16, SERVER)
        put16(packet, 20, CLIENT_PORT)
        put16(packet, 22, port)
        put16(packet, 24, 8 + payload.size)
        payload.copyInto(packet, 28)
        put16(packet, 10, AdultFilter.checksum(packet, 0, 20))
        return packet
    }

    private fun headerSum(packet: ByteArray): Int {
        var sum = 0
        for (index in 0 until 20 step 2) sum += bits(packet, index)
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum
    }

    private fun sameBytes(expected: ByteArray, actual: ByteArray) {
        assertTrue("hai mảng byte khác nhau", expected.contentEquals(actual))
    }

    private fun write16(out: ByteArrayOutputStream, value: Int) {
        out.write((value shr 8) and 0xFF)
        out.write(value and 0xFF)
    }

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

    private fun bits(data: ByteArray, at: Int) =
        ((data[at].toInt() and 0xFF) shl 8) or (data[at + 1].toInt() and 0xFF)

    private fun dword(data: ByteArray, at: Int) =
        ((data[at].toInt() and 0xFF) shl 24) or ((data[at + 1].toInt() and 0xFF) shl 16) or
            ((data[at + 2].toInt() and 0xFF) shl 8) or (data[at + 3].toInt() and 0xFF)
}
