import Link from "next/link";

const LINKS = [
  { href: "/", label: "Trang chủ" },
  { href: "/tai-nguyen", label: "Tải về" },
  { href: "/huong-dan", label: "Hướng dẫn" },
  { href: "/#hoi-dap", label: "Hỏi đáp" },
  { href: "/#rieng-tu", label: "Quyền riêng tư" },
];

export default function SiteFooter() {
  return (
    <footer>
      <div className="wrap cols">
        <div>
          <p>
            <strong>Kỷ luật.</strong> Dự án cá nhân, không bán, không bảo hành, không hỗ trợ kỹ thuật.
          </p>
          <p style={{ marginTop: "6px" }}>Ảnh giao diện dùng dữ liệu giả. Bản hiện tại: Windows v1, Android 3.4.</p>
        </div>
        <nav aria-label="Liên kết cuối trang">
          {LINKS.map((l) => (
            <Link key={l.href + l.label} href={l.href}>
              {l.label}
            </Link>
          ))}
        </nav>
      </div>
    </footer>
  );
}
