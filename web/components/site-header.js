"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import Icon from "@/components/icon";

const LINKS = [
  { href: "/#cach-hoat-dong", label: "Cách hoạt động" },
  { href: "/#nguyen-tac", label: "Nguyên tắc" },
  { href: "/huong-dan", label: "Hướng dẫn" },
  { href: "/an-toan", label: "Vì sao an toàn" },
  { href: "/#hoi-dap", label: "Hỏi đáp" },
];

export default function SiteHeader() {
  const path = usePathname();
  return (
    <header className="site">
      <div className="wrap bar">
        <Link className="brand" href="/">
          <Icon name="shield" className="mark" />
          <span>Kỷ luật</span>
        </Link>
        <a className="menu" href="#nav" aria-label="Mở menu điều hướng">
          <Icon name="menu" />
          <span className="vh">Mở menu</span>
        </a>
        <nav className="nav" id="nav">
          {LINKS.map((l) => (
            <Link key={l.href} href={l.href}>
              {l.label}
            </Link>
          ))}
          <Link className="btn primary" href="/tai-nguyen" aria-current={path === "/tai-nguyen" ? "page" : undefined}>
            Tải về
          </Link>
        </nav>
      </div>
    </header>
  );
}
