package hr.finestar.barion.data.di

import android.util.Log
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import hr.finestar.barion.BuildConfig
import hr.finestar.barion.data.payment.viva.EnvVivaSecretsProvider
import hr.finestar.barion.data.payment.viva.VivaApp2AppPaymentAdapter
import hr.finestar.barion.data.payment.viva.VivaObligationsApi
import hr.finestar.barion.data.payment.viva.VivaObligationsPaymentAdapter
import hr.finestar.barion.data.payment.viva.VivaPaymentAdapter
import hr.finestar.barion.data.payment.viva.VivaSecretsProvider
import hr.finestar.barion.data.payment.viva.VivaTestPaymentAdapter
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object PaymentModule {
    private const val TAG = "PaymentModule"

    @Provides
    @Singleton
    fun provideVivaSecretsProvider(): VivaSecretsProvider = EnvVivaSecretsProvider()

    @Provides
    @Singleton
    fun provideVivaObligationsApi(
        gson: Gson,
        loggingInterceptor: HttpLoggingInterceptor,
        dns: Dns
    ): VivaObligationsApi {
        val client = OkHttpClient.Builder()
            .dns(dns)
            .addInterceptor(loggingInterceptor)
            .build()
        val baseUrl = BuildConfig.VIVA_OBLIGATIONS_BASE_URL
            .ifBlank { "https://demo-api.vivapayments.com/" }
            .let { if (it.endsWith("/")) it else "$it/" }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(VivaObligationsApi::class.java)
    }

    internal fun selectVivaAdapter(
        mode: String,
        app2AppAdapter: VivaPaymentAdapter,
        obligationsAdapter: VivaPaymentAdapter,
        testAdapter: VivaPaymentAdapter
    ): VivaPaymentAdapter {
        return when (mode.trim().uppercase()) {
            "APP2APP" -> app2AppAdapter
            "OBLIGATIONS" -> obligationsAdapter
            else -> testAdapter
        }
    }

    @Provides
    @Singleton
    fun provideVivaPaymentAdapter(
        app2AppAdapter: VivaApp2AppPaymentAdapter,
        obligationsAdapter: VivaObligationsPaymentAdapter,
        testAdapter: VivaTestPaymentAdapter
    ): VivaPaymentAdapter {
        val selected = selectVivaAdapter(
            mode = BuildConfig.VIVA_PROVIDER_MODE,
            app2AppAdapter = app2AppAdapter,
            obligationsAdapter = obligationsAdapter,
            testAdapter = testAdapter
        )
        val selectedName = when (selected) {
            app2AppAdapter -> "APP2APP"
            obligationsAdapter -> "OBLIGATIONS"
            else -> "TEST"
        }
        Log.i(TAG, "provideVivaPaymentAdapter mode=${BuildConfig.VIVA_PROVIDER_MODE} selected=$selectedName")
        return selected
    }
}
