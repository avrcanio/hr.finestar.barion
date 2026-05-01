package hr.finestar.barion.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import hr.finestar.barion.data.repo.RemoteAuthRepository
import hr.finestar.barion.data.repo.RemoteCatalogRepository
import hr.finestar.barion.data.repo.RemoteCheckRepository
import hr.finestar.barion.data.repo.RemoteFloorPlanRepository
import hr.finestar.barion.domain.repo.AuthRepository
import hr.finestar.barion.domain.repo.CatalogRepository
import hr.finestar.barion.domain.repo.CheckRepository
import hr.finestar.barion.domain.repo.FloorPlanRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindFloorPlanRepository(repository: RemoteFloorPlanRepository): FloorPlanRepository

    @Binds
    @Singleton
    abstract fun bindCheckRepository(repository: RemoteCheckRepository): CheckRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(repository: RemoteAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindCatalogRepository(repository: RemoteCatalogRepository): CatalogRepository
}
