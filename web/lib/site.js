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
  "Kỷ luật chặn app và trang web bạn tự chọn trong giờ tập trung. Chạy trên máy của bạn, không tài khoản, không máy chủ. Nới lỏng luật phải chờ 24 giờ.";

const RELEASE = "https://github.com/HairBaconGamming/kyluat/releases/download/v1.0";

// Bộ cài là bản khuyên dùng; bản nén để dành cho máy không muốn cài hoặc cần chép tay.
export const WINDOWS = {
  file: "KYLUAT-Setup-v1.1.exe",
  url: `${RELEASE}/KYLUAT-Setup-v1.1.exe`,
  size: "38,94 MB",
  note: "bản 1.1, có installer",
  bytes: "38.938.046",
  sha256: "49a3976d0c4e9c4f9be012807ba8083bdc57f782fe78a139923fd31c4c7f504f",
};

export const WINDOWS_ZIP = {
  file: "KYLUAT-windows-x64-v1.1.zip",
  url: `${RELEASE}/KYLUAT-windows-x64-v1.1.zip`,
  size: "58,56 MB",
  note: "bản 1.1, không cần cài",
  bytes: "58.560.153",
  sha256: "3fe229858264dcf23ffb3cd7792ee798cad7388ff118efc21a0f25ca8e60a9f2",
};

export const ANDROID = {
  file: "kyluat-android-v3.5.apk",
  url: `${RELEASE}/kyluat-android-v3.5.apk`,
  size: "3,81 MB",
  note: "phiên bản 3.5 (số 10)",
  bytes: "3.810.480",
  sha256: "351a7a75e4288d36df4a1413b3f07edb899d61d066868534d0d21ea95bd0af71",
};
