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

Bản v1.0 có hai file:

- `Kyluat-windows-x64-v1.zip` cho Windows 10 và 11 bản 64-bit. Giải nén thư mục rồi mở `Kyluat.exe`. Không cần cài Python.
- `kyluat-android-v3.5.apk` cho Android 8.0 trở lên. Cài thủ công, không lên CH Play.

File exe chưa có chữ ký số, nên SmartScreen cảnh báo một lần: chọn More info rồi Run anyway sau khi đã so SHA-256 ghi trong trang release.

![Màn hình Bảo vệ](web/public/assets/img/desktop-bao-ve.png)

## Kiến trúc

Máy tính và điện thoại nói chuyện với nhau qua Wi-Fi nội bộ, mỗi bên tự giữ luật của mình.

- `enforcer/` chạy trên Windows. Tiến trình nền kiểm tra mỗi 3 giây và tắt tiến trình thuộc danh sách chặn trong giờ tập trung.
- `android/` chạy trên Android. Màn khoá chỉ là một lớp phủ hình ảnh do `WindowManager.addView` với cửa sổ `TYPE_APPLICATION_OVERLAY` vẽ lên, còn AccessibilityService chỉ báo về app nào đang ở phía trước. Lớp phủ không lấy focus, không đổi chính sách hệ thống, không đọc nội dung màn hình. Hai nút trên màn khoá luôn còn: "Quay lại Kỷ luật" và "Dùng app khác"; thu hồi quyền Trợ năng là app dừng chặn ngay.
- Ghép nối: máy tính sinh mã 6 số và QR, hết hạn sau 5 phút, sai 5 lần thì phải lấy mã mới. Server chỉ lắng nghe trong mạng nội bộ, cổng 8766. Key nằm trong Android Keystore.

### Chặn web bằng DNS sinkhole local

Thay vì cấu hình router hay hosts, máy tính tự chạy một DNS server nhỏ trên chính nó. Tên miền trong danh sách chặn nhận câu trả lời `127.0.0.1` với TTL 5 giây, mọi truy vấn khác được chuyển tiếp nguyên trạng lên DNS cũ. Vì vậy chỉ máy này bị ảnh hưởng, router và các thiết bị khác không đổi theo, và đây là cơ chế chặn tên miền chứ không phải chặn thiết bị.

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

`desktop` mở app Qt. `dashboard` mở dashboard local trong trình duyệt. `net install` và `turn-off` cần PowerShell Administrator. `install` tạo Scheduled Task để daemon tự chạy và watchdog mỗi phút.

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

31 test, chạy được mà không cần quyền Administrator và không đụng tới hệ thống thật.

### Đóng gói exe Windows

```powershell
python -m PyInstaller packaging/Kyluat.spec
```

Ra thư mục `dist\Kyluat\`. Toàn bộ dữ liệu nằm trong `data\` cạnh file exe, nên chép cả thư mục là chuyển được máy.

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
| `web/` | trang giới thiệu, hướng dẫn, tải về |
| `tests/` | pytest |
| `packaging/` | PyInstaller spec và entry point cho exe |
| `tools/` | script hỗ trợ build |
| `config.example.toml` | luật mẫu |
| `Tat_Enforcer_Admin.cmd` | nhấp đúp để tắt gấp: tự xin quyền Administrator, khôi phục DNS, xóa task, dừng daemon |

## Dữ liệu và quyền riêng tư

Lịch sử chặn, check-in và luật đang chạy nằm trong `data\enforcer.db`, một file SQLite duy nhất trên máy bạn, xoá được. Tự động backup giữ 7 bản gần nhất trong `data\backups\`. Dữ liệu không mã hoá: ai mở được máy bạn thì đọc được lịch sử, và token ghép nối cũng nằm trong file đó.

Không có telemetry. Không có tài khoản. File phát hành để trên GitHub Releases.

## Giới hạn của bản này

- Không có bản iOS, không có đồng bộ đám mây.
- Android chỉ chặn ứng dụng, không chặn trang web.
- Không có gì là không thể vượt qua. Người quyết định gỡ vẫn là bạn, và lối gỡ luôn còn: màn Nâng cao trong app, `Kyluat.exe turn-off`, hoặc nhấp đúp `Tat_Enforcer_Admin.cmd`.
- Chặn web phụ thuộc DNS của máy. Trình duyệt bật DoH, ví dụ Firefox trong Settings → Privacy & Security, thì vẫn vượt được.

## Giấy phép và bảo hành

Dự án cá nhân, không bán, không bảo hành, không hỗ trợ kỹ thuật. Repo chưa kèm file LICENSE nên mặc định là toàn quyền bảo lưu. Bạn dùng ở máy khác thì đọc kỹ `enforcer/net.py` và `enforcer/guard.py` trước, vì hai chỗ đó sửa cấu hình mạng của máy.
