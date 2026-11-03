import Icon from "@/components/icon";

export const metadata = {
  title: "Hướng dẫn",
  description:
    "Cài Kỷ luật trên Windows và Android từng bước, kèm lệnh kiểm tra và cách gỡ sạch.",
  alternates: { canonical: "/huong-dan" },
  openGraph: { title: "Hướng dẫn cài Kỷ luật", description: "Sáu bước cho Windows, chín bước cho Android.", url: "/huong-dan" },
};

export default function Page() {
  return (
    <main id="main">
      <section className="hero compact">
          <div className="wrap">
            <div>
              <p className="eyebrow">Hướng dẫn</p>
              <h1>Mười phút là vào nền nếp</h1>
              <p className="lede below">Bạn chỉ cần làm phần Windows. Phần Android là tuỳ chọn, dùng khi bạn muốn quản luôn điện thoại.</p>
            </div>
          </div>
        </section>

        <section className="alt tight" id="windows">
          <div className="wrap prose">
            <h2>Windows</h2>

            <div className="block">
              <h3>1. Chạy bộ cài</h3>
              <p>Mở <code>KYLUAT-Setup-v1.8.exe</code>. App đặt vào <code>%LOCALAPPDATA%\Programs\Kyluat</code> và có lối tắt trong Start Menu; bộ cài không đòi quyền Administrator.</p>
              <p>Không muốn cài gì thì tải bản <code>.zip</code>, giải nén thư mục <code>Kyluat</code> vào nơi bạn muốn rồi mở <code>Kyluat.exe</code>. Ở cách nào, dữ liệu cũng nằm trong <code>data\</code> cạnh file exe, chép cả thư mục là chuyển được máy.</p>
              <p>Lần mở đầu tiên, app hiện bộ thiết lập sáu bước: chọn app và trang web, đặt lịch, mục tiêu, tuỳ chọn chặn web và ghép điện thoại. Bước chọn mục chặn là bắt buộc, các bước còn lại bỏ qua được. Mở lại bằng <strong>Cài đặt</strong> → <strong>Chạy lại thiết lập</strong>.</p>
            </div>

            <div className="block">
              <h3>2. Khai ra thứ làm bạn xao nhãng</h3>
              <p>Vào màn <strong>Bảo vệ</strong>. Gạt công tắc với app có sẵn, hoặc chọn <strong>Thêm app khác</strong> để lấy đúng app đang cài trên máy.</p>
              <p>Cùng màn hình đó có mục <strong>Trang web</strong> cho tên miền. Thêm tên miền là chặn luôn các trang con.</p>
              <p>Ngay đầu màn là khối <strong>Nội dung 18+</strong>. Gạt <strong>Chặn trang người lớn</strong> là chặn hơn 200.000 tên miền có sẵn suốt ngày đêm, không chờ giờ tập trung. Luật này cần bật chặn web: bấm nút <strong>Bật chặn web</strong> ngay dưới công tắc (cần Administrator), hoặc làm ở bước 4. Tắt công tắc thì chờ 60 giây suy nghĩ, không cần gõ cam kết.</p>
              <p>Bên dưới là công tắc <strong>Chặn ảnh 18+ trên trình duyệt</strong>. Bật thì daemon chụp cửa sổ trình duyệt đang mở khoảng 4 giây một lần, chấm bằng mô hình ngay trên máy; thấy nội dung thì thu nhỏ cửa sổ và báo. Ảnh không được lưu hay gửi đi, tắt công tắc là dừng ngay. Có thể nhận nhầm ảnh bơi lội hoặc em bé.</p>
              <figure className="shot below">
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
              <p>Chặn web hoạt động bằng cách trỏ DNS của máy về chính nó. Danh sách trang bạn tự thêm chỉ bị chặn trong giờ tập trung, còn luật 18+ chạy cả ngày. Mở PowerShell bằng quyền Administrator rồi chạy:</p>
              <p><code>cd $env:LOCALAPPDATA\Programs\Kyluat</code><br /><code>.\Kyluat-cli.exe net install</code></p>
              <p>Muốn tắt: <code>.\Kyluat-cli.exe net uninstall</code>. Lệnh này khôi phục đúng DNS bạn đang dùng.</p>
              <p>Đang bật 18+ thì máy hỏi bộ lọc gia đình AdGuard (<code>94.140.14.14</code>) thay vì Cloudflare; <code>.\Kyluat-cli.exe adult status</code> đọc rõ đang dùng DNS nào.</p>
              <p>Bật xong, bấm <strong>Kiểm tra chặn thử</strong> ở màn Bảo vệ hoặc chạy <code>.\Kyluat-cli.exe adult test</code>. Nếu trình duyệt tự bật DoH, trang vẫn có thể mở dù DNS của máy đã bị chặn. Muốn kiểm tra mô hình nhận diện ảnh đã nạp được, chạy <code>.\Kyluat-cli.exe adult visual</code>.</p>
            </div>

            <div className="block">
              <h3>5. Kiểm tra xem máy đã vào nền nếp chưa</h3>
              <p><code>.\Kyluat-cli.exe doctor</code> đọc to mọi thứ: luật đang chạy, daemon còn sống, DNS có khớp, hôm nay chặn được bao nhiêu lần.</p>
              <p>Nếu bạn đổi Wi-Fi hoặc router làm lại DNS, chạy <code>.\Kyluat-cli.exe net repair</code>.</p>
            </div>

            <div className="block">
              <h3>6. Gỡ sạch khi không dùng nữa</h3>
              <p>Trong app: màn <strong>Nâng cao</strong> → <strong>Tắt bảo vệ</strong>, gõ lại đoạn cam kết và chờ 60 giây. Hết giờ, app tự kiểm tra lại DNS, task và daemon rồi mới báo đã tắt; nếu chưa tắt được nó nói rõ và đưa lệnh để bạn tự chạy.</p>
              <p>Rồi mở Add or remove programs, gỡ <strong>Kỷ luật</strong>. Bộ gỡ tự chạy <code>turn-off</code> trước nên DNS được khôi phục, task và rule tường lửa bị xóa; <code>data\</code> vẫn ở lại cho tới khi bạn tự xóa thư mục.</p>
            </div>

            <p className="note warn">
              <Icon name="alert" />
              <span>File exe chưa có chữ ký số. Windows SmartScreen sẽ hiện cảnh báo lần đầu: chọn More info rồi Run anyway, sau khi đã so SHA-256 ở trang tải về. Muốn xem app đụng vào gì trên máy, đọc <a href="/an-toan">Vì sao an toàn</a>.</span>
            </p>
          </div>
        </section>

        <section id="android">
          <div className="wrap prose">
            <h2>Android</h2>
            <p>Bản Android tự giữ luật trên chính cái máy bạn cầm, không cần máy tính bật suốt. Chín bước dưới đây là toàn bộ những gì phải làm.</p>

            <div className="block">
              <h3>1. Cài file APK</h3>
              <p>Tải <code>kyluat-android-v3.14.apk</code> sang điện thoại và mở file. Máy sẽ hỏi quyền cài từ nguồn không xác định, cho phép một lần là đủ.</p>
              <p>Lần mở đầu tiên có bộ thiết lập: cấp hai quyền cốt lõi, chọn app cần chặn, đặt quỹ và app gym, rồi tuỳ chọn ghép máy tính và lọc 18+. Sau đó app tự kiểm tra bản mới hằng ngày khi bạn để bật; tắt trong <strong>Cài đặt</strong> → <strong>Cập nhật</strong>.</p>
              <p>Android 8.0 trở lên. Không lên CH Play.</p>
              <p>File phát hành trên GitHub nên Google không nhận ra người gửi và hiện cảnh báo <strong>Ứng dụng chưa được xác minh</strong>; trên vài máy phải bấm <strong>Chi tiết khác</strong> rồi mới thấy <strong>Vẫn cài</strong>. Trước khi bấm, so dãy SHA-256 ở trang <a href="/tai-nguyen">Tải về</a> với file vừa tải để chắc nó không bị sửa giữa đường.</p>
              <p>Bản 3.6 trở lên cài đè nhau bình thường. Máy đang cài bản 3.5 thì gỡ bản đó trước, vì hai bản ký bằng hai key khác nhau.</p>
            </div>

            <div className="block">
              <h3>2. Cấp hai quyền bắt buộc</h3>
              <p>App sẽ dẫn bạn qua <strong>Hiển thị trên ứng dụng khác</strong> và <strong>Trợ năng</strong>. Trợ năng chỉ theo dõi cửa sổ nào đang ở phía trước; quyền đọc cây nội dung cửa sổ vẫn tắt. Khi bạn bật nhận diện ảnh, Android cấp thêm quyền chụp màn hình cho đúng service đó.</p>
              <p>Quyền thông báo là tuỳ chọn, cần nếu bạn muốn đồng hồ buổi tập hiện trên thanh trạng thái.</p>
            </div>

            <div className="block">
              <h3>3. Chọn app bị chặn</h3>
              <p>Ở tab <strong>Bảo vệ</strong>, app đang cài trên máy hiện ra để bạn gạt công tắc. Không có danh sách tự động, không có gợi ý app nào "nên chặn".</p>
              <p>Bấm <strong>Thêm app khác</strong> nếu app bạn cần không nằm trong danh sách đang hiện.</p>
              <p>Có những thứ app loại trừ cứng và bạn không chọn được: quay số, tin nhắn, Cài đặt và màn hình chính. App ngoài danh sách luôn mở bình thường.</p>
              <figure className="shot phone-shot inline-phone">
                <img src="/assets/img/android-bao-ve.png" alt="Tab Bảo vệ trên điện thoại: mỗi ứng dụng một dòng kèm công tắc" width="1080" height="2548" loading="lazy" />
                <figcaption>Tab Bảo vệ: một dòng một app, gạt là chặn.</figcaption>
              </figure>
            </div>

            <div className="block">
              <h3>4. Bật bộ lọc nội dung 18+ (tuỳ chọn)</h3>
              <p>Ở tab <strong>Bảo vệ</strong>, gạt <strong>Chặn trang người lớn</strong>. Android hỏi duyệt quyền VPN một lần: đây là đường hầm chỉ mang truy vấn DNS, không phải VPN đi web.</p>
              <p>Tên nào khớp danh sách hơn 200.000 tên miền bị trả lời là không tồn tại; trang còn lại đi hỏi bộ lọc gia đình AdGuard. Luật chạy suốt ngày đêm, không cần phiên tập trung.</p>
              <p>Cần Android 8.1 trở lên. Máy Android 8.0 không hiện công tắc này, vì mở đường hầm an toàn kiểu đó cần bản mới hơn.</p>
              <p>Bộ lọc tự rút nếu không gọi được máy chủ DNS và không tự bật lại sau khi lỗi. Thông báo đang chạy có nút <strong>Tắt bộ lọc</strong>; tắt bảo vệ trong app cũng tháo đường hầm. Trang trong app tự tra DNS riêng, ví dụ Chrome bật DoH, thì đi vòng qua bộ lọc.</p>
              <p>Bên dưới còn công tắc <strong>Chặn ảnh 18+ trên màn hình</strong>, cần Android 11 trở lên. Bật thì app đọc màn hình khoảng 5 giây một lần khi bạn đang ở trình duyệt, YouTube hoặc app mạng xã hội đã chọn, chạy mô hình ngay trên máy và đưa bạn về màn hình chính khi thấy nội dung. Ảnh chỉ nằm trong bộ nhớ, không lưu, không gửi đi; tắt công tắc là dừng ngay. Có thể nhận nhầm ảnh bơi lội hoặc em bé.</p>
              <p>Đây là lớp lọc tên miền: nội dung nằm trong YouTube hoặc trong một trang được phép không đi qua bộ lọc. Lớp nhận diện ảnh bổ sung cho khoảng trống đó nhưng không bảo đảm tuyệt đối. Danh sách mở rộng tham khảo BlockNSFW (MIT); bản 3.12 thêm nhận diện ảnh và giữ phần sửa đường hầm DNS của 3.11.</p>
            </div>

            <div className="block">
              <h3>5. Hiểu quy tắc quỹ mạng xã hội</h3>
              <p>App trong danh sách bị khoá theo mặc định. Muốn dùng, bạn mở Kỷ luật và bấm <strong>Nghỉ có kiểm soát</strong>, chọn 10, 15 hoặc 30 phút. Chỉ trong khoảng nghỉ đó thời gian lướt mới trừ vào quỹ.</p>
              <p>Quỹ là dùng chung cho cả nhóm app đã chọn, không phải mỗi app một tiếng, mặc định 60 phút và làm mới lúc 0:00 theo ngày trên máy. Hết giờ nghỉ là khoá lại, còn nghỉ thì bấm <strong>Kết thúc nghỉ</strong> bất cứ lúc nào.</p>
              <p>Đổi mức quỹ ở <strong>Cài đặt</strong> → <strong>Mục tiêu</strong> → <strong>Quỹ mạng xã hội</strong>: chọn từ 15 đến 120 phút, gõ lại đúng đoạn cam kết rồi chờ 30 giây. Giảm mức có hiệu lực ngay, tăng mức phải chờ đến 0:00 hôm sau.</p>
              <figure className="shot phone-shot inline-phone">
                <img src="/assets/img/android-doi-quy.png" alt="Bước chọn mức quỹ: các mức 15 đến 120 phút, đang chọn 90, kèm dòng giải thích tăng quỹ có hiệu lực từ 0:00 hôm sau" width="1080" height="2157" loading="lazy" />
                <figcaption>Bước 1 của ba bước đổi mức quỹ.</figcaption>
              </figure>
              <p>Đang có phiên focus chạy thì không xin nghỉ được. Muốn lướt trong lúc focus, bạn phải dừng phiên trước, và dừng giữa chừng thì vẫn còn chờ theo đúng cơ chế đã cam kết.</p>
              <figure className="shot phone-shot inline-phone">
                <img src="/assets/img/android-hom-nay.png" alt="Tab Hôm nay trên điện thoại: vòng quỹ mạng xã hội 42 phút còn lại và nút Kết thúc nghỉ" width="1080" height="2608" loading="lazy" />
                <figcaption>Tab Hôm nay: vòng quỹ và nút kết thúc nghỉ đang chạy.</figcaption>
              </figure>
            </div>

            <div className="block">
              <h3>6. Màn khoá nói cho bạn biết vì sao bị chặn</h3>
              <p>Lớp phủ hiện ngay trên mặt app với tiêu đề và lý do: app đang trong danh sách chặn, phiên focus đang chạy, hay đã dùng hết quỹ hôm nay. Trường hợp hết quỹ ghi luôn còn bao lâu tới 0:00.</p>
              <p>Hai nút luôn có ở trạng thái thường: <strong>Quay lại Kỷ luật</strong> và <strong>Dùng app khác</strong> để về màn hình chính. Màn khoá không giữ bạn ở lại.</p>
              <figure className="shot phone-shot inline-phone">
                <img src="/assets/img/android-man-khoa.png" alt="Lớp phủ màn khoá màu đậm với hai nút Quay lại Kỷ luật và Dùng app khác" width="1080" height="2400" loading="lazy" />
                <figcaption>Màn khoá thật, vẽ bằng chính app Kỷ luật.</figcaption>
              </figure>
            </div>

            <div className="block">
              <h3>7. Buổi tập gym</h3>
              <p>Ở tab <strong>Hôm nay</strong>, chọn app tập của bạn rồi bấm bắt đầu. Giữ app đó trên màn hình đủ 10 phút liên tục thì được tính một buổi.</p>
              <p>Rời app hoặc tắt màn hình là đếm lại từ đầu. Trong lúc tập, một đồng hồ nhỏ chạy ở giữa mép trên màn hình và có thông báo ở thanh trạng thái.</p>
              <p>Buổi tập được ghi sang máy tính khi hai máy đang ghép nối và cùng có mạng.</p>
            </div>

            <div className="block">
              <h3>8. Ghép với máy tính (tuỳ chọn)</h3>
              <p>Trên máy tính, mở <strong>Cài đặt</strong> → <strong>Tạo mã kết nối</strong>. Hai máy phải dùng chung một mạng Wi-Fi.</p>
              <p>Trên điện thoại, quét mã QR hoặc nhập 6 số. Mã hết hạn sau 5 phút và sai 5 lần thì phải lấy mã mới.</p>
              <p>Sau khi ghép, điện thoại hiện lịch học và lịch tập của máy tính, bấm bắt đầu phiên focus 25, 45 hoặc 60 phút từ điện thoại, và mục tiêu tuần hiển thị chung ở cả hai bên.</p>
              <p>Không ghép thì mọi thứ ở bước 3 đến bước 7 vẫn chạy đầy đủ.</p>
            </div>

            <div className="block">
              <h3>9. Tắt bảo vệ trên điện thoại</h3>
              <p>Trong app, màn <strong>Cài đặt</strong> → <strong>An toàn</strong> có lối tắt bảo vệ. Tắt theo cách này là ba bước: đọc cảnh báo 60 giây, gõ lại đúng đoạn cam kết (dán văn bản không được tính), chờ nốt 60 giây. Đường hầm lọc 18+ cũng bị tháo trong một lần đó. Bật lại thì một chạm, không chờ gì.</p>
              <p>Không có thời gian chờ 24 giờ trên điện thoại, và cũng không cần máy tính. Nhanh nhất là thu hồi quyền Trợ năng trong Cài đặt của máy: màn khoá biến mất lập tức.</p>
              <figure className="shot phone-shot inline-phone">
                <img src="/assets/img/android-cai-dat.png" alt="Màn Cài đặt trên điện thoại: mục Kết nối, Quyền, Mục tiêu và An toàn với dòng Tắt bảo vệ chữ đỏ" width="1080" height="2544" loading="lazy" />
                <figcaption>Màn Cài đặt: mục An toàn ở cuối trang là lối tắt tắt bảo vệ.</figcaption>
              </figure>
            </div>

            <p className="note">
              <Icon name="info" />
              <span>Điện thoại chỉ lọc được nội dung 18+ qua DNS. Muốn chặn một trang web tuỳ ý thì vẫn phải dùng bản Windows.</span>
            </p>
            <p className="note warn">
              <Icon name="alert" />
              <span>Năm ảnh điện thoại trong phần này dựng lại bằng code từ đúng màu, chữ và số đo trong mã giao diện, không phải ảnh chụp từ máy thật.</span>
            </p>
          </div>
        </section>

        <section className="alt" id="su-co">
          <div className="wrap prose">
            <h2>Gặp trục trặc</h2>
            <div className="grid two">
              <div className="card">
                <h3>Trang web không bị chặn</h3>
                <p className="muted">Kiểm tra <code>Kyluat-cli.exe net status</code>. Firefox tắt DoH thủ công trong Settings → Privacy &amp; Security.</p>
              </div>
              <div className="card">
                <h3>Điện thoại không tìm thấy máy tính</h3>
                <p className="muted">Mở firewall bằng PowerShell Administrator: <code>.\Kyluat-cli.exe phone firewall</code>. Router bật cách ly thiết bị thì không ghép được.</p>
              </div>
              <div className="card">
                <h3>Màn khoá Android không hiện lên app</h3>
                <p className="muted">Kiểm tra hai quyền ở màn <strong>Cài đặt</strong> → <strong>Quyền</strong>, rồi mở tab <strong>Bảo vệ</strong> xác nhận app đó đã được gạt công tắc. Thiếu một trong hai thì không có màn khoá.</p>
              </div>
              <div className="card">
                <h3>Bộ lọc 18+ trên Android chưa chặn được</h3>
                <p className="muted">Tab <strong>Bảo vệ</strong> phải ghi <strong>Đang lọc từng truy vấn DNS của máy</strong>. Máy dưới Android 8.1 không hiện công tắc này. Công tắc tự tắt nghĩa là không mở được đường hầm hoặc không gọi được máy chủ phân giải, lý do nằm trong thông báo. App tự tra DNS riêng, ví dụ Chrome bật DoH, thì đi vòng qua bộ lọc.</p>
              </div>
              <div className="card">
                <h3>Mạng bị mất sau khi tắt máy đột ngột</h3>
                <p className="muted">DNS còn trỏ về máy bạn. Chạy <code>.\Kyluat-cli.exe net uninstall</code> bằng quyền Administrator là về cũ.</p>
              </div>
              <div className="card">
                <h3>Muốn biết hôm nay máy đã chặn gì</h3>
                <p className="muted">Màn <strong>Nâng cao</strong> → <strong>Nhật ký gần đây</strong>. Lịch sử nằm trong <code>data\enforcer.db</code>.</p>
              </div>
            </div>
            <p className="note below">
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
              <li>Chặn theo tên miền nên phụ thuộc DNS: trình duyệt bật DoH hay app tự tra máy chủ phân giải riêng đều đi vòng qua được.</li>
              <li>Bộ lọc 18+ trên Android cần Android 8.1 trở lên.</li>
              <li>Không có gì là không thể vượt qua. Người quyết định gỡ vẫn là bạn.</li>
            </ul>
            <div className="actions below">
              <a className="btn primary" href="/tai-nguyen">Tải về</a>
              <a className="btn" href="/?q=faq-tat#hoi-dap">Cách tắt hẳn bảo vệ</a>
            </div>
          </div>
        </section>
    </main>
  );
}
