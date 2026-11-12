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
      <section className="hero compact">
          <div className="wrap">
            <div>
              <p className="eyebrow">Tải về</p>
              <h1>Ba file, có số để kiểm tra</h1>
              <p className="lede below">File phát hành nằm trên GitHub. Sau khi tải, chạy <code>certutil -hashfile ten_file SHA256</code> rồi so với dãy bên dưới.</p>
            </div>
          </div>
        </section>

        <section className="alt tight" id="windows">
          <div className="wrap">
            <div className="panel">
              <div className="top">
                <span className="ico"><Icon name="desktop" /></span>
                <div>
                  <h3>Windows 10 và 11, bản 64-bit</h3>
                  <p className="lede sm">Chạy <code>KYLUAT-Setup-v1.9.exe</code>: app cài vào thư mục của riêng bạn, có lối tắt trong Start Menu, gỡ bằng Add or remove programs. Không đòi quyền quản trị, không cần cài Python.</p>
                </div>
              </div>
              <div className="files">
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
                  <p className="muted">File chưa mua chứng chỉ chữ ký số nên SmartScreen hiện "Unknown publisher". Chọn <strong>More info</strong> rồi <strong>Run anyway</strong> sau khi đã so SHA-256 ở trên. Vì sao file này không phải virus thì giải thích từng câu ở <a href="/an-toan">trang an toàn</a>.</p>
                </div>
              </div>
              <div className="actions">
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
                  <p className="lede sm">Cài file APK thủ công. Không lên CH Play.</p>
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
                    <li>Android 11 trở lên có thêm công tắc <strong>Chặn ảnh 18+ trên màn hình</strong>: chỉ đọc màn hình trình duyệt, YouTube và app mạng xã hội bạn chọn, ảnh không lưu và không gửi đi.</li>
                    <li>Đã cài bản cũ thì cài đè được, dữ liệu giữ nguyên.</li>
                    <li>Lần mở đầu tiên có bộ thiết lập 5 bước; mở lại được từ <strong>Cài đặt</strong>.</li>
                    <li>Tự kiểm tra bản mới hằng ngày khi bạn để bật, tắt được trong <strong>Cài đặt</strong>.</li>
                  </ul>
                </div>
                <div className="card">
                  <h3>Play Protect có thể hỏi bạn một lần</h3>
                  <p className="muted">File được ký bằng key phát hành riêng, không phải chữ ký debug, nhưng app không lên CH Play nên Google vẫn chưa nhận ra người gửi. Nếu máy báo "ứng dụng chưa được xác minh", so SHA-256 ở trên rồi chọn cài tiếp.</p>
                </div>
              </div>
              <div className="actions">
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
            <div className="block table-wrap">
              <table className="stack timeline">
                <caption>Bản Windows 1.1 là bản đầu tiên có bộ cài đặt.</caption>
                <thead>
                  <tr><th scope="col">Bản</th><th scope="col">Ngày</th><th scope="col">Thay đổi</th></tr>
                </thead>
                <tbody>
                  <tr>
                    <td className="yes">Android 3.20</td>
                    <td>12/11/2026</td>
                    <td><strong>Bảng đếm thu gọn khi đang tập.</strong> Trong lúc tập, bảng dưới cùng thu thành thanh mỏng (số đếm + điểm + Dừng) để không che bàn chân và khung xương; chạm thanh để mở lại bảng đầy đủ, bấm "Thu gọn bảng" để thu lại. Nếu bàn chân vẫn khuất sau bảng, app hiện và đọc câu nhắc đặt máy xa hơn hoặc nâng camera. Giữ nguyên tỉ lệ 3/5/10 và các sửa nhận diện khớp của 3.19. Thay tầng nhận diện bằng MediaPipe Pose Landmarker (Apache-2.0, chạy cục bộ, không telemetry) với góc khớp 3D không phụ thuộc góc camera; bỏ ML Kit nên APK nhẹ hơn còn 60,60 MB. Chưa kiểm chứng trên điện thoại thật.</td>
                  </tr>
                  <tr>
                    <td className="yes">Android 3.19</td>
                    <td>12/11/2026</td>
                    <td><strong>Sửa nhận diện khớp cho màn tập camera.</strong> Khung xương vẽ đúng hệ tọa độ của khung xem trước thay vì phép đổi tự đoán; khớp được làm mượt, giữ tối đa 400ms khi mất dấu thoáng qua và đổi bên chỉ sau 600ms bằng chứng. Squat không cần thấy tay (khoanh tay vẫn đếm) và đo được góc chính diện bằng độ hạ hông; chống đẩy/plank nhìn chính diện sẽ được hướng dẫn xoay máy. Nhãn góc chỉ đổi sau 600ms ổn định nên không xoá nhịp đang đếm. Tỉ lệ điểm giữ nguyên 3/5/10. Chưa kiểm chứng trên điện thoại thật.</td>
                  </tr>
                  <tr>
                    <td className="yes">Android 3.18</td>
                    <td>11/11/2026</td>
                    <td><strong>Màn tập camera toàn màn hình, đa góc và giọng sửa tư thế.</strong> Camera tràn màn hình, tự nhận góc bên hông / chính diện / chếch 45°; giọng tiếng Việt chỉ đọc câu sửa tư thế khi chưa ổn (tắt được bằng nút loa, máy thiếu giọng thì còn chữ). Đổi tỉ lệ điểm: 3 chống đẩy = 1 điểm, 5 squat = 1 điểm, 10 giây plank = 1 điểm. Camera vẫn chỉ bật khi bấm Bắt đầu, dừng khi rời màn; ảnh không lưu/gửi, SDK ML Kit có gửi số liệu kỹ thuật tới Google. Chưa kiểm chứng trên điện thoại thật.</td>
                  </tr>
                  <tr>
                    <td className="yes">Android 3.17</td>
                    <td>11/11/2026</td>
                    <td><strong>Điểm theo thời gian và bài tập camera.</strong> Phiên tập trung 25/45/60 phút = 25/50/70 điểm; buổi tập 10/20/30/45/60 phút liên tục = 20/40/50/70/100 điểm (chỉ cộng phần chênh). Thêm chống đẩy 1 cái = 1 điểm, mỗi 2 squat = 2 điểm, mỗi 5 giây plank = 1 điểm. Camera chỉ bật khi bấm Bắt đầu, dừng khi rời màn; ảnh không lưu/gửi, SDK ML Kit có gửi số liệu kỹ thuật tới Google. Bộ đếm chưa được kiểm chứng trên điện thoại thật.</td>
                  </tr>
                  <tr>
                    <td className="yes">Android 3.16</td>
                    <td>10/11/2026</td>
                    <td><strong>Điểm học chỉ đến từ phiên tập trung.</strong> Hoàn thành một phiên tập trung — trên điện thoại hay trên máy tính đã ghép — được 25 điểm, đổi ra phút quỹ thưởng; bỏ hết cách cộng điểm bằng không chạm điện thoại và bằng app AI, nên Trợ năng không còn theo dõi thao tác chạm. Trợ năng chỉ nhận sự kiện đổi cửa sổ như bản 3.14, và mọi lỗi trong service được nuốt lại để Android không tắt quyền.</td>
                  </tr>
                  <tr>
                    <td className="yes">Android 3.15</td>
                    <td>08/11/2026</td>
                    <td><strong>Quỹ là của cả ngày.</strong> Mở app mạng xã hội là thời gian lướt trừ vào quỹ ngay, bỏ hẳn bước xin <em>Nghỉ có kiểm soát</em>; màn khoá chỉ còn hiện khi đang tập trung hoặc đã hết quỹ, và không còn tự ẩn vì chính cửa sổ của app. Đồng hồ đếm ngược nhỏ hiện số phút quỹ còn lại khi bạn đang lướt và đếm tới 10 phút khi đang tập. Điểm học thưởng thêm quỹ: 1 phút không chạm điện thoại = 5 điểm, 1 phút trong app AI (Gemini, ChatGPT, Claude...) = 2 điểm, 5 điểm đổi được 1 phút quỹ, trần 120 phút mỗi ngày. Mọi thao tác nới lỏng dùng chung một cổng cam kết 60 giây: mở một lần là chỉnh được hết trong 10 phút.</td>
                  </tr>
                  <tr>
                    <td className="yes">Windows 1.9</td>
                    <td>04/11/2026</td>
                    <td><strong>Không để mất mạng vì bộ lọc chết.</strong> Máy chỉ trỏ DNS về bộ lọc khi bộ lọc đã trả lời được, và nếu bộ lọc dừng giữa chừng thì app tự trả DNS về máy trong khoảng hai phút rồi báo cho bạn biết. Nút <strong>Tắt bảo vệ</strong> và lệnh gỡ đọc thẳng card mạng, nên cứu được máy kể cả khi dữ liệu app bị mất dấu; màn hình hiện ô đỏ khi mạng đang bị chặn. Chạy nhầm bản Python giờ hiện câu hướng dẫn thay vì lỗi kỹ thuật.</td>
                  </tr>
                  <tr>
                    <td className="yes">Android 3.14</td>
                    <td>03/11/2026</td>
                    <td>Lịch focus trên máy tính không còn khoá điện thoại: chỉ phiên bật tay mới chặn app và cấm tiêu quỹ, nên <strong>Nghỉ có kiểm soát</strong> mở được bình thường; khi vẫn bị chặn thì hộp thoại nêu đúng nguyên nhân. Nút <strong>Tắt bảo vệ</strong> thay bằng công tắc <strong>Bảo vệ</strong>, tắt vẫn phải qua 3 bước cản. Tự cập nhật lấy bản APK lớn nhất trong release và kiểm tra mỗi 12 giờ dù không mở app. Sửa icon bị ẩn trong ô app.</td>
                  </tr>
                  <tr>
                    <td className="yes">Windows 1.8</td>
                    <td>03/11/2026</td>
                    <td>Thêm công tắc <strong>Chặn ảnh 18+ trên trình duyệt</strong>: mô hình chạy trên máy đọc cửa sổ trình duyệt đang mở, thu nhỏ cửa sổ khi phát hiện. Ảnh không lưu, không gửi đi; lệnh kiểm tra <code>adult visual</code>.</td>
                  </tr>
                  <tr>
                    <td className="yes">Android 3.12</td>
                    <td>03/11/2026</td>
                    <td>Thêm <strong>Chặn ảnh 18+ trên màn hình</strong> cho Android 11+: chỉ đọc trình duyệt, YouTube và app mạng xã hội đã chọn, đưa về màn hình chính khi phát hiện. Model 17 MB làm APK tăng lên khoảng 27 MB.</td>
                  </tr>
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
