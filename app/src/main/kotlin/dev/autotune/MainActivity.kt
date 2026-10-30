package dev.autotune

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import dev.autotune.ui.AutoTuneScreen
import dev.autotune.ui.Sharer
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: TuneViewModel by viewModels()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = vm.refreshMic()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = vm.refreshMic()
    }

    private val sharer = object : Sharer {
        override fun text(t: String) {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t),
                    "Share",
                ),
            )
        }

        override fun files(list: List<File>, mime: String, title: String) {
            val uris = ArrayList(list.map { FileProvider.getUriForFile(this@MainActivity, "$packageName.files", it) })
            val send = (if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
            else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris))
                .setType(mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(send, title))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        vm.setPermission(
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
        (getSystemService(Context.AUDIO_SERVICE) as AudioManager).registerAudioDeviceCallback(deviceCallback, null)
        dev.autotune.ui.LaunchRequest.tab.value = intent?.getStringExtra("open")
        setContent { AutoTuneScreen(vm, sharer) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dev.autotune.ui.LaunchRequest.tab.value = intent.getStringExtra("open")
    }

    override fun onResume() {
        super.onResume()
        vm.syncExternal()
    }

    override fun onDestroy() {
        (getSystemService(Context.AUDIO_SERVICE) as AudioManager).unregisterAudioDeviceCallback(deviceCallback)
        super.onDestroy()
    }
}
