"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
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
  const [open, setOpen] = useState(false);
  const header = useRef(null);

  useEffect(() => setOpen(false), [path]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e) => e.key === "Escape" && setOpen(false);
    const onClick = (e) => {
      if (!header.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener("keydown", onKey);
    document.addEventListener("pointerdown", onClick);
    return () => {
      document.removeEventListener("keydown", onKey);
      document.removeEventListener("pointerdown", onClick);
    };
  }, [open]);

  return (
    <header className="site" ref={header} data-nav={open ? "open" : undefined}>
      <div className="wrap bar">
        <Link className="brand" href="/">
          <Icon name="shield" className="mark" />
          <span>Kỷ luật</span>
        </Link>
        <button
          type="button"
          className="menu"
          aria-expanded={open}
          aria-controls="nav"
          aria-label={open ? "Đóng menu" : "Mở menu"}
          onClick={() => setOpen((v) => !v)}
        >
          <Icon name={open ? "close" : "menu"} />
        </button>
        <nav className="nav" id="nav" onClick={() => setOpen(false)}>
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
