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
import pos.finestar.barion.domain.model.TableStatus
import pos.finestar.barion.domain.repo.AuthRepository
import pos.finestar.barion.domain.usecase.CreateCheckForTableUseCase
import pos.finestar.barion.domain.usecase.GetFloorTablesUseCase
import pos.finestar.barion.domain.usecase.GetOpenCheckForTableUseCase
import retrofit2.HttpException

@HiltViewModel
class FloorPlanViewModel @Inject constructor(
    private val getFloorTables: GetFloorTablesUseCase,
    private val createCheckForTable: CreateCheckForTableUseCase,
    private val getOpenCheckForTable: GetOpenCheckForTableUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val tables: List<FloorTable> = emptyList(),
        val userDisplayName: String? = null,
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
        loadUserProfile()
        loadTables(forceLoading = true)
    }

    fun onResume() {
        loadTables(forceLoading = false)
    }

    fun onTableClick(tableId: Long) {
        viewModelScope.launch {
            val table = _uiState.value.tables.firstOrNull { it.id == tableId }
            runCatching {
                if (table?.status == TableStatus.OPEN) {
                    getOpenCheckForTable(tableId)
                } else {
                    createCheckForTable(tableId)
                }
            }.onSuccess { check ->
                _events.emit(
                    Event.OpenCheck(
                        checkId = check.checkId,
                        tableName = table?.name ?: check.tableName
                    )
                )
            }.onFailure { throwable ->
                val message = when {
                    throwable is HttpException && throwable.code() == 400 ->
                        "Server je odbio zahtjev (HTTP 400). Provjeri stanje stola i pokusaj ponovo."

                    throwable is HttpException ->
                        "Mrezna greska (HTTP ${throwable.code()})."

                    else ->
                        throwable.message ?: "Ne mogu otvoriti sto."
                }
                _uiState.update { it.copy(error = message) }
            }
        }
    }

    private fun loadTables(forceLoading: Boolean) {
        viewModelScope.launch {
            if (forceLoading) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            } else {
                _uiState.update { it.copy(error = null) }
            }

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

    private fun loadUserProfile() {
        viewModelScope.launch {
            val displayName = authRepository.currentUserDisplayName()
            _uiState.update { it.copy(userDisplayName = displayName) }
        }
    }
}
