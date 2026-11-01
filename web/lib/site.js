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

export const WINDOWS = {
  file: "Kyluat-windows-x64-v1.zip",
  url: `${RELEASE}/Kyluat-windows-x64-v1.zip`,
  size: "54,2 MB",
  note: "bản 1",
  bytes: "54.164.390",
  sha256: "cbb63713f4ef4a561aca356be063712eb1ad1fc973b444282f7d205c4d325565",
};

export const ANDROID = {
  file: "kyluat-android-v3.5.apk",
  url: `${RELEASE}/kyluat-android-v3.5.apk`,
  size: "3,81 MB",
  note: "phiên bản 3.5 (số 10)",
  bytes: "3.810.480",
  sha256: "351a7a75e4288d36df4a1413b3f07edb899d61d066868534d0d21ea95bd0af71",
};
