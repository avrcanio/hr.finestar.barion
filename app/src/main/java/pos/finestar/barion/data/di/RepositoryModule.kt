package pos.finestar.barion.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import pos.finestar.barion.data.repo.RemoteAuthRepository
import pos.finestar.barion.data.repo.RemoteCatalogRepository
import pos.finestar.barion.data.repo.RemoteCheckRepository
import pos.finestar.barion.data.repo.RemoteFloorPlanRepository
import pos.finestar.barion.domain.repo.AuthRepository
import pos.finestar.barion.domain.repo.CatalogRepository
import pos.finestar.barion.domain.repo.CheckRepository
import pos.finestar.barion.domain.repo.FloorPlanRepository

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
