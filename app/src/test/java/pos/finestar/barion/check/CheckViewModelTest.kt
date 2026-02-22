package pos.finestar.barion.check

import androidx.lifecycle.SavedStateHandle
import java.net.SocketTimeoutException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pos.finestar.barion.domain.model.CheckItem
import pos.finestar.barion.domain.model.CheckSession
import pos.finestar.barion.domain.model.TableStatus
import pos.finestar.barion.domain.repo.CheckRepository
import pos.finestar.barion.domain.usecase.AddItemToCheckUseCase
import pos.finestar.barion.domain.usecase.GetCheckByIdUseCase
import pos.finestar.barion.domain.usecase.RemoveItemFromCheckUseCase
import pos.finestar.barion.domain.usecase.UpdateCheckItemQtyUseCase
import pos.finestar.barion.testutil.MainDispatcherRule
import pos.finestar.barion.ui.navigation.NavRoutes

@OptIn(ExperimentalCoroutinesApi::class)
class CheckViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads items and clears loading state`() = runTest {
        val repository = object : CheckRepository {
            override suspend fun createCheck(tableId: Long): CheckSession = error("unused")
            override suspend fun getOpenCheckByTable(tableId: Long): CheckSession = error("unused")
            override suspend fun getCheck(checkId: Long): CheckSession {
                return CheckSession(
                    checkId = checkId,
                    tableId = 7L,
                    tableName = "Sto 7",
                    status = TableStatus.OPEN,
                    items = listOf(CheckItem(name = "Espresso", qty = 1, price = 2.5)),
                    subtotal = 2.5,
                    tax = 0.5,
                    total = 3.0
                )
            }
            override suspend fun addItem(checkId: Long, productId: Long, qty: Int): CheckSession = error("unused")
            override suspend fun updateItem(checkId: Long, itemId: Long, qty: Int): CheckSession = error("unused")
            override suspend fun removeItem(checkId: Long, itemId: Long): CheckSession = error("unused")
        }

        val vm = CheckViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    NavRoutes.ARG_CHECK_ID to 21L,
                    NavRoutes.ARG_TABLE_NAME to "Sto+7"
                )
            ),
            getCheckByIdUseCase = GetCheckByIdUseCase(repository),
            addItemToCheckUseCase = AddItemToCheckUseCase(repository),
            updateCheckItemQtyUseCase = UpdateCheckItemQtyUseCase(repository),
            removeItemFromCheckUseCase = RemoveItemFromCheckUseCase(repository)
        )

        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(1, state.items.size)
        assertEquals("Espresso", state.items.first().name)
    }

    @Test
    fun `sets error state when repository fails`() = runTest {
        val repository = object : CheckRepository {
            override suspend fun createCheck(tableId: Long): CheckSession = error("unused")
            override suspend fun getOpenCheckByTable(tableId: Long): CheckSession = error("unused")
            override suspend fun getCheck(checkId: Long): CheckSession {
                throw IllegalStateException("Network error")
            }
            override suspend fun addItem(checkId: Long, productId: Long, qty: Int): CheckSession = error("unused")
            override suspend fun updateItem(checkId: Long, itemId: Long, qty: Int): CheckSession = error("unused")
            override suspend fun removeItem(checkId: Long, itemId: Long): CheckSession = error("unused")
        }

        val vm = CheckViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    NavRoutes.ARG_CHECK_ID to 21L,
                    NavRoutes.ARG_TABLE_NAME to "Sto+7"
                )
            ),
            getCheckByIdUseCase = GetCheckByIdUseCase(repository),
            addItemToCheckUseCase = AddItemToCheckUseCase(repository),
            updateCheckItemQtyUseCase = UpdateCheckItemQtyUseCase(repository),
            removeItemFromCheckUseCase = RemoveItemFromCheckUseCase(repository)
        )

        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.error?.contains("Network error") == true)
    }

    @Test
    fun `sets message and resets mutating state when add item times out`() = runTest {
        val repository = object : CheckRepository {
            override suspend fun createCheck(tableId: Long): CheckSession = error("unused")
            override suspend fun getOpenCheckByTable(tableId: Long): CheckSession = error("unused")
            override suspend fun getCheck(checkId: Long): CheckSession {
                return CheckSession(
                    checkId = checkId,
                    tableId = 7L,
                    tableName = "Sto 7",
                    status = TableStatus.OPEN,
                    items = listOf(CheckItem(itemId = 101L, name = "Espresso", qty = 1, price = 2.5))
                )
            }

            override suspend fun addItem(checkId: Long, productId: Long, qty: Int): CheckSession {
                throw SocketTimeoutException("timeout")
            }

            override suspend fun updateItem(checkId: Long, itemId: Long, qty: Int): CheckSession = error("unused")
            override suspend fun removeItem(checkId: Long, itemId: Long): CheckSession = error("unused")
        }

        val vm = CheckViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    NavRoutes.ARG_CHECK_ID to 21L,
                    NavRoutes.ARG_TABLE_NAME to "Sto+7"
                )
            ),
            getCheckByIdUseCase = GetCheckByIdUseCase(repository),
            addItemToCheckUseCase = AddItemToCheckUseCase(repository),
            updateCheckItemQtyUseCase = UpdateCheckItemQtyUseCase(repository),
            removeItemFromCheckUseCase = RemoveItemFromCheckUseCase(repository)
        )

        advanceUntilIdle()
        vm.onAddItem(productId = 2L, qty = 1)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isMutating)
        assertTrue(state.message?.contains("timeout") == true)
        assertEquals(1, state.items.size)
    }
}
