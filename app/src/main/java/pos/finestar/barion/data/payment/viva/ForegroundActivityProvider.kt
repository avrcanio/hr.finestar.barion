package pos.finestar.barion.data.payment.viva

import android.app.Activity
import android.util.Log
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForegroundActivityProvider @Inject constructor() {
    private companion object {
        const val TAG = "ForegroundActivityProv"
    }

    @Volatile
    private var activityRef: WeakReference<Activity>? = null

    fun set(activity: Activity?) {
        activityRef = activity?.let { WeakReference(it) }
        Log.d(TAG, "set activity=${activity?.javaClass?.simpleName ?: "null"}")
    }

    fun current(): Activity? {
        val current = activityRef?.get()
        Log.d(TAG, "current activity=${current?.javaClass?.simpleName ?: "null"}")
        return current
    }
}
