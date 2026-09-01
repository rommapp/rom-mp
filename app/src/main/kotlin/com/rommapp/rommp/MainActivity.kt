package com.rommapp.rommp

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rommapp.rommp.data.playback.PlayerLockController
import com.rommapp.rommp.ui.RootScreen
import com.rommapp.rommp.ui.theme.RomMMusicTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var lockController: PlayerLockController

    /**
     * Rotate-shut on this handheld emits KEYCODE_F12 (down = shut, up = open).
     * Only while the player is visible, shut engages the in-app lock and open
     * releases it; the event is never consumed, so the OS keyguard still fires
     * (occluded by the player's setShowWhenLocked). Everywhere else F12 falls
     * through untouched.
     *
     * Lock is edge-triggered on the first down only (repeatCount == 0). The key
     * stays down the whole time the device is shut, so honoring key-repeat would
     * re-lock and stomp a manual unlock made while still shut.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (lockController.playerVisible.get() && event.keyCode == KeyEvent.KEYCODE_F12) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) lockController.requestLock()
                KeyEvent.ACTION_UP -> lockController.requestUnlock()
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Keep the display awake and hide the system bars for a full-screen handheld UI.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        setContent {
            RomMMusicTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RootScreen()
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
