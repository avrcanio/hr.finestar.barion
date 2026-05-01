package hr.finestar.barion.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import hr.finestar.barion.data.local.ApiCacheDao
import hr.finestar.barion.data.local.BarionDatabase
import hr.finestar.barion.data.local.CatalogSyncDao

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BarionDatabase {
        return Room.databaseBuilder(
            context,
            BarionDatabase::class.java,
            BarionDatabase.DB_NAME
        ).fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideApiCacheDao(database: BarionDatabase): ApiCacheDao = database.apiCacheDao()

    @Provides
    @Singleton
    fun provideCatalogSyncDao(database: BarionDatabase): CatalogSyncDao = database.catalogSyncDao()
}
