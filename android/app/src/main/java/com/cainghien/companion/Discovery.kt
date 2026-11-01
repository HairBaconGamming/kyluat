package com.cainghien.companion

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

data class PcService(val name: String, val host: String, val port: Int)

object PcDiscovery {
    private const val SERVICE_TYPE = "_kyluat._tcp."

    fun browse(
        context: Context,
        onResult: (List<PcService>) -> Unit,
        onError: (String) -> Unit,
        timeoutMs: Long = 9000,
    ) {
        val appContext = context.applicationContext
        val nsd = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
        if (nsd == null) {
            onError("Thiết bị không hỗ trợ tìm máy trong mạng.")
            return
        }
        val handler = Handler(Looper.getMainLooper())
        val results = mutableListOf<PcService>()
        val finished = AtomicBoolean(false)
        var discovery: NsdManager.DiscoveryListener? = null
        var multicast: WifiManager.MulticastLock? = null
        try {
            val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicast = wifi?.createMulticastLock("cainghien-browse")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (error: Exception) {
            // mDNS vẫn có thể hoạt động
        }

        fun cleanup() {
            try {
                discovery?.let { nsd.stopServiceDiscovery(it) }
            } catch (error: Exception) {
            }
            try {
                multicast?.release()
            } catch (error: Exception) {
            }
        }

        discovery = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                if (!finished.compareAndSet(false, true)) return
                cleanup()
                handler.post { onError("Không bắt đầu được tìm máy (mã $errorCode).") }
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
            }

            override fun onDiscoveryStopped(serviceType: String?) {
            }

            override fun onDiscoveryStarted(serviceType: String?) {
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                if (serviceInfo == null || finished.get()) return
                try {
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo?, errorCode: Int) {
                        }

                        override fun onServiceResolved(info: NsdServiceInfo?) {
                            val host = info?.host?.hostAddress
                            if (host.isNullOrEmpty()) return
                            handler.post {
                                if (finished.get()) return@post
                                val entry = PcService(info.serviceName ?: "Máy tính", host, info.port)
                                if (results.none { it.host == entry.host && it.port == entry.port }) {
                                    results.add(entry)
                                    onResult(results.toList())
                                }
                            }
                        }
                    })
                } catch (error: Exception) {
                    // bỏ qua dịch vụ không đọc được
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
            }
        }

        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
        } catch (error: Exception) {
            onError("Không tìm được máy tính trong mạng.")
            return
        }
        handler.postDelayed({
            if (finished.compareAndSet(false, true)) cleanup()
        }, timeoutMs)
    }

    fun find(
        context: Context,
        onFound: (host: String, port: Int) -> Unit,
        onError: (String) -> Unit,
    ) {
        val appContext = context.applicationContext
        val nsd = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
        if (nsd == null) {
            onError("Thiết bị không hỗ trợ tìm máy trong mạng.")
            return
        }
        val finished = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        var discovery: NsdManager.DiscoveryListener? = null
        var multicast: WifiManager.MulticastLock? = null
        try {
            val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicast = wifi?.createMulticastLock("cainghien-nsd")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (error: Exception) {
            // mDNS vẫn có thể hoạt động
        }

        fun cleanup() {
            try {
                discovery?.let { nsd.stopServiceDiscovery(it) }
            } catch (error: Exception) {
            }
            try {
                multicast?.release()
            } catch (error: Exception) {
            }
            handler.removeCallbacksAndMessages(null)
        }

        fun succeed(host: String, port: Int) {
            if (!finished.compareAndSet(false, true)) return
            cleanup()
            onFound(host, port)
        }

        fun fail(message: String) {
            if (!finished.compareAndSet(false, true)) return
            cleanup()
            onError(message)
        }

        discovery = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                fail("Không bắt đầu được tìm máy (mã $errorCode).")
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
            }

            override fun onDiscoveryStopped(serviceType: String?) {
            }

            override fun onDiscoveryStarted(serviceType: String?) {
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                if (serviceInfo == null || finished.get()) return
                try {
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo?, errorCode: Int) {
                            fail("Không đọc được địa chỉ máy tính (mã $errorCode).")
                        }

                        override fun onServiceResolved(info: NsdServiceInfo?) {
                            val host = info?.host?.hostAddress
                            if (host.isNullOrEmpty()) {
                                fail("Máy tính không trả về địa chỉ mạng.")
                                return
                            }
                            succeed(host, info.port)
                        }
                    })
                } catch (error: Exception) {
                    fail("Không kết nối được dịch vụ tìm máy.")
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
            }
        }

        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
        } catch (error: Exception) {
            fail("Không tìm được máy tính trong mạng.")
            return
        }
        handler.postDelayed({
            fail("Không thấy máy tính trong 7 giây. Kiểm tra cùng Wi-Fi hoặc dùng mã QR.")
        }, 7000)
    }
}
