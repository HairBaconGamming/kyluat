# Kỷ luật

Kỷ luật là máy chặn xao nhãng gồm bốn phần: một tiến trình nền Python và PySide6 trên Windows, một DNS sinkhole chạy ngay trên máy để chặn tên miền, một app Android phủ màn khoá lên ứng dụng đang mở, và trang web tải về viết bằng Next.js. Mã nguồn ở [github.com/HairBaconGamming/kyluat](https://github.com/HairBaconGamming/kyluat); thứ phát hành công khai duy nhất tính tới lúc này là [GitHub Releases v1.0](https://github.com/HairBaconGamming/kyluat/releases/tag/v1.0), vì trang web trong thư mục `web/` mới dựng được trên máy dev và chưa deploy.

![Màn hình Hôm nay trên Windows](web/public/assets/img/desktop-hom-nay.png)

Ảnh trên và ảnh dưới dùng dữ liệu demo, không phải lịch sử thật của ai.

## Luật bản hiện tại

| Việc | Bản này |
| --- | --- |
| Quỹ mạng xã hội mỗi ngày trên Android | 60 phút, làm mới lúc 0 giờ, không có nút gia hạn |
| Xin dừng một phiên tập trung giữa chừng | phải chờ 10 phút |
| Nới lỏng luật (bỏ chặn, giảm giờ, giảm cooldown) | có hiệu lực sau 24 giờ |
| Thêm ràng buộc mới | có hiệu lực ngay |
| Tắt hẳn bảo vệ | màn Nâng cao, gõ lại đoạn cam kết và chờ 60 giây |
| Chặn trang web | chỉ trên Windows, theo tên miền, tính cả trang con |

Danh sách app bị chặn không tự sinh ra. Máy không tự đoán cái nào gây nghiện.

## Tải về

Bản v1.0 có hai file:

- `Kyluat-windows-x64-v1.zip` cho Windows 10 và 11 bản 64-bit. Giải nén thư mục rồi mở `Kyluat.exe`. Không cần cài Python.
- `kyluat-android-v3.4.apk` cho Android 8.0 trở lên. Cài thủ công, không lên CH Play.

File exe chưa có chữ ký số, nên SmartScreen cảnh báo một lần: chọn More info rồi Run anyway sau khi đã so SHA-256 ghi trong trang release.

![Màn hình Bảo vệ](web/public/assets/img/desktop-bao-ve.png)

## Kiến trúc

Máy tính và điện thoại nói chuyện với nhau qua Wi-Fi nội bộ, mỗi bên tự giữ luật của mình.

- `enforcer/` chạy trên Windows. Tiến trình nền kiểm tra mỗi 3 giây và tắt tiến trình thuộc danh sách chặn trong giờ tập trung. Chặn web bằng cách chạy một DNS sinkhole ngay trên máy: nó trả `127.0.0.1` cho tên miền bị chặn và chuyển tiếp mọi truy vấn khác lên DNS cũ. `net install` trỏ DNS của card mạng về `127.0.0.1`, `net uninstall` khôi phục đúng DNS cũ.
- `android/` chạy trên Android. Lớp phủ toàn màn hình do một AccessibilityService vẽ, quyền Trợ năng chỉ dùng để biết app nào đang ở phía trước, không đọc nội dung màn hình. Không đổi DNS, không đổi chính sách hệ thống.
- Ghép nối: máy tính sinh mã 6 số và QR, hết hạn sau 5 phút, sai 5 lần thì phải lấy mã mới. Server chỉ lắng nghe trong mạng nội bộ, cổng 8766. Key nằm trong Android Keystore.

## Chạy từ mã nguồn

Yêu cầu tuỳ theo phần bạn chạy:

| Phần | Yêu cầu |
| --- | --- |
| `enforcer/` và `tests/` | Windows 10 hoặc 11, Python 3.12 |
| `android/` | JDK 17, Android SDK 34, Gradle 8.7 |
| `web/` | Node 20.9 trở lên, vì `next@16.4.3` khai báo `engines.node >= 20.9.0`. `npm run build` cũng cần chừng đó. |

Python 3.12 là môi trường tôi dev và test. `requirements.txt` chỉ đặt sàn `PySide6>=6.7` và không pin bản Python, nên bản thấp hơn chưa được kiểm chứng.

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

`applicationId` là `com.cainghien.companion`, `minSdk` 26, `versionCode` 9 tương ứng `versionName` 3.4. APK build ra nằm ở `android/app/build/` và không được commit.

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
