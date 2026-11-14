package dev.autotune.ui

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Whether the phone's own auto-rotate is on (the quick-settings tile). Off when it cannot be read. */
fun autoRotateOn(ctx: Context): Boolean =
    runCatching { Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1 }.getOrDefault(false)

/** The phone's auto-rotate setting, kept up to date while the screen is up: flipping the tile takes effect at once. */
@Composable
fun rememberAutoRotate(): State<Boolean> {
    val ctx = LocalContext.current
    val state = remember { mutableStateOf(autoRotateOn(ctx)) }
    DisposableEffect(ctx) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { state.value = autoRotateOn(ctx) }
        }
        runCatching {
            ctx.contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, observer)
        }
        state.value = autoRotateOn(ctx)
        onDispose { runCatching { ctx.contentResolver.unregisterContentObserver(observer) } }
    }
    return state
}
