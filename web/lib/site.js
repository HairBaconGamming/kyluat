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
  file: "KYLUAT-Setup-v1.4.exe",
  url: `${RELEASE}/KYLUAT-Setup-v1.4.exe`,
  size: "38,96 MB",
  note: "bản 1.4, có installer",
  bytes: "38.964.265",
  sha256: "c6cc54f066bd9a3a0de2ca49e52100d18efbaa8a7ac3b94f4f09fd6eb7413cd1",
};

export const WINDOWS_ZIP = {
  file: "KYLUAT-windows-x64-v1.4.zip",
  url: `${RELEASE}/KYLUAT-windows-x64-v1.4.zip`,
  size: "58,62 MB",
  note: "bản 1.4, không cần cài",
  bytes: "58.618.043",
  sha256: "857585418b3b323c03bf7f6a1f2cbb9e07ccdd10678499d6ae5c43344c5c5bd4",
};

export const ANDROID = {
  file: "kyluat-android-v3.9.apk",
  url: `${RELEASE}/kyluat-android-v3.9.apk`,
  size: "2,93 MB",
  note: "phiên bản 3.9 (số 14)",
  bytes: "2.928.693",
  sha256: "1e3d73dc84c7d4984c17b3d4e36986676fa7889bcbb44d7f6de2f1f293b35255",
};
