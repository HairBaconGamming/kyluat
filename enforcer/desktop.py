from __future__ import annotations

import ctypes
import json
import os
import re
import shutil
import sys
import tempfile
import winreg
from dataclasses import replace
from datetime import datetime, time
from pathlib import Path

import psutil
from PySide6.QtCore import QBuffer, QByteArray, QElapsedTimer, QIODevice, QRectF, QSize, Qt, QThread, QTime, QTimer
from PySide6.QtGui import QColor, QFont, QIcon, QKeySequence, QPainter, QPen, QPixmap
from PySide6.QtSvg import QSvgRenderer
from PySide6.QtWidgets import (
    QAbstractButton,
    QAbstractItemView,
    QAbstractSpinBox,
    QApplication,
    QButtonGroup,
    QDialog,
    QFrame,
    QGridLayout,
    QHBoxLayout,
    QHeaderView,
    QLabel,
    QLayout,
    QLineEdit,
    QMainWindow,
    QMessageBox,
    QPlainTextEdit,
    QProgressBar,
    QProgressDialog,
    QPushButton,
    QScrollArea,
    QSizePolicy,
    QSpinBox,
    QStackedWidget,
    QStyledItemDelegate,
    QTableWidget,
    QTableWidgetItem,
    QTimeEdit,
    QVBoxLayout,
    QWidget,
)

from . import catalog, net, pairing, phone_api, proc, rules, sessions
from .adult_list import ADULT_DOMAINS
from .cli import (
    CLI_HINT,
    CONFIG_PATH,
    DB_PATH,
    FROZEN,
    LOCK_PATH,
    ROOT,
    TASK_NAME,
    _create_task,
    _daemon_pid,
    _port_open,
    _restart_daemon,
    _start_daemon,
    _task_status,
    seed_initial_law,
)
from .config import Config, FocusWindow, Plan, Reminders, load_config, load_plan, load_reminders
from .config_writer import write_plan, write_reminders, write_rules
from .dashboard import build_summary
from .habits import WEEKDAY_VI
from .store import APPROVED_KEY, NET_ENABLED_KEY, PENDING_KEY, Store
from .visual import model_available

CUSTOM_APPS_KEY = "desktop_custom_apps"
CUSTOM_SITES_KEY = "desktop_custom_sites"

STYLE = """
QMainWindow, QScrollArea, QWidget#root { background: #f5f6f7; }
QDialog { background: #ffffff; }
QMessageBox { background: #ffffff; }
QWidget { color: #10151a; font-family: "Segoe UI Variable Text", "Segoe UI"; font-size: 14px; }
QWidget#rail { background: #ffffff; border-right: 1px solid #e8ebed; }

QLabel { background: transparent; }
QLabel#title { font-size: 23px; font-weight: 600; }
QLabel#section, QLabel#eyebrow { color: #8b949b; font-size: 11px; font-weight: 600; }
QLabel#muted { color: #8b949b; font-size: 12px; }
QLabel#body { color: #5c666f; font-size: 13px; }
QLabel#rowTitle { font-size: 14px; font-weight: 600; }
QLabel#rowSub { color: #8b949b; font-size: 12px; }
QLabel#cardLabel { color: #8b949b; font-size: 10.5px; font-weight: 600; }
QLabel#cardValue { font-size: 22px; font-weight: 600; }
QLabel#cardSub { color: #8b949b; font-size: 12px; }
QLabel#heroTitle { font-size: 20px; font-weight: 600; }

QFrame#card, QFrame#group { background: #ffffff; border: 1px solid #e8ebed; border-radius: 16px; }
QFrame#hairline { background: #f1f3f4; border: none; }
QFrame#softCard { background: #f7f8f9; border: 1px solid #e8ebed; border-radius: 14px; }
QFrame#warnCard { background: #fdf4e7; border: 1px solid #f0dfc2; border-radius: 14px; }
QFrame#dangerCard { background: #fceeed; border: 1px solid #f0d3cf; border-radius: 16px; }
QFrame#chipBar { background: #f7f8f9; border: 1px solid #f1f3f4; border-radius: 12px; }

QPushButton { background: #ffffff; border: 1px solid #e8ebed; border-radius: 10px; padding: 8px 14px; font-size: 13.5px; color: #3f474e; }
QPushButton:hover { border-color: #c8d0d5; background: #fbfcfc; }
QPushButton:pressed { background: #f2f4f5; }
QPushButton:disabled { color: #a0a5ab; border-color: #eef1f3; background: #fafbfb; }
QPushButton#primary { background: #2f6f5e; border: 1px solid #2f6f5e; color: #ffffff; font-weight: 600; }
QPushButton#primary:hover { background: #275d4e; border-color: #275d4e; }
QPushButton#primary:disabled { background: #d7dce0; border-color: #d7dce0; color: #ffffff; }
QPushButton#danger { color: #b42318; border: 1px solid #f0d3cf; background: #ffffff; font-weight: 600; }
QPushButton#danger:hover { background: #fceeed; border-color: #e9c3bd; }
QPushButton#danger:disabled { color: #c9a6a2; border-color: #f5e5e3; background: #fdf7f6; }
QPushButton#big { padding: 11px 18px; border-radius: 12px; font-size: 14.5px; }
QPushButton#nav { border: 1px solid transparent; background: transparent; text-align: left; padding: 9px 11px; border-radius: 9px; color: #5c666f; font-size: 13.5px; }
QPushButton#nav:hover { background: #f7f8f9; }
QPushButton#nav:checked { background: #edf3f0; border: 1px solid #d5e4dd; color: #2f6f5e; font-weight: 600; }
QPushButton#chip { background: transparent; border: 1px solid transparent; padding: 7px 15px; border-radius: 8px; font-size: 13.5px; color: #5c666f; }
QPushButton#chip:hover { background: #eef1f3; }
QPushButton#chip:checked { background: #ffffff; border: 1px solid #e8ebed; color: #10151a; font-weight: 600; }
QPushButton#link { background: transparent; border: none; color: #2f6f5e; font-weight: 600; padding: 4px 6px; }
QPushButton#link:hover { color: #275d4e; background: #edf3f0; border-radius: 8px; }

QProgressBar { background: #eef0f2; border: none; border-radius: 2px; height: 4px; }
QProgressBar::chunk { background: #2f6f5e; border-radius: 2px; }
QProgressBar#amber { background: #eef0f2; }
QProgressBar#amber::chunk { background: #96610a; }

QTableWidget { background: #ffffff; border: none; gridline-color: transparent; font-size: 13.5px; }
QTableWidget::item { padding: 9px 12px; border-bottom: 1px solid #f1f3f4; color: #5c666f; }
QTableWidget::item:selected { background: transparent; color: #5c666f; }
QHeaderView::section { background: #ffffff; color: #8b949b; border: none; border-bottom: 1px solid #e8ebed; padding: 10px 12px; font-size: 10.5px; font-weight: 600; }
QTableCornerButton::section { background: #ffffff; border: none; }

QLineEdit, QSpinBox, QTimeEdit { background: #ffffff; border: 1px solid #e8ebed; border-radius: 10px; padding: 8px 11px; font-size: 13.5px; color: #10151a; selection-background-color: #d5e4dd; selection-color: #10151a; }
QLineEdit:focus, QSpinBox:focus, QTimeEdit:focus { border-color: #a9c6ba; }
QPlainTextEdit { background: #ffffff; border: 1px solid #e8ebed; border-radius: 12px; padding: 10px 12px; font-size: 13.5px; color: #10151a; }

QScrollArea { border: none; }
QScrollBar:vertical { background: transparent; width: 10px; margin: 2px; }
QScrollBar::handle:vertical { background: #d5dade; border-radius: 5px; min-height: 28px; }
QScrollBar::handle:vertical:hover { background: #bcc3c8; }
QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical { height: 0; }
QScrollBar::add-page:vertical, QScrollBar::sub-page:vertical { background: transparent; }
QScrollBar:horizontal { height: 10px; margin: 2px; background: transparent; }
QScrollBar::handle:horizontal { background: #d5dade; border-radius: 5px; min-width: 28px; }
QScrollBar::add-line:horizontal, QScrollBar::sub-line:horizontal { width: 0; }

QToolTip { background: #10151a; color: #ffffff; border: none; padding: 6px 9px; border-radius: 8px; font-size: 12px; }
"""


PARAGRAPH = (
    "Tôi hiểu rằng tắt Kỷ luật là tự mình phá bỏ ranh giới đã đặt ra. "
    "Tôi đang chọn sự thoải mái trước mắt thay vì mục tiêu dài hạn, và tôi chấp nhận mất chuỗi ngày, "
    "mất quỹ mạng xã hội hôm nay, và mất lớp bảo vệ mà tôi đã dựng nên. Tôi vẫn muốn tiếp tục."
)

ICONS = {
    "home": '<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5"/>',
    "shield": '<path d="M12 3.5 19 6v5.5c0 4.4-3 7.4-7 9-4-1.6-7-4.6-7-9V6l7-2.5Z"/><path d="m9 12 2 2 4-4"/>',
    "gear": '<path d="M4 8h8.4M17.6 8H20M4 16h2.4M11.6 16H20"/><circle cx="15" cy="8" r="2.4"/><circle cx="9" cy="16" r="2.4"/>',
    "phone": '<rect x="7" y="2.5" width="10" height="19" rx="2"/><path d="M11 18.5h2"/>',
    "lock": '<rect x="6" y="10" width="12" height="10" rx="2"/><path d="M9 10V7a3 3 0 0 1 6 0v3"/>',
    "plus": '<path d="M12 5.5v13M5.5 12h13"/>',
    "close": '<path d="M6.5 6.5 17.5 17.5M17.5 6.5 6.5 17.5"/>',
    "check": '<path d="m5.5 12.5 4 4 9-9"/>',
    "clock": '<circle cx="12" cy="12" r="8.2"/><path d="M12 7.6V12l3.2 2"/>',
    "globe": '<circle cx="12" cy="12" r="8"/><path d="M4.4 9.6h15.2M4.4 14.4h15.2M12 4a15 15 0 0 1 0 16M12 4a15 15 0 0 0 0 16"/>',
    "desktop": '<rect x="3.5" y="4.5" width="17" height="11" rx="1.8"/><path d="M8.5 19.5h7M12 15.5v4"/>',
    "bell": '<path d="M8.4 17.2h7.2M9.6 10.2a2.9 2.9 0 0 1 5.8 0c0 3.4 1.3 4.3 1.3 4.3H8.3s1.3-.9 1.3-4.3Z"/>',
    "file": '<path d="M6.5 3.8h7l4.5 4.5v11.9h-11.5Z"/><path d="M9.5 12h5M9.5 15.2h5"/>',
    "database": '<ellipse cx="12" cy="6.4" rx="7" ry="2.6"/><path d="M5 6.4v11.2c0 1.4 3.1 2.6 7 2.6s7-1.2 7-2.6V6.4"/><path d="M5 12c0 1.4 3.1 2.6 7 2.6s7-1.2 7-2.6"/>',
    "info": '<circle cx="12" cy="12" r="8.2"/><path d="M12 8v4.6M12 15.6v.1"/>',
    "alert": '<path d="M12 4.6 20.2 19H3.8Z"/><path d="M12 10.2v3.4M12 16.2v.1"/>',
    "power": '<path d="M12 4.4v6.4"/><path d="M7.6 7a6.6 6.6 0 1 0 8.8 0"/>',
    "search": '<circle cx="11" cy="11" r="6.2"/><path d="m15.6 15.6 4 4"/>',
    "eye": '<path d="M2.8 12s3.4-5.8 9.2-5.8S21.2 12 21.2 12s-3.4 5.8-9.2 5.8S2.8 12 2.8 12Z"/><circle cx="12" cy="12" r="2.6"/>',
    "chart": '<path d="M4.5 19.5h15"/><path d="M7.5 16V10M12 16V5.5M16.5 16v-4"/>',
    "refresh": '<path d="M19.5 12a7.5 7.5 0 1 1-2.2-5.3"/><path d="M19.6 4.4v3.6h-3.6"/>',
    "target": '<circle cx="12" cy="12" r="7.4"/><circle cx="12" cy="12" r="3"/><path d="M12 4.6V2.4M19.6 12h2.2"/>',
    "dumbbell": '<path d="M5 9.5h14M5 14.5h14M3.2 7v10M20.8 7v10"/>',
    "flame": '<path d="M9 4.4c1.6 3.2-.8 4.4.6 6.6 1.2 1.8 3.6 1 3.6-1.6 2 1.4 2.6 3.4 1.6 5.2-1.2 2.2-3.8 3-6 2.2-3-1.1-4-4.4-2.4-7.2C7.4 8 9.2 6.2 9 4.4Z"/>',
    "calendar": '<rect x="4" y="5.5" width="16" height="14.5" rx="2"/><path d="M4 10h16M9 5.5V3M15 5.5V3"/>',
    "chevron": '<path d="m9.5 6 6 6-6 6"/>',
    "steps": '<path d="M5 12.5 9 16.5 19 6.5"/>',
}


def svg_pixmap(name: str, color: str = "#5c666f", size: int = 18) -> QPixmap:
    svg = (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" '
        f'stroke="{color}" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">'
        f"{ICONS.get(name, '')}</svg>"
    )
    renderer = QSvgRenderer(QByteArray(svg.encode("utf-8")))
    pixmap = QPixmap(size, size)
    pixmap.fill(Qt.transparent)
    painter = QPainter(pixmap)
    renderer.render(painter)
    painter.end()
    return pixmap


def svg_icon(name: str, color: str = "#5c666f", size: int = 18) -> QIcon:
    return QIcon(svg_pixmap(name, color, size))


def make_icon() -> QIcon:
    pixmap = QPixmap(64, 64)
    pixmap.fill(Qt.transparent)
    painter = QPainter(pixmap)
    painter.setRenderHint(QPainter.Antialiasing)
    pen = QPen(QColor("#2f6f5e"))
    pen.setWidth(8)
    painter.setPen(pen)
    painter.setBrush(Qt.NoBrush)
    painter.drawEllipse(10, 10, 44, 44)
    painter.setPen(Qt.NoPen)
    painter.setBrush(QColor("#2f6f5e"))
    painter.drawEllipse(28, 28, 8, 8)
    painter.end()
    return QIcon(pixmap)


def write_ico(path: Path) -> Path:
    pixmap = QPixmap(256, 256)
    pixmap.fill(QColor("#1f2a28"))
    painter = QPainter(pixmap)
    painter.setRenderHint(QPainter.Antialiasing)
    pen = QPen(QColor("#f5f6f7"))
    pen.setWidth(19)
    painter.setPen(pen)
    painter.setBrush(Qt.NoBrush)
    painter.drawEllipse(56, 56, 144, 144)
    painter.setPen(Qt.NoPen)
    painter.setBrush(QColor("#f5f6f7"))
    painter.drawEllipse(112, 112, 32, 32)
    painter.end()
    buffer = QBuffer()
    buffer.open(QIODevice.WriteOnly)
    pixmap.save(buffer, "PNG")
    data = bytes(buffer.data())
    header = b"\x00\x00\x01\x00\x01\x00"
    entry = bytes([0, 0, 0, 0]) + (1).to_bytes(2, "little") + (32).to_bytes(2, "little")
    entry += len(data).to_bytes(4, "little") + (22).to_bytes(4, "little")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(header + entry + data)
    return path


def desktop_directory() -> Path:
    try:
        buffer = ctypes.create_unicode_buffer(260)
        if ctypes.windll.shell32.SHGetFolderPathW(None, 0x0010, None, 0, buffer) == 0 and buffer.value:
            return Path(buffer.value)
    except (AttributeError, OSError):
        pass
    return Path.home() / "Desktop"


def create_shortcut(icon_path: Path, desktop_dir: Path | None = None) -> Path:
    pythonw = Path(sys.executable).with_name("pythonw.exe")
    if not pythonw.exists():
        pythonw = Path(sys.executable)
    folder = desktop_dir if desktop_dir is not None else desktop_directory()
    folder.mkdir(parents=True, exist_ok=True)
    final = folder / "Kỷ luật.lnk"
    temp = Path(tempfile.gettempdir()) / f"cainghien-{os.getpid()}.lnk"
    temp.unlink(missing_ok=True)
    env = dict(
        os.environ,
        CAINGHIEN_LNK=str(temp),
        CAINGHIEN_TARGET=str(pythonw),
        CAINGHIEN_ARGS=f'"{ROOT / "run_desktop.pyw"}"',
        CAINGHIEN_WORKDIR=str(ROOT),
        CAINGHIEN_ICON=str(icon_path),
    )
    script = (
        "$ws = New-Object -ComObject WScript.Shell\n"
        "$s = $ws.CreateShortcut($env:CAINGHIEN_LNK)\n"
        "$s.TargetPath = $env:CAINGHIEN_TARGET\n"
        "$s.Arguments = $env:CAINGHIEN_ARGS\n"
        "$s.WorkingDirectory = $env:CAINGHIEN_WORKDIR\n"
        "$s.IconLocation = $env:CAINGHIEN_ICON\n"
        "$s.Save()\n"
    )
    result = proc.run(
        ["powershell", "-NoProfile", "-NonInteractive", "-Command", script],
        capture_output=True,
        env=env,
    )
    if result.returncode != 0 or not temp.exists():
        message = (result.stderr or result.stdout).decode("utf-8", "replace").strip()
        raise RuntimeError(message or "tạo shortcut thất bại")
    final.unlink(missing_ok=True)
    shutil.move(str(temp), str(final))
    return final


class Switch(QAbstractButton):
    ON = "#2f6f5e"
    OFF = "#d7dce0"

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setCheckable(True)
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        self.setFixedSize(40, 22)

    def sizeHint(self) -> QSize:
        return QSize(40, 22)

    def paintEvent(self, event) -> None:
        painter = QPainter(self)
        painter.setRenderHint(QPainter.Antialiasing)
        painter.setPen(Qt.PenStyle.NoPen)
        painter.setBrush(QColor(self.ON if self.isChecked() else self.OFF))
        painter.drawRoundedRect(QRectF(0, 0, 40, 22), 11, 11)
        knob = 17.0
        inset = 2.5
        x = 40 - knob - inset if self.isChecked() else inset
        painter.setBrush(QColor("#ffffff"))
        painter.drawEllipse(QRectF(x, inset, knob, knob))
        painter.end()


class Ring(QWidget):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setFixedSize(128, 128)
        self.value = 0
        self.goal = 1
        self.unit = "′"
        self.color = "#2f6f5e"

    def set_values(self, value: int, goal: int) -> None:
        self.value = value
        self.goal = max(1, goal)
        self.update()

    def paintEvent(self, event) -> None:
        painter = QPainter(self)
        painter.setRenderHint(QPainter.Antialiasing)
        rect = QRectF(11, 11, 106, 106)
        painter.setPen(QPen(QColor("#eef0f2"), 9))
        painter.drawArc(rect, 0, 360 * 16)
        ratio = min(1.0, self.value / self.goal)
        pen = QPen(QColor(self.color), 9)
        pen.setCapStyle(Qt.PenCapStyle.RoundCap)
        painter.setPen(pen)
        painter.drawArc(rect, 90 * 16, -int(360 * 16 * ratio))
        painter.setPen(QColor("#10151a"))
        bold = QFont("Segoe UI", 16)
        bold.setBold(True)
        painter.setFont(bold)
        painter.drawText(self.rect().adjusted(0, -9, 0, 0), Qt.AlignmentFlag.AlignCenter, f"{self.value}{self.unit}")
        painter.setPen(QColor("#8b949b"))
        small = QFont("Segoe UI", 8)
        small.setBold(True)
        painter.setFont(small)
        painter.drawText(self.rect().adjusted(0, 30, 0, 0), Qt.AlignmentFlag.AlignCenter, f"/ {self.goal}")
        painter.end()


class StudyBarDelegate(QStyledItemDelegate):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.max_value = 1

    def paint(self, painter, option, index) -> None:
        value = int(index.data(Qt.ItemDataRole.UserRole) or 0)
        painter.save()
        painter.fillRect(option.rect, QColor("#ffffff"))
        area = option.rect.adjusted(9, 0, -44, 0)
        track = QRectF(area.left(), option.rect.center().y() - 2, area.width(), 4)
        painter.setPen(Qt.PenStyle.NoPen)
        painter.setBrush(QColor("#eef0f2"))
        painter.drawRoundedRect(track, 2, 2)
        ratio = min(1.0, value / max(1, self.max_value))
        if ratio > 0:
            painter.setBrush(QColor("#2f6f5e"))
            painter.drawRoundedRect(QRectF(track.left(), track.top(), track.width() * ratio, 4), 2, 2)
        painter.setPen(QColor("#10151a"))
        font = painter.font()
        font.setPointSizeF(9.5)
        font.setBold(True)
        painter.setFont(font)
        painter.drawText(
            option.rect.adjusted(option.rect.width() - 42, 0, -12, 0),
            Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter,
            f"{value}′",
        )
        painter.restore()


INK = "#10151a"
INK_2 = "#5c666f"
INK_3 = "#8b949b"
LINE = "#e8ebed"
LINE_SOFT = "#f1f3f4"
SURFACE = "#ffffff"
SURFACE_2 = "#f7f8f9"
ACCENT = "#2f6f5e"
ACCENT_2 = "#275d4e"
ACCENT_SOFT = "#edf3f0"
ACCENT_LINE = "#d5e4dd"
WARN = "#96610a"
WARN_SOFT = "#fdf4e7"
WARN_LINE = "#f0dfc2"
DANGER = "#b42318"
DANGER_SOFT = "#fceeed"
DANGER_LINE = "#f0d3cf"

PILL_TONES = {
    "accent": (ACCENT_SOFT, ACCENT_LINE, ACCENT),
    "neutral": ("#f1f3f4", "#e4e7e9", "#59636b"),
    "warn": (WARN_SOFT, WARN_LINE, WARN),
    "danger": (DANGER_SOFT, DANGER_LINE, DANGER),
}
TILE_TONES = {
    "neutral": ("#f2f4f5", "#e6e9eb", INK_2),
    "accent": (ACCENT_SOFT, ACCENT_LINE, ACCENT),
    "warn": (WARN_SOFT, WARN_LINE, WARN),
    "danger": (SURFACE, DANGER_LINE, DANGER),
}
NOTE_TONES = {
    "neutral": (SURFACE_2, "#e6e9eb", INK_2),
    "accent": (ACCENT_SOFT, ACCENT_LINE, ACCENT),
    "warn": (WARN_SOFT, WARN_LINE, WARN),
    "danger": (DANGER_SOFT, DANGER_LINE, DANGER),
}


def _frame_layout(parent=None):
    frame = QFrame(parent)
    frame.setObjectName("card")
    layout = QVBoxLayout(frame)
    layout.setContentsMargins(16, 15, 16, 15)
    layout.setSpacing(9)
    return frame, layout


def _label(text: str, name: str = "muted") -> QLabel:
    label = QLabel(text)
    label.setObjectName(name)
    label.setWordWrap(True)
    return label


def _section(text: str) -> QLabel:
    label = _label(text.upper(), "section")
    label.setContentsMargins(2, 6, 0, 0)
    return label


def _button(text: str, primary: bool = False, danger: bool = False, big: bool = False) -> QPushButton:
    button = QPushButton(text)
    button.setCursor(Qt.CursorShape.PointingHandCursor)
    if primary:
        button.setObjectName("primary")
    elif danger:
        button.setObjectName("danger")
    if big:
        button.setStyleSheet("padding:11px 18px;border-radius:12px;font-size:14.5px;")
    return button


def _field(widget, width: int = 0):
    widget.setButtonSymbols(QAbstractSpinBox.ButtonSymbols.NoButtons)
    widget.setAlignment(Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter)
    if width:
        widget.setFixedWidth(width)
    # Qt bo hẹp ô nhập bên trong khi spin box bỏ nút và dính QSS có padding.
    widget.lineEdit().setMinimumWidth(max(40, width - 24) if width else 40)
    return widget


def _tile(text: str = "", icon: str = "", tone: str = "neutral", size: int = 34) -> QLabel:
    background, border, foreground = TILE_TONES[tone]
    label = QLabel()
    label.setAlignment(Qt.AlignmentFlag.AlignCenter)
    label.setFixedSize(size, size)
    label.setStyleSheet(
        f"background:{background};border:1px solid {border};border-radius:{size // 3}px;"
        f"color:{foreground};font-weight:600;font-size:12px;"
    )
    if icon:
        label.setPixmap(svg_pixmap(icon, foreground, 17))
    else:
        label.setText(text[:1].upper())
    return label


def _hairline() -> QFrame:
    line = QFrame()
    line.setObjectName("hairline")
    line.setFixedHeight(1)
    return line


class Group(QFrame):
    """Thẻ danh sách: tự chèn kẻ mảnh giữa các hàng, giống nhóm hàng trên app điện thoại."""

    def __init__(self, parent=None, margins=(14, 1, 14, 1)):
        super().__init__(parent)
        self.setObjectName("group")
        self.box = QVBoxLayout(self)
        self.box.setContentsMargins(*margins)
        self.box.setSpacing(0)
        self.rows = 0

    def add(self, widget):
        if self.rows:
            self.box.addWidget(_hairline())
        self.box.addWidget(widget)
        self.rows += 1
        return widget

    def reset(self) -> None:
        while self.box.count():
            item = self.box.takeAt(0)
            child = item.widget()
            if child is not None:
                child.setParent(None)
                child.deleteLater()
        self.rows = 0


class Row(QWidget):
    def __init__(self, title: str = "", subtitle: str = "", tile: QLabel | None = None,
                 handler=None, parent=None):
        super().__init__(parent)
        self.handler = handler
        self.box = QHBoxLayout(self)
        self.box.setContentsMargins(0, 11, 0, 11)
        self.box.setSpacing(11)
        if tile is not None:
            self.box.addWidget(tile)
        texts = QVBoxLayout()
        texts.setContentsMargins(0, 0, 0, 0)
        texts.setSpacing(1)
        self.title = QLabel(title)
        self.title.setObjectName("rowTitle")
        self.subtitle = _label(subtitle, "rowSub")
        self.subtitle.setVisible(bool(subtitle))
        texts.addWidget(self.title)
        texts.addWidget(self.subtitle)
        self.box.addLayout(texts, 1)

    def mousePressEvent(self, event) -> None:
        if self.handler is not None and event.button() == Qt.MouseButton.LeftButton:
            self.handler()
        event.accept()

    def set_text(self, title: str, subtitle: str = "") -> None:
        self.title.setText(title)
        self.subtitle.setText(subtitle)
        self.subtitle.setVisible(bool(subtitle))

    def add(self, widget, stretch: int = 0):
        self.box.addWidget(widget, stretch)
        return widget


def _link_row(title: str, subtitle: str, handler, icon: str = "plus") -> Row:
    row = Row(title, subtitle, _tile(icon=icon, tone="accent"), handler=handler)
    row.title.setStyleSheet(f"background:transparent;color:{ACCENT};font-size:14px;font-weight:600;")
    glyph = QLabel()
    glyph.setPixmap(svg_pixmap("chevron", "#c2c8cc", 16))
    row.add(glyph)
    return row


class Pill(QFrame):
    def __init__(self, text: str = "—", tone: str = "accent", dot: bool = True, parent=None):
        super().__init__(parent)
        self.dot = QLabel()
        self.dot.setFixedSize(6, 6)
        self.text = QLabel(text)
        box = QHBoxLayout(self)
        box.setContentsMargins(11, 5, 12, 5)
        box.setSpacing(6)
        if dot:
            box.addWidget(self.dot)
            self.dot.setStyleSheet("border-radius:3px;")
        box.addWidget(self.text)
        self.set_state(text, tone)

    def set_state(self, text: str, tone: str = "accent") -> None:
        background, border, foreground = PILL_TONES[tone]
        self.setStyleSheet(f"QFrame{{background:{background};border:1px solid {border};border-radius:12px;}}")
        self.text.setStyleSheet(
            f"background:transparent;color:{foreground};font-size:11.5px;font-weight:600;border:none;"
        )
        self.dot.setStyleSheet(f"background:{foreground};border-radius:3px;border:none;")
        self.text.setText(text)


def _note(text: str, icon: str = "info", tone: str = "neutral") -> QFrame:
    background, border, foreground = NOTE_TONES[tone]
    frame = QFrame()
    frame.setStyleSheet(f"background:{background};border:1px solid {border};border-radius:14px;")
    box = QHBoxLayout(frame)
    box.setContentsMargins(13, 12, 14, 12)
    box.setSpacing(10)
    glyph = QLabel()
    glyph.setPixmap(svg_pixmap(icon, foreground, 17))
    body = _label(text, "body")
    box.addWidget(glyph, 0, Qt.AlignmentFlag.AlignTop)
    box.addWidget(body, 1)
    frame.body = body
    return frame


def _chips(values, selected, on_select) -> QFrame:
    bar = QFrame()
    bar.setObjectName("chipBar")
    box = QHBoxLayout(bar)
    box.setContentsMargins(3, 3, 3, 3)
    box.setSpacing(3)
    group = QButtonGroup(bar)
    group.setExclusive(True)
    for value in values:
        chip = QPushButton(f"{value} phút")
        chip.setObjectName("chip")
        chip.setCheckable(True)
        chip.setChecked(value == selected)
        chip.setCursor(Qt.CursorShape.PointingHandCursor)
        chip.clicked.connect(lambda _=False, v=value: on_select(v))
        group.addButton(chip)
        box.addWidget(chip)
    box.addStretch(1)
    bar.group = group
    return bar


def _stat(caption: str, value: str = "—", sub: str = "", tone: str = "accent"):
    card, box = _frame_layout()
    box.addWidget(_label(caption.upper(), "cardLabel"))
    number = QLabel(value)
    number.setObjectName("cardValue")
    box.addWidget(number)
    detail = _label(sub, "cardSub")
    box.addWidget(detail)
    bar = QProgressBar()
    bar.setRange(0, 100)
    bar.setValue(0)
    bar.setTextVisible(False)
    bar.setFixedHeight(4)
    if tone != "accent":
        bar.setObjectName(tone)
    box.addWidget(bar)
    return card, number, detail, bar


class MainWindow(QMainWindow):
    def __init__(self) -> None:
        super().__init__()
        self.setWindowTitle("Kỷ luật")
        self.resize(1180, 840)
        self.setMinimumSize(1000, 720)
        self.setWindowIcon(make_icon())
        self.selected_minutes = 45
        self._build()
        self.timer = QTimer(self)
        self.timer.timeout.connect(self.refresh)
        self.timer.start(15000)
        self.refresh()
        self.refresh_protect()
        self.refresh_settings()

    def _build(self) -> None:
        root = QWidget()
        root.setObjectName("root")
        self.setCentralWidget(root)
        layout = QHBoxLayout(root)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)

        rail = QWidget()
        rail.setObjectName("rail")
        rail.setFixedWidth(212)
        rail_layout = QVBoxLayout(rail)
        rail_layout.setContentsMargins(13, 17, 13, 14)
        rail_layout.setSpacing(3)

        brand = QLabel("Kỷ luật")
        brand.setStyleSheet(f"background:transparent;font-size:16px;font-weight:600;padding:2px 9px 0;")
        rail_layout.addWidget(brand)
        sub = QLabel("Bản Windows")
        sub.setStyleSheet(f"background:transparent;color:{INK_3};font-size:11px;padding:1px 9px 15px;")
        rail_layout.addWidget(sub)

        self.nav_group = QButtonGroup(self)
        self.nav_group.setExclusive(True)
        self.nav_icons = ("home", "shield", "gear", "lock")
        for index, text in enumerate(("Hôm nay", "Bảo vệ", "Cài đặt", "Nâng cao")):
            button = QPushButton(text)
            button.setObjectName("nav")
            button.setCheckable(True)
            button.setIcon(svg_icon(self.nav_icons[index], ACCENT if index == 0 else INK_2))
            button.setIconSize(QSize(17, 17))
            button.clicked.connect(lambda _=False, i=index: self.go(i))
            self.nav_group.addButton(button, index)
            rail_layout.addWidget(button)
        rail_layout.addStretch(1)

        status = QFrame()
        status.setObjectName("softCard")
        status_layout = QVBoxLayout(status)
        status_layout.setContentsMargins(12, 11, 12, 12)
        status_layout.setSpacing(6)
        caption = QLabel("TRẠNG THÁI")
        caption.setStyleSheet(f"background:transparent;color:{INK_3};font-size:10.5px;font-weight:600;")
        self.rail_title = QLabel("Đang kiểm tra…")
        self.rail_title.setObjectName("rowTitle")
        self.rail_info = _label("—", "rowSub")
        self.rail_pill = Pill("—", "neutral", dot=False)
        self.rail_pill.setFixedWidth(96)
        status_layout.addWidget(caption)
        status_layout.addWidget(self.rail_title)
        status_layout.addWidget(self.rail_info)
        status_layout.addWidget(self.rail_pill, alignment=Qt.AlignmentFlag.AlignLeft)
        rail_layout.addWidget(status)

        layout.addWidget(rail)

        self.stack = QStackedWidget()
        self.stack.addWidget(self._build_today())
        self.stack.addWidget(self._build_protect())
        self.stack.addWidget(self._build_settings())
        self.stack.addWidget(self._build_advanced())
        layout.addWidget(self.stack, 1)
        self.nav_group.button(0).setChecked(True)
        self.stack.setCurrentIndex(0)

    def go(self, index: int) -> None:
        self.stack.setCurrentIndex(index)
        self.nav_group.button(index).setChecked(True)
        for position, icon_name in enumerate(self.nav_icons):
            tone = ACCENT if position == index else INK_2
            self.nav_group.button(position).setIcon(svg_icon(icon_name, tone))
        if index == 1:
            self.refresh_protect()
        elif index >= 2:
            self.refresh_settings()
        else:
            self.refresh()

    def _header(self, title: str, eyebrow: str = ""):
        wrapper = QWidget()
        box = QHBoxLayout(wrapper)
        box.setContentsMargins(0, 0, 0, 0)
        box.setSpacing(14)
        texts = QVBoxLayout()
        texts.setContentsMargins(0, 0, 0, 0)
        texts.setSpacing(2)
        eyebrow_label = _label(eyebrow, "eyebrow")
        eyebrow_label.setWordWrap(False)
        eyebrow_label.setVisible(bool(eyebrow))
        heading = QLabel(title)
        heading.setObjectName("title")
        texts.addWidget(eyebrow_label)
        texts.addWidget(heading)
        box.addLayout(texts)
        box.addStretch(1)
        return wrapper, box, eyebrow_label, heading


    def _build_today(self) -> QWidget:
        page = QWidget()
        outer = QVBoxLayout(page)
        outer.setContentsMargins(0, 0, 0, 0)
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        content = QWidget()
        layout = QVBoxLayout(content)
        layout.setContentsMargins(24, 20, 24, 22)
        layout.setSpacing(12)

        header, box, self.today_clock, _ = self._header("Hôm nay", "Đang tải…")
        self.pill = Pill("—", "neutral")
        box.addWidget(self.pill, alignment=Qt.AlignmentFlag.AlignVCenter)
        layout.addWidget(header)

        columns = QHBoxLayout()
        columns.setSpacing(12)
        columns.setAlignment(Qt.AlignmentFlag.AlignTop)

        hero, hero_layout = _frame_layout()
        hero_layout.setContentsMargins(20, 18, 20, 18)
        hero_layout.setSpacing(5)
        hero_layout.addWidget(_label("TẬP TRUNG", "cardLabel"))
        self.hero_title = QLabel(f"{self.selected_minutes} phút không xao nhãng")
        self.hero_title.setObjectName("heroTitle")
        hero_layout.addWidget(self.hero_title)
        self.hero_sub = _label("Trong phiên, các ứng dụng và web bạn chọn sẽ bị khóa.", "body")
        hero_layout.addWidget(self.hero_sub)
        hero_layout.addSpacing(8)
        hero_layout.addWidget(_chips((25, 45, 60, 90), self.selected_minutes, self._select_minutes))
        hero_layout.addSpacing(4)
        actions = QHBoxLayout()
        actions.setSpacing(9)
        self.start_button = _button(f"Bắt đầu {self.selected_minutes} phút", primary=True, big=True)
        self.start_button.clicked.connect(self.start_session)
        self.stop_button = _button("Dừng phiên", big=True)
        self.stop_button.clicked.connect(self.stop_session)
        self.cancel_button = _button("Hủy dừng", big=True)
        self.cancel_button.clicked.connect(self.cancel_stop)
        actions.addWidget(self.start_button)
        actions.addWidget(self.stop_button)
        actions.addWidget(self.cancel_button)
        actions.addStretch(1)
        hero_layout.addLayout(actions)
        self.hero_note = _label(
            "Dừng sớm sẽ phải chờ 10 phút — để bạn không tự phá cam kết lúc mất kiên nhẫn.", "rowSub"
        )
        hero_layout.addSpacing(3)
        hero_layout.addWidget(self.hero_note)
        hero_layout.addStretch(1)

        foot = QHBoxLayout()
        foot.setSpacing(16)
        self.ring = Ring()
        foot.addWidget(self.ring, alignment=Qt.AlignmentFlag.AlignVCenter)
        ring_box = QVBoxLayout()
        ring_box.setSpacing(3)
        self.ring_label = _label("Học hôm nay", "rowTitle")
        self.ring_detail = _label("", "rowSub")
        ring_box.addWidget(self.ring_label)
        ring_box.addWidget(self.ring_detail)
        ring_box.addStretch(1)
        foot.addLayout(ring_box, 1)
        hero_layout.addLayout(foot)
        columns.addWidget(hero, 5, Qt.AlignmentFlag.AlignTop)

        side = QVBoxLayout()
        side.setSpacing(12)

        study_card, self.study_value, self.study_sub, self.study_bar = _stat("Học hôm nay")
        study_actions = QHBoxLayout()
        study_actions.setSpacing(8)
        study_actions.addWidget(self._action("+30 phút", lambda: self.checkin("study", 30), True))
        study_actions.addWidget(self._action("+60 phút", lambda: self.checkin("study", 60), False))
        study_actions.addStretch(1)
        study_card.layout().addLayout(study_actions)
        side.addWidget(study_card)

        gym_card, self.gym_value, self.gym_sub, self.gym_bar = _stat("Gym tuần này")
        gym_actions = QHBoxLayout()
        gym_actions.setSpacing(8)
        gym_actions.addWidget(self._action("+1 buổi gym", lambda: self.checkin("gym", 0), True))
        gym_actions.addStretch(1)
        gym_card.layout().addLayout(gym_actions)
        side.addWidget(gym_card)

        block_card, self.block_value, self.block_sub, self.block_bar = _stat("Bị chặn hôm nay", tone="amber")
        self.block_bar.setMaximum(1)
        self.block_bar.setValue(0)
        block_card.layout().addStretch(1)
        side.addWidget(block_card)

        columns.addLayout(side, 3)
        layout.addLayout(columns)

        layout.addWidget(_section("7 ngày qua"))
        table_card = QFrame()
        table_card.setObjectName("card")
        table_layout = QVBoxLayout(table_card)
        table_layout.setContentsMargins(1, 1, 1, 1)
        table_layout.setSpacing(0)
        self.table = QTableWidget(0, 5)
        self.table.setHorizontalHeaderLabels(["Ngày", "Học", "Gym", "Bị chặn", "Trạng thái"])
        self.table.verticalHeader().setVisible(False)
        self.table.verticalHeader().setDefaultSectionSize(36)
        self.table.setShowGrid(False)
        self.table.setSelectionMode(QAbstractItemView.SelectionMode.NoSelection)
        self.table.setEditTriggers(QAbstractItemView.EditTrigger.NoEditTriggers)
        self.table.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.table.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        header_view = self.table.horizontalHeader()
        header_view.setDefaultAlignment(Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter)
        header_view.setHighlightSections(False)
        for column, mode in (
            (0, QHeaderView.ResizeMode.ResizeToContents),
            (1, QHeaderView.ResizeMode.Stretch),
            (2, QHeaderView.ResizeMode.ResizeToContents),
            (3, QHeaderView.ResizeMode.ResizeToContents),
            (4, QHeaderView.ResizeMode.ResizeToContents),
        ):
            header_view.setSectionResizeMode(column, mode)
        self.table.setColumnWidth(1, 200)
        self.study_delegate = StudyBarDelegate(self.table)
        self.table.setItemDelegateForColumn(1, self.study_delegate)
        self.table.setFixedHeight(290)
        table_layout.addWidget(self.table)
        layout.addWidget(table_card)

        self.onboard_banner = QFrame()
        self.onboard_banner.setObjectName("card")
        onboard_layout = QHBoxLayout(self.onboard_banner)
        onboard_layout.setContentsMargins(15, 11, 12, 11)
        onboard_layout.setSpacing(10)
        onboard_layout.addWidget(
            _label("Lần đầu dùng? Chọn ứng dụng cần chặn và mục tiêu trong 2 phút.", "body"), 1
        )
        onboard_button = _button("Thiết lập nhanh", primary=True)
        onboard_button.clicked.connect(self.quick_setup)
        onboard_layout.addWidget(onboard_button)
        layout.addWidget(self.onboard_banner)
        layout.addStretch(1)
        scroll.setWidget(content)
        outer.addWidget(scroll, 1)
        return page

    def _action(self, text: str, action, primary: bool) -> QPushButton:
        button = _button(text, primary=primary)
        button.clicked.connect(action)
        return button


    def _build_protect(self) -> QWidget:
        self.app_switches: dict[str, Switch] = {}
        self.web_switches: dict[str, Switch] = {}
        self.protect_apps: list[dict] = []
        self.protect_sites: list[dict] = []
        self.protect_signature: tuple[str, ...] = ()
        page = QWidget()
        outer = QVBoxLayout(page)
        outer.setContentsMargins(24, 20, 24, 22)
        outer.setSpacing(12)
        header, box, _, _ = self._header("Bảo vệ", "Luật chặn trên máy tính")
        self.protect_pill = Pill("—", "neutral", dot=False)
        box.addWidget(self.protect_pill, alignment=Qt.AlignmentFlag.AlignVCenter)
        outer.addWidget(header)

        self.protect_note = _note("", "alert", "warn")
        self.protect_note.hide()
        outer.addWidget(self.protect_note)

        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        content = QWidget()
        columns = QHBoxLayout(content)
        columns.setContentsMargins(0, 0, 8, 0)
        columns.setSpacing(14)

        left = QVBoxLayout()
        left.setSpacing(9)
        left.addWidget(_section("Nội dung 18+"))
        adult_group = Group()
        self.adult_switch = Switch()
        self.adult_switch.setAccessibleName("Chặn nội dung 18+")
        self.adult_switch.toggled.connect(self.toggle_adult)
        adult_row = Row("Chặn trang người lớn", "Đang kiểm tra…", _tile(icon="alert", tone="accent"))
        adult_row.add(self.adult_switch)
        self.adult_status = adult_row.subtitle
        adult_group.add(adult_row)

        self.visual_switch = Switch()
        self.visual_switch.setAccessibleName("Chặn ảnh 18+ trên trình duyệt")
        self.visual_switch.toggled.connect(self.toggle_visual)
        self.visual_row = Row(
            "Chặn ảnh 18+ trên trình duyệt",
            "Đang kiểm tra…",
            _tile(icon="eye", tone="accent"),
        )
        self.visual_row.add(self.visual_switch)
        self.visual_status = self.visual_row.subtitle
        adult_group.add(self.visual_row)

        self.web_pill = Pill("Cần bật", "neutral", dot=False)
        self.web_row = Row(
            "Bật chặn web",
            "Cần Administrator · để luật web và 18+ có tác dụng",
            _tile(icon="globe", tone="accent"),
            handler=self._offer_web_blocking,
        )
        self.web_row.add(self.web_pill)
        self.web_row.setCursor(Qt.CursorShape.PointingHandCursor)
        adult_group.add(self.web_row)
        adult_group.add(
            _link_row(
                "Kiểm tra chặn thử",
                "Hỏi bộ lọc bằng một tên miền mẫu",
                self.test_adult_filter,
                icon="search",
            )
        )
        left.addWidget(adult_group)
        left.addWidget(
            _note(
                "Luật này chạy cả ngày. Trang bạn tự thêm ở mục Trang web chỉ bị chặn trong giờ tập trung.",
                "info",
            )
        )
        left.addWidget(
            _note(
                "Nhận diện ảnh chỉ đọc cửa sổ trình duyệt đang mở, ảnh không lưu và không gửi đi. "
                "Có thể nhận nhầm ảnh bơi lội hoặc em bé; tắt công tắc là dừng ngay.",
                "info",
            )
        )
        left.addWidget(_section("Ứng dụng"))
        self.apps_group = Group()
        left.addWidget(self.apps_group)
        left.addWidget(_section("Trang web"))
        self.sites_group = Group()
        left.addWidget(self.sites_group)
        left.addStretch(1)
        columns.addLayout(left, 1)

        right = QVBoxLayout()
        right.setSpacing(9)
        right.addWidget(_section("Lịch tập trung"))
        self.schedule_group = Group()
        right.addWidget(self.schedule_group)
        right.addWidget(
            _note(
                "Nới lỏng luật phải chờ 24 giờ; siết chặt áp dụng ngay. Riêng công tắc 18+ chờ 60 giây.",
                "clock",
                "warn",
            )
        )
        right.addWidget(
            _note(
                "Kỷ luật không tự đoán app mạng xã hội hay game. Bạn chọn cái nào, chặn đúng cái đó.",
                "info",
            )
        )
        right.addStretch(1)
        columns.addLayout(right, 1)

        scroll.setWidget(content)
        outer.addWidget(scroll, 1)
        return page

    def _build_settings(self) -> QWidget:
        page = QWidget()
        outer = QVBoxLayout(page)
        outer.setContentsMargins(24, 20, 24, 22)
        outer.setSpacing(12)
        header, _, _, _ = self._header("Cài đặt", "Mục tiêu, nhắc nhở và điện thoại")
        outer.addWidget(header)

        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        content = QWidget()
        band = QHBoxLayout(content)
        band.setContentsMargins(0, 0, 8, 0)
        band.setSpacing(0)
        band.addStretch(1)
        holder = QWidget()
        column = QVBoxLayout(holder)
        column.setContentsMargins(0, 0, 0, 0)
        column.setSpacing(9)
        holder.setMinimumWidth(520)
        holder.setMaximumWidth(680)
        band.addWidget(holder)
        band.addStretch(1)
        scroll.setWidget(content)
        outer.addWidget(scroll, 1)

        column.addWidget(_section("Mục tiêu"))
        plan_group = Group()
        self.goal_study = QSpinBox()
        self.goal_study.setRange(0, 720)
        self.goal_study.setSingleStep(15)
        self.goal_study.setSuffix(" phút")
        _field(self.goal_study, 124)
        row = Row("Học mỗi ngày", "Phút bạn muốn hoàn thành trong một ngày", _tile(icon="target", tone="accent"))
        row.add(self.goal_study)
        plan_group.add(row)
        self.goal_gym = QSpinBox()
        self.goal_gym.setRange(0, 14)
        self.goal_gym.setSuffix(" buổi")
        _field(self.goal_gym, 124)
        row = Row("Gym mỗi tuần", "Số buổi tập có xác nhận", _tile(icon="dumbbell", tone="accent"))
        row.add(self.goal_gym)
        plan_group.add(row)
        column.addWidget(plan_group)
        save_plan = _button("Lưu mục tiêu", primary=True)
        save_plan.clicked.connect(self.save_plan)
        column.addWidget(save_plan, alignment=Qt.AlignmentFlag.AlignLeft)

        column.addWidget(_section("Nhắc nhở"))
        reminder_group = Group()
        self.reminder_time = QTimeEdit()
        self.reminder_time.setDisplayFormat("HH:mm")
        _field(self.reminder_time, 96)
        self.reminder_switch = Switch()
        row = Row("Nhắc khi chưa vào guồng", "Kiểm tra mỗi ngày lúc", _tile(icon="bell", tone="accent"))
        row.add(self.reminder_time)
        row.add(self.reminder_switch)
        reminder_group.add(row)
        column.addWidget(reminder_group)
        save_reminder = _button("Lưu nhắc nhở")
        save_reminder.clicked.connect(self.save_reminders)
        column.addWidget(save_reminder, alignment=Qt.AlignmentFlag.AlignLeft)

        column.addWidget(_section("Điện thoại"))
        phone_group = Group()
        row = Row("Trạng thái", "Đang kiểm tra…", _tile(icon="phone", tone="accent"))
        self.phone_status = row.subtitle
        phone_group.add(row)
        phone_group.add(
            _link_row(
                "Tạo mã kết nối",
                "Quét QR hoặc nhập mã 6 số trên điện thoại",
                self.open_pairing,
                icon="refresh",
            )
        )
        column.addWidget(phone_group)
        column.addWidget(_note("Hai máy phải cùng một mạng Wi-Fi. Không cần nhập địa chỉ IP.", "info"))
        column.addWidget(_section("Thiết lập"))
        setup_group = Group()
        setup_group.add(
            _link_row(
                "Chạy lại thiết lập",
                "Xem lại mục chặn, lịch, mục tiêu và kết nối",
                self.open_setup,
                icon="steps",
            )
        )
        column.addWidget(setup_group)
        column.addStretch(1)
        return page

    def _build_advanced(self) -> QWidget:
        page = QWidget()
        outer = QVBoxLayout(page)
        outer.setContentsMargins(24, 20, 24, 22)
        outer.setSpacing(9)
        header, box, _, _ = self._header("Nâng cao", "Tiện ích kỹ thuật — bỏ qua nếu bạn không cần")
        self.advanced_pill = Pill("—", "neutral", dot=False)
        box.addWidget(self.advanced_pill, alignment=Qt.AlignmentFlag.AlignVCenter)
        outer.addWidget(header)

        outer.addWidget(_section("Trạng thái"))
        status_group = Group()
        row = Row("Nền bảo vệ", "—", _tile(icon="lock", tone="neutral"))
        self.advanced_status = row.subtitle
        status_group.add(row)
        row = Row("Luật & dữ liệu", "—", _tile(icon="database", tone="neutral"))
        self.advanced_detail = row.subtitle
        status_group.add(row)
        outer.addWidget(status_group)

        outer.addWidget(_section("Tiện ích"))
        tools = Group()
        tools.add(
            _link_row("Mở config.toml", "File luật đang áp dụng", lambda: os.startfile(CONFIG_PATH), icon="file")
        )
        tools.add(_link_row("Nhật ký gần đây", "30 sự kiện chặn và focus", self.show_logs, icon="chart"))
        tools.add(_link_row("Sao lưu dữ liệu", "Snapshot SQLite vào thư mục backups", self.backup_data, icon="database"))
        tools.add(_link_row("Cài task tự chạy", "Giữ nền bảo vệ bật mỗi phút", self.install_task, icon="refresh"))
        outer.addWidget(tools)

        card = QFrame()
        card.setObjectName("dangerCard")
        card_layout = QHBoxLayout(card)
        card_layout.setContentsMargins(16, 13, 14, 13)
        card_layout.setSpacing(12)
        texts = QVBoxLayout()
        texts.setSpacing(2)
        title = QLabel("Tắt bảo vệ")
        title.setStyleSheet(f"background:transparent;color:{DANGER};font-size:14px;font-weight:600;")
        texts.addWidget(title)
        texts.addWidget(_label("Phải gõ lại toàn bộ đoạn cam kết, rồi chờ 60 giây.", "rowSub"))
        card_layout.addLayout(texts, 1)
        disable = _button("Tắt bảo vệ", danger=True)
        disable.clicked.connect(self.open_disable)
        card_layout.addWidget(disable, alignment=Qt.AlignmentFlag.AlignVCenter)
        outer.addWidget(card)
        outer.addStretch(1)
        return page

    def refresh(self) -> None:
        try:
            data = build_summary(DB_PATH, CONFIG_PATH, LOCK_PATH)
        except Exception as exc:
            self.today_clock.setText(f"Lỗi: {exc}")
            return
        focus = data["focus"]
        study = data["habits"]["study"]
        gym = data["habits"]["gym"]
        blocks = data["blocks"]
        self.today_clock.setText(f"{data['date']} · {data['time']}")
        self.pill.set_state(
            "Đang focus" if focus["active"] else "Ngoài giờ",
            "accent" if focus["active"] else "neutral",
        )
        note = focus["text"]
        if focus["detail"]:
            note += f" · {focus['detail']}"
        self.hero_note.setText(note)

        self.ring.set_values(study["today"], study["goal"])
        self.ring_detail.setText(f"Chuỗi {study['streak']} ngày · tuần {study['week']}/{study['week_goal']} phút")
        self.study_value.setText(f"{study['today']}′")
        self.study_sub.setText(f"mục tiêu {study['goal']}′ trong ngày")
        self.study_bar.setRange(0, max(1, study["goal"]))
        self.study_bar.setValue(min(study["today"], max(1, study["goal"])))
        self.gym_value.setText(f"{gym['week']} / {gym['goal']}")
        self.gym_sub.setText(f"Chuỗi {gym['streak_weeks']} tuần đạt mục tiêu")
        self.gym_bar.setRange(0, max(1, gym["goal"]))
        self.gym_bar.setValue(min(gym["week"], max(1, gym["goal"])))
        self.block_value.setText(str(blocks["today"]))
        aggregated: dict[str, int] = {}
        for name, count in blocks["top"]:
            label = catalog.display_block(name)
            aggregated[label] = aggregated.get(label, 0) + count
        self.block_sub.setText(
            "7 ngày: " + (", ".join(f"{n} ({c})" for n, c in aggregated.items()) or "chưa có")
        )
        self.block_bar.setMaximum(1)
        self.block_bar.setValue(1 if blocks["today"] else 0)

        self.rail_title.setText("Đang bảo vệ" if data["daemon"] else "Chưa bảo vệ")
        self.rail_info.setText(
            f"{data['rules']['processes']} ứng dụng · {data['rules']['domains']} trang web"
        )
        connected = bool(self._phone_token())
        self.rail_pill.set_state("Đã ghép nối" if connected else "Chưa ghép nối", "accent" if connected else "neutral")

        store = self._store()
        try:
            onboarded = store.get_state("onboarded_v2") == "1"
        finally:
            store.close()
        self.onboard_banner.setVisible(not onboarded)

        session = data["session"]
        if session:
            self.hero_title.setText(f"Đang tập trung · còn {session['remaining_minutes']} phút")
            detail = f"«{session['label']}» đến {session['end'][11:16]}"
            if session["stop_after"]:
                detail += f" · dừng lúc {session['stop_after'][11:16]}"
            self.hero_sub.setText(detail)
            self.start_button.setEnabled(False)
            self.stop_button.setEnabled(True)
            self.cancel_button.setEnabled(bool(session["stop_after"]))
        else:
            self.hero_title.setText(f"{self.selected_minutes} phút không xao nhãng")
            self.hero_sub.setText("Trong phiên, các ứng dụng và web bạn chọn sẽ bị khóa.")
            self.start_button.setEnabled(True)
            self.stop_button.setEnabled(False)
            self.cancel_button.setEnabled(False)
            self.start_button.setText(f"Bắt đầu {self.selected_minutes} phút")
        self.study_goal = study["goal"]
        self._fill_table(study["series"], gym["series"], blocks["series"])

    def _store(self) -> Store:
        return Store(DB_PATH)

    def _phone_token(self) -> str | None:
        store = self._store()
        try:
            return phone_api.get_token(store)
        finally:
            store.close()

    def _fill_table(self, study, gym, blocks) -> None:
        goal = max(1, getattr(self, "study_goal", 0))
        self.table.setRowCount(len(study))
        max_value = max([row["value"] for row in study] + [1])
        self.study_delegate.max_value = max_value
        for index, row in enumerate(study):
            day = QTableWidgetItem(row["label"])
            day.setForeground(QColor(INK))
            self.table.setItem(index, 0, day)
            study_item = QTableWidgetItem()
            study_item.setData(Qt.ItemDataRole.UserRole, row["value"])
            self.table.setItem(index, 1, study_item)
            value = row["value"]
            gym_item = QTableWidgetItem(str(gym[index]["value"] if index < len(gym) else 0))
            gym_item.setTextAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)
            gym_item.setForeground(QColor(INK_2))
            block_item = QTableWidgetItem(str(blocks[index]["value"] if index < len(blocks) else 0))
            block_item.setTextAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)
            block_item.setForeground(QColor(INK_2))
            status_item = QTableWidgetItem(
                "Đạt" if value >= goal else ("Một phần" if value else "Nghỉ")
            )
            status_item.setForeground(QColor(ACCENT if value >= goal else (INK_2 if value else INK_3)))
            self.table.setItem(index, 2, gym_item)
            self.table.setItem(index, 3, block_item)
            self.table.setItem(index, 4, status_item)

    def _select_minutes(self, minutes: int) -> None:
        self.selected_minutes = minutes
        self.refresh()

    def start_session(self) -> None:
        store = self._store()
        try:
            try:
                session = sessions.start(store, self.selected_minutes, "Desktop focus", datetime.now())
            except ValueError as exc:
                QMessageBox.warning(self, "Không bắt đầu được", str(exc))
                return
        finally:
            store.close()
        if _daemon_pid() is None:
            _start_daemon()
        self.refresh()

    def stop_session(self) -> None:
        self._session_action("stop")

    def cancel_stop(self) -> None:
        self._session_action("cancel")

    def _session_action(self, action: str) -> None:
        store = self._store()
        try:
            approved = rules.approved_config(store)
            delay = approved.session_stop_delay_minutes if approved else 10
            try:
                if action == "stop":
                    sessions.request_stop(store, delay, datetime.now())
                else:
                    sessions.cancel_stop(store, datetime.now())
            except ValueError as exc:
                QMessageBox.warning(self, "Không thực hiện được", str(exc))
                return
        finally:
            store.close()
        self.refresh()

    def checkin(self, habit: str, minutes: int) -> None:
        store = self._store()
        try:
            store.add_checkin(habit, minutes)
        finally:
            store.close()
        self.refresh()

    def quick_setup(self) -> None:
        store = self._store()
        try:
            store.set_state("onboarded_v2", "1")
        finally:
            store.close()
        self.onboard_banner.hide()
        self.go(1)
        QMessageBox.information(
            self,
            "Thiết lập nhanh",
            "Bật các ứng dụng và trang web bạn muốn chặn. Thay đổi siết chặt áp dụng ngay; "
            "thay đổi nới lỏng sẽ chờ 24 giờ, riêng công tắc 18+ chờ 60 giây. Mục tiêu học/gym ở tab Cài đặt.",
        )

    def edit_schedule(self) -> None:
        store = self._store()
        try:
            approved = rules.approved_config(store)
        finally:
            store.close()
        if approved is None:
            QMessageBox.warning(self, "Chưa có luật", f"Hãy chạy {CLI_HINT} apply trước.")
            return
        ScheduleDialog(self, approved).exec()
        self.refresh_protect()

    def custom_entries(self, kind: str, store: Store | None = None) -> list[dict]:
        key = CUSTOM_APPS_KEY if kind == "app" else CUSTOM_SITES_KEY
        field = "processes" if kind == "app" else "domains"
        if store is None:
            own = self._store()
            try:
                raw = own.get_state(key)
            finally:
                own.close()
        else:
            raw = store.get_state(key)
        try:
            rows = json.loads(raw) if raw else []
        except (TypeError, ValueError):
            rows = []
        entries = []
        for row in rows:
            if not isinstance(row, dict):
                continue
            name = str(row.get("name", "")).strip()
            values = tuple(str(v).strip().lower() for v in row.get(field, ()) if str(v).strip())
            if name and values:
                entries.append({"name": name, "category": "Bạn tự thêm", field: values, "custom": True})
        return entries

    def refresh_protect(self) -> None:
        store = self._store()
        try:
            approved = rules.approved_config(store)
            pending = store.get_state(PENDING_KEY)
            net_enabled = store.get_state(NET_ENABLED_KEY) == "1"
            apps = list(catalog.APP_CATALOG) + self.custom_entries("app", store)
            sites = list(catalog.WEB_CATALOG) + self.custom_entries("web", store)
        finally:
            store.close()
        self.protect_apps = apps
        self.protect_sites = sites
        self.sync_adult(approved, net_enabled)
        self.sync_visual(approved)
        if not approved:
            self.protect_note.body.setText(f"Chưa có luật. Hãy chạy {CLI_HINT} apply trước.")
            self.protect_note.show()
            self.protect_pill.set_state("chưa có luật", "danger")
            return
        signature = tuple(entry["name"] for entry in apps) + tuple(entry["name"] for entry in sites)
        if signature != self.protect_signature:
            self.build_protect_rows(apps, sites)
        blocked_apps = 0
        for entry in apps:
            switch = self.app_switches[entry["name"]]
            active = catalog.app_enabled(approved.processes, entry)
            switch.blockSignals(True)
            switch.setChecked(active)
            switch.blockSignals(False)
            blocked_apps += 1 if active else 0
        blocked_sites = 0
        for entry in sites:
            switch = self.web_switches[entry["name"]]
            active = catalog.web_enabled(approved.web_domains, entry)
            switch.blockSignals(True)
            switch.setChecked(active)
            switch.blockSignals(False)
            blocked_sites += 1 if active else 0
        anything = bool(blocked_apps or blocked_sites or approved.adult_block or approved.adult_visual)
        parts = [f"{blocked_apps} app", f"{blocked_sites} web"]
        if approved.adult_block:
            parts.append("18+")
        if approved.adult_visual:
            parts.append("ảnh 18+")
        self.protect_pill.set_state(" · ".join(parts) + " đang chặn", "accent" if anything else "neutral")

        self.schedule_group.reset()
        for window in approved.windows:
            days = " · ".join(WEEKDAY_VI[day].replace("Thứ ", "T") for day in window.days)
            row = Row(
                f"{window.start:%H:%M} – {window.end:%H:%M}",
                f"{days} · {window.label}",
                _tile(icon="clock", tone="accent"),
            )
            self.schedule_group.add(row)
        self.schedule_group.add(
            _link_row("Sửa lịch", "Chọn ngày và khung giờ tập trung", self.edit_schedule, icon="calendar")
        )

        if pending:
            try:
                data = json.loads(pending)
                self.protect_note.body.setText(
                    f"Thay đổi nới lỏng đang chờ hiệu lực lúc {data['effective_at'][11:16]} ngày "
                    f"{data['effective_at'][8:10]}/{data['effective_at'][5:7]}."
                )
                self.protect_note.show()
            except (TypeError, ValueError, KeyError):
                self.protect_note.hide()
        else:
            self.protect_note.hide()

    def build_protect_rows(self, apps: list[dict], sites: list[dict]) -> None:
        self.app_switches = {}
        self.web_switches = {}
        self.apps_group.reset()
        for entry in apps:
            self.apps_group.add(self.rule_row(entry, "app", self.app_switches))
        self.apps_group.add(
            _link_row("Thêm app khác", "Chọn từ ứng dụng đang cài trên máy", self.add_app)
        )
        self.sites_group.reset()
        for entry in sites:
            self.sites_group.add(self.rule_row(entry, "web", self.web_switches))
        self.sites_group.add(
            _link_row("Thêm trang web khác", "Nhập tên và domain muốn chặn", self.add_site)
        )
        self.protect_signature = tuple(e["name"] for e in apps) + tuple(e["name"] for e in sites)

    def rule_row(self, entry: dict, kind: str, registry: dict) -> Row:
        tone = "accent" if entry.get("custom") else "neutral"
        tile = (
            _tile(icon="globe" if kind == "web" else "desktop", tone=tone)
            if entry.get("custom")
            else _tile(entry["name"], tone=tone)
        )
        row = Row(entry["name"], entry.get("category", ""), tile)
        if entry.get("custom"):
            remove = QPushButton()
            remove.setIcon(svg_icon("close", INK_3, 14))
            remove.setFixedSize(28, 28)
            remove.setCursor(Qt.CursorShape.PointingHandCursor)
            remove.setToolTip("Bỏ khỏi danh sách")
            remove.setStyleSheet(
                "QPushButton{background:transparent;border:none;border-radius:8px;}"
                "QPushButton:hover{background:#f2f4f5;}"
            )
            remove.clicked.connect(
                lambda _=False, k=kind, item=entry: self.remove_custom(k, item)
            )
            row.add(remove)
        switch = Switch()
        switch.setAccessibleName(f"Chặn {entry['name']}")
        handler = self.toggle_app if kind == "app" else self.toggle_web
        switch.toggled.connect(lambda checked, item=entry: handler(item, checked))
        registry[entry["name"]] = switch
        row.add(switch)
        return row

    def add_app(self) -> None:
        blocked = {value for entry in self.protect_apps for value in entry["processes"]}
        dialog = AddAppDialog(self, blocked)
        if dialog.exec() != QDialog.DialogCode.Accepted or not dialog.entries:
            return
        self.apply_custom("app", added=dialog.entries)

    def add_site(self) -> None:
        blocked = {value for entry in self.protect_sites for value in entry["domains"]}
        dialog = AddSiteDialog(self, blocked)
        if dialog.exec() != QDialog.DialogCode.Accepted or not dialog.entries:
            return
        self.apply_custom("web", added=dialog.entries)

    def remove_custom(self, kind: str, entry: dict) -> None:
        box = QMessageBox(self)
        box.setWindowTitle("Bỏ khỏi danh sách")
        box.setText(
            f"Bỏ «{entry['name']}» khỏi danh sách chặn. Vì đây là nới lỏng luật, thay đổi chỉ có "
            "hiệu lực sau 24 giờ."
        )
        box.setStandardButtons(QMessageBox.StandardButton.Cancel | QMessageBox.StandardButton.Ok)
        if box.exec() != QMessageBox.StandardButton.Ok:
            return
        self.apply_custom(kind, removed=[entry])

    def apply_custom(
        self, kind: str, added: list[dict] | None = None, removed: list[dict] | None = None
    ) -> None:
        key = CUSTOM_APPS_KEY if kind == "app" else CUSTOM_SITES_KEY
        field = "processes" if kind == "app" else "domains"
        added = added or []
        removed = removed or []
        store = self._store()
        try:
            kept = list(self.custom_entries(kind, store))
            gone = {entry["name"].lower() for entry in removed}
            kept = [entry for entry in kept if entry["name"].lower() not in gone]
            known = {entry["name"].lower() for entry in kept}
            for entry in added:
                if entry["name"].lower() in known:
                    continue
                kept.append(
                    {
                        "name": entry["name"],
                        "category": "Bạn tự thêm",
                        field: tuple(entry[field]),
                        "custom": True,
                    }
                )
                known.add(entry["name"].lower())
            payload = [{"name": entry["name"], field: list(entry[field])} for entry in kept]
            store.set_state(key, json.dumps(payload, ensure_ascii=False))
            approved = rules.approved_config(store)
            if approved is None:
                QMessageBox.warning(self, "Chưa có luật", f"Hãy chạy {CLI_HINT} apply trước.")
                return
            processes, domains = approved.processes, approved.web_domains
            for entry in added:
                if kind == "app":
                    processes = catalog.set_app(processes, entry, True)
                else:
                    domains = catalog.set_web(domains, entry, True)
            for entry in removed:
                if kind == "app":
                    processes = catalog.set_app(processes, entry, False)
                else:
                    domains = catalog.set_web(domains, entry, False)
            candidate = replace(approved, processes=processes, web_domains=domains)
            write_rules(CONFIG_PATH, candidate)
            result = rules.apply_candidate(store, candidate)
        finally:
            store.close()
        self.refresh_protect()
        if result.state == "queued":
            self.protect_note.body.setText(
                "Thay đổi làm yếu luật nên sẽ có hiệu lực sau 24 giờ. Trong lúc chờ, luật cũ vẫn thi hành."
            )
            self.protect_note.show()

    def toggle_app(self, entry: dict, checked: bool) -> None:
        self._toggle_rule(app=entry, enabled=checked)

    def toggle_web(self, entry: dict, checked: bool) -> None:
        self._toggle_rule(web=entry, enabled=checked)

    def sync_web_row(self, net_enabled: bool) -> None:
        if not hasattr(self, "web_row"):
            return
        if net_enabled:
            # Luật bật mà cổng 53 không ai trả lời nghĩa là DNS vẫn trỏ về máy và cả máy không
            # phân giải được tên miền nào — phải nói rõ chứ không được để hiện "đang bật" êm ru.
            if net.probe() is None:
                self.web_row.set_text(
                    "Chặn web đang bật nhưng bộ lọc chết",
                    f"DNS trỏ về {net.LOOPBACK_V4}, không ai nghe cổng 53 → chưa vào được web. "
                    f"Bấm Tắt bảo vệ, hoặc chạy PowerShell Administrator: {CLI_HINT} net uninstall",
                )
                self.web_row.title.setStyleSheet(
                    f"background:transparent;color:{DANGER};font-size:14px;font-weight:600;"
                )
                self.web_pill.set_state("Mạng đang bị chặn", "danger")
                self.web_row.handler = None
                self.web_row.setCursor(Qt.CursorShape.ArrowCursor)
                return
            self.web_row.set_text("Chặn web đang bật", "DNS máy trỏ về 127.0.0.1 · tắt bằng Tắt bảo vệ")
            self.web_row.title.setStyleSheet(
                f"background:transparent;color:{INK};font-size:14px;font-weight:600;"
            )
            self.web_pill.set_state("Đang bật", "accent")
            self.web_row.handler = None
            self.web_row.setCursor(Qt.CursorShape.ArrowCursor)
        else:
            self.web_row.set_text("Bật chặn web", "Cần Administrator · để luật web và 18+ có tác dụng")
            self.web_row.title.setStyleSheet(
                f"background:transparent;color:{ACCENT};font-size:14px;font-weight:600;"
            )
            self.web_pill.set_state("Cần bật", "neutral")
            self.web_row.handler = self._offer_web_blocking
            self.web_row.setCursor(Qt.CursorShape.PointingHandCursor)

    def test_adult_filter(self) -> None:
        QApplication.setOverrideCursor(Qt.CursorShape.WaitCursor)
        try:
            _, _, message = net.adult_selftest()
        finally:
            QApplication.restoreOverrideCursor()
        QMessageBox.information(self, "Kiểm tra chặn thử", message)

    def sync_adult(self, approved: Config | None, net_enabled: bool) -> None:
        self.sync_web_row(net_enabled)
        active = bool(approved and approved.adult_block)
        self.adult_switch.blockSignals(True)
        self.adult_switch.setChecked(active)
        self.adult_switch.blockSignals(False)
        count = f"{len(ADULT_DOMAINS):,}".replace(",", ".")
        if not active:
            self.adult_status.setText(f"{count} tên miền · đang tắt")
        elif not net_enabled:
            self.adult_status.setText("Đã bật luật · máy chưa bật chặn web nên chưa có tác dụng")
        else:
            self.adult_status.setText(f"{count} tên miền · chạy suốt ngày đêm")

    def sync_visual(self, approved: Config | None) -> None:
        active = bool(approved and approved.adult_visual)
        self.visual_switch.blockSignals(True)
        self.visual_switch.setChecked(active)
        self.visual_switch.blockSignals(False)
        if not model_available():
            self.visual_switch.setEnabled(False)
            self.visual_status.setText("Thiếu mô hình nhận diện trong bản cài")
            return
        self.visual_switch.setEnabled(True)
        if active and _daemon_pid() is None:
            self.visual_status.setText("Đã bật · chờ bảo vệ nền khởi động")
        elif active:
            self.visual_status.setText("Trình duyệt · quét 4 giây một lần, ảnh không rời máy")
        else:
            self.visual_status.setText("Tắt · thu nhỏ cửa sổ trình duyệt khi thấy ảnh 18+")

    def toggle_visual(self, checked: bool) -> None:
        if checked and not self._confirm_visual_on():
            self.refresh_protect()
            return
        store = self._store()
        try:
            approved = rules.approved_config(store)
            if approved is None:
                QMessageBox.warning(self, "Chưa có luật", f"Hãy chạy {CLI_HINT} apply trước.")
                self.refresh_protect()
                return
            candidate = replace(approved, adult_visual=checked)
            write_rules(CONFIG_PATH, candidate)
            rules.apply_now(
                store,
                candidate,
                "bật nhận diện ảnh 18+" if checked else "tắt nhận diện ảnh 18+",
            )
        finally:
            store.close()
        if checked and _daemon_pid() is None:
            _start_daemon()
        self.refresh_protect()

    def _confirm_visual_on(self) -> bool:
        box = QMessageBox(self)
        box.setWindowTitle("Bật nhận diện ảnh 18+?")
        box.setText("Kỷ luật sẽ đọc cửa sổ trình duyệt đang mở khoảng 4 giây một lần.")
        box.setInformativeText(
            "Ảnh chỉ được xử lý trong bộ nhớ bằng mô hình trên máy, không lưu và không gửi đi. "
            "Khi thấy nội dung 18+, cửa sổ trình duyệt bị thu nhỏ.\n\n"
            "Có thể nhận nhầm ảnh bơi lội hoặc em bé. Tắt công tắc này bất cứ lúc nào."
        )
        box.setIcon(QMessageBox.Icon.Warning)
        on = box.addButton("Bật", QMessageBox.ButtonRole.AcceptRole)
        box.addButton("Để sau", QMessageBox.ButtonRole.RejectRole)
        box.exec()
        return box.clickedButton() is on

    def toggle_adult(self, checked: bool) -> None:
        store = self._store()
        try:
            approved = rules.approved_config(store)
        finally:
            store.close()
        if approved is None:
            QMessageBox.warning(self, "Chưa có luật", f"Hãy chạy {CLI_HINT} apply trước.")
            self.refresh_protect()
            return
        if not checked and not self._confirm_adult_off():
            self.refresh_protect()
            return
        store = self._store()
        try:
            approved = rules.approved_config(store)
            if approved is None:
                self.refresh_protect()
                return
            candidate = replace(approved, adult_block=checked)
            write_rules(CONFIG_PATH, candidate)
            if checked:
                rules.apply_candidate(store, candidate)
            else:
                rules.apply_now(store, candidate, "tắt 18+ sau 60 giây suy nghĩ")
            net_enabled = store.get_state(NET_ENABLED_KEY) == "1"
        finally:
            store.close()
        if checked and net_enabled:
            net.flush_dns()
        self.refresh_protect()
        if checked and not net_enabled:
            self._offer_web_blocking()

    def _confirm_adult_off(self) -> bool:
        progress = QProgressDialog(
            "Tắt chặn nội dung 18+ sau 60 giây. Bấm Giữ bật để hủy.",
            "Giữ bật",
            0,
            60,
            self,
        )
        progress.setWindowTitle("Tắt chặn 18+?")
        progress.setWindowModality(Qt.WindowModal)
        progress.setMinimumDuration(0)
        progress.setAutoClose(False)
        progress.setAutoReset(False)
        progress.setValue(0)

        def tick() -> None:
            value = min(progress.maximum(), progress.value() + 1)
            progress.setValue(value)
            remaining = progress.maximum() - value
            progress.setLabelText(
                f"Tắt chặn nội dung 18+ sau {remaining} giây. Bấm Giữ bật để hủy."
            )
            if value >= progress.maximum():
                progress.accept()

        timer = QTimer(progress)
        timer.setInterval(1000)
        timer.timeout.connect(tick)
        timer.start()
        progress.exec()
        timer.stop()
        return not progress.wasCanceled()

    def _offer_web_blocking(self) -> None:
        answer = QMessageBox.question(
            self,
            "Bật chặn web để luật 18+ có tác dụng",
            "Luật 18+ chỉ chặn được khi Windows hỏi DNS của Kỷ luật.\n\n"
            "Bước này đổi DNS card mạng sang 127.0.0.1, tắt DoH của Chrome và Edge, "
            "và cần quyền Administrator. Tắt bảo vệ sẽ trả DNS về cũ.",
            QMessageBox.Yes | QMessageBox.Cancel,
            QMessageBox.Yes,
        )
        if answer != QMessageBox.Yes:
            self.protect_note.body.setText(
                "Luật 18+ đã ghi nhưng chưa có tác dụng. Khi cần, chạy "
                f"{CLI_HINT} net install trong PowerShell Administrator."
            )
            self.protect_note.show()
            return
        params = "net install" if FROZEN else f'"{ROOT / "run_enforcer.pyw"}" net install'
        rc = ctypes.windll.shell32.ShellExecuteW(None, "runas", sys.executable, params, str(ROOT), 1)
        if rc <= 32:
            QMessageBox.warning(
                self,
                "Chưa bật được chặn web",
                "UAC bị hủy hoặc Windows không mở được tiến trình quản trị.\n"
                f"Hãy chạy PowerShell Administrator: {CLI_HINT} net install",
            )
            return
        self.protect_note.body.setText(
            "Đang bật chặn web bằng quyền Administrator. Đợi vài giây, khởi động lại Chrome/Edge; "
            "Firefox thì tắt DNS over HTTPS trong Cài đặt."
        )
        self.protect_note.show()

        def check_install() -> None:
            self.refresh_protect()
            store = self._store()
            try:
                enabled = store.get_state(NET_ENABLED_KEY) == "1"
            finally:
                store.close()
            if not enabled:
                self.protect_note.body.setText(
                    "Chưa bật được chặn web. Chạy PowerShell Administrator: "
                    f"{CLI_HINT} net install"
                )
                self.protect_note.show()

        QTimer.singleShot(20000, check_install)

    def _toggle_rule(self, app: dict | None = None, web: dict | None = None, enabled: bool = True) -> None:
        store = self._store()
        try:
            approved = rules.approved_config(store)
            if not approved:
                QMessageBox.warning(self, "Chưa có luật", f"Hãy chạy {CLI_HINT} apply trước.")
                return
            processes = approved.processes
            domains = approved.web_domains
            if app is not None:
                processes = catalog.set_app(processes, app, enabled)
            if web is not None:
                domains = catalog.set_web(domains, web, enabled)
            from dataclasses import replace

            candidate = replace(approved, processes=processes, web_domains=domains)
            write_rules(CONFIG_PATH, candidate)
            result = rules.apply_candidate(store, candidate)
        finally:
            store.close()
        self.refresh_protect()
        if result.state == "queued":
            self.protect_note.body.setText(
                "Thay đổi làm yếu luật nên sẽ có hiệu lực sau 24 giờ. "
                "Trong lúc chờ, luật cũ vẫn thi hành."
            )
            self.protect_note.show()

    def refresh_settings(self) -> None:
        plan = load_plan(CONFIG_PATH)
        reminders = load_reminders(CONFIG_PATH)
        self.goal_study.setValue(plan.study_minutes_daily)
        self.goal_gym.setValue(plan.gym_sessions_weekly)
        self.reminder_switch.blockSignals(True)
        self.reminder_switch.setChecked(reminders.enabled)
        self.reminder_switch.blockSignals(False)
        self.reminder_time.setTime(QTime(reminders.study_check_time.hour, reminders.study_check_time.minute))
        store = self._store()
        try:
            token = phone_api.get_token(store)
            net_enabled = store.get_state(NET_ENABLED_KEY) == "1"
            approved = rules.approved_config(store)
        finally:
            store.close()
        self.phone_status.setText(
            f"Đã ghép nối · http://{phone_api.lan_ip()}:{phone_api.PHONE_PORT}"
            if token
            else "Chưa kết nối điện thoại."
        )
        pid = _daemon_pid()
        self.advanced_status.setText(
            f"Daemon {'đang chạy' if pid else 'không chạy'} · "
            f"tự chạy {'có' if _task_status().startswith('có') else 'chưa cài'} · "
            f"chặn web {'bật' if net_enabled else 'tắt'}"
        )
        self.advanced_pill.set_state("Nền đang chạy" if pid else "Nền đang tắt", "accent" if pid else "warn")
        self.advanced_detail.setText(
            f"{len(approved.processes) if approved else 0} tiến trình · "
            f"{len(approved.web_domains) if approved else 0} domain · "
            f"{len(approved.windows) if approved else 0} khung giờ · {DB_PATH}"
        )

    def save_plan(self) -> None:
        plan = load_plan(CONFIG_PATH)
        from dataclasses import replace

        write_plan(CONFIG_PATH, replace(plan, study_minutes_daily=self.goal_study.value(), gym_sessions_weekly=self.goal_gym.value()))
        self.refresh()

    def save_reminders(self) -> None:
        reminders = load_reminders(CONFIG_PATH)
        chosen = self.reminder_time.time()
        from dataclasses import replace

        write_reminders(
            CONFIG_PATH,
            replace(
                reminders,
                enabled=self.reminder_switch.isChecked(),
                study_check_time=time(chosen.hour(), chosen.minute()),
            ),
        )

    def open_pairing(self) -> None:
        PairingDialog(self).exec()

    def open_setup(self) -> None:
        self.hide()
        try:
            SetupDialog(self).exec()
        finally:
            self.show()
            self.refresh()
            self.refresh_protect()
            self.refresh_settings()

    def open_disable(self) -> None:
        DisableDialog(self).exec()

    def show_logs(self) -> None:
        store = self._store()
        try:
            rows = store.recent_events(30)
        finally:
            store.close()
        text = "\n".join(f"{ts.replace('T', ' ')}  {kind}  {detail}" for ts, kind, detail in rows)
        box = QMessageBox(self)
        box.setWindowTitle("Nhật ký gần đây")
        box.setText(text or "Chưa có sự kiện.")
        box.exec()

    def backup_data(self) -> None:
        store = self._store()
        try:
            target = store.backup(DB_PATH.parent / "backups")
        finally:
            store.close()
        QMessageBox.information(self, "Sao lưu", f"Đã sao lưu: {target.name}")

    def install_task(self) -> None:
        result = _create_task(elevated=False)
        if result.returncode == 0:
            QMessageBox.information(self, "Tự chạy", "Đã cài task tự chạy mỗi phút.")
        else:
            message = (result.stderr or result.stdout).decode("utf-8", "replace").strip()
            QMessageBox.warning(self, "Không cài được task", message or "schtasks thất bại")
        self.refresh_settings()

    def closeEvent(self, event) -> None:
        event.accept()


BLOCK_DENY = frozenset(
    {
        "explorer.exe", "dwm.exe", "csrss.exe", "wininit.exe", "winlogon.exe", "smss.exe",
        "services.exe", "svchost.exe", "lsass.exe", "lsm.exe", "conhost.exe", "taskhostw.exe",
        "userinit.exe", "fontdrvhost.exe", "sihost.exe", "ctfmon.exe", "runtimebroker.exe",
        "searchindexer.exe", "searchui.exe", "startmenuexperiencehost.exe", "shellhost.exe",
        "taskkill.exe", "cmd.exe", "powershell.exe", "ms-mpeng.exe", "nissrv.exe", "nvcontainer.exe",
    }
)
GENERIC_LAUNCHERS = frozenset({"msiexec.exe", "unins000.exe", "unins001.exe", "setup.exe", "uninstall.exe"})
DOMAIN_RE = re.compile(r"^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)*\.[a-z]{2,}$")


def _safe_process(exe: str) -> bool:
    name = exe.strip().lower()
    if not name.endswith(".exe") or name in BLOCK_DENY or name in GENERIC_LAUNCHERS:
        return False
    return name != Path(sys.executable).name.lower()


def _exe_from(raw: object) -> str:
    text = str(raw or "").strip()
    if not text:
        return ""
    quoted = re.match(r'^"([^"]+)"', text)
    candidate = quoted.group(1) if quoted else text
    index = candidate.lower().find(".exe")
    if index < 0:
        return ""
    return Path(candidate[: index + 4]).name.lower()


def installed_apps() -> list[dict]:
    """Đọc app đã cài từ registry Windows, chỉ giữ app xác định được tên tiến trình."""
    hives = (
        (winreg.HKEY_LOCAL_MACHINE, r"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall", winreg.KEY_WOW64_64KEY),
        (winreg.HKEY_LOCAL_MACHINE, r"SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall", 0),
        (winreg.HKEY_CURRENT_USER, r"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall", winreg.KEY_WOW64_64KEY),
    )
    found: dict[str, dict] = {}
    for hive, path, flag in hives:
        try:
            with winreg.OpenKey(hive, path, 0, winreg.KEY_READ | flag) as root_key:
                count = winreg.QueryInfoKey(root_key)[0]
                for index in range(count):
                    try:
                        with winreg.OpenKey(root_key, winreg.EnumKey(root_key, index), 0, winreg.KEY_READ) as entry:
                            name = str(winreg.QueryValueEx(entry, "DisplayName")[0]).strip()
                            exe = ""
                            for field in ("DisplayIcon", "UninstallString", "QuietUninstallString"):
                                try:
                                    exe = _exe_from(winreg.QueryValueEx(entry, field)[0])
                                except OSError:
                                    exe = ""
                                if exe:
                                    break
                    except OSError:
                        continue
                    if not name or not _safe_process(exe) or exe in found:
                        continue
                    found[exe] = {"name": name, "processes": (exe,), "running": False}
        except OSError:
            continue
    return sorted(found.values(), key=lambda item: item["name"].lower())


def window_titles() -> dict[int, str]:
    """pid -> tiêu đề cửa sổ cấp cao đang hiện, để chỉ liệt kê app thật sự đang mở."""
    titles: dict[int, str] = {}
    try:
        user32 = ctypes.windll.user32
        callback = ctypes.WINFUNCTYPE(ctypes.c_bool, ctypes.c_void_p, ctypes.c_void_p)

        def visit(hwnd: int, _lparam: int) -> bool:
            if not user32.IsWindowVisible(hwnd) or user32.GetWindow(hwnd, 4):
                return True
            length = user32.GetWindowTextLengthW(hwnd)
            if not length:
                return True
            buffer = ctypes.create_unicode_buffer(length + 1)
            user32.GetWindowTextW(hwnd, buffer, length + 1)
            pid = ctypes.c_ulong(0)
            user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
            if pid.value and buffer.value:
                titles.setdefault(pid.value, buffer.value)
            return True

        user32.EnumWindows(callback(visit), 0)
    except Exception:
        return {}
    return titles


def running_apps(covered: set[str]) -> list[dict]:
    titles = window_titles()
    entries: dict[str, dict] = {}
    for candidate in psutil.process_iter(["pid", "name"]):
        try:
            pid = int(candidate.info["pid"])
            name = str(candidate.info["name"] or "").lower()
        except (psutil.Error, AttributeError, TypeError, ValueError):
            continue
        if not _safe_process(name) or name in covered or pid not in titles:
            continue
        label = titles[pid].rsplit(" - ", 1)[-1].strip()
        entries.setdefault(name, {"name": label or Path(name).stem, "processes": (name,), "running": True})
    return sorted(entries.values(), key=lambda item: item["name"].lower())


class AddAppDialog(QDialog):
    LIMIT = 120

    def __init__(self, parent, blocked: set[str]):
        super().__init__(parent)
        self.setWindowTitle("Thêm app cần chặn")
        self.setModal(True)
        self.setMinimumSize(580, 620)
        self.blocked = {name.lower() for name in blocked}
        self.selected: dict[str, dict] = {}
        self.pills: dict[str, Pill] = {}
        self.entries: list[dict] = []
        self.candidates: list[dict] = []

        layout = QVBoxLayout(self)
        layout.setContentsMargins(18, 16, 18, 16)
        layout.setSpacing(10)
        title = QLabel("Chọn app bạn muốn khóa trong phiên tập trung")
        title.setObjectName("title")
        title.setStyleSheet("font-size: 18px;")
        layout.addWidget(title)
        self.search = QLineEdit()
        self.search.setPlaceholderText("Tìm theo tên app hoặc tên tiến trình…")
        self.search.textChanged.connect(self.rebuild)
        layout.addWidget(self.search)

        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        content = QWidget()
        box = QVBoxLayout(content)
        box.setContentsMargins(0, 0, 8, 0)
        self.group = Group()
        box.addWidget(self.group)
        box.addStretch(1)
        scroll.setWidget(content)
        layout.addWidget(scroll, 1)

        layout.addWidget(
            _note(
                "Danh sách gồm ứng dụng đã cài và ứng dụng đang mở cửa sổ. Kỷ luật chỉ đóng đúng tiến trình "
                "bạn chọn, trong lúc focus.",
                "shield",
            )
        )
        buttons = QHBoxLayout()
        cancel = _button("Hủy")
        cancel.clicked.connect(self.reject)
        self.add_button = _button("Thêm", primary=True)
        self.add_button.setEnabled(False)
        self.add_button.clicked.connect(self.confirm)
        buttons.addStretch(1)
        buttons.addWidget(cancel)
        buttons.addWidget(self.add_button)
        layout.addLayout(buttons)

        self.group.add(Row("Đang đọc danh sách app…", "Quét ứng dụng đã cài và đang chạy", _tile(icon="search")))
        QTimer.singleShot(80, self.load)

    def load(self) -> None:
        try:
            installed = installed_apps()
            covered = {entry["processes"][0] for entry in installed}
            self.candidates = installed + running_apps(covered)
        except Exception as exc:
            self.candidates = []
            self.group.reset()
            self.group.add(Row("Không đọc được danh sách app", str(exc), _tile(icon="alert", tone="warn")))
            return
        self.rebuild()

    def rebuild(self) -> None:
        term = self.search.text().strip().lower()
        self.group.reset()
        self.pills = {}
        shown = 0
        matched = 0
        for entry in self.candidates:
            exe = entry["processes"][0]
            if term and term not in entry["name"].lower() and term not in exe:
                continue
            matched += 1
            if shown >= self.LIMIT:
                continue
            shown += 1
            self.group.add(self.row_for(entry, exe))
        if not matched:
            self.group.add(Row("Không tìm thấy app", "Thử gõ tên khác, ví dụ “zoom” hoặc “chrome”"))
        elif matched > shown:
            self.group.add(Row(f"Còn {matched - shown} app nữa", "Nhập từ khóa để thu hẹp danh sách"))
        self.count_changed()

    def row_for(self, entry: dict, exe: str) -> Row:
        if exe in self.blocked:
            row = Row(entry["name"], f"{exe} · đã có trong danh sách chặn", _tile(entry["name"]))
            row.add(Pill("Đang chặn", "neutral", dot=False))
            return row
        source = "đang chạy" if entry.get("running") else "đã cài"
        row = Row(entry["name"], f"{exe} · {source}", _tile(entry["name"]), handler=lambda key=exe: self.toggle(key))
        pill = Pill("Thêm", "neutral", dot=False)
        self.pills[exe] = pill
        row.add(pill)
        return row

    def toggle(self, exe: str) -> None:
        if exe in self.selected:
            self.selected.pop(exe)
        else:
            self.selected[exe] = next(item for item in self.candidates if item["processes"][0] == exe)
        self.count_changed()

    def count_changed(self) -> None:
        for exe, pill in self.pills.items():
            chosen = exe in self.selected
            pill.set_state("Đã chọn" if chosen else "Thêm", "accent" if chosen else "neutral")
        self.add_button.setText(f"Thêm {len(self.selected)} app" if self.selected else "Thêm")
        self.add_button.setEnabled(bool(self.selected))

    def confirm(self) -> None:
        self.entries = [
            {"name": entry["name"], "processes": entry["processes"]} for entry in self.selected.values()
        ]
        self.accept()


class AddSiteDialog(QDialog):
    def __init__(self, parent, blocked: set[str]):
        super().__init__(parent)
        self.setWindowTitle("Thêm trang web cần chặn")
        self.setModal(True)
        self.setMinimumWidth(470)
        self.blocked = {name.lower() for name in blocked}
        self.entries: list[dict] = []
        self.ready = None

        layout = QVBoxLayout(self)
        layout.setContentsMargins(18, 16, 18, 16)
        layout.setSpacing(9)
        title = QLabel("Thêm trang web")
        title.setObjectName("title")
        title.setStyleSheet("font-size: 19px;")
        layout.addWidget(title)
        layout.addWidget(
            _label("Chặn domain thì chặn luôn trang con: twitch.tv cũng khóa tv.twitch.tv.", "body")
        )

        name_box = QVBoxLayout()
        name_box.setSpacing(4)
        name_box.addWidget(_label("TÊN HIỂN THỊ", "cardLabel"))
        self.name = QLineEdit()
        self.name.setPlaceholderText("Ví dụ: Twitch")
        self.name.textChanged.connect(self.validate)
        name_box.addWidget(self.name)
        layout.addLayout(name_box)

        domains_box = QVBoxLayout()
        domains_box.setSpacing(4)
        domains_box.addWidget(_label("DOMAIN", "cardLabel"))
        self.domains = QLineEdit()
        self.domains.setPlaceholderText("twitch.tv, clips.twitch.tv")
        self.domains.textChanged.connect(self.validate)
        domains_box.addWidget(self.domains)
        layout.addLayout(domains_box)

        self.hint = _label("", "rowSub")
        self.hint.setStyleSheet(f"background:transparent;color:{DANGER};")
        layout.addWidget(self.hint)
        layout.addWidget(
            _note("Đừng chặn trang bạn dùng để học hay làm việc. Danh sách này chỉ nên là mạng xã hội, video, game.", "info")
        )

        buttons = QHBoxLayout()
        cancel = _button("Hủy")
        cancel.clicked.connect(self.reject)
        self.add_button = _button("Thêm", primary=True)
        self.add_button.setEnabled(False)
        self.add_button.clicked.connect(self.confirm)
        buttons.addStretch(1)
        buttons.addWidget(cancel)
        buttons.addWidget(self.add_button)
        layout.addLayout(buttons)
        self.validate()

    def parsed(self) -> tuple[list[str], list[str]]:
        values: list[str] = []
        invalid: list[str] = []
        for token in re.split(r"[,\s;]+", self.domains.text()):
            clean = token.strip().lower()
            clean = re.sub(r"^[a-z]+://", "", clean).split("/", 1)[0].split("?", 1)[0]
            clean = clean.removeprefix("www.")
            if not clean:
                continue
            if not DOMAIN_RE.match(clean):
                if clean not in invalid:
                    invalid.append(clean)
            elif clean not in values:
                values.append(clean)
        return values, invalid

    def validate(self) -> None:
        values, invalid = self.parsed()
        duplicates = [value for value in values if value in self.blocked]
        values = [value for value in values if value not in duplicates]
        if invalid:
            self.hint.setText("Bỏ “%s” — không phải tên miền hợp lệ." % ", ".join(invalid))
        elif duplicates:
            self.hint.setText("“%s” đã nằm trong danh sách chặn." % ", ".join(duplicates))
        else:
            self.hint.setText("")
        name = self.name.text().strip()
        ok = bool(name) and bool(values) and not invalid
        self.ready = (name, tuple(values)) if ok else None
        self.add_button.setText(f"Thêm {name}" if ok else "Thêm")
        self.add_button.setEnabled(ok)

    def confirm(self) -> None:
        name, values = self.ready
        self.entries = [{"name": name, "domains": values}]
        self.accept()


class ScheduleDialog(QDialog):
    CHIP = (
        "QPushButton{background:#f7f8f9;border:1px solid #e8ebed;border-radius:8px;"
        "min-width:34px;padding:6px 4px;color:#5c666f;font-size:12.5px;}"
    )
    CHIP_ON = (
        "QPushButton{background:#edf3f0;border:1px solid #d5e4dd;border-radius:8px;"
        "min-width:34px;padding:6px 4px;color:#2f6f5e;font-weight:600;font-size:12.5px;}"
    )

    def __init__(self, parent, approved: Config):
        super().__init__(parent)
        self.approved = approved
        self.rows = []
        self.setWindowTitle("Sửa lịch tập trung")
        self.setMinimumSize(920, 500)
        layout = QVBoxLayout(self)
        layout.setContentsMargins(18, 16, 18, 16)
        layout.setSpacing(10)

        title = QLabel("Lịch tập trung")
        title.setObjectName("title")
        title.setStyleSheet("font-size: 19px;")
        layout.addWidget(title)
        layout.addWidget(
            _note(
                "Khung giờ nào có mặt ở đây thì app và web bạn chọn sẽ bị khóa trong khung đó. "
                "Thêm giờ áp dụng ngay; bỏ giờ phải chờ 24 giờ.",
                "clock",
            )
        )

        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        content = QWidget()
        self.rows_layout = QVBoxLayout(content)
        self.rows_layout.setContentsMargins(0, 0, 8, 0)
        self.rows_layout.setSpacing(9)
        scroll.setWidget(content)
        layout.addWidget(scroll, 1)
        for window in approved.windows:
            self.add_row(window)

        add = _button("  +  Thêm khung giờ")
        add.setObjectName("link")
        add.clicked.connect(lambda: self.add_row(None))
        layout.addWidget(add, alignment=Qt.AlignmentFlag.AlignLeft)

        buttons = QHBoxLayout()
        buttons.addStretch(1)
        cancel = _button("Hủy")
        cancel.clicked.connect(self.reject)
        save = _button("Lưu lịch", primary=True)
        save.clicked.connect(self.save)
        buttons.addWidget(cancel)
        buttons.addWidget(save)
        layout.addLayout(buttons)

    def style_chip(self, chip: QPushButton) -> None:
        chip.setStyleSheet(self.CHIP_ON if chip.isChecked() else self.CHIP)

    def add_row(self, window) -> None:
        container = QFrame()
        container.setObjectName("softCard")
        row = QHBoxLayout(container)
        row.setContentsMargins(13, 11, 11, 11)
        row.setSpacing(8)
        label = QLineEdit(window.label if window else "Học")
        label.setFixedWidth(168)
        label.setPlaceholderText("Nhãn")
        start = QTimeEdit()
        start.setDisplayFormat("HH:mm")
        _field(start, 84)
        end = QTimeEdit()
        end.setDisplayFormat("HH:mm")
        _field(end, 84)
        start.setTime(QTime(8, 0) if window is None else QTime(window.start.hour, window.start.minute))
        end.setTime(QTime(11, 30) if window is None else QTime(window.end.hour, window.end.minute))
        for widget in (label, start, end):
            widget.setFixedHeight(34)
        row.addWidget(label)
        row.addWidget(start)
        dash = QLabel("–")
        dash.setStyleSheet("background:transparent;color:#8b949b;")
        row.addWidget(dash)
        row.addWidget(end)
        row.addSpacing(6)
        days = []
        for index, name in enumerate(("T2", "T3", "T4", "T5", "T6", "T7", "CN")):
            chip = QPushButton(name)
            chip.setCheckable(True)
            chip.setChecked((index < 5) if window is None else index in window.days)
            chip.setCursor(Qt.CursorShape.PointingHandCursor)
            self.style_chip(chip)
            chip.toggled.connect(lambda _=False, c=chip: self.style_chip(c))
            row.addWidget(chip)
            days.append(chip)
        row.addStretch(1)
        delete = _button("Xóa", danger=True)
        delete.setFixedHeight(34)
        delete.clicked.connect(lambda: self.remove_row(container))
        row.addWidget(delete)
        self.rows.append((container, label, start, end, days))
        self.rows_layout.addWidget(container)

    def remove_row(self, container: QWidget) -> None:
        self.rows = [item for item in self.rows if item[0] is not container]
        container.setParent(None)
        container.deleteLater()

    def save(self) -> None:
        windows = []
        for container, label, start, end, days in self.rows:
            chosen = tuple(index for index, box in enumerate(days) if box.isChecked())
            if not chosen:
                QMessageBox.warning(self, "Thiếu ngày", "Mỗi khung giờ cần ít nhất một ngày.")
                return
            start_time = start.time()
            end_time = end.time()
            start_value = time(start_time.hour(), start_time.minute())
            end_value = time(end_time.hour(), end_time.minute())
            if start_value == end_value:
                QMessageBox.warning(self, "Giờ không hợp lệ", "Giờ bắt đầu và kết thúc không được trùng.")
                return
            windows.append(FocusWindow(chosen, start_value, end_value, label.text().strip() or "Focus"))
        candidate = replace(self.approved, windows=tuple(windows))
        store = Store(DB_PATH)
        try:
            write_rules(CONFIG_PATH, candidate)
            result = rules.apply_candidate(store, candidate)
        finally:
            store.close()
        if result.state == "queued":
            QMessageBox.information(
                self,
                "Đã ghi nhận",
                "Thay đổi làm yếu lịch nên sẽ có hiệu lực sau 24 giờ.",
            )
        self.accept()


class PairingDialog(QDialog):
    def __init__(self, parent):
        super().__init__(parent)
        self.setWindowTitle("Kết nối điện thoại")
        self.setModal(True)
        self.setMinimumWidth(450)
        self.issued_at = datetime.now()
        layout = QVBoxLayout(self)
        layout.setContentsMargins(20, 18, 20, 18)
        layout.setSpacing(9)
        layout.addWidget(_label("ĐIỆN THOẠI", "eyebrow"))
        title = QLabel("Kết nối điện thoại")
        title.setObjectName("title")
        title.setStyleSheet("font-size: 20px;")
        layout.addWidget(title)
        layout.addWidget(
            _label(
                "Quét QR hoặc nhập mã 6 số trong app Kỷ luật. Hai máy cùng một Wi-Fi, không cần nhập IP.",
                "body",
            )
        )

        card = QFrame()
        card.setObjectName("softCard")
        card_layout = QVBoxLayout(card)
        card_layout.setContentsMargins(18, 16, 18, 16)
        card_layout.setSpacing(8)
        self.qr_label = QLabel()
        self.qr_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.qr_label.setFixedSize(224, 224)
        self.qr_label.setStyleSheet("background:#ffffff;border:1px solid #e8ebed;border-radius:12px;")
        card_layout.addWidget(self.qr_label, alignment=Qt.AlignmentFlag.AlignCenter)
        self.code_label = QLabel("—")
        self.code_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.code_label.setStyleSheet(
            f"background:transparent;color:{ACCENT};font-size:27px;font-weight:600;letter-spacing:8px;"
        )
        card_layout.addWidget(self.code_label)
        self.status = _label("Mã có hiệu lực 5 phút.", "rowSub")
        self.status.setAlignment(Qt.AlignmentFlag.AlignCenter)
        card_layout.addWidget(self.status)
        layout.addWidget(card)

        buttons = QHBoxLayout()
        buttons.addStretch(1)
        refresh = _button("Làm mới mã", primary=True)
        refresh.clicked.connect(self.issue)
        close = _button("Đóng")
        close.clicked.connect(self.accept)
        buttons.addWidget(refresh)
        buttons.addWidget(close)
        layout.addLayout(buttons)
        self.timer = QTimer(self)
        self.timer.timeout.connect(self.poll)
        self.timer.start(2000)
        self.issue()

    def issue(self) -> None:
        store = Store(DB_PATH)
        try:
            if phone_api.get_token(store) is None:
                phone_api.create_token(store)
            code = pairing.issue_code(store)
        finally:
            store.close()
        if _daemon_pid() is None:
            _start_daemon()
        else:
            _restart_daemon()
        ip = phone_api.lan_ip()
        payload = pairing.qr_payload(ip, phone_api.PHONE_PORT, code)
        pixmap = QPixmap()
        pixmap.loadFromData(pairing.qr_png(payload))
        self.qr_label.setPixmap(pixmap.scaled(212, 212, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation))
        self.code_label.setText(code)
        self.status.setText(f"Đang chờ điện thoại · http://{ip}:{phone_api.PHONE_PORT}")
        self.issued_at = datetime.now()

    def poll(self) -> None:
        store = Store(DB_PATH)
        try:
            success = store.get_state("pair_success_at")
        finally:
            store.close()
        if not success:
            return
        try:
            if datetime.fromisoformat(success) >= self.issued_at:
                self.status.setText("Đã kết nối. Bạn có thể đóng cửa sổ này.")
                self.timer.stop()
        except ValueError:
            pass


class NoPasteEdit(QPlainTextEdit):
    def insertFromMimeData(self, source) -> None:
        return

    def canInsertFromMimeData(self, source) -> bool:
        return False

    def keyPressEvent(self, event) -> None:
        if event.matches(QKeySequence.StandardKey.Paste):
            return
        super().keyPressEvent(event)


class DisableDialog(QDialog):
    READ_SECONDS = 60
    FINAL_SECONDS = 60

    def __init__(self, parent):
        super().__init__(parent)
        self.setWindowTitle("Tắt bảo vệ")
        self.setModal(True)
        self.setMinimumSize(560, 540)
        self.read_left = self.READ_SECONDS
        self.final_left = self.FINAL_SECONDS
        self.stack = QStackedWidget()
        layout = QVBoxLayout(self)
        layout.setContentsMargins(20, 18, 20, 18)
        layout.addWidget(self.stack)
        self.stack.addWidget(self._warning_page())
        self.stack.addWidget(self._paragraph_page())
        self.stack.addWidget(self._final_page())
        self.timer = QTimer(self)
        self.timer.timeout.connect(self.tick)
        self.timer.start(1000)

    def _warning_page(self) -> QWidget:
        page = QWidget()
        layout = QVBoxLayout(page)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(9)
        layout.addWidget(_section("Thay đổi lớn"))
        title = QLabel("Tắt bảo vệ?")
        title.setObjectName("title")
        title.setStyleSheet("font-size: 22px;")
        layout.addWidget(title)
        layout.addWidget(
            _label(
                "Khi tắt, app ngừng chặn ứng dụng và web; quỹ mạng xã hội, cảnh báo và chuỗi ngày "
                "sẽ không còn tác dụng cho đến khi bật lại.",
                "body",
            )
        )
        card = QFrame()
        card.setObjectName("softCard")
        card_layout = QVBoxLayout(card)
        card_layout.setContentsMargins(15, 13, 15, 13)
        card_layout.setSpacing(6)
        for text in (
            "Không còn cảnh báo khi mở app gây xao nhãng.",
            "Quỹ mạng xã hội hôm nay của điện thoại bị hủy.",
            "Bạn có thể bật lại bất cứ lúc nào, nhưng chuỗi ngày có thể bị gián đoạn.",
        ):
            card_layout.addWidget(_label("•  " + text, "body"))
        layout.addWidget(card)
        self.read_label = _label("", "rowSub")
        layout.addWidget(self.read_label)
        stay = _button("Ở lại bảo vệ", primary=True, big=True)
        stay.clicked.connect(self.reject)
        self.continue_button = _button("Tiếp tục tắt", danger=True, big=True)
        self.continue_button.setEnabled(False)
        self.continue_button.clicked.connect(lambda: self.stack.setCurrentIndex(1))
        layout.addWidget(stay)
        layout.addWidget(self.continue_button)
        layout.addWidget(
            _label(
                "Khẩn cấp? Bạn luôn có thể tắt quyền trợ năng của app trong Cài đặt hệ thống.",
                "rowSub",
            )
        )
        layout.addStretch(1)
        return page

    def _paragraph_page(self) -> QWidget:
        page = QWidget()
        layout = QVBoxLayout(page)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(8)
        layout.addWidget(_section("Bước 2 / 3"))
        title = QLabel("Nhập đúng đoạn cam kết")
        title.setObjectName("title")
        title.setStyleSheet("font-size: 19px;")
        layout.addWidget(title)
        layout.addWidget(_label("Sai một chữ cũng phải làm lại. Không hỗ trợ dán.", "body"))
        quote = QLabel(PARAGRAPH)
        quote.setWordWrap(True)
        quote.setTextInteractionFlags(Qt.TextInteractionFlag.NoTextInteraction)
        quote.setStyleSheet(
            f"background:{DANGER_SOFT};border:1px solid {DANGER_LINE};border-radius:12px;"
            f"padding:11px 13px;font-size:13px;color:{INK_2};"
        )
        layout.addWidget(quote)
        self.paragraph_edit = NoPasteEdit()
        self.paragraph_edit.setPlaceholderText("Nhập đoạn cam kết ở trên…")
        self.paragraph_edit.setMinimumHeight(112)
        self.paragraph_edit.textChanged.connect(self.validate_paragraph)
        layout.addWidget(self.paragraph_edit)
        self.counter = _label("0 / %d ký tự" % len(PARAGRAPH), "rowSub")
        layout.addWidget(self.counter)
        buttons = QHBoxLayout()
        buttons.setSpacing(9)
        back = _button("Quay lại", big=True)
        back.clicked.connect(lambda: self.stack.setCurrentIndex(0))
        self.disable_button = _button("Tắt bảo vệ", danger=True, big=True)
        self.disable_button.setEnabled(False)
        self.disable_button.clicked.connect(lambda: self.stack.setCurrentIndex(2))
        buttons.addWidget(back)
        buttons.addWidget(self.disable_button, 1)
        layout.addLayout(buttons)
        layout.addStretch(1)
        return page

    def _final_page(self) -> QWidget:
        page = QWidget()
        layout = QVBoxLayout(page)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(9)
        layout.addWidget(_section("Bước 3 / 3"))
        title = QLabel("Còn 60 giây để đổi ý")
        title.setObjectName("title")
        title.setStyleSheet("font-size: 20px;")
        layout.addWidget(title)
        layout.addWidget(
            _label("Sau khi hết giờ, bảo vệ sẽ tắt. Bạn có thể bật lại bất cứ lúc nào.", "body")
        )
        self.final_label = QLabel("60")
        self.final_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.final_label.setStyleSheet(f"color:{DANGER};font-size:46px;font-weight:600;")
        layout.addWidget(self.final_label)
        cancel = _button("Đổi ý", primary=True, big=True)
        cancel.clicked.connect(self.reject)
        layout.addWidget(cancel)
        layout.addStretch(1)
        return page

    def tick(self) -> None:
        index = self.stack.currentIndex()
        if index == 0:
            self.read_left -= 1
            if self.read_left > 0:
                self.read_label.setText(f"Đọc kỹ trước khi tiếp tục · còn {self.read_left} giây")
            else:
                self.read_label.setText("Bạn có thể tiếp tục khi đã đọc xong.")
                self.continue_button.setEnabled(True)
        elif index == 2:
            self.final_left -= 1
            self.final_label.setText(str(max(0, self.final_left)))
            if self.final_left <= 0:
                self.timer.stop()
                self.perform_off()

    def validate_paragraph(self) -> None:
        text = self.paragraph_edit.toPlainText().strip()
        self.counter.setText(f"{len(text)} / {len(PARAGRAPH)} ký tự")
        self.disable_button.setEnabled(text == PARAGRAPH)

    def _wait_until_off(self, seconds: int = 20) -> bool:
        """Tiến trình nâng quyền không còn cửa sổ riêng để nhìn, nên đọc lại DB và task mà chờ."""
        timer = QElapsedTimer()
        timer.start()
        while True:
            store = Store(DB_PATH)
            try:
                net_enabled = store.get_state(NET_ENABLED_KEY) == "1"
            finally:
                store.close()
            if not net_enabled and not _task_status().startswith("có") and _daemon_pid() is None:
                return True
            if timer.elapsed() >= seconds * 1000:
                return False
            QApplication.processEvents()
            QThread.msleep(400)

    def perform_off(self) -> None:
        store = Store(DB_PATH)
        try:
            net_enabled = store.get_state(NET_ENABLED_KEY) == "1"
            store.del_state(sessions.SESSION_KEY)
        finally:
            store.close()
        task_installed = _task_status().startswith("có")
        if net_enabled or task_installed:
            params = "turn-off" if FROZEN else f'"{ROOT / "run_enforcer.pyw"}" turn-off'
            rc = ctypes.windll.shell32.ShellExecuteW(None, "runas", sys.executable, params, str(ROOT), 1)
            if rc <= 32:
                QMessageBox.warning(
                    self,
                    "Cần quyền Administrator",
                    "Không mở được UAC. Hãy chạy PowerShell Admin:\n" f"{CLI_HINT} turn-off",
                )
                self.reject()
                return
        else:
            pid = _daemon_pid()
            if pid is not None:
                try:
                    psutil.Process(pid).terminate()
                except psutil.Error:
                    pass
            proc.run(["schtasks", "/Delete", "/F", "/TN", TASK_NAME], capture_output=True)
        self.final_label.setText("Đang tắt…")
        self.final_label.setStyleSheet(f"color:{INK_2};font-size:20px;font-weight:600;")
        if not self._wait_until_off():
            QMessageBox.warning(
                self,
                "Chưa tắt được",
                "Sau 20 giây máy vẫn còn dấu vết bảo vệ: DNS, task hoặc daemon.\n"
                "Mở PowerShell Administrator rồi chạy:\n"
                f"{CLI_HINT} turn-off\n"
                "Chi tiết nằm trong data\\turnoff.log.",
            )
            self.reject()
            return
        QMessageBox.information(self, "Đã tắt bảo vệ", "Bảo vệ đã được tắt. Bạn có thể bật lại bằng cách chạy app và bật lại.")
        self.accept()


SETUP_KEY = "desktop_setup_done"
SETUP_MIGRATED_KEY = "desktop_setup_migrated"


def start_net_install(parent) -> bool:
    params = "net install" if FROZEN else f'"{ROOT / "run_enforcer.pyw"}" net install'
    rc = ctypes.windll.shell32.ShellExecuteW(None, "runas", sys.executable, params, str(ROOT), 1)
    return rc > 32


class SetupDialog(QDialog):
    DAYS = ("T2", "T3", "T4", "T5", "T6", "T7", "CN")
    CHIP = (
        "QPushButton{background:#f7f8f9;border:1px solid #e8ebed;border-radius:8px;"
        "min-width:38px;padding:6px 4px;color:#5c666f;font-size:12.5px;}"
    )
    CHIP_ON = (
        "QPushButton{background:#edf3f0;border:1px solid #d5e4dd;border-radius:8px;"
        "min-width:38px;padding:6px 4px;color:#2f6f5e;font-weight:600;font-size:12.5px;}"
    )

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Thiết lập Kỷ luật")
        self.setWindowIcon(make_icon())
        self.setWindowModality(Qt.WindowModality.ApplicationModal)
        self.setMinimumSize(1020, 700)
        self.resize(1120, 760)
        screen = QApplication.primaryScreen()
        if screen is not None:
            area = screen.availableGeometry()
            self.move(area.center() - self.rect().center())
        self.step = 0
        self.web_pending = False
        self.web_polls = 0
        self._load()
        self._build()
        self._render()
        self.timer = QTimer(self)
        self.timer.timeout.connect(self._poll_web)
        self.timer.start(2000)

    # ---------- state ----------

    def _load(self) -> None:
        store = Store(DB_PATH)
        try:
            self.approved = rules.approved_config(store)
            self.processes = set(self.approved.processes) if self.approved else set()
            self.domains = set(self.approved.web_domains) if self.approved else set()
            self.windows = list(self.approved.windows) if self.approved else []
            self.adult = bool(self.approved.adult_block) if self.approved else False
            self.web_on = store.get_state(NET_ENABLED_KEY) == "1"
            self.paired = bool(store.get_state("pair_success_at"))
        finally:
            store.close()
        plan = load_plan(CONFIG_PATH)
        self.study = plan.study_minutes_daily
        self.gym = plan.gym_sessions_weekly
        first = self.windows[0] if self.windows else FocusWindow((0, 1, 2, 3, 4), time(8, 0), time(11, 30), "Khung giờ 1")
        self.days = set(first.days)
        self.start = first.start
        self.end = first.end
        self.schedule_on = True
        covered = {process for entry in catalog.APP_CATALOG for process in entry["processes"]}
        denied = {
            "systemsettings.exe", "textinputhost.exe", "searchapp.exe", "shellexperiencehost.exe",
            "applicationframehost.exe", "lockapp.exe", "taskmgr.exe", "mmc.exe", "control.exe",
        }
        running = [entry for entry in running_apps(covered) if entry["processes"][0] not in denied]
        seen_labels = {entry["name"].strip().lower() for entry in catalog.APP_CATALOG}
        extras = []
        for entry in running:
            label = entry["name"].strip().lower()
            if label in seen_labels:
                continue
            seen_labels.add(label)
            extras.append(entry)
        self.app_entries = list(catalog.APP_CATALOG) + extras[:10]
        known = {process for entry in self.app_entries for process in entry["processes"]}
        for exe in sorted(self.processes - known):
            self.app_entries.append(
                {"name": catalog.display_block(exe), "category": "Tự thêm", "processes": (exe,)}
            )
        self.site_entries = list(catalog.WEB_CATALOG)
        covered_domains = {domain for entry in self.site_entries for domain in entry["domains"]}
        for domain in sorted(self.domains - covered_domains):
            self.site_entries.append({"name": domain, "category": "Tự thêm", "domains": (domain,)})

    def _lowered_processes(self) -> set[str]:
        return {name.lower() for name in self.processes}

    def _lowered_domains(self) -> set[str]:
        return {name.lower() for name in self.domains}

    def _app_checked(self, entry: dict) -> bool:
        lowered = self._lowered_processes()
        return any(process.lower() in lowered for process in entry["processes"])

    def _site_checked(self, entry: dict) -> bool:
        lowered = self._lowered_domains()
        return any(domain.lower() in lowered for domain in entry["domains"])

    def _selection_count(self) -> int:
        return sum(1 for entry in self.app_entries if self._app_checked(entry)) + sum(
            1 for entry in self.site_entries if self._site_checked(entry)
        )

    # ---------- layout ----------

    def _build(self) -> None:
        layout = QHBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)

        rail = QWidget()
        rail.setObjectName("rail")
        rail.setFixedWidth(212)
        rail_box = QVBoxLayout(rail)
        rail_box.setContentsMargins(13, 17, 13, 14)
        rail_box.setSpacing(3)
        brand = QLabel("Kỷ luật")
        brand.setStyleSheet("background:transparent;font-size:16px;font-weight:600;padding:2px 9px 0;")
        rail_box.addWidget(brand)
        sub = QLabel("Thiết lập lần đầu")
        sub.setStyleSheet(f"background:transparent;color:{INK_3};font-size:11px;padding:1px 9px 15px;")
        rail_box.addWidget(sub)
        self.step_group = QButtonGroup(self)
        self.step_group.setExclusive(True)
        for index, name in enumerate(("Chào", "Mục chặn", "Lịch tập trung", "Mục tiêu", "Tuỳ chọn", "Tóm tắt")):
            button = QPushButton(f"{index + 1}   {name}")
            button.setObjectName("nav")
            button.setCheckable(True)
            button.clicked.connect(lambda _=False, i=index: self.go(i))
            self.step_group.addButton(button, index)
            rail_box.addWidget(button)
        rail_box.addStretch(1)
        foot = _label("Bước mục chặn là bắt buộc. Các bước còn lại bỏ qua được. Mở lại được từ Cài đặt.", "rowSub")
        rail_box.addWidget(foot)
        layout.addWidget(rail)

        main = QVBoxLayout()
        main.setContentsMargins(30, 22, 30, 16)
        main.setSpacing(12)
        self.scroll = QScrollArea()
        self.scroll.setWidgetResizable(True)
        content = QWidget()
        self.body = QVBoxLayout(content)
        self.body.setContentsMargins(0, 0, 10, 0)
        self.body.setSpacing(9)
        self.body.setSizeConstraint(QLayout.SizeConstraint.SetMinimumSize)
        self.scroll.setWidget(content)
        main.addWidget(self.scroll, 1)

        bar = QHBoxLayout()
        bar.setSpacing(10)
        self.hint = _label("", "rowSub")
        self.hint.setWordWrap(True)
        bar.addWidget(self.hint, 1)
        self.skip_button = _button("Bỏ qua")
        self.skip_button.clicked.connect(self._skip)
        bar.addWidget(self.skip_button)
        self.primary = _button("Tiếp tục", primary=True, big=True)
        self.primary.setMinimumWidth(170)
        self.primary.clicked.connect(self._next)
        bar.addWidget(self.primary)
        main.addLayout(bar)
        layout.addLayout(main, 1)

    def _heading(self, eyebrow: str, title: str, lede: str, big: bool = False) -> None:
        self.body.addWidget(_label(eyebrow.upper(), "eyebrow"))
        label = QLabel(title)
        label.setObjectName("title")
        label.setWordWrap(True)
        if big:
            label.setStyleSheet("font-size:26px;font-weight:600;")
        self.body.addWidget(label)
        text = _label(lede, "body")
        text.setContentsMargins(0, 0, 0, 8)
        self.body.addWidget(text)

    def _card(self) -> tuple[QFrame, QVBoxLayout]:
        frame = QFrame()
        frame.setObjectName("card")
        box = QVBoxLayout(frame)
        box.setContentsMargins(17, 15, 17, 13)
        box.setSpacing(0)
        return frame, box

    def _columns(self, left: QWidget, right: QWidget) -> None:
        row = QHBoxLayout()
        row.setSpacing(14)
        row.setContentsMargins(0, 6, 0, 0)
        row.addWidget(left, 1)
        row.addWidget(right, 1)
        self.body.addLayout(row)

    def _clear(self, layout) -> None:
        while layout.count():
            item = layout.takeAt(0)
            widget = item.widget()
            if widget is not None:
                widget.setParent(None)
                widget.deleteLater()
            elif item.layout() is not None:
                self._clear(item.layout())

    def _render(self, keep_scroll: bool = False) -> None:
        position = self.scroll.verticalScrollBar().value() if keep_scroll else 0
        self._clear(self.body)
        if self.step in (0, 6):
            self.body.addStretch(1)
        [self._welcome, self._pick, self._schedule, self._goals, self._optional, self._summary, self._done][self.step]()
        self.body.addStretch(1)
        self.step_group.button(min(self.step, 5)).setChecked(True)
        self._update_actions()
        QTimer.singleShot(0, lambda: self.scroll.verticalScrollBar().setValue(position))

    # ---------- steps ----------

    def _welcome(self) -> None:
        self._heading(
            "Bắt đầu",
            "Ba phút để máy giữ luật hộ bạn",
            "Bạn chọn thứ cần tránh và giờ cần tập trung. Kỷ luật lo phần còn lại, chạy hoàn toàn trên máy này.",
            big=True,
        )
        left, left_box = self._card()
        left_box.addWidget(_tile(icon="shield", tone="accent", size=44))
        title = QLabel("Bạn chọn, máy không đoán")
        title.setStyleSheet("font-size:16px;font-weight:600;padding-top:12px;")
        left_box.addWidget(title)
        left_box.addWidget(_label("Không có app nào bị chặn sẵn. Danh sách do bạn viết ra và sửa được bất cứ lúc nào.", "body"))
        left.setMinimumHeight(150)
        right, right_box = self._card()
        right_box.addWidget(_tile(icon="calendar", tone="neutral", size=44))
        title2 = QLabel("Thêm ràng buộc ngay, nới lỏng phải chờ")
        title2.setStyleSheet("font-size:16px;font-weight:600;padding-top:12px;")
        title2.setWordWrap(True)
        right_box.addWidget(title2)
        right_box.addWidget(_label("Thêm app hay giờ thì có hiệu lực ngay. Bỏ ra thì phải chờ 24 giờ, riêng công tắc 18+ chờ 60 giây.", "body"))
        right.setMinimumHeight(150)
        self._columns(left, right)
        self.body.addWidget(_note("Bước chọn mục chặn là bắt buộc. Lịch, mục tiêu, chặn web và ghép điện thoại đều bỏ qua được.", "info"))

    def _pick(self) -> None:
        self._heading(
            "Bước 1 / 5 · Bắt buộc",
            "Chọn thứ bạn muốn máy giữ hộ",
            "Chỉ mục bạn gạt mới bị chặn. App và trang web ngoài danh sách không bị đụng tới.",
        )
        apps, apps_box = self._card()
        head = QHBoxLayout()
        apps_title = QLabel("Ứng dụng")
        apps_title.setObjectName("rowTitle")
        self.apps_count = _label("0 đã chọn", "rowSub")
        head.addWidget(apps_title)
        head.addStretch(1)
        head.addWidget(self.apps_count)
        apps_box.addLayout(head)
        apps_box.addWidget(_hairline())
        for entry in self.app_entries:
            checked = self._app_checked(entry)
            row = Row(
                entry["name"],
                entry.get("category", "Đang chạy"),
                _tile(text=entry["name"], tone="accent" if checked else "neutral"),
            )
            switch = Switch()
            switch.setChecked(checked)
            switch.toggled.connect(lambda value, e=entry: self._toggle_app(e, value))
            row.add(switch)
            row.setMinimumHeight(56)
            apps_box.addWidget(row)
            apps_box.addWidget(_hairline())
        apps_box.addWidget(_label("Danh sách lấy từ app đang chạy và danh mục; thêm app khác ở tab Bảo vệ.", "rowSub"))

        sites, sites_box = self._card()
        site_head = QHBoxLayout()
        sites_title = QLabel("Trang web")
        sites_title.setObjectName("rowTitle")
        self.sites_count = _label("0 đã chọn", "rowSub")
        site_head.addWidget(sites_title)
        site_head.addStretch(1)
        site_head.addWidget(self.sites_count)
        sites_box.addLayout(site_head)
        sites_box.addWidget(_hairline())
        for entry in self.site_entries:
            checked = self._site_checked(entry)
            row = Row(
                entry["name"],
                entry.get("category", "Tự thêm"),
                _tile(text=entry["name"], tone="accent" if checked else "neutral"),
            )
            switch = Switch()
            switch.setChecked(checked)
            switch.toggled.connect(lambda value, e=entry: self._toggle_site(e, value))
            row.add(switch)
            row.setMinimumHeight(56)
            sites_box.addWidget(row)
            sites_box.addWidget(_hairline())
        add_row = QHBoxLayout()
        add_row.setContentsMargins(0, 10, 0, 0)
        self.site_input = QLineEdit()
        self.site_input.setPlaceholderText("Thêm tên miền, vd: twitch.tv")
        self.site_input.returnPressed.connect(self._add_domain)
        add_row.addWidget(self.site_input, 1)
        add_button = _button("Thêm")
        add_button.clicked.connect(self._add_domain)
        add_row.addWidget(add_button)
        sites_box.addLayout(add_row)

        self._columns(apps, sites)
        self.body.addWidget(_note("Cuộc gọi, Tin nhắn và Cài đặt không bao giờ bị chặn, kể cả khi bạn chọn.", "shield"))
        self._refresh_counts()

    def _toggle_app(self, entry: dict, checked: bool) -> None:
        for process in entry["processes"]:
            if checked:
                self.processes.add(process)
            else:
                self.processes.discard(process)
        self._refresh_counts()

    def _toggle_site(self, entry: dict, checked: bool) -> None:
        for domain in entry["domains"]:
            if checked:
                self.domains.add(domain)
            else:
                self.domains.discard(domain)
        self._refresh_counts()

    def _refresh_counts(self) -> None:
        try:
            self.apps_count.setText(f"{sum(1 for e in self.app_entries if self._app_checked(e))} đã chọn")
            self.sites_count.setText(f"{sum(1 for e in self.site_entries if self._site_checked(e))} đã chọn")
        except (AttributeError, RuntimeError):
            pass
        self._update_actions()

    def _add_domain(self) -> None:
        text = self.site_input.text().strip().lower()
        text = re.sub(r"^https?://", "", text).split("/")[0].strip(".")
        if not text or "." not in text or " " in text:
            QMessageBox.warning(
                self, "Tên miền chưa hợp lệ", "Nhập tên miền như twitch.tv, không kèm https:// hay đường dẫn."
            )
            return
        known = {domain.lower() for entry in self.site_entries for domain in entry["domains"]}
        if text in known:
            return
        self.domains.add(text)
        self.site_entries.append({"name": text, "category": "Tự thêm", "domains": (text,)})
        self._render(keep_scroll=True)

    def _schedule(self) -> None:
        self._heading(
            "Bước 2 / 5",
            "Khung giờ đầu tiên",
            "Đúng giờ là app trong danh sách bị đóng, hết giờ là mở lại. Có thể thêm khung giờ sau ở tab Bảo vệ.",
        )
        card, box = self._card()
        head = QHBoxLayout()
        title = QLabel("Khung giờ 1")
        title.setObjectName("rowTitle")
        switch = Switch()
        switch.setChecked(self.schedule_on)
        switch.toggled.connect(self._set_schedule_on)
        head.addWidget(title)
        head.addStretch(1)
        head.addWidget(switch)
        box.addLayout(head)
        box.addWidget(_label("Chọn ngày áp dụng", "rowSub"))
        days_row = QHBoxLayout()
        days_row.setSpacing(5)
        self.day_buttons = []
        for index, name in enumerate(self.DAYS):
            chip = QPushButton(name)
            chip.setCheckable(True)
            chip.setChecked(index in self.days)
            chip.setCursor(Qt.CursorShape.PointingHandCursor)
            chip.setFixedHeight(30)
            self._style_day(chip)
            chip.toggled.connect(lambda _=False, c=chip: self._style_day(c))
            chip.toggled.connect(lambda value, i=index: self._toggle_day(i, value))
            days_row.addWidget(chip)
            self.day_buttons.append(chip)
        days_row.addStretch(1)
        box.addLayout(days_row)
        time_row = QHBoxLayout()
        time_row.setContentsMargins(0, 12, 0, 0)
        start = QTimeEdit()
        start.setDisplayFormat("HH:mm")
        start.setTime(QTime(self.start.hour, self.start.minute))
        _field(start, 96)
        end = QTimeEdit()
        end.setDisplayFormat("HH:mm")
        end.setTime(QTime(self.end.hour, self.end.minute))
        _field(end, 96)
        start.timeChanged.connect(lambda value: setattr(self, "start", time(value.hour(), value.minute())))
        end.timeChanged.connect(lambda value: setattr(self, "end", time(value.hour(), value.minute())))
        time_row.addWidget(start)
        time_row.addWidget(_label("đến", "rowSub"))
        time_row.addWidget(end)
        time_row.addWidget(_label(f"{len(self.days)} ngày mỗi tuần", "rowSub"))
        time_row.addStretch(1)
        box.addLayout(time_row)
        self.body.addWidget(card)
        self.body.addWidget(
            _note(
                "Thêm khung giờ áp dụng ngay. Bỏ một khung giờ phải chờ 24 giờ. "
                "Lịch để trống thì chỉ luật app và web 18+ chạy.",
                "clock",
            )
        )
        if len(self.windows) > 1:
            self.body.addWidget(
                _note("Các khung giờ khác giữ nguyên; sửa đầy đủ ở tab Bảo vệ.", "info")
            )

    def _style_day(self, chip: QPushButton) -> None:
        chip.setStyleSheet(self.CHIP_ON if chip.isChecked() else self.CHIP)

    def _toggle_day(self, index: int, checked: bool) -> None:
        if checked:
            self.days.add(index)
        else:
            self.days.discard(index)
        self._update_actions()

    def _set_schedule_on(self, checked: bool) -> None:
        self.schedule_on = checked
        self._update_actions()

    def _stepper(self, value: int, minimum: int, maximum: int, step: int, unit: str, on_change) -> QWidget:
        holder = QWidget()
        row = QHBoxLayout(holder)
        row.setContentsMargins(0, 12, 0, 0)
        row.setSpacing(8)
        button_style = (
            "QPushButton{background:#ffffff;border:1px solid #e8ebed;border-radius:9px;"
            "font-size:16px;color:#5c666f;padding:0;}"
            "QPushButton:hover{background:#f7f8f9;border-color:#c8d0d5;}"
        )
        minus = QPushButton("−")
        minus.setFixedSize(32, 32)
        minus.setCursor(Qt.CursorShape.PointingHandCursor)
        minus.setStyleSheet(button_style)
        value_label = QLabel(str(value))
        value_label.setMinimumWidth(46)
        value_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        value_label.setStyleSheet("font-size:22px;font-weight:600;background:transparent;")
        unit_label = _label(unit, "rowSub")
        plus = QPushButton("+")
        plus.setFixedSize(32, 32)
        plus.setCursor(Qt.CursorShape.PointingHandCursor)
        plus.setStyleSheet(button_style)
        current = [value]

        def change(delta: int) -> None:
            current[0] = max(minimum, min(maximum, current[0] + delta))
            value_label.setText(str(current[0]))
            on_change(current[0])
            self._update_actions()

        minus.clicked.connect(lambda: change(-step))
        plus.clicked.connect(lambda: change(step))
        row.addWidget(minus)
        row.addWidget(value_label)
        row.addWidget(unit_label)
        row.addWidget(plus)
        row.addStretch(1)
        return holder

    def _goals(self) -> None:
        self._heading(
            "Bước 3 / 5",
            "Mục tiêu của bạn",
            "Hai con số này chỉ để nhìn lại mỗi tuần, không tham gia vào việc chặn app.",
        )
        left, left_box = self._card()
        title = QLabel("Học mỗi ngày")
        title.setObjectName("rowTitle")
        left_box.addWidget(title)
        left_box.addWidget(self._stepper(self.study, 0, 720, 15, "phút/ngày", lambda value: setattr(self, "study", value)))
        left_box.addWidget(_label("Từ 0 đến 720 phút, mỗi lần 15 phút.", "rowSub"))

        right, right_box = self._card()
        title2 = QLabel("Tập gym")
        title2.setObjectName("rowTitle")
        right_box.addWidget(title2)
        right_box.addWidget(self._stepper(self.gym, 0, 14, 1, "buổi/tuần", lambda value: setattr(self, "gym", value)))
        right_box.addWidget(_label("Một buổi cần 10 phút trong app tập.", "rowSub"))
        self._columns(left, right)
        self.body.addWidget(_note("Không đặt mục tiêu cũng được. Đổi lại bất cứ lúc nào ở tab Cài đặt.", "info"))

    def _optional(self) -> None:
        self._heading(
            "Bước 4 / 5",
            "Hai thứ nên bật, nhưng không bắt buộc",
            "Bỏ qua được, bật sau ở tab Bảo vệ hoặc Cài đặt.",
        )
        web, web_box = self._card()
        head = QHBoxLayout()
        head.addWidget(_tile(icon="globe", tone="accent", size=34))
        web_title = QLabel("Chặn web bằng DNS")
        web_title.setObjectName("rowTitle")
        head.addWidget(web_title)
        head.addStretch(1)
        web_pill = Pill("Đang bật" if self.web_on else "Chưa bật", "accent" if self.web_on else "neutral", dot=False)
        head.addWidget(web_pill)
        web_box.addLayout(head)
        web_box.addWidget(_label("Đổi DNS card mạng sang 127.0.0.1 và tắt DoH của Chrome/Edge. Cần quyền Administrator qua UAC.", "body"))
        adult_row = QHBoxLayout()
        adult_row.setContentsMargins(0, 8, 0, 8)
        adult_row.addWidget(_label("Chặn trang người lớn (lọc DNS)", "body"), 1)
        adult_switch = Switch()
        adult_switch.setChecked(self.adult)
        adult_switch.toggled.connect(self._set_adult)
        adult_row.addWidget(adult_switch)
        web_box.addLayout(adult_row)
        web_button = _button("Đang bật" if self.web_on else "Bật chặn web")
        web_button.setEnabled(not self.web_on and not self.web_pending)
        if self.web_pending:
            web_button.setText("Đang bật…")
        web_button.clicked.connect(self._toggle_web)
        web_box.addWidget(web_button, alignment=Qt.AlignmentFlag.AlignLeft)

        pair, pair_box = self._card()
        pair_head = QHBoxLayout()
        pair_head.addWidget(_tile(icon="desktop", tone="neutral", size=34))
        pair_title = QLabel("Ghép điện thoại")
        pair_title.setObjectName("rowTitle")
        pair_head.addWidget(pair_title)
        pair_head.addStretch(1)
        pair_head.addWidget(Pill("Đã ghép" if self.paired else "Chưa ghép", "accent" if self.paired else "neutral", dot=False))
        pair_box.addLayout(pair_head)
        pair_box.addWidget(_label("Máy tính và điện thoại nhìn chung lịch học, lịch tập và trạng thái phiên.", "body"))
        pair_box.addStretch(1)
        pair_button = _button("Ghép lại" if self.paired else "Mở mã ghép")
        pair_button.clicked.connect(self._open_pairing)
        pair_box.addWidget(pair_button, alignment=Qt.AlignmentFlag.AlignLeft)
        self._columns(web, pair)

        if self.adult and not self.web_on:
            self.body.addWidget(
                _note("Luật 18+ cần chặn web đang bật. Tắt bảo vệ sẽ trả DNS về cũ; danh sách 81 tên miền chạy suốt ngày đêm.", "alert", "warn")
            )
        else:
            self.body.addWidget(_note("Cả hai đều tắt được bất cứ lúc nào. Tắt chặn web sẽ khôi phục đúng DNS cũ.", "info"))

    def _set_adult(self, checked: bool) -> None:
        if checked and not self.web_on:
            answer = QMessageBox.question(
                self,
                "Cần chặn web",
                "Luật 18+ chỉ chạy khi chặn web đang bật. Bật chặn web ngay bằng quyền Administrator?",
                QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.Cancel,
                QMessageBox.StandardButton.Yes,
            )
            if answer != QMessageBox.StandardButton.Yes:
                if self.step == 4:
                    self._render()
                return
            self.adult = True
            self._toggle_web()
            return
        self.adult = checked

    def _toggle_web(self) -> None:
        if self.web_on:
            QMessageBox.information(self, "Đang bật", "Tắt chặn web bằng 'Tắt bảo vệ' ở tab Bảo vệ để trả DNS về cũ.")
            return
        if self.web_pending:
            return
        answer = QMessageBox.question(
            self,
            "Bật chặn web?",
            "Windows sẽ hỏi quyền Administrator. Kỷ luật sẽ đổi DNS card mạng sang 127.0.0.1, "
            "tắt DoH của Chrome và Edge, và trả DNS về cũ khi bạn tắt bảo vệ.",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.Cancel,
            QMessageBox.StandardButton.Yes,
        )
        if answer != QMessageBox.StandardButton.Yes:
            return
        if not start_net_install(self):
            QMessageBox.warning(
                self,
                "Chưa bật được chặn web",
                "UAC bị hủy hoặc Windows không mở được tiến trình quản trị.\n"
                f"Hãy chạy PowerShell Administrator: {CLI_HINT} net install",
            )
            return
        self.web_pending = True
        self.web_polls = 0
        if self.step == 4:
            self._render()

    def _poll_web(self) -> None:
        if not self.web_pending:
            return
        self.web_polls += 1
        store = Store(DB_PATH)
        try:
            enabled = store.get_state(NET_ENABLED_KEY) == "1"
        finally:
            store.close()
        if enabled:
            self.web_pending = False
            self.web_on = True
            if self.step == 4:
                self._render()
            return
        if self.web_polls >= 15:
            self.web_pending = False
            if self.step == 4:
                self._render()
            QMessageBox.warning(
                self,
                "Chưa bật được chặn web",
                f"Không thấy DNS chuyển sang 127.0.0.1. Chạy PowerShell Administrator: {CLI_HINT} net install",
            )

    def _open_pairing(self) -> None:
        PairingDialog(self).exec()
        store = Store(DB_PATH)
        try:
            self.paired = bool(store.get_state("pair_success_at"))
        finally:
            store.close()
        if self.step == 4:
            self._render()

    def _summary(self) -> None:
        self._heading(
            "Bước 5 / 5",
            "Sẵn sàng giữ luật?",
            "Kiểm lại một lần. Mọi thứ đổi được sau trong app, nhưng nới lỏng phải chờ.",
        )
        card, box = self._card()
        rows = [
            ("App và trang web", f"{self._selection_count()} mục", 1),
            ("Lịch tập trung", self._schedule_text(), 2),
            ("Mục tiêu", f"{self.study} phút/ngày · {self.gym} buổi/tuần", 3),
            ("Chặn web", "Bật" if self.web_on else "Tắt", 4),
            ("Lọc 18+", "Bật" if self.adult else "Tắt", 4),
            ("Điện thoại", "Đã ghép" if self.paired else "Chưa ghép", 4),
        ]
        for index, (label, value, target) in enumerate(rows):
            if index:
                box.addWidget(_hairline())
            line = QHBoxLayout()
            line.setContentsMargins(0, 9, 0, 9)
            line.setSpacing(10)
            line.addWidget(_label(label, "body"), 1)
            value_label = QLabel(value)
            value_label.setObjectName("rowTitle")
            value_label.setAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)
            line.addWidget(value_label)
            edit = _button("Sửa")
            edit.setObjectName("link")
            edit.clicked.connect(lambda _=False, t=target: self.go(t))
            line.addWidget(edit)
            box.addLayout(line)
        self.body.addWidget(card)
        if self.adult and not self.web_on:
            self.body.addWidget(_note("Luật 18+ đang bật nhưng chặn web chưa bật nên chưa có tác dụng.", "alert", "warn"))
        else:
            self.body.addWidget(_note("Dữ liệu nằm trên máy bạn. Không tài khoản, không máy chủ.", "info"))

    def _schedule_text(self) -> str:
        if not self.schedule_on or not self.days:
            return "Khung giờ 1 đang tắt" if self.windows else "Chưa có lịch"
        days = ", ".join(self.DAYS[index] for index in sorted(self.days))
        return f"{days} · {self.start:%H:%M}–{self.end:%H:%M}"

    def _done(self) -> None:
        circle = QLabel()
        circle.setFixedSize(64, 64)
        circle.setAlignment(Qt.AlignmentFlag.AlignCenter)
        circle.setStyleSheet("background:#edf3f0;border:1px solid #d5e4dd;border-radius:32px;")
        circle.setPixmap(svg_pixmap("steps", ACCENT, 30))
        self.body.addWidget(circle, alignment=Qt.AlignmentFlag.AlignHCenter)
        title = QLabel("Thiết lập xong")
        title.setObjectName("title")
        title.setStyleSheet("font-size:26px;font-weight:600;")
        title.setAlignment(Qt.AlignmentFlag.AlignHCenter)
        self.body.addWidget(title)
        body = _label(
            "Wizard sẽ đóng và mở tab Hôm nay. Tắt bảo vệ cần đọc cảnh báo, gõ cam kết và chờ 60 giây.",
            "body",
        )
        body.setAlignment(Qt.AlignmentFlag.AlignHCenter)
        self.body.addWidget(body)
        pills = QHBoxLayout()
        pills.setAlignment(Qt.AlignmentFlag.AlignHCenter)
        pills.addWidget(Pill(f"{self._selection_count()} mục bị chặn", "accent", dot=False))
        pills.addWidget(Pill(f"{self.study} phút học/ngày", "accent", dot=False))
        if self.web_on:
            pills.addWidget(Pill("Chặn web đang bật", "accent", dot=False))
        self.body.addLayout(pills)

    # ---------- actions ----------

    def _update_actions(self) -> None:
        labels = {0: "Bắt đầu", 1: "Tiếp tục", 2: "Tiếp tục", 3: "Tiếp tục", 4: "Tiếp tục", 5: "Hoàn tất", 6: "Vào Kỷ luật"}
        self.primary.setText(labels[self.step])
        self.skip_button.setVisible(self.step in (2, 3, 4))
        count = self._selection_count()
        enabled = True
        warn = False
        hint = ""
        if self.step == 0:
            hint = "Khoảng 3 phút · 6 bước."
        elif self.step == 1:
            enabled = count > 0
            warn = count == 0
            hint = f"{count} mục sẽ bị chặn theo mặc định." if count else "Cần ít nhất một mục để tiếp tục."
        elif self.step == 2:
            if self.schedule_on and not self.days:
                warn = True
                hint = "Chưa chọn ngày nào, khung giờ sẽ không chạy."
            else:
                hint = "Có thể bỏ qua và thêm lịch sau."
        elif self.step == 3:
            hint = f"{self.study} phút học mỗi ngày · {self.gym} buổi gym mỗi tuần."
        elif self.step == 4:
            hint = "Bỏ qua nếu bạn muốn thiết lập sau."
        elif self.step == 5:
            enabled = count > 0
            warn = count == 0
            hint = "Có thể xem lại từ Cài đặt." if count else "Cần ít nhất một app hoặc trang web."
        else:
            hint = "Bước tiếp theo trong app là tab Hôm nay."
        self.primary.setEnabled(enabled)
        self.hint.setText(hint)
        self.hint.setStyleSheet(f"color:{WARN if warn else INK_3};font-size:12px;")

    def go(self, index: int) -> None:
        if index == self.step:
            return
        self.step = index
        self._render()

    def _next(self) -> None:
        if self.step == 1 and self._selection_count() == 0:
            return
        if self.step == 5:
            if self._selection_count() == 0:
                return
            self._apply()
            self.step = 6
            self._render()
            return
        if self.step == 6:
            self.accept()
            return
        self.step += 1
        self._render()

    def _skip(self) -> None:
        self.step = min(5, self.step + 1)
        self._render()

    def _apply(self) -> None:
        rest = self.windows[1:] if self.windows else []
        windows = list(rest)
        if self.schedule_on and self.days:
            windows.insert(
                0,
                FocusWindow(tuple(sorted(self.days)), self.start, self.end, "Khung giờ 1"),
            )
        store = Store(DB_PATH)
        result = None
        try:
            approved = rules.approved_config(store)
            base = approved or Config(
                poll_seconds=3,
                cooldown_hours=24,
                kill_browsers=False,
                processes=(),
                web_domains=(),
                windows=(),
            )
            candidate = replace(
                base,
                processes=tuple(sorted(self.processes)),
                web_domains=tuple(sorted(self.domains)),
                windows=tuple(windows),
                adult_block=self.adult,
            )
            same_law = (
                approved is not None
                and set(approved.processes) == set(candidate.processes)
                and set(approved.web_domains) == set(candidate.web_domains)
                and tuple(approved.windows) == tuple(candidate.windows)
                and approved.adult_block == candidate.adult_block
            )
            if not same_law:
                write_rules(CONFIG_PATH, candidate)
                if approved is None:
                    rules.apply_now(store, candidate, "thiết lập lần đầu")
                else:
                    result = rules.apply_candidate(store, candidate)
            write_plan(CONFIG_PATH, Plan(self.study, self.gym))
            store.set_state(SETUP_KEY, "1")
        finally:
            store.close()
        if result is not None and result.state == "queued":
            QMessageBox.information(
                self,
                "Đã ghi nhận",
                "Một số thay đổi nới lỏng sẽ có hiệu lực sau 24 giờ. Trong lúc chờ, luật cũ vẫn thi hành.",
            )


def run(screenshot: str | None = None) -> int:
    app = QApplication.instance() or QApplication([sys.argv[0]])
    app.setApplicationName("Kỷ luật")
    app.setStyleSheet(STYLE)
    store = Store(DB_PATH)
    try:
        if store.get_state(SETUP_MIGRATED_KEY) != "1":
            store.set_state(SETUP_MIGRATED_KEY, "1")
            if store.get_state(APPROVED_KEY):
                store.set_state(SETUP_KEY, "1")
        setup_done = store.get_state(SETUP_KEY) == "1"
        if setup_done:
            seed_initial_law(store)
    finally:
        store.close()
    window = MainWindow()
    if not setup_done and screenshot is None:
        window.hide()
        accepted = SetupDialog(window).exec() == QDialog.DialogCode.Accepted
        if not accepted:
            store = Store(DB_PATH)
            try:
                seed_initial_law(store)
            finally:
                store.close()
        window.refresh()
        window.refresh_protect()
        window.refresh_settings()
    elif not setup_done:
        store = Store(DB_PATH)
        try:
            seed_initial_law(store)
        finally:
            store.close()
    window.show()
    if screenshot:
        def capture() -> None:
            window.grab().save(screenshot)
            QApplication.quit()

        QTimer.singleShot(1100, capture)
    return app.exec()
