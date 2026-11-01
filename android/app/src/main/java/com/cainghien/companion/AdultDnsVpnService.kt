package com.cainghien.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Đường hầm VPN chỉ lấy riêng truy vấn DNS của máy: tên 18+ trả về NXDOMAIN, phần còn lại
 * đẩy lên bộ lọc gia đình AdGuard.
 *
 * Ba nguyên tắc sống còn, vì một VPN lỗi có thể làm mất mạng cả điện thoại:
 * - fail-open tuyệt đối: packet lạ thì bỏ qua, hai máy chủ upstream đều không trả lời
 *   thì tự rút đường hầm để hệ thống dùng DNS bình thường trở lại;
 * - không tự bật lại sau khi lỗi, người dùng phải chạm công tắc lần nữa;
 * - chỉ nhận IPv4, UDP, cổng 53, không đụng tới loại traffic nào khác.
 */
class AdultDnsVpnService : VpnService() {
    @Volatile private var running = false
    private var tunnel: ParcelFileDescriptor? = null
    private var domains: Set<String> = emptySet()
    private val misses = ArrayDeque<Long>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs(this).adultFilterEnabled = false
            stopEverything()
            return START_NOT_STICKY
        }
        if (!running) begin()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }

    private fun begin() {
        if (Build.VERSION.SDK_INT < 27) {
            giveUp("Android 8.0 trở xuống không mở được đường hầm lọc DNS an toàn")
            return
        }
        domains = try {
            AdultDomains.load(this)
        } catch (error: Exception) {
            giveUp("Không đọc được danh sách chặn")
            return
        }
        if (domains.isEmpty()) {
            giveUp("Danh sách chặn trống")
            return
        }
        val device = try {
            openTunnel()
        } catch (error: Exception) {
            null
        }
        if (device == null) {
            giveUp("Không mở được đường hầm lọc DNS")
            return
        }
        tunnel = device
        running = true
        Runtime.adultFilter = true
        notify(STILL, "Đang chặn nội dung 18+", "Chạy suốt ngày đêm · ${domains.size} tên miền")
        Thread { pump(device) }.start()
    }

    private fun openTunnel(): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession(SESSION)
            .addAddress(TUNNEL_IP, 32)
            .addDnsServer(FAKE_DNS)
            .addRoute(FAKE_DNS, 32)
            .addDisallowedApplication(packageName)
            .setMtu(1500)
        // setBlocking(false) cần API 27. Không có nó thì Android 8.0 chỉ nhận mỗi traffic
        // khớp route và rơi phần còn lại, nghĩa là mất mạng.
        if (Build.VERSION.SDK_INT < 27) return null
        return builder.setBlocking(false).establish()
    }

    private fun pump(device: ParcelFileDescriptor) {
        val buffer = ByteArray(65535)
        try {
            FileInputStream(device.fileDescriptor).use { input ->
                FileOutputStream(device.fileDescriptor).use { output ->
                    while (running) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read > 0) handle(buffer, read, output)
                    }
                }
            }
        } catch (error: IOException) {
            // Hệ thống đóng tunnel (tắt VPN, đổi mạng, gỡ app): để nó tắt hẳn.
        } finally {
            stopEverything()
        }
    }

    private fun handle(packet: ByteArray, length: Int, output: OutputStream) {
        val query = AdultFilter.parseQuery(packet, length) ?: return
        val name = AdultFilter.questionName(query.payload) ?: return
        val answer = if (AdultFilter.blockedBy(name, domains)) {
            AdultFilter.nxdomain(query)
        } else {
            askUpstream(query.payload) ?: answerOrGiveUp(query)
        } ?: return
        val reply = AdultFilter.replyPacket(query, answer) ?: return
        try {
            synchronized(output) {
                output.write(reply)
                output.flush()
            }
        } catch (error: IOException) {
            stopEverything()
        }
    }

    private fun answerOrGiveUp(query: AdultFilter.Query): ByteArray? {
        val now = SystemClock.elapsedRealtime()
        misses.addLast(now)
        while (misses.isNotEmpty() && now - misses.first() > FAILURE_WINDOW_MS) misses.removeFirst()
        if (misses.size >= FAILURE_LIMIT) {
            giveUp("Không gọi được máy chủ DNS nên bộ lọc tự tắt")
            return null
        }
        return AdultFilter.servfail(query)
    }

    private fun askUpstream(payload: ByteArray): ByteArray? {
        for (server in UPSTREAMS) {
            val reply = askOne(server, payload) ?: continue
            if (reply.size >= 12 && reply[0] == payload[0] && reply[1] == payload[1]) return reply
        }
        return null
    }

    private fun askOne(server: String, payload: ByteArray): ByteArray? {
        val socket = try {
            DatagramSocket()
        } catch (error: Exception) {
            return null
        }
        return try {
            socket.soTimeout = QUERY_TIMEOUT_MS
            socket.connect(InetAddress.getByName(server), AdultFilter.DNS_PORT)
            socket.send(DatagramPacket(payload, payload.size))
            val buffer = ByteArray(4096)
            val incoming = DatagramPacket(buffer, buffer.size)
            socket.receive(incoming)
            buffer.copyOf(incoming.length)
        } catch (error: Exception) {
            null
        } finally {
            socket.close()
        }
    }

    /** Rút đường hầm, DNS hệ thống trở lại bình thường. Không bao giờ tự chạy lại. */
    private fun giveUp(reason: String) {
        Prefs(this).adultFilterEnabled = false
        notify(WENT, "Bộ lọc 18+ đã tự tắt", reason)
        stopEverything()
    }

    private fun stopEverything() {
        running = false
        val device = tunnel
        tunnel = null
        try {
            device?.close()
        } catch (error: IOException) {
            // đóng hai lần thì bỏ qua
        }
        Runtime.adultFilter = false
        manager().cancel(STILL)
        manager().cancel(WENT)
        stopSelf()
    }

    private fun notify(id: Int, title: String, text: String) {
        val manager = manager()
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Chặn nội dung 18+", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, AdultDnsVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(id == STILL)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(R.drawable.ic_shield, "Tắt bộ lọc", stop).build())
            .build()
        try {
            manager.notify(id, notification)
        } catch (error: Exception) {
            // Chưa cấp quyền thông báo thì đường hầm vẫn chạy, chỉ mất nút tắt nhanh.
        }
    }

    private fun manager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val ACTION_STOP = "com.cainghien.companion.ADULT_STOP"
        private const val SESSION = "Kỷ luật"
        private const val TUNNEL_IP = "10.113.23.2"
        private const val FAKE_DNS = "10.113.23.1"
        private val UPSTREAMS = listOf("94.140.14.14", "94.140.14.15")
        private const val CHANNEL = "adult_filter"
        private const val STILL = 44
        private const val WENT = 45
        private const val QUERY_TIMEOUT_MS = 2000
        private const val FAILURE_WINDOW_MS = 30_000L
        private const val FAILURE_LIMIT = 4
    }
}
