package com.gbtminhtri.guitar

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}

/**
 * Gọi onStop khi app xuống nền (bấm Home, khóa máy, chuyển app).
 * DisposableEffect/onDispose chỉ chạy khi RỜI MÀN HÌNH trong app, không chạy khi app xuống nền,
 * nên metronome và micro tuner sẽ chạy tiếp nếu không dùng cái này.
 */
@Composable
fun StopWhenBackgrounded(onStop: () -> Unit) {
    val activity = LocalContext.current.findComponentActivity()
    val currentOnStop by rememberUpdatedState(onStop)
    DisposableEffect(activity) {
        val lifecycle = activity?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) currentOnStop()
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
}