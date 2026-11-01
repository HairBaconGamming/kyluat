"use client";

import { useEffect } from "react";

/** Mở đúng hộp hỏi đáp mà liên kết trỏ tới qua ?q=faq-..., vì <details> không tự mở theo URL. */
export default function FaqOpener() {
  useEffect(() => {
    const q = new URLSearchParams(window.location.search).get("q");
    if (!q) return;
    const box = document.getElementById(q);
    if (box && box.tagName === "DETAILS") {
      box.open = true;
      box.scrollIntoView();
    }
  }, []);
  return null;
}
