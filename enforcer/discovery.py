from __future__ import annotations

import socket

from zeroconf import ServiceInfo, Zeroconf

SERVICE_TYPE = "_kyluat._tcp.local."
_zeroconf: Zeroconf | None = None


def start(host: str, port: int) -> bool:
    global _zeroconf
    if _zeroconf is not None:
        return True
    try:
        hostname = socket.gethostname().split(".")[0]
        info = ServiceInfo(
            SERVICE_TYPE,
            f"KyLuat-{hostname}.{SERVICE_TYPE}",
            addresses=[socket.inet_aton(host)],
            port=port,
            properties={"version": "1", "path": "/api/phone/pair"},
            server=f"{hostname}.local.",
        )
        zeroconf = Zeroconf()
        zeroconf.register_service(info)
    except Exception:
        return False
    _zeroconf = zeroconf
    return True
