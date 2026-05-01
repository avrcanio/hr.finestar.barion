package hr.finestar.barion.check

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
import hr.finestar.barion.domain.model.CheckItem
import hr.finestar.barion.domain.model.CheckSession
import hr.finestar.barion.domain.model.SettlementMethod
import hr.finestar.barion.domain.model.SettlementReceipt
import hr.finestar.barion.domain.model.SettlementState
import hr.finestar.barion.domain.model.SettlementStatePart
import hr.finestar.barion.domain.model.TableStatus
import hr.finestar.barion.domain.repo.AuthRepository
import hr.finestar.barion.domain.repo.CheckRepository
import hr.finestar.barion.domain.usecase.AddItemToCheckUseCase
import hr.finestar.barion.domain.usecase.GetCheckByIdUseCase
import hr.finestar.barion.domain.usecase.GratisCheckItemUseCase
import hr.finestar.barion.domain.usecase.CloseCheckUseCase
import hr.finestar.barion.domain.usecase.ConfirmSettlementPartCardUseCase
import hr.finestar.barion.domain.usecase.GetSettlementStateUseCase
import hr.finestar.barion.domain.usecase.FiscalizeReceiptUseCase
import hr.finestar.barion.domain.usecase.GetCheckRoundStateUseCase
import hr.finestar.barion.domain.usecase.OtpisCheckItemUseCase
import hr.finestar.barion.domain.usecase.PaySettlementPartCashUseCase
import hr.finestar.barion.domain.usecase.PrepareSettlementPartUseCase
import hr.finestar.barion.domain.usecase.RemoveItemFromCheckUseCase
import hr.finestar.barion.domain.usecase.SendToBarUseCase
import hr.finestar.barion.domain.usecase.StornoCheckItemUseCase
import hr.finestar.barion.domain.usecase.UpdateCheckItemQtyUseCase
import hr.finestar.barion.testutil.MainDispatcherRule
import hr.finestar.barion.ui.navigation.NavRoutes

@OptIn(ExperimentalCoroutinesApi::class)
class CheckViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = object : AuthRepository {
        override suspend fun bootstrapSession(): Boolean = true
        override suspend fun loginWithPin(pin: String, username: String?, deviceId: String?) = Unit
        override suspend fun verifyPin(pin: String) = Unit
        override suspend fun currentUserDisplayName(): String? = "Test User"
        override suspend fun logout() = Unit
    }

    @Test
    fun `loads items and clears loading state`() = runTest {
        val repository = object : CheckRepository {
            override suspend fun createCheck(tableId: Long): CheckSession = error("unused")
            override suspend fun getOpenCheckByTable(tableId: Long): CheckSession = error("unused")
            override suspend fun getCheck(checkId: Long, forceRefresh: Boolean): CheckSession {
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
            override suspend fun addItem(
                checkId: Long,
                productId: Long,
                qty: Int,
                unitPrice: Double,
                productName: String?,
                modifiers: List<hr.finestar.barion.domain.model.SelectedModifier>,
                note: String?
            ): CheckSession = error("unused")
            override suspend fun updateItem(checkId: Long, itemId: Long, qty: Int): CheckSession = error("unused")
            override suspend fun removeItem(checkId: Long, itemId: Long): CheckSession = error("unused")
            override suspend fun stornoItem(checkId: Long, itemId: Long, reason: String?, qty: Int?): CheckSession = error("unused")
            override suspend fun gratisItem(checkId: Long, itemId: Long, reason: String?, qty: Int?): CheckSession = error("unused")
            override suspend fun otpisItem(checkId: Long, itemId: Long, reason: String?, qty: Int?): CheckSession = error("unused")
            override suspend fun sendToBar(checkId: Long): CheckSession = error("unused")
            override suspend fun closeCheck(checkId: Long): CheckSession? = error("unused")
            override suspend fun issueReceipt(checkId: Long, fiscalize: Boolean): CheckSession? = error("unused")
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
            removeItemFromCheckUseCase = RemoveItemFromCheckUseCase(repository),
            stornoCheckItemUseCase = StornoCheckItemUseCase(repository),
            gratisCheckItemUseCase = GratisCheckItemUseCase(repository),
            otpisCheckItemUseCase = OtpisCheckItemUseCase(repository),
            closeCheckUseCase = CloseCheckUseCase(repository),
            sendToBarUseCase = SendToBarUseCase(repository),
            prepareSettlementPartUseCase = PrepareSettlementPartUseCase(repository),
            paySettlementPartCashUseCase = PaySettlementPartCashUseCase(repository),
            confirmSettlementPartCardUseCase = ConfirmSettlementPartCardUseCase(repository),
            fiscalizeReceiptUseCase = FiscalizeReceiptUseCase(repository),
            getSettlementStateUseCase = GetSettlementStateUseCase(repository),
            getCheckRoundStateUseCase = GetCheckRoundStateUseCase(repository),
            authRepository = authRepository
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
            override suspend fun getCheck(checkId: Long, forceRefresh: Boolean): CheckSession {
                throw IllegalStateException("Network error")
            }
            override suspend fun addItem(
                checkId: Long,
                productId: Long,
                qty: Int,
                unitPrice: Double,
                productName: String?,
                modifiers: List<hr.finestar.barion.domain.model.SelectedModifier>,
                note: String?
            ): CheckSession = error("unused")
            override suspend fun updateItem(checkId: Long, itemId: Long, qty: Int): CheckSession = error("unused")
            override suspend fun removeItem(checkId: Long, itemId: Long): CheckSession = error("unused")
            override suspend fun stornoItem(checkId: Long, itemId: Long, reason: String?, qty: Int?): CheckSession = error("unused")
            override suspend fun gratisItem(checkId: Long, itemId: Long, reason: String?, qty: Int?): CheckSession = error("unused")
            override suspend fun otpisItem(checkId: Long, itemId: Long, reason: String?, qty: Int?): CheckSession = error("unused")
            override suspend fun sendToBar(checkId: Long): CheckSession = error("unused")
            override suspend fun closeCheck(checkId: Long): CheckSession? = error("unused")
            override suspend fun issueReceipt(checkId: Long, fiscalize: Boolean): CheckSession? = error("unused")
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
            removeItemFromCheckUseCase = RemoveItemFromCheckUseCase(repository),
            stornoCheckItemUseCase = StornoCheckItemUseCase(repository),
            gratisCheckItemUseCase = GratisCheckItemUseCase(repository),
            otpisCheckItemUseCase = OtpisCheckItemUseCase(repository),
            closeCheckUseCase = CloseCheckUseCase(repository),
            sendToBarUseCase = SendToBarUseCase(repository),
            prepareSettlementPartUseCase = PrepareSettlementPartUseCase(repository),
            paySettlementPartCashUseCase = PaySettlementPartCashUseCase(repository),
            confirmSettlementPartCardUseCase = ConfirmSettlementPartCardUseCase(repository),
            fiscalizeReceiptUseCase = FiscalizeReceiptUseCase(repository),
            getSettlementStateUseCase = GetSettlementStateUseCase(repository),
            getCheckRoundStateUseCase = GetCheckRoundStateUseCase(repository),
            authRepository = authRepository
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
            override suspend fun getCheck(checkId: Long, forceRefresh: Boolean): CheckSession {
                return CheckSession(
                    checkId = checkId,
                    tableId = 7L,
                    tableName = "Sto 7",
                    status = TableStatus.OPEN,
                    items = listOf(CheckItem(itemId = 101L, name = "Espresso", qty = 1, price = 2.5))
                )
            }

            override suspend fun addItem(
                checkId: Long,
                productId: Long,
                qty: Int,
                unitPrice: Double,
                productName: String?,
                modifiers: List<hr.finestar.barion.domain.model.SelectedModifier>,
                note: String?
            ): CheckSession {
                throw SocketTimeoutException("timeout")
            }

            override suspend fun updateItem(checkId: Long, itemId: Long, qty: Int): CheckSession = error("unused")
            override suspend fun removeItem(checkId: Long, itemId: Long): CheckSession = error("unused")
            override suspend fun stornoItem(checkId: Long, itemId: Long, reason: String?, qty: Int?): CheckSession = error("unused")
            override suspend fun gratisItem(checkId: Long, itemId: Long, reason: String?, qty: Int?): CheckSession = error("unused")
            override suspend fun otpisItem(checkId: Long, itemId: Long, reason: String?, qty: Int?): CheckSession = error("unused")
            override suspend fun sendToBar(checkId: Long): CheckSession = error("unused")
            override suspend fun closeCheck(checkId: Long): CheckSession? = error("unused")
            override suspend fun issueReceipt(checkId: Long, fiscalize: Boolean): CheckSession? = error("unused")
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
            removeItemFromCheckUseCase = RemoveItemFromCheckUseCase(repository),
            stornoCheckItemUseCase = StornoCheckItemUseCase(repository),
            gratisCheckItemUseCase = GratisCheckItemUseCase(repository),
            otpisCheckItemUseCase = OtpisCheckItemUseCase(repository),
            closeCheckUseCase = CloseCheckUseCase(repository),
            sendToBarUseCase = SendToBarUseCase(repository),
            prepareSettlementPartUseCase = PrepareSettlementPartUseCase(repository),
            paySettlementPartCashUseCase = PaySettlementPartCashUseCase(repository),
            confirmSettlementPartCardUseCase = ConfirmSettlementPartCardUseCase(repository),
            fiscalizeReceiptUseCase = FiscalizeReceiptUseCase(repository),
            getSettlementStateUseCase = GetSettlementStateUseCase(repository),
            getCheckRoundStateUseCase = GetCheckRoundStateUseCase(repository),
            authRepository = authRepository
        )

        advanceUntilIdle()
        vm.onAddItem(productId = 2L, qty = 1, unitPrice = 2.5)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isMutating)
        assertTrue(state.message?.contains("timeout") == true)
        assertEquals(1, state.items.size)
    }

    @Test
    fun `maps receipt payment method by receipt id instead of part index`() = runTest {
        val repository = object : CheckRepository {
            override suspend fun createCheck(tableId: Long): CheckSession = error("unused")
            override suspend fun getOpenCheckByTable(tableId: Long): CheckSession = error("unused")
            override suspend fun getCheck(checkId: Long, forceRefresh: Boolean): CheckSession {
                return CheckSession(
                    checkId = checkId,
                    tableId = 7L,
                    tableName = "Sto 7",
                    status = TableStatus.OPEN,
                    items = emptyList(),
                    subtotal = 4.60,
                    tax = 0.0,
                    total = 4.60
                )
            }

            override suspend fun getSettlementState(checkId: Long): SettlementState {
                return SettlementState(
                    checkStatus = "OPEN",
                    paymentStatus = "PAID",
                    posReceiptIds = listOf(12L, 11L),
                    receipts = listOf(
                        SettlementReceipt(id = 12L, receiptNumber = 12, totalAmount = 2.60),
                        SettlementReceipt(id = 11L, receiptNumber = 11, totalAmount = 2.00)
                    ),
                    parts = listOf(
                        SettlementStatePart(
                            partId = 195L,
                            status = "PAID",
                            method = SettlementMethod.CASH,
                            amount = 2.00,
                            issuedReceiptId = 11L
                        ),
                        SettlementStatePart(
                            partId = 193L,
                            status = "PAID",
                            method = SettlementMethod.CARD,
                            amount = 2.60,
                            issuedReceiptId = 12L,
                            cardMaskedPan = "539982******9303"
                        )
                    )
                )
            }

            override suspend fun addItem(
                checkId: Long,
                productId: Long,
                qty: Int,
                unitPrice: Double,
                productName: String?,
                modifiers: List<hr.finestar.barion.domain.model.SelectedModifier>,
                note: String?
            ): CheckSession = error("unused")

            override suspend fun updateItem(checkId: Long, itemId: Long, qty: Int): CheckSession = error("unused")
            override suspend fun removeItem(checkId: Long, itemId: Long): CheckSession = error("unused")
            override suspend fun stornoItem(checkId: Long, itemId: Long, reason: String?, qty: Int?): CheckSession = error("unused")
            override suspend fun gratisItem(checkId: Long, itemId: Long, reason: String?, qty: Int?): CheckSession = error("unused")
            override suspend fun otpisItem(checkId: Long, itemId: Long, reason: String?, qty: Int?): CheckSession = error("unused")
            override suspend fun sendToBar(checkId: Long): CheckSession = error("unused")
            override suspend fun closeCheck(checkId: Long): CheckSession? = error("unused")
            override suspend fun issueReceipt(checkId: Long, fiscalize: Boolean): CheckSession? = error("unused")
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
            removeItemFromCheckUseCase = RemoveItemFromCheckUseCase(repository),
            stornoCheckItemUseCase = StornoCheckItemUseCase(repository),
            gratisCheckItemUseCase = GratisCheckItemUseCase(repository),
            otpisCheckItemUseCase = OtpisCheckItemUseCase(repository),
            closeCheckUseCase = CloseCheckUseCase(repository),
            sendToBarUseCase = SendToBarUseCase(repository),
            prepareSettlementPartUseCase = PrepareSettlementPartUseCase(repository),
            paySettlementPartCashUseCase = PaySettlementPartCashUseCase(repository),
            confirmSettlementPartCardUseCase = ConfirmSettlementPartCardUseCase(repository),
            fiscalizeReceiptUseCase = FiscalizeReceiptUseCase(repository),
            getSettlementStateUseCase = GetSettlementStateUseCase(repository),
            getCheckRoundStateUseCase = GetCheckRoundStateUseCase(repository),
            authRepository = authRepository
        )

        advanceUntilIdle()

        val receipts = vm.uiState.value.settlementReceipts
        assertEquals(2, receipts.size)
        assertEquals(12L, receipts[0].id)
        assertEquals("CARD", receipts[0].paymentMethod)
        assertEquals("539982******9303", receipts[0].cardMaskedPan)
        assertEquals(11L, receipts[1].id)
        assertEquals("CASH", receipts[1].paymentMethod)
        assertNull(receipts[1].cardMaskedPan)
    }
}
