plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    val envValues = run {
        val file = rootProject.file(".env")
        if (!file.exists()) {
            emptyMap()
        } else {
            file.readLines()
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
                .associate { line ->
                    val idx = line.indexOf('=')
                    val key = line.substring(0, idx).trim()
                    val rawValue = line.substring(idx + 1).trim()
                    val value = rawValue.removeSurrounding("\"").removeSurrounding("'")
                    key to value
                }
        }
    }

    fun propertyOrEnv(name: String, defaultValue: String): String {
        val projectValue = (project.findProperty(name) as String?)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (projectValue != null) return projectValue
        return envValues[name]
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: defaultValue
    }

    val apiBaseUrl = propertyOrEnv("BARION_API_BASE_URL", "https://mozart.sibenik1983.hr/")
    val vivaMerchantId = propertyOrEnv("VIVA_MERCHANT_ID", "")
    val vivaApiKey = propertyOrEnv("VIVA_API_KEY", "")
    val vivaPosClientId = propertyOrEnv("VIVA_POS_CLIENT_ID", "")
    val vivaPosClientSecret = propertyOrEnv("VIVA_POS_CLIENT_SECRET", "")
    val vivaProviderMode = propertyOrEnv("VIVA_PROVIDER_MODE", "APP2APP")
    val vivaEnv = propertyOrEnv("VIVA_ENV", "DEMO")
    val vivaObligationsBaseUrl = propertyOrEnv("VIVA_OBLIGATIONS_BASE_URL", "https://demo-api.vivapayments.com/")
    val vivaObligationsSourceCode = propertyOrEnv("VIVA_OBLIGATIONS_SOURCE_CODE", "")
    val vivaObligationsMerchantId = propertyOrEnv("VIVA_OBLIGATIONS_MERCHANT_ID", vivaMerchantId)
    val vivaObligationsPersonId = propertyOrEnv("VIVA_OBLIGATIONS_PERSON_ID", vivaObligationsMerchantId)
    val vivaObligationsWalletId = propertyOrEnv("VIVA_OBLIGATIONS_WALLET_ID", "")
    val vivaObligationsBearerToken = propertyOrEnv("VIVA_OBLIGATIONS_BEARER_TOKEN", "")
    val vivaCallbackScheme = propertyOrEnv("VIVA_CALLBACK_SCHEME", "barionviva")
    val vivaCallbackHost = propertyOrEnv("VIVA_CALLBACK_HOST", "result")
    val vivaTerminalPackage = propertyOrEnv("VIVA_TERMINAL_PACKAGE", "com.vivawallet.spoc.payapp")
    val vivaCallbackTimeoutMs = propertyOrEnv("VIVA_CALLBACK_TIMEOUT_MS", "45000")

    fun asBuildConfigString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        return "\"$escaped\""
    }

    namespace = "pos.finestar.barion"
    compileSdk = 35

    defaultConfig {
        applicationId = "pos.finestar.barion"
        minSdk = 26
        targetSdk = 35
        versionCode = 134
        versionName = "1.134"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "BARION_API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "VIVA_MERCHANT_ID", asBuildConfigString(vivaMerchantId))
        buildConfigField("String", "VIVA_API_KEY", asBuildConfigString(vivaApiKey))
        buildConfigField("String", "VIVA_POS_CLIENT_ID", asBuildConfigString(vivaPosClientId))
        buildConfigField("String", "VIVA_POS_CLIENT_SECRET", asBuildConfigString(vivaPosClientSecret))
        buildConfigField("String", "VIVA_PROVIDER_MODE", asBuildConfigString(vivaProviderMode))
        buildConfigField("String", "VIVA_ENV", asBuildConfigString(vivaEnv))
        buildConfigField("String", "VIVA_OBLIGATIONS_BASE_URL", asBuildConfigString(vivaObligationsBaseUrl))
        buildConfigField("String", "VIVA_OBLIGATIONS_SOURCE_CODE", asBuildConfigString(vivaObligationsSourceCode))
        buildConfigField("String", "VIVA_OBLIGATIONS_MERCHANT_ID", asBuildConfigString(vivaObligationsMerchantId))
        buildConfigField("String", "VIVA_OBLIGATIONS_PERSON_ID", asBuildConfigString(vivaObligationsPersonId))
        buildConfigField("String", "VIVA_OBLIGATIONS_WALLET_ID", asBuildConfigString(vivaObligationsWalletId))
        buildConfigField("String", "VIVA_OBLIGATIONS_BEARER_TOKEN", asBuildConfigString(vivaObligationsBearerToken))
        buildConfigField("String", "VIVA_CALLBACK_SCHEME", asBuildConfigString(vivaCallbackScheme))
        buildConfigField("String", "VIVA_CALLBACK_HOST", asBuildConfigString(vivaCallbackHost))
        buildConfigField("String", "VIVA_TERMINAL_PACKAGE", asBuildConfigString(vivaTerminalPackage))
        buildConfigField("int", "VIVA_CALLBACK_TIMEOUT_MS", vivaCallbackTimeoutMs)
        manifestPlaceholders["VIVA_CALLBACK_SCHEME"] = vivaCallbackScheme
        manifestPlaceholders["VIVA_CALLBACK_HOST"] = vivaCallbackHost
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-android-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
