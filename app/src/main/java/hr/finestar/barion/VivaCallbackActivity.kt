package hr.finestar.barion

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import hr.finestar.barion.data.payment.viva.VivaCallbackParser
import hr.finestar.barion.data.payment.viva.VivaPaymentCallbackRegistry

@AndroidEntryPoint
class VivaCallbackActivity : ComponentActivity() {
    private companion object {
        const val TAG = "VivaCallbackActivity"
    }

    @Inject
    lateinit var callbackParser: VivaCallbackParser

    @Inject
    lateinit var callbackRegistry: VivaPaymentCallbackRegistry

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        val extras = intent?.extras

        Log.i(
            TAG,
            "Received callback action=${intent?.action} dataString=${intent?.dataString} uri=${data?.toString()} categories=${intent?.categories?.joinToString(",") ?: "none"} extrasKeys=${extras?.keySet()?.joinToString(",") ?: "none"}"
        )
        extras?.keySet()?.forEach { key ->
            Log.i(TAG, "Callback extra $key=${extras.get(key)}")
        }

        when {
            data != null -> {
                runCatching {
                    callbackParser.parse(data)
                }.onSuccess { parsed ->
                    callbackRegistry.complete(parsed)
                }.onFailure {
                    Log.e(TAG, "Failed to parse callback uri", it)
                }
            }
            extras != null -> {
                runCatching {
                    callbackParser.parse(extras)
                }.onSuccess { parsed ->
                    callbackRegistry.complete(parsed)
                }.onFailure {
                    Log.e(TAG, "Failed to parse callback extras", it)
                }
            }
            else -> {
                Log.w(TAG, "Callback received without uri and without extras.")
            }
        }
        finish()
    }
}
