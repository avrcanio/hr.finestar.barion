package pos.finestar.barion.floorplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pos.finestar.barion.domain.model.FloorTable
import pos.finestar.barion.domain.usecase.GetFloorTablesUseCase

@HiltViewModel
class FloorPlanViewModel @Inject constructor(
    private val getFloorTables: GetFloorTablesUseCase
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val tables: List<FloorTable> = emptyList(),
        val selectedTableId: Long? = null,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadTables()
    }

    fun onTableClick(tableId: Long) {
        _uiState.update { it.copy(selectedTableId = tableId) }
    }

    private fun loadTables() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { getFloorTables() }
                .onSuccess { tables ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            tables = tables,
                            error = null
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = throwable.message ?: "Unexpected error"
                        )
                    }
                }
        }
    }
}
