from __future__ import annotations

import ctypes
import ctypes.wintypes as wt
import time
from pathlib import Path

import psutil

from . import notify

MODEL_PATH = Path(__file__).with_name("data") / "nsfw_model.tflite"
THRESHOLD = 0.60
SCAN_INTERVAL_SECONDS = 3.5
BLOCK_PAUSE_SECONDS = 12.0
ERROR_PAUSE_SECONDS = 300.0
BROWSERS = frozenset(
    {
        "chrome.exe",
        "msedge.exe",
        "firefox.exe",
        "brave.exe",
        "opera.exe",
        "vivaldi.exe",
        "chromium.exe",
        "whale.exe",
        "arc.exe",
    }
)
TILES = (
    (0.15, 0.15, 0.85, 0.85),
    (0.0, 0.0, 0.5, 0.5),
    (0.5, 0.0, 1.0, 0.5),
    (0.0, 0.5, 0.5, 1.0),
    (0.5, 0.5, 1.0, 1.0),
)


def model_available() -> bool:
    return MODEL_PATH.is_file()


class VisualGuard:
    """Nhận diện ảnh 18+ trong cửa sổ trình duyệt đang mở, ngay trên máy.

    Ảnh chỉ tồn tại trong bộ nhớ của tiến trình daemon; không ghi tệp, không gửi mạng.
    Mọi lỗi đều bỏ qua và tạm dừng quét, không được phép làm hỏng DNS hay daemon.
    """

    def __init__(self) -> None:
        self._interpreter = None
        self._input_index = 0
        self._output_index = 0
        self._last_scan = 0.0
        self._paused_until = 0.0
        self._failures = 0
        self._aware()

    @staticmethod
    def _aware() -> None:
        try:
            ctypes.windll.shcore.SetProcessDpiAwareness(2)
        except (AttributeError, OSError):
            try:
                ctypes.windll.user32.SetProcessDPIAware()
            except (AttributeError, OSError):
                pass

    def _load(self) -> None:
        if self._interpreter is not None:
            return
        from ai_edge_litert.interpreter import Interpreter

        interpreter = Interpreter(model_path=str(MODEL_PATH), num_threads=2)
        interpreter.allocate_tensors()
        self._input_index = interpreter.get_input_details()[0]["index"]
        self._output_index = interpreter.get_output_details()[0]["index"]
        self._interpreter = interpreter

    def _scan(self, image) -> float:
        import numpy as np
        from PIL import Image

        width, height = image.size
        worst = 0.0
        for left, top, right, bottom in TILES:
            box = (
                int(width * left),
                int(height * top),
                max(int(width * right), int(width * left) + 2),
                max(int(height * bottom), int(height * top) + 2),
            )
            tile = image.crop(box).resize((224, 224), Image.Resampling.BILINEAR)
            data = np.asarray(tile, dtype=np.float32) / 255.0
            self._interpreter.set_tensor(self._input_index, np.expand_dims(data, 0))
            self._interpreter.invoke()
            scores = self._interpreter.get_tensor(self._output_index)[0]
            worst = max(worst, float(scores[1]) + float(scores[3]))
        return worst

    def check(self) -> bool:
        now = time.monotonic()
        if now < self._paused_until or now - self._last_scan < SCAN_INTERVAL_SECONDS:
            return False
        self._last_scan = now

        hwnd, process = _foreground()
        if not hwnd or process not in BROWSERS:
            return False
        box = _window_box(hwnd)
        if box is None:
            return False

        try:
            from PIL import ImageGrab

            self._load()
            image = ImageGrab.grab(bbox=box, all_screens=True).convert("RGB")
            worst = self._scan(image)
            self._failures = 0
            if worst >= THRESHOLD and _foreground()[0] == hwnd:
                _minimize(hwnd)
                notify.toast_async(
                    "Đã chặn nội dung 18+",
                    "Cửa sổ trình duyệt vừa được thu nhỏ. Hãy đóng trang đang mở.",
                )
                self._paused_until = time.monotonic() + BLOCK_PAUSE_SECONDS
                return True
        except Exception:
            self._failures += 1
            if self._failures >= 3:
                self._paused_until = time.monotonic() + ERROR_PAUSE_SECONDS
                self._failures = 0
                self.close()
        return False

    def close(self) -> None:
        if self._interpreter is not None:
            try:
                self._interpreter.close()
            except Exception:
                pass
            self._interpreter = None


def _foreground() -> tuple[int, str]:
    try:
        user32 = ctypes.windll.user32
        hwnd = int(user32.GetForegroundWindow())
        if not hwnd:
            return 0, ""
        pid = wt.DWORD()
        user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
        try:
            name = (psutil.Process(pid.value).name() or "").lower()
        except psutil.Error:
            name = ""
        return hwnd, name
    except (AttributeError, OSError, ValueError):
        return 0, ""


def _window_box(hwnd: int) -> tuple[int, int, int, int] | None:
    try:
        user32 = ctypes.windll.user32
        rect = wt.RECT()
        if not user32.GetWindowRect(hwnd, ctypes.byref(rect)):
            return None
        left = max(int(rect.left), user32.GetSystemMetrics(76))
        top = max(int(rect.top), user32.GetSystemMetrics(77))
        right = min(int(rect.right), user32.GetSystemMetrics(76) + user32.GetSystemMetrics(78))
        bottom = min(int(rect.bottom), user32.GetSystemMetrics(77) + user32.GetSystemMetrics(79))
        if right - left < 120 or bottom - top < 120:
            return None
        return left, top, right, bottom
    except (AttributeError, OSError):
        return None


def _minimize(hwnd: int) -> None:
    try:
        ctypes.windll.user32.ShowWindow(hwnd, 6)
    except (AttributeError, OSError):
        pass
