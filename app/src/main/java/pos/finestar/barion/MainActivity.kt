package pos.finestar.barion

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import pos.finestar.barion.BuildConfig
import pos.finestar.barion.data.payment.viva.ForegroundActivityProvider
import pos.finestar.barion.ui.navigation.BarionNavHost
import pos.finestar.barion.ui.theme.BarionTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private companion object {
        const val TAG = "MainActivity"
    }

    @Inject
    lateinit var foregroundActivityProvider: ForegroundActivityProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        Log.i(TAG, "onResume")
        foregroundActivityProvider.set(this)
    }

    override fun onPause() {
        Log.i(TAG, "onPause")
        foregroundActivityProvider.set(null)
        super.onPause()
    }
}
