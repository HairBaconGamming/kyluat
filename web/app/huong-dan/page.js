import Icon from "@/components/icon";

export const metadata = {
  title: "Hướng dẫn",
  description:
    "Cài Kỷ luật trên Windows và Android từng bước, kèm lệnh kiểm tra và cách gỡ sạch.",
  alternates: { canonical: "/huong-dan" },
  openGraph: { title: "Hướng dẫn cài Kỷ luật", description: "Sáu bước cho Windows, tám bước cho Android.", url: "/huong-dan" },
};

export default function Page() {
  return (
    <main id="main">
      <section className="hero" style={{ paddingBottom: '32px' }}>
          <div className="wrap" style={{ display: 'block' }}>
            <p className="eyebrow">Hướng dẫn</p>
            <h1>Mười phút là vào nền nếp</h1>
            <p className="lede" style={{ marginTop: '12px' }}>Bạn chỉ cần làm phần Windows. Phần Android là tuỳ chọn, dùng khi bạn muốn quản luôn điện thoại.</p>
          </div>
        </section>

        <section className="alt" id="windows" style={{ paddingTop: '48px' }}>
          <div className="wrap prose">
            <h2>Windows</h2>

            <div className="block">
              <h3>1. Giải nén và chạy</h3>
              <p>Giải nén thư mục <code>Kyluat</code> vào nơi bạn muốn, ví dụ <code>D:\Kyluat</code>. Mở <code>Kyluat.exe</code>. Không có bước cài đặt, không cần Python.</p>
              <p>Toàn bộ dữ liệu nằm trong thư mục <code>data\</code> cạnh file exe. Muốn chuyển máy, bạn chép cả thư mục.</p>
            </div>

            <div className="block">
              <h3>2. Khai ra thứ làm bạn xao nhãng</h3>
              <p>Vào màn <strong>Bảo vệ</strong>. Gạt công tắc với app có sẵn, hoặc chọn <strong>Thêm app khác</strong> để lấy đúng app đang cài trên máy.</p>
              <p>Cùng màn hình đó có mục <strong>Trang web</strong> cho tên miền. Thêm tên miền là chặn luôn các trang con.</p>
              <figure className="shot" style={{ marginTop: '14px' }}>
                <img src="/assets/img/desktop-them-app.png" alt="Hộp thoại Thêm app với danh sách ứng dụng đang cài trên máy, có ô tìm kiếm" width="580" height="620" loading="lazy" />
                <figcaption>Hộp thoại Thêm app: chọn từ danh sách cài thật, không có gợi ý tự động.</figcaption>
              </figure>
            </div>

            <div className="block">
              <h3>3. Đặt giờ tập trung</h3>
              <p>Ở cột phải màn Bảo vệ, chọn <strong>Sửa lịch</strong>. Mỗi khung giờ có tên, các ngày trong tuần và giờ bắt đầu, kết thúc.</p>
              <p>Bắt đầu với hai khung mỗi ngày là đủ. Thêm khung giờ có hiệu lực ngay, bỏ khung giờ phải chờ 24 giờ.</p>
            </div>

            <div className="block">
              <h3>4. Bật chặn trang web (một lần, cần quyền Administrator)</h3>
              <p>Chặn web hoạt động bằng cách trỏ DNS của máy về chính nó trong giờ tập trung. Mở PowerShell bằng quyền Administrator rồi chạy:</p>
              <p><code>cd D:\Kyluat</code><br /><code>.\Kyluat.exe net install</code></p>
              <p>Muốn tắt: <code>.\Kyluat.exe net uninstall</code>. Lệnh này khôi phục đúng DNS bạn đang dùng.</p>
            </div>

            <div className="block">
              <h3>5. Kiểm tra xem máy đã vào nền nếp chưa</h3>
              <p><code>.\Kyluat.exe doctor</code> đọc to mọi thứ: luật đang chạy, daemon còn sống, DNS có khớp, hôm nay chặn được bao nhiêu lần.</p>
              <p>Nếu bạn đổi Wi-Fi hoặc router làm lại DNS, chạy <code>.\Kyluat.exe net repair</code>.</p>
            </div>

            <div className="block">
              <h3>6. Gỡ sạch khi không dùng nữa</h3>
              <p>Trong app: màn <strong>Nâng cao</strong> → <strong>Tắt bảo vệ</strong>, gõ lại đoạn cam kết và chờ 60 giây.</p>
              <p>Hoặc từ PowerShell Administrator: <code>.\Kyluat.exe turn-off</code>. Lệnh này khôi phục DNS, xóa task tự chạy và dừng tiến trình nền. Xong thì xoá thư mục là sạch.</p>
            </div>

            <p className="note warn">
              <Icon name="alert" />
              <span>File exe chưa có chữ ký số. Windows SmartScreen sẽ hiện cảnh báo lần đầu: chọn More info rồi Run anyway, sau khi đã so SHA-256 ở trang tải về.</span>
            </p>
          </div>
        </section>

        <section id="android">
          <div className="wrap prose">
            <h2>Android</h2>
            <p>Bản Android tự giữ luật trên chính cái máy bạn cầm, không cần máy tính bật suốt. Tám bước dưới đây là toàn bộ những gì phải làm.</p>

            <div className="block">
              <h3>1. Cài file APK</h3>
              <p>Tải <code>kyluat-android-v3.4.apk</code> sang điện thoại và mở file. Máy sẽ hỏi quyền cài từ nguồn không xác định, cho phép một lần là đủ.</p>
              <p>Android 8.0 trở lên. Không lên CH Play.</p>
            </div>

            <div className="block">
              <h3>2. Cấp hai quyền bắt buộc</h3>
              <p>App sẽ dẫn bạn qua <strong>Hiển thị trên ứng dụng khác</strong> và <strong>Trợ năng</strong>. Trợ năng khai báo ở mức thấp nhất: chỉ theo dõi cửa sổ nào đang ở phía trước, còn tuỳ chọn đọc nội dung cửa sổ bị tắt ngay trong file khai báo.</p>
              <p>Quyền thông báo là tuỳ chọn, cần nếu bạn muốn đồng hồ buổi tập hiện trên thanh trạng thái.</p>
            </div>

            <div className="block">
              <h3>3. Chọn app bị chặn</h3>
              <p>Ở tab <strong>Bảo vệ</strong>, app đang cài trên máy hiện ra để bạn gạt công tắc. Không có danh sách tự động, không có gợi ý app nào "nên chặn".</p>
              <p>Bấm <strong>Thêm app khác</strong> nếu app bạn cần không nằm trong danh sách đang hiện.</p>
              <p>Có những thứ app loại trừ cứng và bạn không chọn được: quay số, tin nhắn, Cài đặt và màn hình chính. App ngoài danh sách luôn mở bình thường.</p>
            </div>

            <div className="block">
              <h3>4. Hiểu quy tắc quỹ 60 phút</h3>
              <p>App trong danh sách bị khoá theo mặc định. Muốn dùng, bạn mở Kỷ luật và bấm <strong>Nghỉ có kiểm soát</strong>, chọn 10, 15 hoặc 30 phút. Chỉ trong khoảng nghỉ đó thời gian lướt mới trừ vào quỹ.</p>
              <p>Quỹ 60 phút là dùng chung cho cả nhóm app đã chọn, không phải mỗi app một tiếng. Quỹ làm mới lúc 0:00 theo ngày trên máy. Hết giờ nghỉ là khoá lại, còn nghỉ thì bấm <strong>Kết thúc nghỉ</strong> bất cứ lúc nào.</p>
              <p>Đang có phiên focus chạy thì không xin nghỉ được. Muốn lướt trong lúc focus, bạn phải dừng phiên trước, và dừng giữa chừng thì vẫn còn chờ theo đúng cơ chế đã cam kết.</p>
            </div>

            <div className="block">
              <h3>5. Màn khoá nói cho bạn biết vì sao bị chặn</h3>
              <p>Lớp phủ hiện ngay trên mặt app với tiêu đề và lý do: app đang trong danh sách chặn, phiên focus đang chạy, hay đã dùng hết 60 phút hôm nay. Trường hợp hết quỹ ghi luôn còn bao lâu tới 0:00.</p>
              <p>Hai nút luôn có ở trạng thái thường: <strong>Quay lại Kỷ luật</strong> và <strong>Dùng app khác</strong> để về màn hình chính. Màn khoá không giữ bạn ở lại.</p>
            </div>

            <div className="block">
              <h3>6. Buổi tập gym</h3>
              <p>Ở tab <strong>Hôm nay</strong>, chọn app tập của bạn rồi bấm bắt đầu. Giữ app đó trên màn hình đủ 10 phút liên tục thì được tính một buổi.</p>
              <p>Rời app hoặc tắt màn hình là đếm lại từ đầu. Trong lúc tập, một đồng hồ nhỏ chạy ở giữa mép trên màn hình và có thông báo ở thanh trạng thái.</p>
              <p>Buổi tập được ghi sang máy tính khi hai máy đang ghép nối và cùng có mạng.</p>
            </div>

            <div className="block">
              <h3>7. Ghép với máy tính (tuỳ chọn)</h3>
              <p>Trên máy tính, mở <strong>Cài đặt</strong> → <strong>Tạo mã kết nối</strong>. Hai máy phải dùng chung một mạng Wi-Fi.</p>
              <p>Trên điện thoại, quét mã QR hoặc nhập 6 số. Mã hết hạn sau 5 phút và sai 5 lần thì phải lấy mã mới.</p>
              <p>Sau khi ghép, điện thoại hiện lịch học và lịch tập của máy tính, bấm bắt đầu phiên focus 25, 45 hoặc 60 phút từ điện thoại, và mục tiêu tuần hiển thị chung ở cả hai bên.</p>
              <p>Không ghép thì mọi thứ ở bước 3 đến bước 6 vẫn chạy đầy đủ.</p>
            </div>

            <div className="block">
              <h3>8. Tắt bảo vệ trên điện thoại</h3>
              <p>Trong app, màn <strong>Cài đặt</strong> → <strong>An toàn</strong> có lối tắt bảo vệ. Tắt theo cách này là ba bước: đọc cảnh báo 60 giây, gõ lại đúng đoạn cam kết (dán văn bản không được tính), chờ nốt 60 giây. Bật lại thì một chạm, không chờ gì.</p>
              <p>Không có thời gian chờ 24 giờ trên điện thoại, và cũng không cần máy tính. Nhanh nhất là thu hồi quyền Trợ năng trong Cài đặt của máy: màn khoá biến mất lập tức.</p>
            </div>

            <p className="note">
              <Icon name="info" />
              <span>Điện thoại không chặn trang web. Muốn chặn web thì dùng bản Windows.</span>
            </p>
          </div>
        </section>

        <section className="alt" id="su-co">
          <div className="wrap prose">
            <h2>Gặp trục trặc</h2>
            <div className="grid two">
              <div className="card">
                <h3>Trang web không bị chặn</h3>
                <p style={{ margin: '0', color: 'var(--ink-2)' }}>Kiểm tra <code>Kyluat.exe net status</code>. Firefox tắt DoH thủ công trong Settings → Privacy &amp; Security.</p>
              </div>
              <div className="card">
                <h3>Điện thoại không tìm thấy máy tính</h3>
                <p style={{ margin: '0', color: 'var(--ink-2)' }}>Mở firewall bằng PowerShell Administrator: <code>.\Kyluat.exe phone firewall</code>. Router bật cách ly thiết bị thì không ghép được.</p>
              </div>
              <div className="card">
                <h3>Màn khoá Android không hiện lên app</h3>
                <p style={{ margin: '0', color: 'var(--ink-2)' }}>Kiểm tra hai quyền ở màn <strong>Cài đặt</strong> → <strong>Quyền</strong>, rồi mở tab <strong>Bảo vệ</strong> xác nhận app đó đã được gạt công tắc. Thiếu một trong hai thì không có màn khoá.</p>
              </div>
              <div className="card">
                <h3>Mạng bị mất sau khi tắt máy đột ngột</h3>
                <p style={{ margin: '0', color: 'var(--ink-2)' }}>DNS còn trỏ về máy bạn. Chạy <code>.\Kyluat.exe net uninstall</code> bằng quyền Administrator là về cũ.</p>
              </div>
              <div className="card">
                <h3>Muốn biết hôm nay máy đã chặn gì</h3>
                <p style={{ margin: '0', color: 'var(--ink-2)' }}>Màn <strong>Nâng cao</strong> → <strong>Nhật ký gần đây</strong>. Lịch sử nằm trong <code>data\enforcer.db</code>.</p>
              </div>
            </div>
            <p className="note" style={{ marginTop: '16px' }}>
              <Icon name="info" />
              <span>Đây là dự án cá nhân, không có kênh hỗ trợ. Bạn tự đọc lỗi và tự sửa là chính.</span>
            </p>
          </div>
        </section>

        <section id="gioi-han">
          <div className="wrap prose">
            <h2>Giới hạn của bản này</h2>
            <ul>
              <li>Không có bản iOS, không có đồng bộ đám mây.</li>
              <li>Không có trí tuệ nhân tạo nào đoán app gây nghiện cho bạn.</li>
              <li>Dữ liệu không được mã hoá, ai mở được máy bạn thì đọc được lịch sử.</li>
              <li>Không có gì là không thể vượt qua. Người quyết định gỡ vẫn là bạn.</li>
            </ul>
            <div className="actions" style={{ display: 'flex', gap: '10px', flexWrap: 'wrap', marginTop: '20px' }}>
              <a className="btn primary" href="/tai-nguyen">Tải về</a>
              <a className="btn" href="/?q=faq-tat#hoi-dap">Cách tắt hẳn bảo vệ</a>
            </div>
          </div>
        </section>
    </main>
  );
}
