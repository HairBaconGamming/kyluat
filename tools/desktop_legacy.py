from __future__ import annotations

import os
import shutil
import subprocess
import sys
import tempfile
import threading
from datetime import datetime
from pathlib import Path

import psutil
from PySide6.QtCore import QBuffer, QIODevice, QRect, QSize, Qt, QTimer, Signal
from PySide6.QtGui import QColor, QIcon, QPainter, QPen, QPixmap
from PySide6.QtWidgets import (
    QAbstractItemView,
    QApplication,
    QFrame,
    QGridLayout,
    QHBoxLayout,
    QHeaderView,
    QLabel,
    QMainWindow,
    QMessageBox,
    QProgressBar,
    QPushButton,
    QScrollArea,
    QStyledItemDelegate,
    QSystemTrayIcon,
    QTableWidget,
    QTableWidgetItem,
    QTabWidget,
    QVBoxLayout,
    QWidget,
)

from . import phone_api, sessions
from .cli import (
    CONFIG_PATH,
    DB_PATH,
    LOCK_PATH,
    ROOT,
    _create_task,
    _daemon_pid,
    _restart_daemon,
    _start_daemon,
    _task_status,
)
from .config import Config, load_config
from .dashboard import build_summary
from .habits import WEEKDAY_VI
from .store import APPROVED_KEY, NET_ENABLED_KEY, Store

STYLE = """
QMainWindow { background: #f4f5f6; }QWidget { color: #17191c; font-family: "Segoe UI"; font-size: 14px; }
QFrame#card { background: #ffffff; border: 1px solid #e3e5e8; border-radius: 10px; }
QLabel#cardLabel { color: #6d737b; font-size: 11px; font-weight: 600; }
QLabel#cardValue { font-size: 24px; font-weight: 600; }
QLabel#cardSub { color: #6d737b; font-size: 12px; }
QLabel#title { font-size: 20px; font-weight: 600; }
QLabel#muted { color: #6d737b; font-size: 13px; }
QLabel#section { color: #6d737b; font-size: 11px; font-weight: 600; }
QPushButton { background: #ffffff; border: 1px solid #e3e5e8; border-radius: 8px; padding: 6px 12px; font-size: 13px; }
QPushButton:hover { border-color: #c8ccd0; }
QPushButton:disabled { color: #a0a5ab; }
QProgressBar { background: #e3e5e8; border: none; border-radius: 3px; height: 6px; text-align: center; color: transparent; }
QProgressBar::chunk { background: #2f6f5e; border-radius: 3px; }
QProgressBar#dayBar { height: 18px; background: #eef0f2; color: #17191c; font-size: 11px; text-align: center; border-radius: 3px; }
QProgressBar#dayBar::chunk { background: #cfe0d9; border-radius: 3px; }
QTableWidget { background: #ffffff; border: 1px solid #e3e5e8; border-radius: 10px; gridline-color: #eef0f2; }
QHeaderView::section { background: #ffffff; color: #6d737b; border: none; border-bottom: 1px solid #e3e5e8; padding: 8px 10px; font-size: 11px; font-weight: 600; }
QTabWidget::pane { border: none; }
QTabBar::tab { background: transparent; color: #6d737b; padding: 8px 14px; border-bottom: 2px solid transparent; }
QTabBar::tab:selected { color: #17191c; border-bottom: 2px solid #2f6f5e; }
QScrollArea { border: none; background: #f4f5f6; }
QScrollBar:vertical { background: transparent; width: 10px; margin: 2px; }
QScrollBar::handle:vertical { background: #d5d9dd; border-radius: 5px; min-height: 24px; }
QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical { height: 0; }
"""

PRIMARY_BUTTON = (
    "background:#2f6f5e;border:1px solid #2f6f5e;color:#ffffff;"
    "border-radius:8px;padding:6px 12px;font-size:13px;"
)
DISABLED_BUTTON = (
    "background:#f0f1f2;border:1px solid #e3e5e8;color:#a0a5ab;"
    "border-radius:8px;padding:6px 12px;font-size:13px;"
)


def _ring_pixmap(size: int, background: QColor | None = None) -> QPixmap:
    pixmap = QPixmap(size, size)
    pixmap.fill(background if background is not None else Qt.transparent)
    painter = QPainter(pixmap)
    painter.setRenderHint(QPainter.Antialiasing)
    pen = QPen(QColor("#f5f6f7") if background is not None else QColor("#2f6f5e"))
    pen.setWidth(max(2, int(size * 0.075)))
    painter.setPen(pen)
    painter.setBrush(Qt.NoBrush)
    margin = int(size * 0.22)
    painter.drawEllipse(margin, margin, size - 2 * margin, size - 2 * margin)
    painter.setPen(Qt.NoPen)
    painter.setBrush(pen.color())
    dot = max(3, int(size * 0.12))
    painter.drawEllipse(size // 2 - dot // 2, size // 2 - dot // 2, dot, dot)
    painter.end()
    return pixmap


def make_icon() -> QIcon:
    return QIcon(_ring_pixmap(64))


def write_ico(path: Path) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    pixmap = _ring_pixmap(256, QColor("#1f2a28"))
    buffer = QBuffer()
    buffer.open(QIODevice.WriteOnly)
    pixmap.save(buffer, "PNG")
    data = bytes(buffer.data())
    header = b"\x00\x00\x01\x00\x01\x00"
    entry = bytes([0, 0, 0, 0]) + (1).to_bytes(2, "little") + (32).to_bytes(2, "little")
    entry += len(data).to_bytes(4, "little") + (22).to_bytes(4, "little")
    path.write_bytes(header + entry + data)
    return path


def desktop_directory() -> Path:
    try:
        import ctypes

        buffer = ctypes.create_unicode_buffer(260)
        result = ctypes.windll.shell32.SHGetFolderPathW(None, 0x0010, None, 0, buffer)
        if result == 0 and buffer.value:
            return Path(buffer.value)
    except (AttributeError, OSError):
        pass
    return Path.home() / "Desktop"


def create_shortcut(icon_path: Path, desktop_dir: Path | None = None) -> Path:
    pythonw = Path(sys.executable).with_name("pythonw.exe")
    if not pythonw.exists():
        pythonw = Path(sys.executable)
    launcher = ROOT / "run_desktop.pyw"
    folder = desktop_dir if desktop_dir is not None else desktop_directory()
    folder.mkdir(parents=True, exist_ok=True)
    final = folder / "Kỷ luật.lnk"

    temp = Path(tempfile.gettempdir()) / f"cainghien-{os.getpid()}.lnk"
    if temp.exists():
        temp.unlink()
    env = dict(
        os.environ,
        CAINGHIEN_LNK=str(temp),
        CAINGHIEN_TARGET=str(pythonw),
        CAINGHIEN_ARGS=f'"{launcher}"',
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
    result = subprocess.run(
        ["powershell", "-NoProfile", "-NonInteractive", "-Command", script],
        capture_output=True,
        env=env,
    )
    if result.returncode != 0 or not temp.exists():
        message = (result.stderr or result.stdout).decode("utf-8", "replace").strip()
        raise RuntimeError(message or "tạo shortcut thất bại")
    if final.exists():
        final.unlink()
    shutil.move(str(temp), str(final))
    if not final.exists():
        raise RuntimeError("không di chuyển được shortcut ra Desktop")
    return final


class StudyBarDelegate(QStyledItemDelegate):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.max_value = 1

    def paint(self, painter, option, index) -> None:
        value = int(index.data(Qt.ItemDataRole.UserRole) or 0)
        painter.save()
        painter.fillRect(option.rect, QColor("#ffffff"))
        bar_area = option.rect.adjusted(10, 0, -46, 0)
        track = QRect(bar_area.left(), option.rect.center().y() - 3, bar_area.width(), 6)
        painter.setPen(Qt.PenStyle.NoPen)
        painter.setBrush(QColor("#e3e5e8"))
        painter.drawRoundedRect(track, 3, 3)
        ratio = min(1.0, value / max(1, self.max_value))
        if ratio > 0:
            fill = QRect(track.left(), track.top(), int(track.width() * ratio), track.height())
            painter.setBrush(QColor("#2f6f5e"))
            painter.drawRoundedRect(fill, 3, 3)
        painter.setPen(QColor("#17191c"))
        font = painter.font()
        font.setPointSizeF(8.5)
        painter.setFont(font)
        text_rect = QRect(option.rect.right() - 44, option.rect.top(), 36, option.rect.height())
        painter.drawText(
            text_rect,
            Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter,
            f"{value}′",
        )
        painter.restore()

    def sizeHint(self, option, index) -> QSize:
        return QSize(140, 30)


class MainWindow(QMainWindow):
    paired = Signal(str)

    def __init__(self) -> None:
        super().__init__()
        self.setWindowTitle("Kỷ luật")
        self.resize(1040, 820)
        self.setMinimumSize(900, 700)
        self.icon = make_icon()
        self.setWindowIcon(self.icon)
        self._force_quit = False
        self._tray_hint_shown = False
        self.paired.connect(self._on_paired)
        self._build_ui()
        self._build_tray()
        self.refresh()
        self.refresh_settings()
        self.timer = QTimer(self)
        self.timer.timeout.connect(self.refresh)
        self.timer.start(15000)

    def _build_ui(self) -> None:
        central = QWidget()
        central.setStyleSheet("background: #f4f5f6;")
        self.setCentralWidget(central)
        outer = QVBoxLayout(central)
        outer.setContentsMargins(24, 20, 24, 18)
        outer.setSpacing(12)

        header = QHBoxLayout()
        left = QVBoxLayout()
        title = QLabel("Kỷ luật")
        title.setObjectName("title")
        left.addWidget(title)
        self.status = QLabel("Đang tải…")
        self.status.setObjectName("muted")
        left.addWidget(self.status)
        header.addLayout(left)
        header.addStretch(1)

        right = QVBoxLayout()
        right.setAlignment(Qt.AlignRight)
        self.clock = QLabel("")
        self.clock.setObjectName("muted")
        right.addWidget(self.clock, alignment=Qt.AlignRight)
        row = QHBoxLayout()
        self.pill = QLabel("—")
        self.pill.setAlignment(Qt.AlignCenter)
        row.addWidget(self.pill)
        refresh_button = self._button("Làm mới", False, self.refresh)
        row.addWidget(refresh_button)
        right.addLayout(row)
        header.addLayout(right)
        outer.addLayout(header)

        self.tabs = QTabWidget()
        self.tabs.addTab(self._build_overview(), "Tổng quan")
        self.tabs.addTab(self._build_settings(), "Thiết lập")
        self.tabs.currentChanged.connect(lambda _: self.refresh_settings())
        outer.addWidget(self.tabs, 1)

        footer = QLabel("Đóng cửa sổ sẽ thu vào khay hệ thống · daemon vẫn chạy nền")
        footer.setObjectName("muted")
        outer.addWidget(footer)

    def _build_overview(self) -> QWidget:
        page = QWidget()
        page.setStyleSheet("background: #f4f5f6;")
        layout = QVBoxLayout(page)
        layout.setContentsMargins(4, 14, 4, 4)
        layout.setSpacing(14)

        grid = QGridLayout()
        grid.setHorizontalSpacing(14)
        grid.setVerticalSpacing(14)
        grid.setColumnStretch(0, 1)
        grid.setColumnStretch(1, 1)
        grid.setColumnStretch(2, 1)
        grid.addWidget(self._study_card(), 0, 0)
        grid.addWidget(self._gym_card(), 0, 1)
        grid.addWidget(self._block_card(), 0, 2)
        layout.addLayout(grid)
        layout.addWidget(self._session_card())
        layout.addWidget(self._week_table())
        layout.addStretch(1)
        return page

    def _make_card(self):
        frame = QFrame()
        frame.setObjectName("card")
        layout = QVBoxLayout(frame)
        layout.setContentsMargins(16, 14, 16, 14)
        layout.setSpacing(6)
        label = QLabel()
        label.setObjectName("cardLabel")
        value = QLabel("—")
        value.setObjectName("cardValue")
        sub = QLabel("")
        sub.setObjectName("cardSub")
        sub.setWordWrap(True)
        bar = QProgressBar()
        bar.setRange(0, 100)
        bar.setValue(0)
        bar.setFixedHeight(6)
        bar.setTextVisible(False)
        layout.addWidget(label)
        layout.addWidget(value)
        layout.addWidget(sub)
        layout.addWidget(bar)
        return frame, layout, label, value, sub, bar

    def _study_card(self) -> QFrame:
        frame, layout, label, value, sub, bar = self._make_card()
        label.setText("HỌC HÔM NAY")
        layout.addSpacing(8)
        actions = QHBoxLayout()
        actions.addWidget(self._button("+30 phút", True, lambda: self.checkin("study", 30)))
        actions.addWidget(self._button("+60 phút", False, lambda: self.checkin("study", 60)))
        actions.addStretch(1)
        layout.addLayout(actions)
        self.study_value, self.study_sub, self.study_bar = value, sub, bar
        return frame

    def _gym_card(self) -> QFrame:
        frame, layout, label, value, sub, bar = self._make_card()
        label.setText("GYM TUẦN NÀY")
        layout.addSpacing(8)
        actions = QHBoxLayout()
        actions.addWidget(self._button("+1 buổi gym", True, lambda: self.checkin("gym", 0)))
        actions.addStretch(1)
        layout.addLayout(actions)
        self.gym_value, self.gym_sub, self.gym_bar = value, sub, bar
        return frame

    def _block_card(self) -> QFrame:
        frame, layout, label, value, sub, bar = self._make_card()
        label.setText("BỊ CHẶN HÔM NAY")
        bar.hide()
        layout.addSpacing(8)
        self.block_value, self.block_sub = value, sub
        return frame

    def _session_card(self) -> QFrame:
        frame = QFrame()
        frame.setObjectName("card")
        layout = QVBoxLayout(frame)
        layout.setContentsMargins(16, 14, 16, 14)
        layout.setSpacing(6)
        label = QLabel("PHIÊN FOCUS")
        label.setObjectName("cardLabel")
        self.session_value = QLabel("—")
        self.session_value.setObjectName("cardValue")
        self.session_sub = QLabel("Không có phiên.")
        self.session_sub.setObjectName("cardSub")
        layout.addWidget(label)
        layout.addWidget(self.session_value)
        layout.addWidget(self.session_sub)
        actions = QHBoxLayout()
        actions.addWidget(self._button("25 phút", True, lambda: self.start_session(25)))
        actions.addWidget(self._button("50 phút", False, lambda: self.start_session(50)))
        self.stop_button = self._button("Dừng phiên", False, self.stop_session)
        self.cancel_button = self._button("Hủy dừng", False, self.cancel_stop)
        actions.addWidget(self.stop_button)
        actions.addWidget(self.cancel_button)
        actions.addStretch(1)
        layout.addLayout(actions)
        return frame

    def _week_table(self) -> QWidget:
        wrapper = QWidget()
        layout = QVBoxLayout(wrapper)
        layout.setContentsMargins(0, 4, 0, 0)
        layout.setSpacing(8)
        heading = QLabel("7 NGÀY QUA")
        heading.setObjectName("section")
        layout.addWidget(heading)
        self.table = QTableWidget(0, 4)
        self.table.setHorizontalHeaderLabels(["Ngày", "Học", "Gym", "Bị chặn"])
        self.table.verticalHeader().setVisible(False)
        self.table.setShowGrid(False)
        self.table.setSelectionMode(QAbstractItemView.SelectionMode.NoSelection)
        self.table.setEditTriggers(QAbstractItemView.EditTrigger.NoEditTriggers)
        header = self.table.horizontalHeader()
        header.setDefaultAlignment(Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter)
        header.setSectionResizeMode(0, QHeaderView.ResizeMode.ResizeToContents)
        header.setSectionResizeMode(1, QHeaderView.ResizeMode.Stretch)
        header.setSectionResizeMode(2, QHeaderView.ResizeMode.ResizeToContents)
        header.setSectionResizeMode(3, QHeaderView.ResizeMode.ResizeToContents)
        self.study_delegate = StudyBarDelegate(self.table)
        self.table.setItemDelegateForColumn(1, self.study_delegate)
        self.table.verticalHeader().setDefaultSectionSize(32)
        self.table.setFixedHeight(300)
        layout.addWidget(self.table)
        return wrapper

    def _build_settings(self) -> QWidget:
        page = QWidget()
        page.setStyleSheet("background: #f4f5f6;")
        outer = QVBoxLayout(page)
        outer.setContentsMargins(4, 14, 4, 4)
        content = QWidget()
        content.setStyleSheet("background: #f4f5f6;")
        layout = QVBoxLayout(content)
        layout.setContentsMargins(16, 14, 16, 16)
        layout.setSpacing(8)

        layout.addWidget(self._section("LUẬT ĐANG ÁP DỤNG"))
        self.rules_apps = self._text_label()
        self.rules_domains = self._text_label()
        self.rules_schedule = self._text_label()
        self.rules_misc = self._text_label()
        for widget in (self.rules_apps, self.rules_domains, self.rules_schedule, self.rules_misc):
            layout.addWidget(widget)

        layout.addSpacing(10)
        layout.addWidget(self._section("TRẠNG THÁI"))
        self.state_daemon = self._text_label()
        self.state_task = self._text_label()
        self.state_net = self._text_label()
        self.state_phone = self._text_label()
        self.state_sync = self._text_label()
        for widget in (
            self.state_daemon,
            self.state_task,
            self.state_net,
            self.state_phone,
            self.state_sync,
        ):
            layout.addWidget(widget)

        layout.addSpacing(10)
        buttons = QGridLayout()
        buttons.setHorizontalSpacing(8)
        buttons.setVerticalSpacing(8)
        self.daemon_button = self._button("Bật daemon", True, self.start_daemon)
        self.task_button = self._button("Cài task tự chạy", False, self.install_task)
        self.pair_button = self._button("Cặp điện thoại", False, self.pair_phone)
        buttons.addWidget(self.daemon_button, 0, 0)
        buttons.addWidget(self.task_button, 0, 1)
        buttons.addWidget(self.pair_button, 0, 2)
        buttons.addWidget(self._button("Mở config.toml", False, self.open_config), 1, 0)
        buttons.addWidget(self._button("Tạo lối tắt Desktop", False, self.make_shortcut), 1, 1)
        buttons.addWidget(self._button("Làm mới", False, self.refresh_settings), 1, 2)
        layout.addLayout(buttons)
        layout.addSpacing(10)
        note = QLabel(
            "Bật/tắt chặn web và mở firewall cần PowerShell quyền Administrator: "
            "python -m enforcer net install / uninstall, python -m enforcer phone firewall."
        )
        note.setObjectName("muted")
        note.setWordWrap(True)
        layout.addWidget(note)
        layout.addStretch(1)

        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setWidget(content)
        outer.addWidget(scroll)
        return page

    def _section(self, text: str) -> QLabel:
        label = QLabel(text)
        label.setObjectName("section")
        return label

    def _text_label(self) -> QLabel:
        label = QLabel("")
        label.setWordWrap(True)
        label.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
        label.setObjectName("muted")
        return label

    def _button(self, text: str, primary: bool, action) -> QPushButton:
        button = QPushButton(text)
        button.setCursor(Qt.CursorShape.PointingHandCursor)
        if primary:
            button.setStyleSheet(PRIMARY_BUTTON)
        button.clicked.connect(action)
        return button

    def _build_tray(self) -> None:
        if not QSystemTrayIcon.isSystemTrayAvailable():
            return
        self.tray = QSystemTrayIcon(self.icon, self)
        self.tray.setToolTip("Kỷ luật")
        menu = self.tray.contextMenu()
        if menu is None:
            from PySide6.QtWidgets import QMenu

            menu = QMenu()
            self.tray.setContextMenu(menu)
        open_action = menu.addAction("Mở Kỷ luật")
        open_action.triggered.connect(self._show_window)
        menu.addSeparator()
        focus25 = menu.addAction("Focus 25 phút")
        focus25.triggered.connect(lambda: self.start_session(25))
        focus50 = menu.addAction("Focus 50 phút")
        focus50.triggered.connect(lambda: self.start_session(50))
        stop_action = menu.addAction("Dừng phiên")
        stop_action.triggered.connect(self.stop_session)
        menu.addSeparator()
        quit_action = menu.addAction("Thoát")
        quit_action.triggered.connect(self._quit)
        self.tray.activated.connect(
            lambda reason: self._show_window() if reason == QSystemTrayIcon.ActivationReason.Trigger else None
        )
        self.tray.show()

    def _show_window(self) -> None:
        self.showNormal()
        self.raise_()
        self.activateWindow()

    def refresh(self) -> None:
        try:
            data = build_summary(DB_PATH, CONFIG_PATH, LOCK_PATH)
        except Exception as exc:
            self.status.setText(f"Lỗi đọc dữ liệu: {exc}")
            return
        focus = data["focus"]
        self.status.setText(focus["text"] + (f" · {focus['detail']}" if focus["detail"] else ""))
        self.clock.setText(f"{data['date']} · {data['time']}")
        if focus["active"]:
            self.pill.setText("Đang focus")
            self.pill.setStyleSheet(
                "color:#2f6f5e;background:#edf3f0;border:1px solid #2f6f5e;"
                "border-radius:10px;padding:2px 10px;font-size:12px;"
            )
        else:
            self.pill.setText("Ngoài giờ")
            self.pill.setStyleSheet(
                "color:#6d737b;background:#ffffff;border:1px solid #e3e5e8;"
                "border-radius:10px;padding:2px 10px;font-size:12px;"
            )

        study = data["habits"]["study"]
        gym = data["habits"]["gym"]
        blocks = data["blocks"]
        self.study_value.setText(f"{study['today']} / {study['goal']} phút")
        suffix = f" · thiếu {len(study['missed'])} ngày" if study["missed"] else ""
        self.study_sub.setText(
            f"Tuần {study['week']}/{study['week_goal']} phút · chuỗi {study['streak']} ngày{suffix}"
        )
        self.study_bar.setRange(0, max(study["goal"], 1))
        self.study_bar.setValue(min(study["today"], study["goal"]))

        self.gym_value.setText(f"{gym['week']} / {gym['goal']} buổi")
        self.gym_sub.setText(f"Chuỗi {gym['streak_weeks']} tuần đạt mục tiêu")
        self.gym_bar.setRange(0, max(gym["goal"], 1))
        self.gym_bar.setValue(min(gym["week"], gym["goal"]))

        self.block_value.setText(f"{blocks['today']} lần")
        top = ", ".join(f"{name} ({count})" for name, count in blocks["top"]) or "chưa có gì bị chặn"
        self.block_sub.setText(f"7 ngày: {top}")

        session = data["session"]
        if session:
            self.session_value.setText(f"{session['remaining_minutes']} phút")
            detail = f"«{session['label']}» đến {session['end'][11:16]}"
            if session["stop_after"]:
                detail += f" · dừng lúc {session['stop_after'][11:16]}"
            self.session_sub.setText(detail + ". Dừng sớm sẽ phải chờ theo cài đặt.")
            self.stop_button.setEnabled(True)
            self.cancel_button.setEnabled(bool(session["stop_after"]))
        else:
            self.session_value.setText("—")
            self.session_sub.setText("Không có phiên. Bắt đầu để khóa app và web ngay.")
            self.stop_button.setEnabled(False)
            self.cancel_button.setEnabled(False)

        self._fill_table(study["series"], gym["series"], blocks["series"])
        if getattr(self, "tray", None) is not None:
            self.tray.setToolTip(self.status.text())

    def _fill_table(self, study_series, gym_series, block_series) -> None:
        self.table.setRowCount(len(study_series))
        max_study = max([row["value"] for row in study_series] + [1])
        self.study_delegate.max_value = max_study
        for index, row in enumerate(study_series):
            day_item = QTableWidgetItem(row["label"])
            day_item.setForeground(QColor("#6d737b"))
            day_item.setTextAlignment(Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter)
            self.table.setItem(index, 0, day_item)

            study_item = QTableWidgetItem()
            study_item.setData(Qt.ItemDataRole.UserRole, row["value"])
            study_item.setTextAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)
            self.table.setItem(index, 1, study_item)

            gym_value = gym_series[index]["value"] if index < len(gym_series) else 0
            block_value = block_series[index]["value"] if index < len(block_series) else 0
            gym_item = QTableWidgetItem(str(gym_value))
            gym_item.setTextAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)
            block_item = QTableWidgetItem(str(block_value))
            block_item.setTextAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)
            block_item.setForeground(QColor("#6d737b"))
            self.table.setItem(index, 2, gym_item)
            self.table.setItem(index, 3, block_item)

    def refresh_settings(self) -> None:
        store = Store(DB_PATH)
        try:
            approved_json = store.get_state(APPROVED_KEY)
            token = phone_api.get_token(store)
            net_enabled = store.get_state(NET_ENABLED_KEY) == "1"
        finally:
            store.close()

        if approved_json:
            approved = Config.from_json(approved_json)
            self.rules_apps.setText(
                f"Ứng dụng bị chặn ({len(approved.processes)}): "
                + (", ".join(approved.processes) if approved.processes else "chưa có")
            )
            self.rules_domains.setText(
                f"Domain web ({len(approved.web_domains)}): "
                + (", ".join(approved.web_domains) if approved.web_domains else "chưa có")
            )
            schedule = []
            for window in approved.windows:
                days = ", ".join(WEEKDAY_VI[day] for day in window.days)
                schedule.append(f"{days}: {window.start:%H:%M}–{window.end:%H:%M} «{window.label}»")
            self.rules_schedule.setText("Lịch focus:\n" + ("\n".join(schedule) if schedule else "chưa có"))
            self.rules_misc.setText(
                f"Cooldown thay đổi luật: {approved.cooldown_hours:g} giờ · "
                f"Chờ dừng phiên: {approved.session_stop_delay_minutes} phút"
            )
            try:
                synced = load_config(CONFIG_PATH).to_dict() == approved.to_dict()
            except Exception:
                synced = False
            self.state_sync.setText(
                "config.toml: " + ("khớp luật đang chạy" if synced else "có thay đổi chưa apply")
            )
        else:
            self.rules_apps.setText("Chưa có luật. Chạy: python -m enforcer apply")
            self.rules_domains.setText("")
            self.rules_schedule.setText("")
            self.rules_misc.setText("")
            self.state_sync.setText("")

        pid = _daemon_pid()
        self.state_daemon.setText(f"Daemon: {'đang chạy (PID ' + str(pid) + ')' if pid else 'không chạy'}")
        if pid is None:
            self.daemon_button.setText("Bật daemon")
            self.daemon_button.setEnabled(True)
            self.daemon_button.setStyleSheet(PRIMARY_BUTTON)
        else:
            self.daemon_button.setText("Daemon đang chạy")
            self.daemon_button.setEnabled(False)
            self.daemon_button.setStyleSheet(DISABLED_BUTTON)

        task_text = _task_status()
        self.state_task.setText("Tự chạy: " + task_text)
        task_installed = task_text.startswith("có")
        self.task_button.setEnabled(not task_installed)
        self.task_button.setText("Task đã cài" if task_installed else "Cài task tự chạy")

        suffix = " · cần daemon chạy" if net_enabled and pid is None else ""
        self.state_net.setText(
            f"Chặn web: {'bật' if net_enabled else 'tắt'}"
            + (suffix if net_enabled else " (bật bằng net install, cần admin)")
        )
        if token:
            self.state_phone.setText(
                f"Điện thoại: đã cặp · http://{phone_api.lan_ip()}:{phone_api.PHONE_PORT}"
            )
        else:
            self.state_phone.setText("Điện thoại: chưa cặp kết nối")
        self.pair_button.setText("Xem cặp kết nối" if token else "Cặp điện thoại")

    def checkin(self, habit: str, minutes: int) -> None:
        store = Store(DB_PATH)
        try:
            store.add_checkin(habit, minutes)
        finally:
            store.close()
        self.refresh()

    def start_session(self, minutes: int) -> None:
        store = Store(DB_PATH)
        try:
            approved_json = store.get_state(APPROVED_KEY)
            try:
                session = sessions.start(store, minutes, "Desktop focus", datetime.now())
            except ValueError as exc:
                QMessageBox.warning(self, "Không bắt đầu được", str(exc))
                return
            delay = Config.from_json(approved_json).session_stop_delay_minutes if approved_json else 10
        finally:
            store.close()
        if _daemon_pid() is None:
            _start_daemon()
        self.refresh()
        if getattr(self, "tray", None) is not None:
            self.tray.showMessage(
                "Đã bắt đầu phiên",
                f"{session.end:%H:%M} · dừng sớm phải chờ {delay} phút",
                QSystemTrayIcon.MessageIcon.Information,
                4000,
            )

    def stop_session(self) -> None:
        store = Store(DB_PATH)
        try:
            approved_json = store.get_state(APPROVED_KEY)
            delay = Config.from_json(approved_json).session_stop_delay_minutes if approved_json else 10
            try:
                session = sessions.request_stop(store, delay, datetime.now())
            except ValueError as exc:
                QMessageBox.warning(self, "Không dừng được", str(exc))
                return
        finally:
            store.close()
        self.refresh()
        if getattr(self, "tray", None) is not None:
            self.tray.showMessage(
                "Đã ghi nhận yêu cầu dừng",
                f"Phiên dừng lúc {session.stop_after:%H:%M}",
                QSystemTrayIcon.MessageIcon.Information,
                4000,
            )

    def cancel_stop(self) -> None:
        store = Store(DB_PATH)
        try:
            try:
                sessions.cancel_stop(store, datetime.now())
            except ValueError as exc:
                QMessageBox.warning(self, "Không hủy được", str(exc))
                return
        finally:
            store.close()
        self.refresh()

    def start_daemon(self) -> None:
        _start_daemon()
        QTimer.singleShot(1500, self.refresh_settings)
        QTimer.singleShot(1500, self.refresh)

    def install_task(self) -> None:
        result = _create_task(elevated=False)
        if result.returncode == 0:
            QMessageBox.information(self, "Tự chạy", "Đã cài task tự chạy mỗi phút (quyền thường).")
        else:
            message = (result.stderr or result.stdout).decode("utf-8", "replace").strip()
            QMessageBox.warning(self, "Không cài được task", message or "schtasks thất bại")
        self.refresh_settings()

    def pair_phone(self) -> None:
        self.pair_button.setEnabled(False)
        self.state_phone.setText("Điện thoại: đang tạo cặp kết nối…")

        def work() -> None:
            store = Store(DB_PATH)
            try:
                token = phone_api.get_token(store)
                created = token is None
                if token is None:
                    token = phone_api.create_token(store)
            finally:
                store.close()
            if _daemon_pid() is None:
                _start_daemon()
            elif created:
                _restart_daemon()
            self.paired.emit(token)

        threading.Thread(target=work, daemon=True).start()

    def _on_paired(self, token: str) -> None:
        self.pair_button.setEnabled(True)
        QApplication.clipboard().setText(token)
        ip = phone_api.lan_ip()
        QMessageBox.information(
            self,
            "Cặp điện thoại",
            f"URL: http://{ip}:{phone_api.PHONE_PORT}\n"
            "Token đã được copy vào clipboard.\n"
            "Nhập URL và token trong app Kỷ luật trên điện thoại.\n"
            "Cùng Wi-Fi, tắt VPN. Nếu không kết nối được: PowerShell Admin → "
            "python -m enforcer phone firewall",
        )
        self.refresh_settings()

    def open_config(self) -> None:
        try:
            os.startfile(CONFIG_PATH)  # type: ignore[attr-defined]
        except OSError as exc:
            QMessageBox.warning(self, "Không mở được", str(exc))

    def make_shortcut(self) -> None:
        try:
            icon_path = write_ico(Path(__file__).resolve().parent / "static" / "app.ico")
            target = create_shortcut(icon_path)
        except Exception as exc:
            QMessageBox.warning(self, "Không tạo được lối tắt", str(exc))
            return
        QMessageBox.information(self, "Lối tắt", f"Đã tạo: {target}")

    def closeEvent(self, event) -> None:
        if self._force_quit or getattr(self, "tray", None) is None:
            event.accept()
            return
        event.ignore()
        self.hide()
        if not self._tray_hint_shown:
            self._tray_hint_shown = True
            self.tray.showMessage(
                "Kỷ luật vẫn chạy",
                "Ứng dụng thu vào khay hệ thống. Chuột phải icon để mở lại hoặc thoát.",
                QSystemTrayIcon.MessageIcon.Information,
                4000,
            )

    def _quit(self) -> None:
        self._force_quit = True
        if getattr(self, "tray", None) is not None:
            self.tray.hide()
        QApplication.quit()


def run(screenshot: str | None = None) -> int:
    app = QApplication.instance() or QApplication([sys.argv[0]])
    app.setApplicationName("Kỷ luật")
    app.setStyleSheet(STYLE)
    window = MainWindow()
    window.show()
    if screenshot:
        def capture() -> None:
            window.grab().save(screenshot)
            QApplication.quit()

        QTimer.singleShot(900, capture)
    return app.exec()
