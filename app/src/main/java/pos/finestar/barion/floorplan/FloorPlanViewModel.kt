package pos.finestar.barion.floorplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pos.finestar.barion.domain.model.FloorTable
import pos.finestar.barion.domain.usecase.GetFloorTablesUseCase
import pos.finestar.barion.domain.usecase.OpenOrCreateCheckForTableUseCase

@HiltViewModel
class FloorPlanViewModel @Inject constructor(
    private val getFloorTables: GetFloorTablesUseCase,
    private val openOrCreateCheckForTable: OpenOrCreateCheckForTableUseCase
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val tables: List<FloorTable> = emptyList(),
        val error: String? = null
    )

    sealed interface Event {
        data class OpenCheck(val checkId: Long, val tableName: String) : Event
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    init {
        loadTables()
    }

    fun onTableClick(tableId: Long) {
        viewModelScope.launch {
            val check = openOrCreateCheckForTable(tableId)
            loadTables()
            _events.emit(Event.OpenCheck(check.checkId, check.tableName))
        }
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
