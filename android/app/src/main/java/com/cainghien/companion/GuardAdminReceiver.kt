package com.cainghien.companion

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/** Chỉ giữ vai trò quản trị để Android thêm bước xác nhận khi gỡ app.
 *  Không có policy nào: không khoá màn hình, không xoá dữ liệu, không đổi mật khẩu. */
class GuardAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Kỷ luật sẽ không còn bước cản gỡ cài đặt. Nếu muốn dừng hẳn, hãy mở Kỷ luật và tắt bảo vệ trước, " +
            "rồi hãy tắt quyền quản lý này."
}
