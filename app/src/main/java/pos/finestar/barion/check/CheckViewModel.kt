package pos.finestar.barion.check

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pos.finestar.barion.domain.model.CheckItem
import pos.finestar.barion.domain.usecase.GetCheckByIdUseCase
import pos.finestar.barion.ui.navigation.NavRoutes

@HiltViewModel
class CheckViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCheckByIdUseCase: GetCheckByIdUseCase
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val checkId: Long = 0L,
        val tableName: String = "",
        val status: String = "OPEN",
        val items: List<CheckItem> = emptyList(),
        val subtotal: Double = 0.0,
        val tax: Double = 0.0,
        val total: Double = 0.0,
        val error: String? = null,
        val message: String? = null
    )

    private val checkId: Long = savedStateHandle[NavRoutes.ARG_CHECK_ID] ?: 0L
    private val tableName: String = savedStateHandle[NavRoutes.ARG_TABLE_NAME] ?: "Unknown"

    private val _uiState = MutableStateFlow(
        UiState(
            checkId = checkId,
            tableName = java.net.URLDecoder.decode(tableName, Charsets.UTF_8.name())
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadCheck()
    }

    fun onAddItem() {
        _uiState.update { it.copy(message = "Dodaj stavku: placeholder") }
    }

    fun onPay() {
        _uiState.update { it.copy(message = "Naplata: placeholder") }
    }

    private fun loadCheck() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            runCatching { getCheckByIdUseCase(checkId) }
                .onSuccess { check ->
                    if (check != null) {
                        val displayItems = if (check.items.isEmpty()) {
                            listOf(
                                CheckItem("Dummy stavka", 1, 5.0),
                                CheckItem("Dummy stavka 2", 2, 3.0)
                            )
                        } else {
                            check.items
                        }
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                status = check.status.name,
                                items = displayItems,
                                subtotal = check.subtotal,
                                tax = check.tax,
                                total = check.total,
                                error = null
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Check nije pronađen."
                            )
                        }
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = throwable.message ?: "Greška pri učitavanju checka."
                        )
                    }
                }
        }
    }
}
