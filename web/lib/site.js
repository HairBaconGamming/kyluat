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
  file: "KYLUAT-Setup-v1.2.exe",
  url: `${RELEASE}/KYLUAT-Setup-v1.2.exe`,
  size: "38,94 MB",
  note: "bản 1.2, có installer",
  bytes: "38.936.578",
  sha256: "0757a235d22d3cb057deb43be91625e54eb68bcdf9b32958dfa330a864464e9f",
};

export const WINDOWS_ZIP = {
  file: "KYLUAT-windows-x64-v1.2.zip",
  url: `${RELEASE}/KYLUAT-windows-x64-v1.2.zip`,
  size: "58,57 MB",
  note: "bản 1.2, không cần cài",
  bytes: "58.571.229",
  sha256: "e1c9126efbf8c2b6d006929b415d3fb0e0bf7b92a7a55c32e0a311f0434aec0f",
};

export const ANDROID = {
  file: "kyluat-android-v3.7.apk",
  url: `${RELEASE}/kyluat-android-v3.7.apk`,
  size: "2,91 MB",
  note: "phiên bản 3.7 (số 12)",
  bytes: "2.905.117",
  sha256: "947d963b18c09def4e8747ba1c6b40e3665f154918075a289418481171a11b86",
};
