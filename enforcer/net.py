from __future__ import annotations

import asyncio
import ctypes
import ipaddress
import json
import os
import socket
import sqlite3
import subprocess
import threading
import time
from pathlib import Path
from typing import Callable

from dnslib import A, AAAA, QTYPE, RR, DNSRecord

from . import proc
from .adult_list import ADULT_DOMAINS
from .config import Config
from .store import Store

UPSTREAMS = ("1.1.1.1", "8.8.8.8")
# AdGuard DNS Family: lớn hơn 1.1.1.1 một bước vì nó đã lọc sẵn nội dung người lớn,
# nên phần việc của danh sách chọn tay chỉ là những nền tảng bị bộ lọc bỏ sót.
ADULT_UPSTREAMS = ("94.140.14.14", "94.140.14.15")
SELF_NAME = "enforcer.local"
LOOPBACK_V4 = "127.0.0.1"
LOOPBACK_V6 = "::1"
DOH_POLICIES = (
    (r"SOFTWARE\Policies\Google\Chrome", "DnsOverHttpsMode"),
    (r"SOFTWARE\Policies\Microsoft\Edge", "DnsOverHttpsMode"),
)


def domain_matches(qname: str, domain: str) -> bool:
    name = qname.rstrip(".").lower()
    base = domain.rstrip(".").lower()
    return name == base or name.endswith("." + base)


_ADULT_SET = frozenset(ADULT_DOMAINS)


def adult_blocked(qname: str) -> bool:
    name = qname.rstrip(".").lower()
    start = 0
    while True:
        if name[start:] in _ADULT_SET:
            return True
        dot = name.find(".", start)
        if dot < 0:
            return False
        start = dot + 1


def local_query(name: str, timeout: float = 2.0) -> list[str] | None:
    """Hỏi đúng sinkhole ở 127.0.0.1, không đi qua DNS hệ thống."""
    sock = None
    try:
        from dnslib import DNSRecord

        query = DNSRecord.question(name, "A")
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.settimeout(timeout)
        sock.sendto(query.pack(), ("127.0.0.1", 53))
        data, _ = sock.recvfrom(4096)
        reply = DNSRecord.parse(data)
        if reply.header.rcode == 3:
            return ["NXDOMAIN"]
        return [str(rr.rdata) for rr in reply.rr]
    except Exception:
        return None
    finally:
        if sock is not None:
            sock.close()


def adult_selftest() -> tuple[bool, bool, str]:
    adult = local_query("pornhub.com")
    safe = local_query("example.com")
    if adult is None or safe is None:
        return False, False, (
            "Không thấy bộ lọc DNS ở 127.0.0.1:53. Bấm Bật chặn web trước, rồi thử lại."
        )
    blocked = any(value in ("0.0.0.0", "::", "NXDOMAIN") for value in adult)
    safe_ok = any(value not in ("0.0.0.0", "::", "NXDOMAIN") for value in safe)
    if blocked and safe_ok:
        return True, True, (
            "Bộ lọc đang chặn đúng: tên mẫu người lớn không phân giải được, tên an toàn vẫn mở.\n\n"
            "Lưu ý: trình duyệt tự bật DNS-over-HTTPS hoặc một VPN khác vẫn có thể đi vòng qua lớp này."
        )
    if not blocked:
        return False, safe_ok, (
            "Bộ lọc chưa chặn tên mẫu. Kiểm tra xem đã bật chặn web chưa, DNS card mạng có trỏ về "
            "127.0.0.1 không, và trình duyệt có đang bật DoH không."
        )
    return True, False, "Bộ lọc đang chặn cả tên miền an toàn; kiểm tra lại cấu hình DNS."


def upstreams_for(config: Config) -> tuple[str, ...]:
    return ADULT_UPSTREAMS if config.adult_block else UPSTREAMS


class _DnsProtocol(asyncio.DatagramProtocol):
    def __init__(self, sinkhole: "Sinkhole"):
        self.sinkhole = sinkhole
        self.transport: asyncio.DatagramTransport | None = None

    def connection_made(self, transport) -> None:
        self.transport = transport

    def datagram_received(self, data: bytes, addr) -> None:
        if self.transport is not None:
            asyncio.create_task(self.sinkhole.handle(data, addr, self.transport))


class _ResponseProtocol(asyncio.DatagramProtocol):
    def __init__(self, future: asyncio.Future):
        self.future = future

    def datagram_received(self, data: bytes, addr) -> None:
        if not self.future.done():
            self.future.set_result(data)


class Sinkhole:
    def __init__(
        self,
        get_config: Callable[[], Config],
        get_blocking: Callable[[], bool],
        db_path: Path,
        port: int = 53,
    ):
        self.get_config = get_config
        self.get_blocking = get_blocking
        self.port = port
        self.store = Store(db_path)
        self._transports: list[asyncio.DatagramTransport] = []
        self._logged_at: dict[str, float] = {}

    def blocked(self, qname: str) -> bool:
        config = self.get_config()
        # 18+ chặn suốt ngày, không chờ phiên focus; danh sách domain vẫn chỉ theo giờ focus.
        if config.adult_block and adult_blocked(qname):
            return True
        if not self.get_blocking():
            return False
        if not config.web_domains:
            return False
        return any(domain_matches(qname, domain) for domain in config.web_domains)

    async def handle(self, data: bytes, addr, transport) -> None:
        try:
            request = DNSRecord.parse(data)
        except Exception:
            return
        qname = str(request.q.qname).rstrip(".").lower()
        if qname == SELF_NAME:
            reply = request.reply()
            reply.add_answer(RR(request.q.qname, QTYPE.A, rdata=A("127.0.0.1"), ttl=5))
            transport.sendto(reply.pack(), addr)
            return
        if qname and self.blocked(qname):
            reply = request.reply()
            if request.q.qtype == QTYPE.A:
                reply.add_answer(RR(request.q.qname, QTYPE.A, rdata=A("0.0.0.0"), ttl=30))
            elif request.q.qtype == QTYPE.AAAA:
                reply.add_answer(RR(request.q.qname, QTYPE.AAAA, rdata=AAAA("::"), ttl=30))
            transport.sendto(reply.pack(), addr)
            self._log(qname, adult=self.get_config().adult_block and adult_blocked(qname))
            return
        try:
            response = await self._forward(data)
        except (OSError, asyncio.TimeoutError):
            return
        transport.sendto(response, addr)

    async def _forward(self, data: bytes) -> bytes:
        for upstream in upstreams_for(self.get_config()):
            try:
                return await asyncio.wait_for(self._query(upstream, data), timeout=2)
            except (OSError, asyncio.TimeoutError):
                continue
        raise OSError("upstream không phản hồi")

    async def _query(self, upstream: str, data: bytes) -> bytes:
        loop = asyncio.get_running_loop()
        future = loop.create_future()
        transport, _ = await loop.create_datagram_endpoint(
            lambda: _ResponseProtocol(future), remote_addr=(upstream, 53)
        )
        try:
            transport.sendto(data)
            return await future
        finally:
            transport.close()

    async def serve(self) -> None:
        loop = asyncio.get_running_loop()
        last_error: OSError | None = None
        for host in ("127.0.0.1", "::1"):
            try:
                transport, _ = await loop.create_datagram_endpoint(
                    lambda: _DnsProtocol(self), local_addr=(host, self.port)
                )
                self._transports.append(transport)
            except OSError as exc:
                last_error = exc
        if not self._transports:
            raise OSError(f"không bind được cổng {self.port}: {last_error}")
        while True:
            await asyncio.sleep(3600)

    def _log(self, qname: str, adult: bool = False) -> None:
        now = time.monotonic()
        if now - self._logged_at.get(qname, 0.0) < 60:
            return
        self._logged_at[qname] = now
        try:
            self.store.event("block_adult" if adult else "block_web", qname)
        except sqlite3.Error:
            pass


def start_sinkhole(
    get_config: Callable[[], Config],
    get_blocking: Callable[[], bool],
    db_path: Path,
    port: int = 53,
    on_dead: Callable[[OSError], None] | None = None,
) -> threading.Thread:
    def runner() -> None:
        sinkhole = Sinkhole(get_config, get_blocking, db_path, port)
        try:
            asyncio.run(sinkhole.serve())
        except OSError as exc:
            try:
                sinkhole.store.event("net_error", str(exc)[:300])
            except sqlite3.Error:
                pass
            print(f"[net] {exc}", flush=True)
            # Máy vẫn đang trỏ DNS về đây, nên thread chết là mạng chết.
            if on_dead is not None:
                try:
                    on_dead(exc)
                except Exception as other:
                    print(f"[net] fail-open lỗi: {other}", flush=True)

    thread = threading.Thread(target=runner, name="enforcer-dns", daemon=True)
    thread.start()
    return thread


def probe(port: int = 53, timeout: float = 2.0) -> str | None:
    try:
        query = DNSRecord.question(SELF_NAME, "A")
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.settimeout(timeout)
            sock.sendto(query.pack(), ("127.0.0.1", port))
            data, _ = sock.recvfrom(4096)
        reply = DNSRecord.parse(data)
        for record in reply.rr:
            if record.rtype == QTYPE.A:
                return str(record.rdata)
    except (OSError, ValueError):
        return None
    return None


def is_admin() -> bool:
    try:
        return bool(ctypes.windll.shell32.IsUserAnAdmin())
    except (AttributeError, OSError):
        return False


def _run_ps(script: str) -> str:
    try:
        result = proc.run(
            ["powershell", "-NoProfile", "-NonInteractive", "-Command", script],
            capture_output=True,
            timeout=20,
        )
    except subprocess.TimeoutExpired:
        raise RuntimeError("PowerShell timeout") from None
    if result.returncode != 0:
        message = (result.stderr or result.stdout).decode("utf-8", "replace").strip()
        raise RuntimeError(message or "PowerShell thất bại")
    return result.stdout.decode("utf-8", "replace").strip()


def active_interface_index() -> int | None:
    try:
        text = _run_ps(
            "(Get-NetIPConfiguration | Where-Object { $_.IPv4DefaultGateway -ne $null } "
            "| Select-Object -First 1).InterfaceIndex"
        )
    except RuntimeError:
        return None
    return int(text) if text.isdigit() else None


def get_dns(index: int) -> dict[str, list[str]]:
    script = (
        f"$i={index}; "
        "$a=(Get-DnsClientServerAddress -InterfaceIndex $i -AddressFamily IPv4 -ErrorAction SilentlyContinue).ServerAddresses; "
        "$b=(Get-DnsClientServerAddress -InterfaceIndex $i -AddressFamily IPv6 -ErrorAction SilentlyContinue).ServerAddresses; "
        "[pscustomobject]@{v4=@($a);v6=@($b)} | ConvertTo-Json -Compress"
    )
    data = json.loads(_run_ps(script) or "{}")

    def as_list(value) -> list[str]:
        if isinstance(value, str):
            return [value]
        return [str(item) for item in (value or [])]

    return {"v4": as_list(data.get("v4")), "v6": as_list(data.get("v6"))}


def set_dns(index: int, v4: list[str], v6: list[str]) -> None:
    if not v4 and not v6:
        _run_ps(f"Set-DnsClientServerAddress -InterfaceIndex {index} -ResetServerAddresses")
        return
    for addresses in (v4, v6):
        if not addresses:
            continue
        for address in addresses:
            ipaddress.ip_address(address)
        joined = ",".join(f"'{address}'" for address in addresses)
        _run_ps(f"Set-DnsClientServerAddress -InterfaceIndex {index} -ServerAddresses @({joined})")


def without_loopback(addresses: list[str], loopback: str) -> list[str]:
    return [address for address in addresses if address != loopback]


def rescue_dns(index: int, backup: dict | None) -> tuple[str, list[str], list[str]]:
    """Trả card mạng về DNS thật, không bao giờ để nó trỏ tiếp về máy.

    Backup có thể được ghi đúng lúc máy đang bị nhốt ở 127.0.0.1; khôi phục value đó là
    nhốt mạng vĩnh viễn. Không có giá trị thật nào thì mặc định trả về DHCP.
    """
    v4 = without_loopback(list((backup or {}).get("v4") or []), LOOPBACK_V4)
    v6 = without_loopback(list((backup or {}).get("v6") or []), LOOPBACK_V6)
    if v4 or v6:
        set_dns(index, v4, v6)
        return "backup", v4, v6
    set_dns(index, [], [])
    return "dhcp", [], []


def reg_get(key: str, name: str) -> str | None:
    result = proc.run(["reg", "query", rf"HKLM\{key}", "/v", name], capture_output=True)
    if result.returncode != 0:
        return None
    for line in result.stdout.decode("utf-8", "replace").splitlines():
        parts = line.split()
        if len(parts) >= 3 and parts[0].lower() == name.lower():
            return parts[-1]
    return ""


def reg_set(key: str, name: str, value: str) -> None:
    result = proc.run(
        ["reg", "add", rf"HKLM\{key}", "/v", name, "/t", "REG_SZ", "/d", value, "/f"],
        capture_output=True,
    )
    if result.returncode != 0:
        message = (result.stderr or result.stdout).decode("utf-8", "replace").strip()
        raise RuntimeError(message or "reg add thất bại")


def reg_delete(key: str, name: str) -> None:
    proc.run(["reg", "delete", rf"HKLM\{key}", "/v", name, "/f"], capture_output=True)


def flush_dns() -> None:
    try:
        proc.popen(["ipconfig", "/flushdns"])
    except OSError:
        pass


def current_wiring() -> dict | None:
    try:
        index = active_interface_index()
        if index is None:
            return None
        dns = get_dns(index)
    except Exception:
        # PowerShell có thể fail vì quyền hoặc card mạng vừa rút; caller luôn có nhánh None.
        return None
    return {
        "index": index,
        "v4": dns["v4"],
        "v6": dns["v6"],
        "loopback": LOOPBACK_V4 in dns["v4"] or LOOPBACK_V6 in dns["v6"],
    }
