package hr.finestar.barion.data.di

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object ImageModule {

    /** ~512 MB; lives under cacheDir (less aggressive cleanup than external cache on MIUI/HyperOS). */
    private const val DISK_CACHE_MAX_BYTES: Long = 512L * 1024L * 1024L

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(DISK_CACHE_MAX_BYTES)
                    .build()
            }
            .okHttpClient(okHttpClient)
            .build()
    }
}
