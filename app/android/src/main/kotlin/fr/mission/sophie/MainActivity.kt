package fr.mission.sophie

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import fr.mission.sophie.nfc.NfcReader
import fr.mission.sophie.ui.AppRoot

class MainActivity : ComponentActivity() {
    private lateinit var nfc: NfcReader
    private val controller get() = (application as SophieApp).controller

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        nfc = NfcReader(this) { uid -> controller.onUid(uid) }
        setContent { AppRoot(controller) }
    }

    override fun onResume() {
        super.onResume()
        controller.setNfcStatus(nfc.status)
        nfc.enable()
        controller.audio.requestFocus()
    }

    override fun onPause() {
        nfc.disable()
        super.onPause()
    }
}
