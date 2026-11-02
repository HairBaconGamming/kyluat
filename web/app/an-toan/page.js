import Icon from "@/components/icon";

export const metadata = {
  title: "Vì sao an toàn",
  description:
    "Kỷ luật chạm vào những gì trên Windows, vì sao SmartScreen vẫn cảnh báo, và năm lệnh để bạn tự kiểm tra file tải về.",
  alternates: { canonical: "/an-toan" },
  openGraph: {
    title: "Kỷ luật không phải virus: những gì app thay đổi trên máy bạn",
    description: "Liệt kê đầy đủ task, DNS, registry, firewall, cùng lệnh tự kiểm chứng.",
    url: "/an-toan",
  },
};

export default function Page() {
  return (
    <main id="main">
      <section className="hero">
        <div className="wrap" style={{ display: "block" }}>
          <p className="eyebrow">Vì sao an toàn</p>
          <h1>Không phải virus, và bạn kiểm tra được từng câu</h1>
          <p className="lede" style={{ marginTop: "12px" }}>
            Trang này liệt kê mọi thứ Kỷ luật đụng vào trên Windows, cái nào cần quyền quản trị, và cách hoàn tác từng cái.
          </p>
          <p className="note" style={{ marginTop: "18px" }}>
            <Icon name="shield" />
            <span>Toàn bộ mã nguồn mở nằm ở <a href="https://github.com/HairBaconGamming/kyluat">github.com/HairBaconGamming/kyluat</a>. Không có mã nào chỉ chạy trên máy chủ người bán.</span>
          </p>
        </div>
      </section>

      <section className="alt">
        <div className="wrap">
          <div className="grid three">
            <div className="card">
              <h3>Không tài khoản, không máy chủ</h3>
              <p>App không đăng nhập đâu cả, không có đường truyền nào tới tác giả. Số liệu học và luật chỉ nằm trong một file SQLite trên máy bạn.</p>
            </div>
            <div className="card">
              <h3>Không tải mã lạ</h3>
              <p>Bản Windows không tự tải gì: exe đã chứa sẵn mọi thứ, không chạy script từ mạng, không cài thêm runtime. Bản Android chỉ đọc danh sách bản phát hành trên GitHub mỗi ngày, và chỉ tải đúng file APK của repo này khi bạn bấm Cập nhật.</p>
            </div>
            <div className="card">
              <h3>Gỡ là sạch</h3>
              <p>Bộ gỡ tắt bảo vệ, xóa task và bỏ rule tường lửa. Sổ tay và luật cũ được giữ lại cho tới khi bạn tự xóa thư mục.</p>
            </div>
          </div>
        </div>
      </section>

      <section>
        <div className="wrap">
          <div className="head">
            <p className="eyebrow">Những gì app chạm vào</p>
            <h2>Bảy thứ thay đổi, và cách hoàn tác</h2>
            <p className="lede">Phần mềm chặn xao nhãng phải đụng vào hệ thống nhiều hơn một app ghi chú. Nên danh sách này là đủ, không có mục ẩn.</p>
          </div>
          <div className="block" style={{ overflowX: "auto" }}>
            <table>
              <caption>Mặc định app chỉ ghi trong thư mục của riêng bạn. Chỉ mục DNS và registry cần quyền Administrator, và chỉ bật khi bạn chủ động chọn chặn web.</caption>
              <thead>
                <tr><th scope="col">Thành phần</th><th scope="col">Khi nào có</th><th scope="col">Hoàn tác</th></tr>
              </thead>
              <tbody>
                <tr>
                  <td>Thư mục <code>%LOCALAPPDATA%\Programs\Kyluat</code></td>
                  <td>Khi chạy bộ cài</td>
                  <td>Add or remove programs, không đòi quyền quản trị</td>
                </tr>
                <tr>
                  <td><code>data\enforcer.db</code> và <code>data\backups\</code></td>
                  <td>Lần chạy đầu, rồi mỗi ngày</td>
                  <td>Xóa thư mục <code>data</code> là mất hết lịch sử</td>
                </tr>
                <tr>
                  <td>Scheduled Task <code>CaiNghienEnforcer</code></td>
                  <td>Khi bạn bấm giữ cho app tự bật lại</td>
                  <td>Nút Tắt bảo vệ, hoặc <code>Kyluat-cli.exe turn-off</code></td>
                </tr>
                <tr>
                  <td>DNS của card mạng trỏ về <code>127.0.0.1</code></td>
                  <td>Chỉ khi bật chặn web (cần Administrator)</td>
                  <td>Tắt bảo vệ khôi phục đúng DNS cũ đã sao lưu; <code>net repair</code> nếu đứt</td>
                </tr>
                <tr>
                  <td>Chính sách DoH của Chrome và Edge trong registry</td>
                  <td>Chỉ khi bật chặn web (cần Administrator)</td>
                  <td>Tắt bảo vệ trả về giá trị cũ của từng trình duyệt</td>
                </tr>
                <tr>
                  <td>Rule tường lửa <code>CaiNghienPhone8766</code></td>
                  <td>Chỉ khi ghép nối điện thoại, áp dụng cho mạng riêng tư</td>
                  <td>Tắt bảo vệ xóa rule này</td>
                </tr>
                <tr>
                  <td>Lối tắt Start Menu và màn hình nền</td>
                  <td>Khi chạy bộ cài</td>
                  <td>Bộ gỡ xóa cả hai</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p className="note">
            <Icon name="lock" />
            <span>App không sửa file <code>hosts</code>, không cài service hay driver, không chạm kernel, không đọc ảnh hay tài liệu của bạn. Ngoài thư mục cài, bộ cài chỉ thêm lối tắt Start Menu và một mục trong Add or remove programs; bộ gỡ xóa cả hai.</span>
          </p>
        </div>
      </section>

      <section className="alt">
        <div className="wrap">
          <div className="head">
            <p className="eyebrow">Mạch mạng</p>
            <h2>Có gì đi ra ngoài</h2>
          </div>
          <div className="grid two">
            <div className="card">
              <h3>Trong mạng nhà</h3>
              <p>Khi bạn bật ghép nối, máy tính quảng bá dịch vụ <code>KyLuat-</code> bằng mDNS và mở cổng <code>8766</code> cho điện thoại trong cùng mạng. Hai thiết bị đổi dữ liệu với nhau bằng mã ghép nối bạn vừa quét.</p>
            </div>
            <div className="card">
              <h3>Ra ngoài internet</h3>
              <p>Khi chặn web đang bật, trình phân giải cục bộ hỏi DNS công cộng cho những tên miền không bị chặn, mặc định là <code>1.1.1.1</code> rồi <code>8.8.8.8</code>, đổi sang <code>94.140.14.14</code> và <code>94.140.14.15</code> của bộ lọc gia đình AdGuard khi bạn bật luật 18+. Trên Android, bộ lọc 18+ cũng gửi đúng câu hỏi DNS tới hai địa chỉ AdGuard đó.</p>
              <p>Ngoài DNS, bản Android còn gọi <code>api.github.com</code> mỗi ngày một lần nếu bạn để bật tự kiểm tra bản mới. Không có lệnh upload, không có telemetry.</p>
            </div>
          </div>
          <p className="note warn">
            <Icon name="alert" />
            <span>Vì đường đó tồn tại, bật chặn web đồng nghĩa nhà cung cấp DNS của bạn chuyển từ nhà mạng sang Cloudflare hoặc Google, và sang AdGuard Family khi luật 18+ bật. Tắt chặn web là trả về cũ.</span>
          </p>
          <div className="block" style={{ marginTop: "18px" }}>
            <h3>Tự kiểm tra bản mới gửi gì</h3>
            <p>Bản Android đọc danh sách bản phát hành công khai trên GitHub, giống mở trang release bằng trình duyệt. GitHub thấy địa chỉ IP và tên app gửi kèm; không có mã thiết bị, không danh sách app, không số liệu sử dụng.</p>
            <p>Khi bạn bấm Cập nhật, app tải đúng file <code>kyluat-android-vX.apk</code> của repo này. Android kiểm tra chữ ký trước khi cài nên file ký bằng key khác bị từ chối, và bạn vẫn phải xác nhận trong hộp thoại cài đặt của hệ thống.</p>
            <p>Tắt ở <strong>Cài đặt</strong> → <strong>Cập nhật</strong> → <strong>Tự kiểm tra bản mới</strong>. Bản Windows không có cơ chế này, exe không tự tải gì.</p>
          </div>
        </div>
      </section>

      <section>
        <div className="wrap">
          <div className="head">
            <p className="eyebrow">Cảnh báo của Windows</p>
            <h2>Vì sao SmartScreen vẫn chặn một lần</h2>
          </div>
          <div className="prose">
            <p>
              File phát hành chưa mua chứng chỉ chữ ký số, nên Windows không biết ai làm ra nó và hiện "Unknown publisher". Đây là hành vi bình thường của phần mềm cá nhân, không phải bằng chứng app có hại.
            </p>
            <p>
              Một lý do nữa: exe được gói bằng PyInstaller, một định dạng hay bị công cụ diệt virus đoán mò vì nó giải mã mã thực thi lúc chạy. Tỷ lệ báo nhầm cao hơn nhiều so với app cài bằng MSI đã ký.
            </p>
            <ul>
              <li>Tải từ <a href="https://github.com/HairBaconGamming/kyluat/releases">GitHub Releases</a> của repo chính thức, không tải từ link gọn hay file đính kèm trong chat.</li>
              <li>So SHA-256 với dãy ở <a href="/tai-nguyen">trang Tải về</a> trước khi chạy.</li>
              <li>Còn nghi thì chạy trong máy ảo hoặc một tài khoản Windows trắng trước.</li>
              <li>Đừng tắt Windows Defender để app chạy. Nếu Defender chặn hẳn, gửi file cho Microsoft ở mục "Report a false positive".</li>
            </ul>
          </div>
        </div>
      </section>

      <section className="alt">
        <div className="wrap">
          <div className="head">
            <p className="eyebrow">Tự kiểm chứng</p>
            <h2>Năm lệnh trong PowerShell</h2>
            <p className="lede">Chạy sau khi cài để xem app đã để lại những gì. Mở PowerShell thường, không cần quyền quản trị.</p>
          </div>
          <pre className="code"><code>{`# 1. Dung lượng và dấu vân tay của bộ cài
certutil -hashfile "$env:USERPROFILE\\Downloads\\KYLUAT-Setup-v1.4.exe" SHA256

# 2. App cài ở đâu, trong đó có những file nào
Get-ChildItem "$env:LOCALAPPDATA\\Programs\\Kyluat"

# 3. Task tự bật lại, nếu có thì mới thấy
schtasks /Query /TN CaiNghienEnforcer

# 4. Rule tường lửa cho điện thoại
netsh advfirewall firewall show rule name=CaiNghienPhone8766

# 5. DNS hiện tại của card mạng đang lên internet
Get-DnsClientServerAddress -AddressFamily IPv4`}</code></pre>
          <p className="note">
            <Icon name="info" />
            <span>Câu 3, 4 và 5 trả về trống hoặc là DNS nhà mạng khi bạn chưa bật tính năng tương ứng.</span>
          </p>
        </div>
      </section>

      <section>
        <div className="wrap">
          <div className="head">
            <p className="eyebrow">Android</p>
            <h2>Bộ lọc 18+ có dùng VPN, và chỉ làm đúng một việc</h2>
            <p className="lede">Android không cho app chặn trang web theo kiểu của Windows. Cách duy nhất hợp lệ là một đường hầm chỉ nhận truy vấn DNS. Nó là tuỳ chọn: không gạt công tắc ở tab <strong>Bảo vệ</strong> thì máy không đổi gì.</p>
          </div>
          <div className="grid three">
            <div className="card">
              <h3>Chỉ đúng truy vấn DNS</h3>
              <p>Đường hầm chỉ mang gói tin gửi tới một địa chỉ DNS nội bộ giả định. Ảnh, video, tin nhắn và mọi app khác vẫn ra Wi-Fi như cũ, không đi qua ngả nào khác.</p>
            </div>
            <div className="card">
              <h3>Không giữ nội dung</h3>
              <p>Câu hỏi DNS chỉ đi qua bộ nhớ trong lúc trả lời. App không ghi lịch sử truy vấn, không dựng biểu mẫu, không có máy chủ của tác giả; nơi phân giải nhờ tới là AdGuard Family, một DNS lọc công cộng.</p>
            </div>
            <div className="card">
              <h3>Tự rút khi có vấn đề</h3>
              <p>Bốn lần hỏi trong nửa phút mà không máy chủ nào trả lời thì đường hầm bị tháo, DNS máy về bình thường và app không tự bật lại. Thà chưa chặn còn hơn mất mạng.</p>
            </div>
          </div>
          <p className="note warn">
            <Icon name="alert" />
            <span>Tắt bằng một chạm: nút <strong>Tắt bộ lọc</strong> trên thông báo, công tắc ở tab <strong>Bảo vệ</strong>, hoặc màn <strong>Tắt bảo vệ</strong> trong app. Muốn kiểm từ hệ thống, mở Cài đặt → Mạng và Internet → VPN và tắt Kỷ luật ở đó. Cần Android 8.1 trở lên.</span>
          </p>
        </div>
      </section>

      <section className="alt">
        <div className="wrap">
          <div className="head">
            <p className="eyebrow">Chặn gỡ cài đặt</p>
            <h2>Một bước cản tuỳ chọn, không phải khoá vĩnh viễn</h2>
            <p className="lede">Mặc định tính năng này tắt. Bật thì Android hỏi lại khi bạn gỡ Kỷ luật, và bạn luôn tắt được quyền đó trong Cài đặt hệ thống.</p>
          </div>
          <div className="grid three">
            <div className="card">
              <h3>Chỉ khi bạn bật</h3>
              <p>App đăng ký làm trình quản lý thiết bị nhưng không giữ policy nào: không khoá máy, không xoá dữ liệu, không đổi mật khẩu. Không dùng Device Owner.</p>
            </div>
            <div className="card">
              <h3>Android luôn cho tắt</h3>
              <p>Quyền này nằm trong Cài đặt → Bảo mật → Quản lý thiết bị. App không chặn màn Cài đặt và không có đường nào để tự khoá bạn ra ngoài.</p>
            </div>
            <div className="card">
              <h3>Tắt bảo vệ là nhả hết</h3>
              <p>Tắt bảo vệ trong app cũng tắt luôn quyền quản lý. Gỡ app thì Android huỷ VPN, lớp phủ và Trợ năng theo tiến trình, không còn gì chặn máy.</p>
            </div>
          </div>
          <p className="note warn">
            <Icon name="alert" />
            <span>Android không cho app tự đặt thời gian chờ hay ô gõ cam kết vào hộp thoại gỡ của hệ thống. Bước cản hợp pháp duy nhất là quyền quản lý thiết bị ở trên; app cố tình không dùng Device Owner để bạn không bao giờ bị khoá vĩnh viễn.</span>
          </p>
        </div>
      </section>

      <section>
        <div className="wrap">
          <div className="head">
            <p className="eyebrow">Giới hạn thật lòng</p>
            <h2>Những điều app không làm được</h2>
          </div>
          <div className="grid three">
            <div className="card">
              <h3>Không chống được người quyết tâm</h3>
              <p>Bạn đổi DNS thủ công hoặc xóa task bằng quyền quản trị là chặn web hết tác dụng. App chỉ giữ bạn bằng một đoạn cam kết gõ tay và thời gian chờ.</p>
            </div>
            <div className="card">
              <h3>Không mã hóa dữ liệu</h3>
              <p>Ai dùng tài khoản Windows của bạn sẽ đọc được lịch sử chặn và sổ tay. Đặt mật khẩu mở máy là lớp bảo vệ duy nhất.</p>
            </div>
            <div className="card">
              <h3>Không có bảo hành</h3>
              <p>Dự án cá nhân, không nhà hỗ trợ, không cam kết tương thích với mọi bản Windows. Có lỗi thì mở issue trên GitHub.</p>
            </div>
          </div>
          <div className="actions" style={{ display: "flex", gap: "10px", flexWrap: "wrap", marginTop: "26px" }}>
            <a className="btn primary" href="/tai-nguyen">Tải về</a>
            <a className="btn" href="/huong-dan">Hướng dẫn cài đặt</a>
          </div>
        </div>
      </section>
    </main>
  );
}
