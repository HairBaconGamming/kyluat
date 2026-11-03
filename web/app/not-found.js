export const metadata = {
  title: "Không tìm thấy trang",
  description: "Đường dẫn không tồn tại. Về trang chủ Kỷ luật hoặc mở trang tải về cho Windows và Android.",
  robots: { index: false, follow: true },
};

export default function NotFound() {
  return (
    <main id="main">
      <section className="hero">
          <div className="wrap">
            <div>
              <p className="eyebrow">Lỗi 404</p>
              <h1>Trang này không tồn tại</h1>
              <p className="lede below">Có thể đường dẫn cũ đã đổi. Hai trang dưới đây là thứ bạn đang tìm.</p>
              <div className="actions">
                <a className="btn primary" href="/">Về trang chủ</a>
                <a className="btn" href="/tai-nguyen">Tải về</a>
              </div>
            </div>
          </div>
        </section>
    </main>
  );
}
