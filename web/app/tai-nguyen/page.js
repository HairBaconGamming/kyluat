import Icon from "@/components/icon";
import { WINDOWS, WINDOWS_ZIP, ANDROID } from "@/lib/site";

export const metadata = {
  title: "Tải về",
  description:
    "Tải Kỷ luật cho Windows và Android. Kèm dung lượng, mã SHA-256 và cách vượt cảnh báo SmartScreen, Play Protect.",
  alternates: { canonical: "/tai-nguyen" },
  openGraph: {
    title: "Tải về Kỷ luật cho Windows và Android",
    description: "Ba file, có dung lượng và SHA-256 để bạn tự kiểm tra.",
    url: "/tai-nguyen",
  },
};

export default function Page() {
  return (
    <main id="main">
      <section className="hero" style={{ paddingBottom: '40px' }}>
          <div className="wrap" style={{ display: 'block' }}>
            <p className="eyebrow">Tải về</p>
            <h1>Ba file, có số để kiểm tra</h1>
            <p className="lede" style={{ marginTop: '12px' }}>File phát hành nằm trên GitHub. Sau khi tải, chạy <code>certutil -hashfile ten_file SHA256</code> rồi so với dãy bên dưới.</p>
          </div>
        </section>

        <section className="alt" id="windows" style={{ paddingTop: '48px' }}>
          <div className="wrap">
            <div className="panel">
              <div className="top">
                <span className="ico"><Icon name="desktop" /></span>
                <div>
                  <h3>Windows 10 và 11, bản 64-bit</h3>
                  <p className="lede" style={{ fontSize: '14px' }}>Chạy <code>KYLUAT-Setup-v1.7.exe</code>: app cài vào thư mục của riêng bạn, có lối tắt trong Start Menu, gỡ bằng Add or remove programs. Không đòi quyền quản trị, không cần cài Python.</p>
                </div>
              </div>
              <div style={{ display: 'grid', gap: '10px' }}>
                <div className="file">
                  <span className="name">{WINDOWS.file}</span>
                  <span className="meta">{`${WINDOWS.size} · ${WINDOWS.note}`}</span>
                  <span className="meta">SHA-256</span>
                  <span className="hash">{WINDOWS.sha256}</span>
                </div>
                <div className="file">
                  <span className="name">{WINDOWS_ZIP.file}</span>
                  <span className="meta">{`${WINDOWS_ZIP.size} · ${WINDOWS_ZIP.note}`}</span>
                  <span className="meta">SHA-256</span>
                  <span className="hash">{WINDOWS_ZIP.sha256}</span>
                </div>
              </div>
              <div className="grid two">
                <div className="card">
                  <h3>Máy bạn nhận được gì</h3>
                  <ul>
                    <li><code>Kyluat.exe</code> trong <code>%LOCALAPPDATA%\Programs\Kyluat</code>: mở app, không có cửa sổ command prompt chớp lúc khởi động.</li>
                    <li><code>Kyluat-cli.exe</code>: cùng phần mềm nhưng để gõ lệnh, ví dụ <code>.\Kyluat-cli.exe doctor</code>.</li>
                    <li><code>config.toml</code>: luật mẫu, bạn sửa trong app là được. Cài đè bản mới không ghi đè luật cũ.</li>
                    <li>Lần mở đầu tiên có bộ thiết lập: chọn app và trang web, đặt lịch, mục tiêu, bật chặn web và ghép điện thoại.</li>
                    <li><code>data\</code>: lịch sử và sổ tay, chỉ sinh ra khi bạn dùng. Gỡ app vẫn giữ lại.</li>
                  </ul>
                </div>
                <div className="card">
                  <h3>Windows sẽ cảnh báo một lần</h3>
                  <p style={{ margin: '0', color: 'var(--ink-2)', fontSize: '14px' }}>File chưa mua chứng chỉ chữ ký số nên SmartScreen hiện "Unknown publisher". Chọn <strong>More info</strong> rồi <strong>Run anyway</strong> sau khi đã so SHA-256 ở trên. Vì sao file này không phải virus thì giải thích từng câu ở <a href="/an-toan">trang an toàn</a>.</p>
                </div>
              </div>
              <div className="actions" style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
                <a className="btn primary" href={WINDOWS.url}>Tải bộ cài .exe</a>
                <a className="btn" href={WINDOWS_ZIP.url}>Tải bản nén .zip</a>
                <a className="btn" href="/huong-dan#windows">Xem hướng dẫn cài</a>
              </div>
            </div>
          </div>
        </section>

        <section id="android">
          <div className="wrap">
            <div className="panel">
              <div className="top">
                <span className="ico"><Icon name="phone" /></span>
                <div>
                  <h3>Android 8.0 trở lên</h3>
                  <p className="lede" style={{ fontSize: '14px' }}>Cài file APK thủ công. Không lên CH Play.</p>
                </div>
              </div>
              <div className="file">
                <span className="name">{ANDROID.file}</span>
                <span className="meta">{`${ANDROID.size} · ${ANDROID.note}`}</span>
                <span className="meta">SHA-256</span>
                <span className="hash">{ANDROID.sha256}</span>
              </div>
              <div className="grid two">
                <div className="card">
                  <h3>Cài thế nào</h3>
                  <ul>
                    <li>Mở file vừa tải, cho phép "cài từ nguồn không xác định" nếu máy hỏi.</li>
                    <li>Bật Quyền Trợ năng cho Kỷ luật trong phần Cài đặt điện thoại.</li>
                    <li>Muốn lọc nội dung 18+ thì gạt thêm <strong>Chặn trang người lớn</strong> ở tab <strong>Bảo vệ</strong>; mục này cần Android 8.1 trở lên.</li>
                    <li>Đã cài bản cũ thì cài đè được, dữ liệu giữ nguyên.</li>
                    <li>Lần mở đầu tiên có bộ thiết lập 5 bước; mở lại được từ <strong>Cài đặt</strong>.</li>
                    <li>Tự kiểm tra bản mới hằng ngày khi bạn để bật, tắt được trong <strong>Cài đặt</strong>.</li>
                  </ul>
                </div>
                <div className="card">
                  <h3>Play Protect có thể hỏi bạn một lần</h3>
                  <p style={{ margin: '0', color: 'var(--ink-2)', fontSize: '14px' }}>File được ký bằng key phát hành riêng, không phải chữ ký debug, nhưng app không lên CH Play nên Google vẫn chưa nhận ra người gửi. Nếu máy báo "ứng dụng chưa được xác minh", so SHA-256 ở trên rồi chọn cài tiếp.</p>
                </div>
              </div>
              <div className="actions" style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
                <a className="btn primary" href={ANDROID.url}>Tải file .apk</a>
                <a className="btn" href="/huong-dan#android">Xem hướng dẫn cài</a>
              </div>
            </div>
          </div>
        </section>

        <section className="alt">
          <div className="wrap">
            <div className="head">
              <p className="eyebrow">Phiên bản</p>
              <h2>Lịch sử phát hành</h2>
            </div>
            <div className="block" style={{ overflowX: 'auto' }}>
              <table>
                <caption>Bản Windows 1.1 là bản đầu tiên có bộ cài đặt.</caption>
                <thead>
                  <tr><th scope="col">Bản</th><th scope="col">Ngày</th><th scope="col">Thay đổi</th></tr>
                </thead>
                <tbody>
                  <tr>
                    <td className="yes">Windows 1.7</td>
                    <td>02/11/2026</td>
                    <td>Danh sách 18+ mở rộng lên 204.935 tên miền (nguồn BlockNSFW, MIT), thêm nút <strong>Kiểm tra chặn thử</strong> và lệnh <code>adult test</code>.</td>
                  </tr>
                  <tr>
                    <td className="yes">Android 3.11</td>
                    <td>02/11/2026</td>
                    <td>Cùng danh sách lớn; sửa đường hầm DNS để không làm hỏng YouTube: xử lý truy vấn song song và hạ EDNS về 1232 để tránh chuyển sang TCP.</td>
                  </tr>
                  <tr>
                    <td className="yes">Android 3.10</td>
                    <td>02/11/2026</td>
                    <td>Sửa nhận diện quyền Trợ năng (Android lưu tên service dạng rút gọn), sửa icon app phình to, và làm lại bước chọn app: ô tìm, icon 38dp, công tắc và phụ đề "Ứng dụng đã cài" cho app ngoài nhóm mạng xã hội.</td>
                  </tr>
                  <tr>
                    <td className="yes">Windows 1.6</td>
                    <td>02/11/2026</td>
                    <td>Thêm nút <strong>Bật chặn web</strong> ngay dưới công tắc 18+ ở màn Bảo vệ, kèm trạng thái Cần bật hoặc Đang bật.</td>
                  </tr>
                  <tr>
                    <td className="yes">Windows 1.5</td>
                    <td>02/11/2026</td>
                    <td>Sửa bộ cài: đóng cưỡng bức tiến trình Kỷ luật đang chạy trước khi cài đè, không còn hộp thoại "unable to close applications" giữa chừng.</td>
                  </tr>
                  <tr>
                    <td className="yes">Windows 1.4</td>
                    <td>02/11/2026</td>
                    <td>Bộ thiết lập lần đầu: chọn app và web, đặt lịch, mục tiêu, bật chặn web và ghép điện thoại. Công tắc 18+ tắt được sau 60 giây suy nghĩ, mở lại wizard từ Cài đặt.</td>
                  </tr>
                  <tr>
                    <td className="yes">Android 3.9</td>
                    <td>02/11/2026</td>
                    <td>Bộ thiết lập lần đầu, tự kiểm tra bản mới hằng ngày, mục Thông tin app và bước cản gỡ cài đặt tuỳ chọn. Sửa công tắc 18+ không tắt được, màn khoá chớp khi mở app và hiện quỹ tiêu dần trong phiên nghỉ.</td>
                  </tr>
                  <tr>
                    <td className="yes">Windows 1.2</td>
                    <td>02/11/2026</td>
                    <td>Luật chặn nội dung 18+ chạy suốt ngày đêm: 81 tên miền có sẵn, công tắc ở màn <strong>Bảo vệ</strong>, thêm lệnh <code>Kyluat-cli.exe adult on|off|status</code>.</td>
                  </tr>
                  <tr>
                    <td className="yes">Android 3.7</td>
                    <td>02/11/2026</td>
                    <td>Cùng danh sách 18+ đó, lọc qua một đường hầm chỉ mang truy vấn DNS. Cần Android 8.1 trở lên và tự rút khi không gọi được máy chủ phân giải.</td>
                  </tr>
                  <tr>
                    <td className="yes">Windows 1.1</td>
                    <td>01/11/2026</td>
                    <td>Bộ cài riêng, gỡ sạch bằng Add or remove programs. Sửa lỗi app chết ngay khi mở (<code>WinError 50</code>), không còn cửa sổ command prompt chớp lúc mở app, tách <code>Kyluat-cli.exe</code> cho lệnh dòng lệnh.</td>
                  </tr>
                  <tr>
                    <td className="yes">Windows v1</td>
                    <td>01/11/2026</td>
                    <td>Đóng gói thành một file exe chạy độc lập, thêm lệnh <code>Kyluat.exe turn-off</code>.</td>
                  </tr>
                  <tr>
                    <td className="yes">Android 3.6</td>
                    <td>01/11/2026</td>
                    <td>Ký bằng key phát hành riêng thay cho chữ ký debug. Máy đang cài bản 3.5 phải gỡ rồi cài lại một lần.</td>
                  </tr>
                  <tr>
                    <td className="yes">Android 3.5</td>
                    <td>01/11/2026</td>
                    <td>Quỹ mạng xã hội đổi được trong khoảng 15–120 phút, kèm đoạn cam kết gõ tay.</td>
                  </tr>
                  <tr>
                    <td className="yes">Android 3.4</td>
                    <td>01/11/2026</td>
                    <td>Giữ màn khoá ổn định khi app khác chèn cửa sổ phụ, viết lại câu chữ cho ngắn.</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p className="note">
              <Icon name="info" />
              <span>Không có kênh cập nhật. Muốn lên bản mới, bạn tải file mới và cài đè.</span>
            </p>
          </div>
        </section>
    </main>
  );
}
