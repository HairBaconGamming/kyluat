/** Một chỗ duy nhất cho tên miền và các file phát hành, để sitemap/og/trang tải về không lệch nhau. */

const DEFAULT_URL = "https://kyluat.vercel.app";

// Ô env của Vercel cho phép nhập host trần ("kyluat.vercel.app") còn new URL() thì từ chối,
// nên miền luôn được chuẩn hoá về dạng có scheme trước khi export.
const configured = (process.env.NEXT_PUBLIC_SITE_URL ?? "").trim();
const base = !configured
  ? DEFAULT_URL
  : /^[a-z][a-z\d+.-]*:\/\//i.test(configured)
    ? configured
    : `https://${configured}`;

export const SITE_URL = base.replace(/\/+$/, "");

export const TITLE = "Kỷ luật — máy chặn xao nhãng cho Windows và Android";
export const DESCRIPTION =
  "Kỷ luật chặn app và trang web bạn tự chọn trong giờ tập trung, kèm bộ lọc nội dung 18+ chạy suốt ngày đêm. Chạy trên máy của bạn, không tài khoản, không máy chủ. Nới lỏng luật phải chờ 24 giờ.";

const RELEASE = "https://github.com/HairBaconGamming/kyluat/releases/download/v1.0";

// Bộ cài là bản khuyên dùng; bản nén để dành cho máy không muốn cài hoặc cần chép tay.
export const WINDOWS = {
  file: "KYLUAT-Setup-v1.7.exe",
  url: `${RELEASE}/KYLUAT-Setup-v1.7.exe`,
  size: "38,97 MB",
  note: "bản 1.7, có installer",
  bytes: "38.967.555",
  sha256: "d80dfcf785fc2d673e1297d0cc5c3b8e00fb29e04182ba32bcb7a91c7ac29b0a",
};

export const WINDOWS_ZIP = {
  file: "KYLUAT-windows-x64-v1.7.zip",
  url: `${RELEASE}/KYLUAT-windows-x64-v1.7.zip`,
  size: "59,60 MB",
  note: "bản 1.7, không cần cài",
  bytes: "59.602.095",
  sha256: "355d9b99e41c197e515e5490caa5efbada569a53341938ab7d118007c73ff1e0",
};

export const ANDROID = {
  file: "kyluat-android-v3.11.apk",
  url: `${RELEASE}/kyluat-android-v3.11.apk`,
  size: "3,91 MB",
  note: "phiên bản 3.11 (số 16)",
  bytes: "3.909.037",
  sha256: "f5c6ae9cd4cbe220519942aec6e39038c91029205e29164ac410979f6ab54930",
};
