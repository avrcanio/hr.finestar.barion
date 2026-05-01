package hr.finestar.barion

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import hr.finestar.barion.BuildConfig
import hr.finestar.barion.data.payment.viva.ForegroundActivityProvider
import hr.finestar.barion.ui.navigation.BarionNavHost
import hr.finestar.barion.ui.theme.BarionTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private companion object {
        const val TAG = "MainActivity"
    }

    @Inject
    lateinit var foregroundActivityProvider: ForegroundActivityProvider

    private fun applyImmersiveStickyMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyImmersiveStickyMode()
        Log.i(
            TAG,
            "onCreate vivaConfig mode=${BuildConfig.VIVA_PROVIDER_MODE} env=${BuildConfig.VIVA_ENV} scheme=${BuildConfig.VIVA_CALLBACK_SCHEME} host=${BuildConfig.VIVA_CALLBACK_HOST} terminalPackage=${BuildConfig.VIVA_TERMINAL_PACKAGE} obligationsBaseUrl=${BuildConfig.VIVA_OBLIGATIONS_BASE_URL} timeoutMs=${BuildConfig.VIVA_CALLBACK_TIMEOUT_MS}"
        )
        setContent {
            BarionTheme {
                BarionNavHost()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveStickyMode()
        Log.i(TAG, "onResume")
        foregroundActivityProvider.set(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveStickyMode()
    }

    override fun onPause() {
        Log.i(TAG, "onPause")
        foregroundActivityProvider.set(null)
        super.onPause()
    }
}
