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
  file: "KYLUAT-Setup-v1.6.exe",
  url: `${RELEASE}/KYLUAT-Setup-v1.6.exe`,
  size: "38,97 MB",
  note: "bản 1.6, có installer",
  bytes: "38.967.667",
  sha256: "4a1bd9825e4f64b8941487944edd2f366f49c925c3c5786dfe362ac91d7adde6",
};

export const WINDOWS_ZIP = {
  file: "KYLUAT-windows-x64-v1.6.zip",
  url: `${RELEASE}/KYLUAT-windows-x64-v1.6.zip`,
  size: "58,62 MB",
  note: "bản 1.6, không cần cài",
  bytes: "58.618.493",
  sha256: "a6c15f63f00656513168615a150694480de768a3f6e3ae297e5675e9e02dc385",
};

export const ANDROID = {
  file: "kyluat-android-v3.9.apk",
  url: `${RELEASE}/kyluat-android-v3.9.apk`,
  size: "2,93 MB",
  note: "phiên bản 3.9 (số 14)",
  bytes: "2.928.693",
  sha256: "1e3d73dc84c7d4984c17b3d4e36986676fa7889bcbb44d7f6de2f1f293b35255",
};
