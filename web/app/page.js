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
                <p>Thời gian chờ khi bạn muốn nới lỏng luật. Thêm ràng buộc thì có hiệu lực ngay.</p>
              </div>
            </div>
          </div>
        </section>

        <section id="cach-hoat-dong">
          <div className="wrap">
            <div className="head">
              <p className="eyebrow">Cách hoạt động</p>
              <h2>Ba bước, sau đó máy tự giữ luật</h2>
              <p className="lede">Bạn chỉ việc khai ra danh sách và giờ giấc. Phần còn lại chạy nền.</p>
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
                  <p>Thêm app bị chặn ngay. Gỡ app ra thì chờ sang hôm sau. Cơn thèm mở YouTube lúc 11 giờ đêm không thắng được.</p>
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
                  <p>Điện thoại chỉ phủ màn khoá lên app bạn chọn. Cuộc gọi, Tin nhắn, Cài đặt và màn hình chính luôn mở.</p>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section id="giao-dien">
          <div className="wrap">
            <div className="head">
              <p className="eyebrow">Giao diện</p>
              <h2>Bốn màn hình, không màn nào cần đọc sách hướng dẫn</h2>
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
            <p className="note" style={{ marginTop: '16px' }}>
              <Icon name="info" />
              <span>Phần kỹ thuật nằm riêng ở màn Nâng cao. Bạn không cần mở nó vẫn dùng được.</span>
            </p>
          </div>
        </section>

        <section className="alt" id="dien-thoai">
          <div className="wrap">
            <div className="head">
              <p className="eyebrow">Trên điện thoại</p>
              <h2>Quỹ 60 phút, không phải chế độ nghiêm khắc</h2>
              <p className="lede">Điện thoại không chặn hết mạng xã hội. Nó cho bạn một quỹ dùng mỗi ngày, hết thì thôi.</p>
            </div>
            <div className="grid two" style={{ alignItems: 'center' }}>
              <div className="phone" aria-hidden="true">
                <div className="screen">
                  <div className="row"><span>Hôm nay</span><span className="chip">Còn 42 phút</span></div>
                  <svg className="ring" width="150" height="150" viewBox="0 0 150 150">
                    <circle cx="75" cy="75" r="62" fill="none" stroke="#e8ebed" strokeWidth="12"></circle>
                    <circle cx="75" cy="75" r="62" fill="none" stroke="#2f6f5e" strokeWidth="12" strokeLinecap="round" strokeDasharray="389.6" strokeDashoffset="272.7" transform="rotate(-90 75 75)"></circle>
                    <text x="75" y="70" textAnchor="middle" fontFamily="system-ui, sans-serif" fontSize="30" fontWeight="650" fill="#10151a">18</text>
                    <text x="75" y="92" textAnchor="middle" fontFamily="system-ui, sans-serif" fontSize="13" fill="#5c666f">/ 60 phút</text>
                  </svg>
                  <div className="line"><span>Buổi tập gym</span><span>10:00</span></div>
                  <div className="bar-track"><div className="bar-fill" style={{ width: '62%' }}></div></div>
                  <div className="line"><span>Zeta Chat</span><span>8 phút</span></div>
                  <div className="bar-track"><div className="bar-fill" style={{ width: '26%' }}></div></div>
                  <div className="line"><span>Loop Shorts</span><span>10 phút</span></div>
                  <div className="bar-track"><div className="bar-fill" style={{ width: '34%' }}></div></div>
                </div>
              </div>
              <div>
                <div className="grid" style={{ gap: '12px' }}>
                  <div className="card principle">
                    <span className="ico"><Icon name="phone" /></span>
                    <div>
                      <h3>Không cần cắm dây</h3>
                      <p>Điện thoại ghép nối với máy tính qua Wi-Fi gia đình bằng mã QR. Không có máy chủ trung gian.</p>
                    </div>
                  </div>
                  <div className="card principle">
                    <span className="ico"><Icon name="dumbbell" /></span>
                    <div>
                      <h3>Gym tính bằng 10 phút thật</h3>
                      <p>Giữ app tập trên màn hình đủ 10 phút liên tục mới được tính một buổi. Rời app là đếm lại.</p>
                    </div>
                  </div>
                  <div className="card principle">
                    <span className="ico"><Icon name="power" /></span>
                    <div>
                      <h3>Bỏ được trong 10 giây</h3>
                      <p>Tắt quyền Trợ năng là mọi thứ dừng ngay. App không giữ điện thoại của bạn làm con tin.</p>
                    </div>
                  </div>
                </div>
                <p className="note warn" style={{ marginTop: '14px' }}>
                  <Icon name="alert" />
                  <span>Hình trên là minh hoạ cấu trúc, không phải ảnh chụp từ điện thoại.</span>
                </p>
              </div>
            </div>
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
                  <li>Quỹ mạng xã hội 60 phút mỗi ngày và màn khoá trong phiên.</li>
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
