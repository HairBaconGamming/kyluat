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
  "Kỷ luật chặn app và trang web bạn tự chọn trong giờ tập trung, kèm bộ lọc 18+ bằng DNS và lớp nhận diện ảnh tuỳ chọn chạy trên máy. Không tài khoản, không máy chủ. Nới lỏng luật phải chờ 24 giờ.";

const RELEASE = "https://github.com/HairBaconGamming/kyluat/releases/download/v1.0";

// Bộ cài là bản khuyên dùng; bản nén để dành cho máy không muốn cài hoặc cần chép tay.
export const WINDOWS = {
  file: "KYLUAT-Setup-v1.8.exe",
  url: `${RELEASE}/KYLUAT-Setup-v1.8.exe`,
  size: "53,90 MB",
  note: "bản 1.8, có installer",
  bytes: "53.903.263",
  sha256: "aff004e4676f5cff6709abe7fd88ad4bf6c1621f91ba75dd2c826d498ffc2d0e",
};

export const WINDOWS_ZIP = {
  file: "KYLUAT-windows-x64-v1.8.zip",
  url: `${RELEASE}/KYLUAT-windows-x64-v1.8.zip`,
  size: "103,76 MB",
  note: "bản 1.8, không cần cài",
  bytes: "103.758.606",
  sha256: "f2e99828fa1ee0f336e9dbba87e1524327d3aad2aaa2e2cca5e41fab1e813872",
};

export const ANDROID = {
  file: "kyluat-android-v3.12.apk",
  url: `${RELEASE}/kyluat-android-v3.12.apk`,
  size: "26,95 MB",
  note: "phiên bản 3.12 (số 17)",
  bytes: "26.954.070",
  sha256: "aaba42a67669e0480a44353c37c3fd2fea2595532aeafd6caf7496c6108ce273",
};
