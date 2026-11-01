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
                <p>Quỹ mạng xã hội mỗi ngày trên điện thoại. Hết quỹ là hết, không có nút gia hạn.</p>
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
                  <p>Điện thoại chỉ phủ màn khoá lên app bạn chọn, không đổi DNS và không đổi chính sách hệ thống. Cuộc gọi, Tin nhắn, Cài đặt và màn hình chính luôn mở.</p>
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
            <h3 style={{ margin: '32px 0 14px', fontSize: '17px' }}>Trên điện thoại</h3>
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
            <p className="note" style={{ marginTop: '16px' }}>
              <Icon name="info" />
              <span>Ảnh điện thoại dựng lại bằng code từ đúng màu, chữ và số đo trong mã giao diện, không phải ảnh chụp thiết bị, và icon ứng dụng để dạng ô trung tính.</span>
            </p>
            <p className="note" style={{ marginTop: '10px' }}>
              <Icon name="info" />
              <span>Phần kỹ thuật nằm riêng ở màn Nâng cao. Bạn không cần mở nó vẫn dùng được.</span>
            </p>
          </div>
        </section>

        <section className="alt" id="dien-thoai">
          <div className="wrap">
            <div className="head">
              <p className="eyebrow">Cách hoạt động · Android</p>
              <h2>Trên điện thoại: app bị khoá sẵn, quỹ mạng xã hội phải chủ động xin dùng</h2>
              <p className="lede">Mặc định là 60 phút cho cả ngày, và nó chỉ trừ dần khi bạn mở một phiên nghỉ có kiểm soát. Đổi mức cũng được, nhưng phải gõ lại đoạn cam kết.</p>
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
                  <h3>Muốn dùng thì xin một phiên nghỉ</h3>
                  <p>Bấm <strong>Nghỉ có kiểm soát</strong>, chọn 10, 15 hoặc 30 phút. Trong phiên nghỉ, app mạng xã hội mở bình thường và thời gian bạn lướt trừ dần vào quỹ.</p>
                  <ul>
                    <li>Hết giờ nghỉ là màn khoá đóng lại, dù quỹ chưa cạn.</li>
                    <li>Đang có phiên focus thì không xin nghỉ được.</li>
                    <li>Nghỉ sớm cũng được: bấm <strong>Kết thúc nghỉ</strong> bất cứ lúc nào.</li>
                  </ul>
                </div>
                <figure className="shot phone-shot step-media">
                  <img src="/assets/img/android-hom-nay.png" alt="Màn Hôm nay trên điện thoại: vòng quỹ còn 42 phút và nút Kết thúc nghỉ đang chạy" width="1080" height="2608" loading="lazy" />
                  <figcaption>Quỹ mạng xã hội và phiên nghỉ nhìn thấy ngay ở tab Hôm nay.</figcaption>
                </figure>
              </div>
              <div className="step">
                <div>
                  <span className="num">3</span>
                  <h3>Màn khoá có bốn trạng thái, và luôn nói lý do</h3>
                  <p>Màn khoá là một lớp phủ do app tự vẽ. Nó hiện tiêu đề và lý do ngay trên mặt app, không phải một trang web hay thông báo.</p>
                  <ul>
                    <li>App đang bị chặn: <strong>Quay lại Kỷ luật</strong> hoặc <strong>Dùng app khác</strong>, nút sau đưa bạn về màn hình chính.</li>
                    <li>Đang focus hoặc đã hết quỹ: chỉ có tiêu đề và thời gian còn lại, không có nút mở app.</li>
                    <li>Đang nghỉ có kiểm soát: không có màn khoá.</li>
                  </ul>
                </div>
                <figure className="shot phone-shot step-media">
                  <img src="/assets/img/android-man-khoa.png" alt="Lớp phủ màn khoá màu đậm với tiêu đề APP NÀY ĐANG TRONG DANH SÁCH CHẶN và hai nút Quay lại Kỷ luật, Dùng app khác" width="1080" height="2400" loading="lazy" />
                  <figcaption>Màn khoá khi bạn mở app đang bị chặn.</figcaption>
                </figure>
              </div>
            </div>
            <div className="grid two" style={{ marginTop: '40px' }}>
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
                  <p>Giữ app tập trên màn hình đủ 10 phút liên tục mới được một buổi. Rời app hoặc tắt màn hình là đếm lại từ đầu, có đồng hồ chạy ở thanh trạng thái.</p>
                </div>
              </div>
              <div className="card principle">
                <span className="ico"><Icon name="clock" /></span>
                <div>
                  <h3>Quỹ dùng chung, và bạn đổi được mức</h3>
                  <p>Một mức quỹ duy nhất dùng cho cả nhóm app đã chọn, không phải mỗi app một tiếng. Mặc định 60 phút, chỉnh được 15 đến 120 phút.</p>
                </div>
              </div>
            </div>
            <p className="note warn" style={{ marginTop: '16px' }}>
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
                    <p className="lede" style={{ fontSize: '14px' }}>Bản nén, giải nén rồi chạy <code>Kyluat.exe</code>.</p>
                  </div>
                </div>
                <ul>
                  <li>Chặn app trong giờ tập trung và chặn trang web qua DNS.</li>
                  <li>Không cài đặt, không ghi vào hệ thống.</li>
                  <li>File chưa có chữ ký số, Windows sẽ hỏi bạn một lần.</li>
                </ul>
                <a className="btn primary" href="/tai-nguyen">Tải bản Windows</a>
              </div>
              <div className="panel">
                <div className="top">
                  <span className="ico"><Icon name="phone" /></span>
                  <div>
                    <h3>Android 8.0 trở lên</h3>
                    <p className="lede" style={{ fontSize: '14px' }}>File <code>APK</code>, cài thủ công.</p>
                  </div>
                </div>
                <ul>
                  <li>Quỹ mạng xã hội mỗi ngày, mặc định 60 phút và đổi được, cùng màn khoá trong phiên.</li>
                  <li>Không chặn trang web trên điện thoại, phần đó thuộc về máy tính.</li>
                  <li>Bản debug cá nhân, Play Protect có thể cảnh báo một lần.</li>
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
                  <p>Không đi đâu cả. Luật, lịch sử chặn và sổ học tập nằm trong một file SQLite trên máy bạn, xem được ở mục Nâng cao.</p>
                  <p>Máy tính và điện thoại nói chuyện với nhau qua Wi-Fi nội bộ, không có máy chủ trung gian.</p>
                </div>
              </details>
              <details id="faq-man-hinh">
                <summary>App có đọc nội dung trên màn hình không?</summary>
                <div className="body">
                  <p>Không. Quyền Trợ năng trên Android chỉ được dùng để biết app nào đang ở phía trước và phủ màn khoá lên app trong danh sách.</p>
                  <p>Tuỳ chọn đọc nội dung cửa sổ bị tắt ngay trong file khai báo của app.</p>
                </div>
              </details>
              <details id="faq-quy">
                <summary>Quỹ mạng xã hội trên điện thoại tiêu thế nào?</summary>
                <div className="body">
                  <p>Mặc định là khoá. Bạn mở Kỷ luật rồi chọn một phiên nghỉ 10, 15 hoặc 30 phút; chỉ trong phiên nghỉ đó thời gian lướt mới trừ vào quỹ của ngày.</p>
                  <p>Hết giờ nghỉ là màn khoá đóng lại, dù quỹ còn. Hết quỹ thì màn khoá nói rõ còn bao lâu tới 0:00.</p>
                </div>
              </details>
              <details id="faq-doi-quy">
                <summary>Đổi mức quỹ 60 phút thì làm thế nào?</summary>
                <div className="body">
                  <p>Ở tab <strong>Cài đặt</strong> → <strong>Mục tiêu</strong> → <strong>Quỹ mạng xã hội</strong>. Chọn mức từ 15 đến 120 phút, gõ lại đúng đoạn cam kết (dán văn bản không được tính), chờ nốt 30 giây. Ba bước này giống hệt lúc tắt bảo vệ.</p>
                  <p>Giảm mức có hiệu lực ngay, tăng mức phải chờ đến 0:00 hôm sau — nên đổi quỹ không cứu được một ngày đã dùng hết. Đã trót hẹn tăng mà đổi ý thì có nút <strong>Hủy lịch đổi</strong> ở bước chọn mức, bỏ lịch đó ngay, không phải gõ lại gì.</p>
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
                  <p>Không. Lớp phủ không nhận thao tác bàn phím và luôn có hai nút: <strong>Quay lại Kỷ luật</strong> và <strong>Dùng app khác</strong>, nút sau đưa bạn về màn hình chính.</p>
                  <p>Lúc đã hết quỹ hoặc đang trong phiên focus, màn khoá chỉ hiện tiêu đề và thời gian còn lại, không có nút mở app.</p>
                </div>
              </details>
              <details id="faq-tat-android">
                <summary>Tắt bảo vệ trên điện thoại mất bao lâu?</summary>
                <div className="body">
                  <p>Trong app là ba bước: đọc cảnh báo 60 giây, gõ lại đúng đoạn cam kết (dán văn bản không được tính), chờ nốt 60 giây. Không có thời gian chờ 24 giờ như trên máy tính.</p>
                  <p>Thu hồi quyền Trợ năng trong Cài đặt thì dừng ngay lập tức.</p>
                </div>
              </details>
              <details id="faq-web">
                <summary>Chặn trang web hoạt động tới đâu?</summary>
                <div className="body">
                  <p>Trên Windows, máy tính chặn theo tên miền trong giờ tập trung, tính cả các trang con.</p>
                  <p>Trên điện thoại thì không. Bản Android chỉ chặn ứng dụng.</p>
                </div>
              </details>
              <details id="faq-gym">
                <summary>Buổi gym được tính thế nào?</summary>
                <div className="body">
                  <p>Giữ app tập của bạn trên màn hình đủ 10 phút liên tục thì được một buổi. Rời app hoặc tắt màn hình là đếm lại từ đầu.</p>
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
            <div className="block" style={{ overflowX: 'auto' }}>
              <table>
                <caption>Đối chiếu những gì dự án có và không có ở bản phát hành này.</caption>
                <thead>
                  <tr><th scope="col">Nội dung</th><th scope="col">Kỷ luật</th><th scope="col">Ghi chú</th></tr>
                </thead>
                <tbody>
                  <tr><td>Tài khoản đăng nhập</td><td className="no">Không có</td><td className="no">Mở là dùng, không định danh ai.</td></tr>
                  <tr><td>Máy chủ riêng của dự án</td><td className="no">Không có</td><td className="no">File phát hành để trên GitHub.</td></tr>
                  <tr><td>Phân tích truy cập trong app</td><td className="no">Không có</td><td className="no">Không gửi số liệu dùng ra ngoài.</td></tr>
                  <tr><td>Đọc nội dung màn hình</td><td className="no">Không</td><td className="no">Chỉ ghi nhận tên app đang mở.</td></tr>
                  <tr><td>Lịch sử chặn và check-in</td><td className="yes">Có, trên máy bạn</td><td className="no">Một file SQLite, bạn xoá được.</td></tr>
                  <tr><td>Mã hoá dữ liệu</td><td className="no">Không</td><td className="no">File nằm mở, ai dùng máy bạn đọc được.</td></tr>
                  <tr><td>Mã ghép nối điện thoại</td><td className="yes">Có</td><td className="no">Key trong Android Keystore, chỉ dùng trong mạng nội bộ.</td></tr>
                  <tr><td>Sao lưu tự động</td><td className="yes">Có</td><td className="no">Giữ 7 bản gần nhất trong thư mục backups.</td></tr>
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
            <div className="actions" style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
              <a className="btn primary" href="/tai-nguyen">Tải về</a>
              <a className="btn" href="/huong-dan">Đọc hướng dẫn cài đặt</a>
            </div>
          </div>
        </section>
      <FaqOpener />
    </main>
  );
}
