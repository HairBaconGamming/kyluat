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
  file: "KYLUAT-Setup-v1.9.exe",
  url: `${RELEASE}/KYLUAT-Setup-v1.9.exe`,
  size: "53,90 MB",
  note: "bản 1.9, có installer",
  bytes: "53.904.736",
  sha256: "0b3e741951bb1119b5559a62818106e35402e3d7a1e754003c4035a3340fb3d2",
};

export const WINDOWS_ZIP = {
  file: "KYLUAT-windows-x64-v1.9.zip",
  url: `${RELEASE}/KYLUAT-windows-x64-v1.9.zip`,
  size: "103,77 MB",
  note: "bản 1.9, không cần cài",
  bytes: "103.765.584",
  sha256: "d7a7f5effce99c260ba13bf17074f956bb54ce4f9907ed76cc84e3db7002bce7",
};

export const ANDROID = {
  file: "kyluat-android-v3.15.apk",
  url: `${RELEASE}/kyluat-android-v3.15.apk`,
  size: "26,97 MB",
  note: "phiên bản 3.15 (số 20)",
  bytes: "26.970.826",
  sha256: "bbdafe5018544966db0c2af60ef1d790900164517767f22d062aa1f71468b4dc",
};
