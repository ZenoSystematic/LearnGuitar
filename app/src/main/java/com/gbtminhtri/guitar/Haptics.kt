package com.gbtminhtri.guitar

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Rung nhẹ khi trả lời đúng/sai. Không cần xin quyền; tuân theo cài đặt "rung khi chạm"
 * của điện thoại (nếu bạn tắt rung cảm ứng trong Cài đặt thì sẽ không rung).
 * Android 11 trở lên có kiểu rung riêng cho "đúng" và "sai"; máy cũ hơn dùng kiểu gần nhất.
 */
class Haptics(private val view: View) {
    fun correct() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.VIRTUAL_KEY
        )
    }

    fun wrong() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
            else HapticFeedbackConstants.LONG_PRESS
        )
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}