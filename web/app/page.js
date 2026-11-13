import FaqOpener from "@/components/faq-opener";
import Icon from "@/components/icon";

export const metadata = {
  alternates: { canonical: "/" },
};

export default function Page() {
  return (
    <main id="main">
      <section className="hero">
          <div className="wrap">
            <div>
              <p className="eyebrow">Máy chặn xao nhãng cho Windows và Android</p>
              <h1>Kỷ luật</h1>
              <p className="lede">Bạn không thiếu ý chí. Bạn thiếu một lớp cản giữa bạn và cơn thèm mở app.</p>
              <div className="actions">
                <a className="btn primary" href="/tai-nguyen">
                  <Icon name="download" />
                  Tải về
                </a>
                <a className="btn" href="#cach-hoat-dong">Xem cách hoạt động</a>
              </div>
              <p className="note">
                <Icon name="info" />
                <span>Chạy hoàn toàn trên máy của bạn. Không tài khoản, không máy chủ.</span>
              </p>
            </div>
            <figure className="shot">
              <img src="/assets/img/desktop-hom-nay.png" alt="Màn hình Hôm nay của Kỷ luật trên Windows, cho thấy phiên tập trung, mục tiêu học và gym" width="1180" height="840" loading="eager" />
              <figcaption>Màn hình Hôm nay trên Windows.</figcaption>
            </figure>
          </div>
        </section>

        <section className="alt" id="van-de">
          <div className="wrap">
            <div className="head">
              <p className="eyebrow">Vấn đề</p>
              <h2>Ý chí yếu đi trong vài giây. Luật thì không.</h2>
              <p className="lede">Kỷ luật biến quyết định của bạn lúc tỉnh táo thành ràng buộc mà bạn không thể gỡ ngay lúc yếu lòng.</p>
            </div>
            <div className="grid three">
              <div className="card stat">
                <p className="big">60 <span>phút</span></p>
                <p>Quỹ mạng xã hội mỗi ngày trên điện thoại, trừ ngay khi bạn mở app. Hoàn thành phiên tập trung hoặc tập thể dục để nhận điểm thưởng thêm quỹ.</p>
              </div>
              <div className="card stat">
                <p className="big">10 <span>phút</span></p>
                <p>Thời gian phải chờ nếu bạn xin dừng một phiên tập trung giữa chừng.</p>
              </div>
              <div className="card stat">
                <p className="big">24 <span>giờ</span></p>
                <p>Thời gian chờ khi bạn nới lỏng luật trên máy tính. Thêm ràng buộc thì có hiệu lực ngay.</p>
              </div>
            </div>
          </div>
        </section>

        <section id="cach-hoat-dong">
          <div className="wrap">
            <div className="head">
              <p className="eyebrow">Cách hoạt động · Windows</p>
              <h2>Trên máy tính: ba bước, sau đó máy tự giữ luật</h2>
              <p className="lede">Bạn chỉ việc khai ra danh sách và giờ giấc. Phần còn lại chạy nền. Điện thoại có cách riêng, đọc ở mục dưới.</p>
            </div>
            <div className="steps">
              <div className="step">
                <div>
                  <span className="num">1</span>
                  <h3>Bạn liệt kê thứ cần tránh</h3>
                  <p>Kỷ luật không tự đoán app nào gây nghiện. Bạn chọn cái nào, máy chặn đúng cái đó.</p>
                  <ul>
                    <li>App trên máy tính và trên điện thoại.</li>
                    <li>Trang web theo tên miền, tính cả các trang con.</li>
                    <li>Hơn 200.000 tên miền người lớn có sẵn, chặn cả ngày lẫn đêm trên cả hai máy.</li>
                  </ul>
                </div>
                <figure className="shot step-media">
                  <img src="/assets/img/desktop-bao-ve.png" alt="Màn hình Bảo vệ với danh sách app và trang web đã bật chặn" width="1180" height="840" loading="lazy" />
                  <figcaption>Màn Bảo vệ: danh sách bạn tự chọn, không có đề xuất tự động.</figcaption>
                </figure>
              </div>
              <div className="step">
                <div>
                  <span className="num">2</span>
                  <h3>Bạn đặt khung giờ tập trung</h3>
                  <p>Mỗi khung giờ có ngày và giờ bắt đầu, kết thúc. Đúng giờ là luật có hiệu lực, hết giờ là mở lại.</p>
                  <ul>
                    <li>Thêm khung giờ: áp dụng ngay.</li>
                    <li>Bỏ khung giờ: phải chờ 24 giờ.</li>
                  </ul>
                </div>
                <figure className="shot step-media">
                  <img src="/assets/img/desktop-lich-trinh.png" alt="Hộp thoại Lịch tập trung với ba khung giờ và các ngày trong tuần" width="920" height="500" loading="lazy" />
                  <figcaption>Lịch tập trung: ba ca mỗi tuần là đủ với đa số người.</figcaption>
                </figure>
              </div>
              <div className="step">
                <div>
                  <span className="num">3</span>
                  <h3>Máy giữ lời cho bạn</h3>
                  <p>Trong phiên, máy tính đóng đúng tiến trình bạn đã chọn và chặn tên miền qua DNS. Điện thoại phủ màn khoá lên app trong danh sách.</p>
                  <ul>
                    <li>Cuộc gọi, tin nhắn và Cài đặt không bao giờ bị chặn.</li>
                    <li>Mọi thứ ghi vào lịch sử trên máy bạn, xem được ở mục Nâng cao.</li>
                  </ul>
                </div>
                <figure className="shot step-media">
                  <img src="/assets/img/desktop-ghep-noi.png" alt="Hộp thoại Kết nối điện thoại với mã QR và mã sáu số" width="450" height="544" loading="lazy" />
                  <figcaption>Điện thoại ghép nối với máy tính bằng QR trong cùng mạng Wi-Fi.</figcaption>
                </figure>
              </div>
            </div>
          </div>
        </section>

        <section className="alt" id="nguyen-tac">
          <div className="wrap">
            <div className="head">
              <p className="eyebrow">Bốn nguyên tắc</p>
              <h2>Phần khó không phải là chặn. Phần khó là không tự nới lỏng.</h2>
            </div>
            <div className="grid two">
              <div className="card principle">
                <span className="ico"><Icon name="shield" /></span>
                <div>
                  <h3>Bạn chọn, máy không đoán</h3>
                  <p>Không có chuyện app tự quyết định gì là xấu cho bạn. Danh sách chặn do bạn viết ra.</p>
                </div>
              </div>
              <div className="card principle">
                <span className="ico"><Icon name="clock" /></span>
                <div>
                  <h3>Nới lỏng phải chờ 24 giờ</h3>
                  <p>Trên máy tính, thêm app thì bị chặn ngay, gỡ app ra thì chờ sang hôm sau. Cơn thèm mở YouTube lúc 11 giờ đêm không thắng được.</p>
                </div>
              </div>
              <div className="card principle">
                <span className="ico"><Icon name="lock" /></span>
                <div>
                  <h3>Tắt hẳn phải gõ lại cam kết</h3>
                  <p>Đọc cảnh báo 60 giây, gõ đúng từng chữ đoạn bạn đã viết, rồi chờ tiếp 60 giây. Dán văn bản không được tính.</p>
                </div>
              </div>
              <div className="card principle">
                <span className="ico"><Icon name="chart" /></span>
                <div>
                  <h3>Không can thiệp hệ thống</h3>
                  <p>Điện thoại đếm quỹ mạng xã hội của cả ngày và chỉ phủ màn khoá khi bạn đang tập trung hoặc đã hết quỹ. Bộ lọc 18+ là đường hầm DNS tự chọn, tắt một chạm là máy về như cũ; lớp nhận diện ảnh là công tắc riêng, mặc định tắt và chỉ đọc màn hình trình duyệt, mạng xã hội khi bạn bật. Cuộc gọi, Tin nhắn, Cài đặt và màn hình chính luôn mở. Bước cản gỡ cài đặt cũng là tuỳ chọn, mặc định tắt và Android luôn cho tắt trong Cài đặt.</p>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section id="giao-dien">
          <div className="wrap">
            <div className="head">
              <p className="eyebrow">Giao diện</p>
              <h2>Bốn màn hình mỗi thiết bị, không màn nào cần đọc sách hướng dẫn</h2>
              <p className="lede">Toàn bộ ảnh dưới đây dùng dữ liệu giả.</p>
            </div>
            <div className="gallery">
              <figure className="shot">
                <img src="/assets/img/desktop-bao-ve.png" alt="Màn Bảo vệ: danh sách app, trang web và lịch tập trung" width="1180" height="840" loading="lazy" />
                <figcaption>Bảo vệ</figcaption>
              </figure>
              <figure className="shot">
                <img src="/assets/img/desktop-cai-dat.png" alt="Màn Cài đặt: mục tiêu học, gym, nhắc nhở và trạng thái điện thoại" width="1180" height="840" loading="lazy" />
                <figcaption>Cài đặt</figcaption>
              </figure>
              <figure className="shot">
                <img src="/assets/img/desktop-nang-cao.png" alt="Màn Nâng cao: trạng thái chặn web, lịch sử sự kiện, bản sao lưu và liên kết tới dashboard" width="1180" height="840" loading="lazy" />
                <figcaption>Nâng cao</figcaption>
              </figure>
              <figure className="shot">
                <img src="/assets/img/desktop-tat-bao-ve.png" alt="Hộp thoại tắt bảo vệ yêu cầu đọc cảnh báo, gõ lại đoạn cam kết và chờ 60 giây" width="560" height="540" loading="lazy" />
                <figcaption>Hộp thoại Tắt bảo vệ</figcaption>
              </figure>
            </div>
            <h3 className="subhead">Trên điện thoại</h3>
            <div className="gallery phones">
              <figure className="shot phone-shot">
                <img src="/assets/img/android-hom-nay.png" alt="Màn Hôm nay trên điện thoại: đồng hồ phiên tập trung, vòng quỹ mạng xã hội còn 42 phút và ba ô số liệu" width="1080" height="2608" loading="lazy" />
                <figcaption>Hôm nay</figcaption>
              </figure>
              <figure className="shot phone-shot">
                <img src="/assets/img/android-bao-ve.png" alt="Màn Bảo vệ trên điện thoại: danh sách ứng dụng kèm công tắc chặn từng cái một" width="1080" height="2548" loading="lazy" />
                <figcaption>Bảo vệ</figcaption>
              </figure>
              <figure className="shot phone-shot">
                <img src="/assets/img/android-cai-dat.png" alt="Màn Cài đặt trên điện thoại: trạng thái kết nối máy tính, ba quyền đang bật và nút tắt bảo vệ" width="1080" height="2544" loading="lazy" />
                <figcaption>Cài đặt</figcaption>
              </figure>
              <figure className="shot phone-shot">
                <img src="/assets/img/android-het-quy.png" alt="Lớp phủ màn khoá khi đã hết quỹ: chỉ còn tiêu đề và thời gian tới 0:00, không có nút mở ứng dụng" width="1080" height="2400" loading="lazy" />
                <figcaption>Màn khoá lúc hết quỹ</figcaption>
              </figure>
            </div>
            <p className="note below">
              <Icon name="info" />
              <span>Ảnh điện thoại dựng lại bằng code từ đúng màu, chữ và số đo trong mã giao diện, không phải ảnh chụp thiết bị, và icon ứng dụng để dạng ô trung tính.</span>
            </p>
            <p className="note below">
              <Icon name="info" />
              <span>Phần kỹ thuật nằm riêng ở màn Nâng cao. Bạn không cần mở nó vẫn dùng được.</span>
            </p>
          </div>
        </section>

        <section className="alt" id="dien-thoai">
          <div className="wrap">
            <div className="head">
              <p className="eyebrow">Cách hoạt động · Android</p>
              <h2>Trên điện thoại: mở app là bắt đầu trừ quỹ của cả ngày</h2>
              <p className="lede">Mặc định 60 phút cho cả ngày, và nó trừ dần ngay khi bạn mở app trong danh sách chặn. Hoàn thành phiên tập trung thì được thưởng thêm phút, còn đổi mức thì phải qua cổng cam kết.</p>
            </div>
            <div className="steps">
              <div className="step">
                <div>
                  <span className="num">1</span>
                  <h3>Bạn gạch tên app, máy không tự đoán</h3>
                  <p>Ở tab <strong>Bảo vệ</strong>, app đang cài trên máy hiện ra để bạn gạt công tắc. Chọn cái nào thì chỉ cái đó bị chặn.</p>
                  <ul>
                    <li>App ngoài danh sách luôn mở bình thường.</li>
                    <li>Quay số, Tin nhắn, Cài đặt và màn hình chính bị loại trừ ngay trong mã, bạn không chọn được.</li>
                  </ul>
                </div>
                <figure className="shot phone-shot step-media">
                  <img src="/assets/img/android-bao-ve.png" alt="Màn Bảo vệ trên điện thoại với danh sách ứng dụng và công tắc chặn từng cái" width="1080" height="2548" loading="lazy" />
                  <figcaption>Tab Bảo vệ: gạt công tắc là chặn đúng app đó.</figcaption>
                </figure>
              </div>
              <div className="step">
                <div>
                  <span className="num">2</span>
                  <h3>Mở app là bắt đầu trừ quỹ</h3>
                  <p>Quỹ tính cho cả ngày. Mở một app trong danh sách chặn thì thời gian bạn lướt trừ dần vào quỹ ngay, không cần xin phiên nghỉ; một đồng hồ nhỏ trên đầu màn hình đếm số phút còn lại.</p>
                  <ul>
                    <li>Hết quỹ là màn khoá đóng lại tới 0:00, dù đang giữa ngày.</li>
                    <li>Đang chạy phiên tập trung thì app bị khoá và không trừ quỹ.</li>
                    <li>Phiên tập trung 25/45/60 phút được 25/50/70 điểm học; buổi tập đủ 10 phút cũng có điểm.</li>
                  </ul>
                </div>
                <figure className="shot phone-shot step-media">
                  <img src="/assets/img/android-hom-nay.png" alt="Màn Hôm nay trên điện thoại: vòng quỹ còn 42 phút và nút Kết thúc nghỉ đang chạy" width="1080" height="2608" loading="lazy" />
                  <figcaption>Vòng quỹ nhìn thấy ngay ở tab Hôm nay (ảnh bản 3.14).</figcaption>
                </figure>
              </div>
              <div className="step">
                <div>
                  <span className="num">3</span>
                  <h3>Màn khoá chỉ hiện khi hết đường dùng</h3>
                  <p>Màn khoá là một lớp phủ do app tự vẽ. Nó hiện tiêu đề và lý do ngay trên mặt app, không phải một trang web hay thông báo.</p>
                  <ul>
                    <li>Đang focus hoặc đã hết quỹ: tiêu đề, lý do và thời gian còn lại tới 0:00, không có nút mở app.</li>
                    <li>Còn quỹ thì không có màn khoá: app mở bình thường, chỉ có đồng hồ đếm ngược phía trên.</li>
                    <li>Nút <strong>Dùng app khác</strong> lúc nào cũng còn và đưa bạn về màn hình chính — màn khoá không giữ bạn ở lại.</li>
                  </ul>
                </div>
                <figure className="shot phone-shot step-media">
                  <img src="/assets/img/android-man-khoa.png" alt="Lớp phủ màn khoá màu đậm với tiêu đề APP NÀY ĐANG TRONG DANH SÁCH CHẶN và hai nút Quay lại Kỷ luật, Dùng app khác" width="1080" height="2400" loading="lazy" />
                  <figcaption>Màn khoá vẽ bằng chính app Kỷ luật (ảnh bản 3.14).</figcaption>
                </figure>
              </div>
            </div>
            <div className="grid two below-lg">
              <div className="card principle">
                <span className="ico"><Icon name="phone" /></span>
                <div>
                  <h3>Chặn được cả khi không có máy tính</h3>
                  <p>Quỹ, danh sách chặn và màn khoá chạy hoàn toàn trên điện thoại. Mất Wi-Fi thì luật vẫn còn, ghép nối chỉ để nhìn lịch học và lịch tập chung với máy tính.</p>
                </div>
              </div>
              <div className="card principle">
                <span className="ico"><Icon name="power" /></span>
                <div>
                  <h3>Tắt trên điện thoại không chờ 24 giờ</h3>
                  <p>Thu hồi quyền Trợ năng là dừng ngay. Còn trong app, tắt bảo vệ là ba bước: đọc cảnh báo 60 giây, gõ lại đoạn cam kết, chờ nốt 60 giây.</p>
                </div>
              </div>
              <div className="card principle">
                <span className="ico"><Icon name="dumbbell" /></span>
                <div>
                  <h3>Gym tính bằng 10 phút thật</h3>
                  <p>Giữ app tập trên màn hình đủ 10 phút liên tục mới được một buổi. Rời app hoặc tắt màn hình là đếm lại từ đầu, có đồng hồ chạy ở thanh trạng thái. Tập càng lâu càng nhiều điểm học: đủ 60 phút là 100 điểm.</p>
                </div>
              </div>
              <div className="card principle">
                <span className="ico"><Icon name="clock" /></span>
                <div>
                  <h3>Quỹ dùng chung, và bạn đổi được mức</h3>
                  <p>Một mức quỹ duy nhất dùng cho cả nhóm app đã chọn, không phải mỗi app một tiếng. Mặc định 60 phút, chỉnh được 15 đến 120 phút, và điểm học có thể thưởng thêm tới 120 phút nữa trong ngày.</p>
                </div>
              </div>
              <div className="card principle">
                <span className="ico"><Icon name="shield" /></span>
                <div>
                  <h3>Nội dung 18+ chặn riêng, suốt ngày đêm</h3>
                  <p>Công tắc <strong>Chặn trang người lớn</strong> ở tab Bảo vệ mở một đường hầm chỉ đọc DNS: tên trong danh sách hơn 200.000 tên miền bị trả lời là không tồn tại, trang khác đi qua bộ lọc gia đình AdGuard. Không cần phiên tập trung, không ghi lại truy vấn, và tắt được bằng một chạm.</p>
                  <p>Bật thêm <strong>Chặn ảnh 18+ trên màn hình</strong> để mô hình trên máy nhận diện ảnh trong trình duyệt và app mạng xã hội. Ảnh chỉ nằm trong bộ nhớ, không lưu, không gửi đi; tắt công tắc là dừng ngay.</p>
                </div>
              </div>
            </div>
            <p className="note warn below">
              <Icon name="alert" />
              <span>Ảnh điện thoại dựng lại bằng code từ màu, chữ và số đo trong mã giao diện, không phải ảnh chụp từ máy thật.</span>
            </p>
          </div>
        </section>

        <section id="tai-ve">
          <div className="wrap">
            <div className="head">
              <p className="eyebrow">Tải về</p>
              <h2>Một file cho máy tính, một file cho điện thoại</h2>
              <p className="lede">Cả hai đều là bản dành cho cá nhân, không có tài khoản đăng nhập.</p>
            </div>
            <div className="grid two">
              <div className="panel">
                <div className="top">
                  <span className="ico"><Icon name="desktop" /></span>
                  <div>
                    <h3>Windows 10 và 11 (64-bit)</h3>
                    <p className="lede sm">Bộ cài v1.9, không đòi quyền quản trị. Lần mở đầu tiên có bộ thiết lập sáu bước.</p>
                  </div>
                </div>
                <ul>
                  <li>Chặn app trong giờ tập trung, chặn trang web theo tên miền, và chặn hơn 200.000 tên miền người lớn suốt ngày đêm.</li>
                  <li>Lớp nhận diện ảnh 18+ tuỳ chọn trong cửa sổ trình duyệt, chạy hoàn toàn trên máy.</li>
                  <li>Công tắc 18+ tắt được sau 60 giây suy nghĩ, không cần gõ cam kết.</li>
                  <li>Cài vào thư mục của riêng bạn, gỡ bằng Add or remove programs.</li>
                  <li>File chưa có chữ ký số, Windows sẽ hỏi bạn một lần.</li>
                </ul>
                <a className="btn primary" href="/tai-nguyen">Tải bản Windows</a>
              </div>
              <div className="panel">
                <div className="top">
                  <span className="ico"><Icon name="phone" /></span>
                  <div>
                    <h3>Android 8.0 trở lên</h3>
                    <p className="lede sm">File <code>APK</code>, cài thủ công.</p>
                  </div>
                </div>
                <ul>
                  <li>Quỹ mạng xã hội mỗi ngày, mặc định 60 phút và đổi được, cùng màn khoá trong phiên.</li>
                  <li>Bộ thiết lập lần đầu và tự kiểm tra bản mới hằng ngày, tắt được trong Cài đặt.</li>
                  <li>Bật thêm bộ lọc 18+ thì máy cần Android 8.1 trở lên; trang web tuỳ ý vẫn thuộc về bản Windows.</li>
                  <li>Nhận diện ảnh 18+ cần Android 11 trở lên; chỉ đọc màn hình trình duyệt, YouTube và app mạng xã hội bạn chọn.</li>
                  <li>File ký bằng key phát hành riêng, không lên CH Play nên máy vẫn hỏi bạn một lần.</li>
                </ul>
                <a className="btn primary" href="/tai-nguyen#android">Tải bản Android</a>
              </div>
            </div>
          </div>
        </section>

        <section className="alt" id="hoi-dap">
          <div className="wrap">
            <div className="head">
              <p className="eyebrow">Hỏi đáp</p>
              <h2>Những câu hay bị hỏi nhất</h2>
            </div>
            <div className="faq">
              <details id="faq-du-lieu">
                <summary>Dữ liệu của tôi đi đâu?</summary>
                <div className="body">
                  <p>Luật, lịch sử chặn và sổ học tập nằm trên máy bạn; dữ liệu Windows ở một file SQLite, xem được ở mục Nâng cao. Ảnh camera dùng để đếm bài tập được xử lý tại máy, không lưu hoặc gửi đi.</p>
                  <p>Từ 3.20, bộ đếm bài tập dùng MediaPipe Pose Landmarker chạy hoàn toàn trên máy, không gửi số liệu hay ảnh đi đâu; các bản 3.17–3.19 dùng ML Kit có gửi số liệu kỹ thuật tới Google. <a href="/an-toan#camera-bai-tap">Đọc chi tiết quyền riêng tư</a>.</p>
                  <p>Máy tính và điện thoại nói chuyện với nhau qua Wi-Fi nội bộ, không có máy chủ trung gian.</p>
                </div>
              </details>
              <details id="faq-virus">
                <summary>Windows báo file này không an toàn. Có thật sự an toàn không?</summary>
                <div className="body">
                  <p>Cảnh báo đó là SmartScreen, vì exe chưa mua chứng chỉ chữ ký số nên Windows không biết ai làm ra nó, cộng thêm việc đóng gói bằng PyInstaller hay bị công cụ diệt virus đoán nhầm. Mã nguồn mở ở <a href="https://github.com/HairBaconGamming/kyluat">github.com/HairBaconGamming/kyluat</a>, và <a href="/an-toan">trang Vì sao an toàn</a> liệt kê từng thứ app đụng vào kèm lệnh để bạn tự kiểm.</p>
                  <p>Vẫn nên so SHA-256 trước khi chạy, và đừng tắt Windows Defender để app chạy.</p>
                </div>
              </details>
              <details id="faq-go">
                <summary>Gỡ khỏi máy thì có để lại gì không?</summary>
                <div className="body">
                  <p>Bộ gỡ tắt bảo vệ trước (xóa task, khôi phục DNS, bỏ rule tường lửa) rồi mới xóa file. Nếu đang bật chặn web mà không tắt được, nó từ chối gỡ để bạn không mất internet giữa chừng.</p>
                  <p>Thư mục <code>data\</code> giữ lại sổ học tập và lịch sử chặn cho tới khi bạn tự xóa.</p>
                </div>
              </details>
              <details id="faq-man-hinh">
                <summary>App có đọc nội dung trên màn hình không?</summary>
                <div className="body">
                  <p>Không. Quyền Trợ năng trên Android chỉ nhận sự kiện đổi cửa sổ để biết app nào đang ở phía trước và để phủ màn khoá; app không theo dõi chạm, cuốn hay gõ phím.</p>
                  <p>Tuỳ chọn đọc nội dung cửa sổ bị tắt ngay trong file khai báo của app. Ngoại lệ duy nhất là <strong>Chặn ảnh 18+ trên màn hình</strong>: khi bạn tự bật thì app mới chụp màn hình của trình duyệt và các app mạng xã hội đã chọn, chạy mô hình trên máy, không lưu và không gửi đi.</p>
                </div>
              </details>
              <details id="faq-quy">
                <summary>Quỹ mạng xã hội trên điện thoại tiêu thế nào?</summary>
                <div className="body">
                  <p>Quỹ tính cho cả ngày. Mở một app trong danh sách chặn là thời gian lướt trừ dần vào quỹ ngay, không cần xin phiên nghỉ như bản cũ; trên đầu màn hình có đồng hồ nhỏ đếm số phút còn lại.</p>
                  <p>Hết quỹ thì màn khoá đóng app đó và nói rõ còn bao lâu tới 0:00. Đang chạy phiên tập trung thì app bị khoá và không trừ quỹ.</p>
                </div>
              </details>
              <details id="faq-diem-hoc">
                <summary>Điểm học là gì và đổi được gì?</summary>
                <div className="body">
                  <p>Điểm học đến từ học và tập. Phiên tập trung chạy hết giờ: 25/45/60 phút được 25/50/70 điểm, dù phiên bật trên điện thoại hay trên máy tính đã ghép nối. Buổi tập đủ thời gian: 10/20/30/45/60 phút liên tục trong app tập được 20/40/50/70/100 điểm, mỗi bậc chỉ cộng thêm phần chênh nên một giờ tập ra đúng 100 điểm. Camera còn tính 3 chống đẩy = 1 điểm, 5 squat = 1 điểm, 10 giây plank = 1 điểm. Cứ 5 điểm thành 1 phút quỹ thưởng — mặc định vậy và chỉnh được từ 3 tới 30 điểm một phút.</p>
                  <p>Dừng phiên giữa chừng thì không có điểm, kể cả khi bạn đã ngồi được 20 phút. Phút thưởng cộng thẳng vào mức quỹ hôm đó nên học đủ nhiều phiên có thể mở lại app đã cạn quỹ. Điểm về 0 lúc 0:00 và trần 120 phút thưởng mỗi ngày. Muốn đổi tỷ lệ điểm thì phải qua cổng cam kết.</p>
                </div>
              </details>
              <details id="faq-doi-quy">
                <summary>Đổi mức quỹ 60 phút thì làm thế nào?</summary>
                <div className="body">
                  <p>Ở tab <strong>Cài đặt</strong> → <strong>Mục tiêu</strong> → <strong>Quỹ mạng xã hội</strong>. Chọn mức từ 15 đến 120 phút. Giảm có hiệu lực ngay; tăng thì app hiện ô báo "Đã hẹn mức X phút từ 0:00 hôm sau" và có nút <strong>Hủy lịch đổi</strong> nếu bạn đổi ý.</p>
                  <p>Tăng quỹ là nới lỏng nên phải qua cổng cam kết 60 giây: đọc cảnh báo, gõ lại đúng đoạn cam kết (dán văn bản không được tính), chờ đủ 60 giây. Siết mức thì làm ngay, không cần cổng.</p>
                </div>
              </details>
              <details id="faq-cam-ket">
                <summary>Một lần cam kết mở được những gì?</summary>
                <div className="body">
                  <p>Từ bản 3.15, mọi thao tác nới lỏng trên điện thoại dùng chung một cổng: tăng quỹ, bỏ app ra khỏi danh sách bị chặn, đổi tỷ lệ điểm học và tắt hẳn bảo vệ. Bạn chỉ phải làm một lần, rồi được quyền chỉnh những thứ đó trong 10 phút; hết 10 phút thì cổng tự đóng lại.</p>
                  <p>Siết luật thì làm ngay, không qua cổng: thêm app vào danh sách chặn, giảm quỹ, giảm tỷ lệ điểm.</p>
                  <p>Cổng tính cả hai đồng hồ — giờ hệ thống và thời gian máy đã bật — nên đổi giờ máy không kéo dài được thời gian mở, và khởi động lại máy thì cổng đóng sớm.</p>
                </div>
              </details>
              <details id="faq-mat-ket-noi">
                <summary>Điện thoại có cần máy tính bật suốt không?</summary>
                <div className="body">
                  <p>Không. Danh sách chặn, quỹ và màn khoá chạy hết trên máy bạn. Ghép nối chỉ để hai bên nhìn chung lịch học và lịch tập, và để bấm bắt đầu hoặc dừng phiên từ điện thoại.</p>
                  <p>Mất Wi-Fi thì điện thoại vẫn chặn như cũ.</p>
                </div>
              </details>
              <details id="faq-man-khoa">
                <summary>Màn khoá có giữ bạn ở lại không?</summary>
                <div className="body">
                  <p>Không. Lớp phủ không nhận thao tác bàn phím và luôn có nút <strong>Dùng app khác</strong> đưa bạn về màn hình chính; mở lại Kỷ luật từ đó thì màn khoá tự tắt.</p>
                  <p>Lúc đã hết quỹ hoặc đang trong phiên focus, màn khoá chỉ hiện tiêu đề, lý do và thời gian còn lại — không có nút mở tiếp app.</p>
                </div>
              </details>
              <details id="faq-tat-android">
                <summary>Tắt bảo vệ trên điện thoại mất bao lâu?</summary>
                <div className="body">
                  <p>Trong app là cổng cam kết chung: đọc cảnh báo 60 giây, gõ lại đúng đoạn cam kết (dán văn bản không được tính), chờ nốt 60 giây. Cổng này cũng chính là cổng dùng cho tăng quỹ và bỏ app bị chặn, nên nếu bạn vừa mở cổng thì tắt bảo vệ không phải chờ lại từ đầu. Không có thời gian chờ 24 giờ như trên máy tính.</p>
                  <p>Thu hồi quyền Trợ năng trong Cài đặt thì dừng ngay lập tức.</p>
                </div>
              </details>
              <details id="faq-web">
                <summary>Chặn trang web hoạt động tới đâu?</summary>
                <div className="body">
                  <p>Trên Windows, máy tính chặn theo tên miền trong giờ tập trung, tính cả các trang con.</p>
                  <p>Trên Android chỉ có bộ lọc 18+ chạy suốt ngày đêm. Muốn chặn một trang tuỳ ý thì vẫn phải dùng máy tính.</p>
                </div>
              </details>
              <details id="faq-18">
                <summary>Bộ lọc 18+ chặn những gì và chặn kiểu gì?</summary>
                <div className="body">
                  <p>Một danh sách hơn 200.000 tên miền dùng chung cho cả hai máy, chặn suốt ngày đêm, không liên quan giờ tập trung. Trên Windows nó đi qua trình phân giải cục bộ; trên Android nó đi qua một đường hầm chỉ đọc DNS.</p>
                  <p>Tên bị chặn nhận trả lời "không tồn tại", còn mọi tên khác được hỏi sang bộ lọc gia đình AdGuard, nên lớp lọc nằm luôn ở máy chủ phân giải. Tắt trên Windows phải chờ 24 giờ vì đó là nới lỏng luật; tắt trên Android có hiệu lực ngay.</p>
                  <p>Bản 1.8 thêm lớp thứ hai, mặc định tắt: mô hình nhận diện ảnh chạy trên máy, đọc cửa sổ trình duyệt trên Windows hoặc màn hình trình duyệt, YouTube, mạng xã hội trên Android 11+. Lớp này bắt được phần nội dung mà DNS không thấy, nhưng có thể nhận nhầm và không bảo đảm tuyệt đối.</p>
                </div>
              </details>
              <details id="faq-18-youtube">
                <summary>Có chặn được nội dung 18+ nằm trong YouTube không?</summary>
                <div className="body">
                  <p>Lớp DNS thì không: nó chỉ đọc tên miền, nên nội dung trong YouTube hoặc trong một trang được phép không đi qua nó. Trình duyệt tự bật DNS-over-HTTPS cũng đi vòng được.</p>
                  <p>Lớp nhận diện ảnh thì có thể bắt hình ảnh trên màn hình: trên Windows nó đọc cửa sổ trình duyệt đang mở, trên Android 11+ nó đọc màn hình YouTube, trình duyệt và app mạng xã hội bạn chọn. Nó chấm khoảng 4–5 giây một lần và đưa bạn ra khỏi nội dung; đoạn video ngắn hơn nhịp quét hoặc hình ảnh bị DRM làm đen có thể lọt. Đây là lớp giảm thiểu, không phải bảo đảm tuyệt đối.</p>
                </div>
              </details>
              <details id="faq-18-visual">
                <summary>Lớp nhận diện ảnh đọc gì, có gửi ảnh đi đâu không?</summary>
                <div className="body">
                  <p>Trên Windows, nó chỉ chụp cửa sổ trình duyệt đang ở phía trước. Trên Android 11+, nó chỉ đọc màn hình khi app đang mở là trình duyệt, YouTube hoặc app mạng xã hội bạn đã chọn; không quét ngân hàng, tin nhắn hay ảnh riêng. Mặc định công tắc tắt.</p>
                  <p>Ảnh chỉ tồn tại trong bộ nhớ trong lúc chấm điểm, không ghi ra file và không gửi lên mạng. Có thể nhận nhầm ảnh bơi lội hoặc em bé; tắt công tắc là dừng ngay, không phải chờ 60 giây.</p>
                </div>
              </details>
              <details id="faq-vpn-android">
                <summary>Bật bộ lọc 18+ trên Android có làm mất mạng không?</summary>
                <div className="body">
                  <p>Đường hầm chỉ mang gói tin DNS gửi tới một địa chỉ nội bộ giả định, nên các kết nối khác vẫn ra Wi-Fi như cũ. Hai máy chủ phân giải cùng im lặng là app tự tháo đường hầm và máy trở về DNS bình thường; thiết kế là fail-open vì mất mạng nặng hơn chưa chặn.</p>
                  <p>Cần Android 8.1 trở lên; máy Android 8.0 thì tab Bảo vệ không hiện công tắc này. Lớp nhận diện ảnh cần thêm Android 11 trở lên. App không tự bật lại sau khi lỗi, và bạn tắt bằng một chạm. Đây vẫn là tính năng mới, chưa được kiểm chứng trên mọi loại máy.</p>
                </div>
              </details>
              <details id="faq-gym">
                <summary>Buổi gym được tính thế nào?</summary>
                <div className="body">
                  <p>Giữ app tập của bạn trên màn hình đủ 10 phút liên tục thì được một buổi. Rời app hoặc tắt màn hình là đếm lại từ đầu, nhưng điểm đã nhận không mất. Cùng đồng hồ đó còn tính điểm học: 10/20/30/45/60 phút = 20/40/50/70/100 điểm, đổi ra phút quỹ lướt.</p>
                  <p>Bản 3.19–3.21 sửa màn tập camera: khung xương bám đúng người (3.21 chiếu đúng hệ tọa độ khung nhìn), khớp được làm mượt và không xoá nhịp đang đếm khi nhãn góc thoáng đổi; bảng đếm thu thành thanh mỏng khi đang tập để thấy cả bàn chân, app nhắc khi chân còn khuất. Squat khoanh tay hoặc nhìn chính diện vẫn đếm được; plank cần góc bên hông hoặc chếch 45°; chống đẩy từ 3.21 chỉ cần nửa người trên và đếm được cả chính diện khi có khớp 3D, thân phải gần song song sàn. Màn tập toàn màn hình, giọng tiếng Việt đọc câu sửa tư thế khi chưa ổn (tắt được bằng nút loa). Tỉ lệ: 3 chống đẩy = 1 điểm, 5 squat = 1 điểm, 10 giây plank = 1 điểm. Phải đủ nhịp động tác hoặc giữ đúng tư thế; camera dừng khi bạn bấm Dừng hay rời màn. Ảnh không lưu hoặc gửi đi; <a href="/an-toan#camera-bai-tap">xem quyền riêng tư của SDK nhận diện</a>.</p>
                </div>
              </details>
              <details id="faq-tat">
                <summary>Tôi có tắt hẳn được không?</summary>
                <div className="body">
                  <p>Được. Ở máy tính, mở màn Nâng cao rồi gõ lại đoạn cam kết. Ở điện thoại, tắt quyền Trợ năng là dừng ngay.</p>
                  <p>Không có chế độ nào khoá máy bạn vĩnh viễn.</p>
                </div>
              </details>
              <details id="faq-ios">
                <summary>Có bản iOS hay đồng bộ đám mây không?</summary>
                <div className="body">
                  <p>Không. Chỉ có Windows và Android, và mọi thứ ở lại trên máy.</p>
                </div>
              </details>
            </div>
          </div>
        </section>

        <section id="rieng-tu">
          <div className="wrap">
            <div className="head">
              <p className="eyebrow">Quyền riêng tư</p>
              <h2>Có và không, nói thẳng</h2>
            </div>
            <div className="block table-wrap">
              <table className="stack">
                <caption>Đối chiếu những gì dự án có và không có ở bản phát hành này.</caption>
                <thead>
                  <tr><th scope="col">Nội dung</th><th scope="col">Kỷ luật</th><th scope="col">Ghi chú</th></tr>
                </thead>
                <tbody>
                  <tr>
                    <td>Tài khoản đăng nhập</td>
                    <td className="no" data-label="Kỷ luật">Không có</td>
                    <td className="no" data-label="Ghi chú">Không cần đăng ký hay đăng nhập.</td>
                  </tr>
                  <tr>
                    <td>Máy chủ riêng của dự án</td>
                    <td className="no" data-label="Kỷ luật">Không có</td>
                    <td className="no" data-label="Ghi chú">File phát hành để trên GitHub.</td>
                  </tr>
                  <tr>
                    <td>Số liệu kỹ thuật SDK Android</td>
                    <td className="no" data-label="Kỷ luật">Không, từ 3.20</td>
                    <td data-label="Ghi chú">MediaPipe chạy cục bộ. Bản 3.17–3.19 dùng ML Kit có gửi số liệu kỹ thuật tới Google, không gồm ảnh. <a href="/an-toan#camera-bai-tap">Chi tiết</a>.</td>
                  </tr>
                  <tr>
                    <td>Đọc nội dung màn hình</td>
                    <td className="no" data-label="Kỷ luật">Không</td>
                    <td className="no" data-label="Ghi chú">Chỉ ghi nhận tên app đang mở.</td>
                  </tr>
                  <tr>
                    <td>Lịch sử chặn và check-in</td>
                    <td className="yes" data-label="Kỷ luật">Có, trên máy bạn</td>
                    <td className="no" data-label="Ghi chú">Một file SQLite, bạn xoá được.</td>
                  </tr>
                  <tr>
                    <td>Mã hoá dữ liệu</td>
                    <td className="no" data-label="Kỷ luật">Không</td>
                    <td className="no" data-label="Ghi chú">File nằm mở, ai dùng máy bạn đọc được.</td>
                  </tr>
                  <tr>
                    <td>Mã ghép nối điện thoại</td>
                    <td className="yes" data-label="Kỷ luật">Có</td>
                    <td className="no" data-label="Ghi chú">Key trong Android Keystore, chỉ dùng trong mạng nội bộ.</td>
                  </tr>
                  <tr>
                    <td>Sao lưu tự động</td>
                    <td className="yes" data-label="Kỷ luật">Có</td>
                    <td className="no" data-label="Ghi chú">Giữ 7 bản gần nhất trong thư mục backups.</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </section>

        <section className="alt">
          <div className="wrap">
            <div className="head">
              <p className="eyebrow">Bắt đầu</p>
              <h2>Mười phút là xong một tuần bảo vệ</h2>
              <p className="lede">Tải về, khai ba cái tên bạn ghét nhất, đặt một khung giờ. Ngày mai máy sẽ nhắc bạn bằng cách im lặng chặn app.</p>
            </div>
            <div className="actions">
              <a className="btn primary" href="/tai-nguyen">Tải về</a>
              <a className="btn" href="/huong-dan">Đọc hướng dẫn cài đặt</a>
            </div>
          </div>
        </section>
      <FaqOpener />
    </main>
  );
}
