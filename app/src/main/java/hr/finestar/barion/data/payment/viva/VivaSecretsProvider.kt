package hr.finestar.barion.data.payment.viva

import javax.inject.Inject
import javax.inject.Singleton
import hr.finestar.barion.BuildConfig

interface VivaSecretsProvider {
    fun merchantId(): String?
    fun apiKey(): String?
    fun posClientId(): String?
    fun posClientSecret(): String?
}

@Singleton
class EnvVivaSecretsProvider @Inject constructor() : VivaSecretsProvider {
    override fun merchantId(): String? = BuildConfig.VIVA_MERCHANT_ID.ifBlank {
        System.getenv("VIVA_MERCHANT_ID")
    }

    override fun apiKey(): String? = BuildConfig.VIVA_API_KEY.ifBlank {
        System.getenv("VIVA_API_KEY")
    }

    override fun posClientId(): String? = BuildConfig.VIVA_POS_CLIENT_ID.ifBlank {
        System.getenv("VIVA_POS_CLIENT_ID")
    }

    override fun posClientSecret(): String? = BuildConfig.VIVA_POS_CLIENT_SECRET.ifBlank {
        System.getenv("VIVA_POS_CLIENT_SECRET")
    }
}
