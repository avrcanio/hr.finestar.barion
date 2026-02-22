package pos.finestar.barion.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import pos.finestar.barion.data.repo.FakePosRepository
import pos.finestar.barion.domain.repo.CheckRepository
import pos.finestar.barion.domain.repo.FloorPlanRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindFloorPlanRepository(repository: FakePosRepository): FloorPlanRepository

    @Binds
    @Singleton
    abstract fun bindCheckRepository(repository: FakePosRepository): CheckRepository
}
