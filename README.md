# Kỷ luật

Kỷ luật là máy chặn xao nhãng gồm bốn phần: một tiến trình nền Python và PySide6 trên Windows, một DNS sinkhole local chặn tên miền, một app Android phủ màn khoá lên ứng dụng đang mở, và trang web hướng dẫn cùng tải về viết bằng Next.js. Mã nguồn ở [github.com/HairBaconGamming/kyluat](https://github.com/HairBaconGamming/kyluat); nơi duy nhất phát hành bản dùng được là [GitHub Releases v1.0](https://github.com/HairBaconGamming/kyluat/releases/tag/v1.0). Trang web chưa có URL công khai, nó mới chạy nội bộ bằng `npm run dev` và chờ được deploy từ repo này.

![Màn hình Hôm nay trên Windows](web/public/assets/img/desktop-hom-nay.png)

Ảnh trên và ảnh dưới dùng dữ liệu demo, không phải lịch sử thật của ai.

## Luật bản hiện tại

| Việc | Bản này |
| --- | --- |
| Quỹ mạng xã hội mỗi ngày trên Android | mặc định 60 phút, làm mới lúc 0 giờ, không có nút gia hạn trong ngày |
| Đổi mức quỹ trên Android | 15–120 phút, gõ lại đoạn cam kết và chờ 30 giây; giảm có hiệu lực ngay, tăng chờ đến 0 giờ hôm sau |
| Xin dừng một phiên tập trung giữa chừng | phải chờ 10 phút |
| Nới lỏng luật (bỏ chặn, giảm giờ, giảm cooldown) | có hiệu lực sau 24 giờ |
| Thêm ràng buộc mới | có hiệu lực ngay |
| Tắt hẳn bảo vệ | màn Nâng cao, gõ lại đoạn cam kết và chờ 60 giây |
| Chặn trang web | chỉ trên Windows, theo tên miền, tính cả trang con |

Danh sách app bị chặn không tự sinh ra. Máy không tự đoán cái nào gây nghiện.

## Tải về

Bản phát hành có ba file trên GitHub Releases:

- `KYLUAT-Setup-v1.1.exe` cho Windows 10 và 11 bản 64-bit. Bộ cài đặt vào `%LOCALAPPDATA%\Programs\Kyluat`, không đòi quyền quản trị, có lối tắt Start Menu và gỡ qua Add or remove programs.
- `KYLUAT-windows-x64-v1.1.zip` cùng bản, cho ai muốn giải nén rồi chạy mà không cài gì.
- `kyluat-android-v3.5.apk` cho Android 8.0 trở lên. Cài thủ công, không lên CH Play.

File exe chưa có chữ ký số, nên SmartScreen cảnh báo một lần: chọn More info rồi Run anyway sau khi đã so SHA-256 ghi trong trang release. Xem mục "Vì sao không phải virus" bên dưới để biết app thay đổi những gì trên máy.

![Màn hình Bảo vệ](web/public/assets/img/desktop-bao-ve.png)

## Kiến trúc

Máy tính và điện thoại nói chuyện với nhau qua Wi-Fi nội bộ, mỗi bên tự giữ luật của mình.

- `enforcer/` chạy trên Windows. Tiến trình nền kiểm tra mỗi 3 giây và tắt tiến trình thuộc danh sách chặn trong giờ tập trung.
- `android/` chạy trên Android. Màn khoá chỉ là một lớp phủ hình ảnh do `WindowManager.addView` với cửa sổ `TYPE_APPLICATION_OVERLAY` vẽ lên, còn AccessibilityService chỉ báo về app nào đang ở phía trước. Lớp phủ không lấy focus, không đổi chính sách hệ thống, không đọc nội dung màn hình. Hai nút trên màn khoá luôn còn: "Quay lại Kỷ luật" và "Dùng app khác"; thu hồi quyền Trợ năng là app dừng chặn ngay.
- Ghép nối: máy tính sinh mã 6 số và QR, hết hạn sau 5 phút, sai 5 lần thì phải lấy mã mới. Server chỉ lắng nghe trong mạng nội bộ, cổng 8766. Key nằm trong Android Keystore.

### Chặn web bằng DNS sinkhole local

Thay vì cấu hình router hay hosts, máy tính tự chạy một DNS server nhỏ trên chính nó. Tên miền trong danh sách chặn nhận câu trả lời `127.0.0.1` với TTL 5 giây, mọi truy vấn khác được chuyển tiếp lên `1.1.1.1` rồi `8.8.8.8` (`UPSTREAMS` trong `enforcer/net.py`). Vì vậy chỉ máy này bị ảnh hưởng, router và các thiết bị khác không đổi theo, và đây là cơ chế chặn tên miền chứ không phải chặn thiết bị. Bù lại, khi chặn web đang bật thì tên miền của bạn đi hỏi DNS công cộng chứ không hỏi DNS nhà mạng; tắt chặn web là trả về DNS cũ.

Cảnh báo cho người review: `net install` sửa cấu hình mạng của máy. Nó cần PowerShell Administrator, ghi DNS của card mạng về `127.0.0.1`, và đặt policy tắt DoH trong registry để trình duyệt không đi vòng qua sinkhole; DNS cũ cùng giá trị registry cũ được lưu trong DB ngay trước khi sửa. Lệnh này thuận nghịch: `net uninstall` khôi phục đúng DNS cũ và dựng lại từng policy DoH về trạng thái trước (xoá nếu trước đó không đặt), `net repair` dùng khi router làm lại DNS sau khi đổi Wi-Fi, còn `turn-off` gom ba bước tắt bảo vệ. Bước khôi phục còn fail-safe: nếu DNS chưa về cũ được thì daemon vẫn chạy và task được bật lại, để bạn không mất internet giữa chừng. Nếu máy tắt đột ngột, `net uninstall` vẫn đưa mọi thứ về cũ.

## Chạy từ mã nguồn

Môi trường đã chạy thật, tuỳ theo phần bạn cần:

| Phần | Chạy được trên | Ghi chú |
| --- | --- | --- |
| `enforcer/` và `tests/` | Windows 10 hoặc 11, Python 3.12.10 | đây là môi trường tôi đã test, `requirements.txt` không pin bản Python nên chưa kiểm chứng bản thấp hơn |
| `android/` | JDK 17, Android SDK 34 | `compileSdk`/`targetSdk` 34, `minSdk` 26, Gradle 8.7 theo wrapper đã pin |
| `web/` | Node 20.9 trở lên, bản tôi dùng là v24.12.0 và npm 11.6.2 | `next@16.4.3` khai báo `engines.node >= 20.9.0` trong `package-lock.json`, nên `npm run build` cần chừng đó |

Python và Node thấp hơn phần khai báo ở trên thì chưa ai chạy thử, kể cả `npm run build` trên Node 20.9: tôi build trên Node v24.12.0.

```powershell
pip install -r requirements.txt
copy config.example.toml config.toml
python -m enforcer apply
python -m enforcer desktop
```

`desktop` mở app Qt. `dashboard` mở dashboard local trong trình duyệt. `net install` cần PowerShell Administrator; `turn-off` chỉ cần quyền đó khi bạn đã từng bật chặn web. `install` tạo Scheduled Task để daemon tự chạy và watchdog mỗi phút.

```powershell
python -m enforcer doctor
```

`doctor` đọc to mọi thứ: luật đang chạy, daemon còn sống, DNS có khớp, hôm nay chặn được bao nhiêu lần.

Lệnh khác: `status`, `today`, `review`, `log`, `checkin`, `focus`, `export`, `phone`, `uninstall`.

### Kiểm thử

```powershell
pip install -r requirements-dev.txt
pytest
```

48 test, chạy được mà không cần quyền Administrator và không đụng tới hệ thống thật. `tests/test_proc_gate.py` giữ hai chuyện: mọi lời gọi tiến trình con trong `enforcer/` phải đi qua `enforcer/proc.py`, và cửa đó vẫn chạy được khi tiến trình không có console hoặc bị mất stdin. `tests/test_packaging_entry.py` đọc luôn header PE của hai file exe đã build để chắc `Kyluat.exe` là subsystem GUI. `tests/test_turn_off.py` kẹp hai nhánh của lệnh tắt bảo vệ: chưa từng đổi DNS thì không đòi quyền admin, còn đổi rồi mà khôi phục thất bại thì từ chối gỡ và bật lại task để máy không mất mạng.

### Đóng gói exe Windows và bộ cài

```powershell
pip install -r requirements-dev.txt
python tools/build_windows.py
```

Lệnh này chạy PyInstaller theo `packaging/Kyluat.spec`, nén ra `dist\KYLUAT-windows-x64-v1.1.zip`, rồi biên dịch `packaging/kyluat.iss` thành `dist\KYLUAT-Setup-v1.1.exe` và in dung lượng cùng SHA-256 cho từng file để dán vào `web/lib/site.js`.

Thư mục exe có hai file, giống cặp `pythonw`/`python` trong source:

| File | subsystem | Dùng để |
| --- | --- | --- |
| `Kyluat.exe` | GUI, không bao giờ mở cửa sổ console | mở app và chạy daemon (task mỗi phút đánh thức file này) |
| `Kyluat-cli.exe` | console | gõ lệnh trong PowerShell: `status`, `doctor`, `turn-off`, `net install`… |

Bản 1.1 trở về trước chỉ có một exe console, nên Windows cấp một cửa sổ đen rồi app phải `FreeConsole()` gỡ ra: người dùng thấy nó chớp một cái lúc mở app. `enforcer/proc.py` cũng đặt `CREATE_NO_WINDOW` cho mọi tiến trình con (`schtasks`, `netsh`, `reg`, `ipconfig`, PowerShell) để không có cửa sổ nào nhấp nháy khi app chạy ngầm.

Đổi lại, không còn cửa sổ terminal để nhìn khi app xin quyền Administrator. Màn Tắt bảo vệ vì thế chờ và đọc lại trạng thái thật (DNS đã tắt flags, task còn hay mất, daemon còn sống) trong tối đa 20 giây rồi mới báo; không tắt được thì app nói rõ và dẫn tới `Kyluat-cli.exe turn-off`.

Bộ cài cần Inno Setup 6.7 trở lên (`WizardStyle=modern`); chưa có thì tool vẫn làm các bước kia và in ra lệnh cài:

```powershell
winget install --id JRSoftware.InnoSetup --scope user
```

Version nằm một chỗ trong `packaging/version.txt`, vừa là tài nguyên phiên bản của exe vừa là số trong tên file bộ cài. `tools/build_windows.py --skip-exe` chỉ đóng gói lại từ exe đã có, `--no-installer` bỏ bước Inno, `--no-zip` bỏ bản nén.

Toàn bộ dữ liệu nằm trong `data\` cạnh file exe, nên chép cả thư mục là chuyển được máy.

### Build APK

Cần JDK 17 và Android SDK 34. `tools/android_setup.sh` tải JDK, command-line tools và Gradle 8.7 vào `~/AndroidToolchain` rồi in ra `JAVA_HOME`, `ANDROID_HOME`, đường dẫn Gradle. `./gradlew` tự tải đúng Gradle 8.7 theo wrapper đã pin trong repo.

```bash
cd android
./gradlew :app:assembleDebug
```

`applicationId` là `com.cainghien.companion`, `minSdk` 26, `versionCode` 10 tương ứng `versionName` 3.5. APK build ra nằm ở `android/app/build/` và không được commit.

### Trang web

`web/` là Next.js 16, cần Node 20.9 trở lên cho cả `npm run dev` và `npm run build`.

```bash
cd web
npm install
npm run dev
```

Biến `NEXT_PUBLIC_SITE_URL` là nguồn duy nhất cho canonical, sitemap, robots và og:url. `lib/site.js` giữ tên file, dung lượng và SHA-256 của hai bản phát hành ở một chỗ, đổi release thì chỉ sửa file đó.

## Cấu trúc

| Đường dẫn | Nội dung |
| --- | --- |
| `enforcer/` | tiến trình nền, CLI, app Qt, DNS sinkhole, ghép nối |
| `android/` | source Android, Gradle wrapper, icons vector |
| `web/` | trang giới thiệu, hướng dẫn, tải về, vì sao an toàn |
| `tests/` | pytest |
| `packaging/` | PyInstaller spec, entry point exe, tài nguyên phiên bản và script bộ cài Inno Setup |
| `tools/` | `build_windows.py` đóng gói và tính SHA-256, `web_release.py` in số liệu release |
| `config.example.toml` | luật mẫu |
| `Tat_Enforcer_Admin.cmd` | nhấp đúp để tắt gấp: tự xin quyền Administrator, khôi phục DNS, xóa task, dừng daemon |

## Vì sao không phải virus

App này là phần mềm chặn xao nhãng nên phải đụng vào hệ thống nhiều hơn một app ghi chú. Đây là toàn bộ những gì nó thay đổi, không có mục ẩn:

| Thành phần | Khi nào có | Hoàn tác |
| --- | --- | --- |
| `%LOCALAPPDATA%\Programs\Kyluat` | chạy bộ cài | Add or remove programs, không cần quyền quản trị |
| `data\enforcer.db`, `data\backups\` | lần chạy đầu và mỗi ngày | xóa thư mục `data` |
| Scheduled Task `CaiNghienEnforcer` | bấm giữ cho app tự bật lại | `Kyluat-cli.exe turn-off`, bộ gỡ tự xóa |
| DNS card mạng trỏ về `127.0.0.1` | chỉ khi bật chặn web, cần Administrator | `net uninstall` khôi phục đúng DNS đã sao lưu |
| Policy DoH của Chrome và Edge trong registry | chỉ khi bật chặn web, cần Administrator | tắt bảo vệ trả về giá trị cũ từng trình duyệt |
| Rule tường lửa `CaiNghienPhone8766` | chỉ khi ghép nối điện thoại, profile private | `turn-off` xóa rule |
| Lối tắt Start Menu và màn hình nền | chạy bộ cài | bộ gỡ xóa cả hai |

App không sửa file `hosts`, không cài service hay driver, không chạm kernel, không đọc ảnh hay tài liệu, không keylogger. Nó ghi nhận tên tiến trình và tiêu đề cửa sổ đang mở để biết app nào đang chạy, và ghi tên miền bị chặn vào SQLite; tên miền không bị chặn không được ghi lại.

Mạch mạng đi ra ngoài chỉ có một: khi chặn web đang bật, sinkhole hỏi `1.1.1.1` và `8.8.8.8` cho tên miền không bị chặn. Không telemetry, không upload, không tự cập nhật, không có máy chủ của tác giả. Ghép nối điện thoại dùng mDNS và cổng 8766 trong mạng nội bộ.

File chưa mua chứng chỉ chữ ký số nên SmartScreen báo "Unknown publisher", và PyInstaller là định dạng hay bị ăn báo nhầm. Muốn tự kiểm:

```powershell
certutil -hashfile "$env:USERPROFILE\Downloads\KYLUAT-Setup-v1.1.exe" SHA256
schtasks /Query /TN CaiNghienEnforcer
netsh advfirewall firewall show rule name=CaiNghienPhone8766
Get-DnsClientServerAddress -AddressFamily IPv4
```

Cùng nội dung này, viết cho người dùng cuối, ở `/an-toan` trên trang web.

## Dữ liệu và quyền riêng tư

Lịch sử chặn, check-in và luật đang chạy nằm trong `data\enforcer.db`, một file SQLite duy nhất trên máy bạn, xoá được. Tự động backup giữ 7 bản gần nhất trong `data\backups\`. Dữ liệu không mã hoá: ai mở được máy bạn thì đọc được lịch sử, và token ghép nối cũng nằm trong file đó.

Không có telemetry. Không có tài khoản. File phát hành để trên GitHub Releases.

## Giới hạn của bản này

- Không có bản iOS, không có đồng bộ đám mây.
- Android chỉ chặn ứng dụng, không chặn trang web.
- Không có gì là không thể vượt qua. Người quyết định gỡ vẫn là bạn, và lối gỡ luôn còn: màn Nâng cao trong app, `Kyluat-cli.exe turn-off`, hoặc nhấp đúp `Tat_Enforcer_Admin.cmd`.
- Chặn web phụ thuộc DNS của máy. Trình duyệt bật DoH, ví dụ Firefox trong Settings → Privacy & Security, thì vẫn vượt được.

## Giấy phép và bảo hành

Repo kèm `LICENSE` theo MIT: bạn dùng, sửa, phân phối lại được, miễn giữ dòng bản quyền. Tác giả không bảo hành, không hỗ trợ kỹ thuật, không chịu trách nhiệm về hậu quả khi phần mềm giữ kỷ luật hộ bạn.

Bạn dùng ở máy khác thì đọc kỹ `enforcer/net.py` và `enforcer/guard.py` trước, vì hai chỗ đó sửa cấu hình mạng và tắt tiến trình của máy.
