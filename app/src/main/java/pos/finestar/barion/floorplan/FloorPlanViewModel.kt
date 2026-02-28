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
import pos.finestar.barion.domain.model.AllowedLayout
import pos.finestar.barion.domain.model.FloorTable
import pos.finestar.barion.domain.model.RuntimeMode
import pos.finestar.barion.domain.model.TableStatus
import pos.finestar.barion.domain.repo.AuthRepository
import pos.finestar.barion.domain.usecase.CreateCheckForTableUseCase
import pos.finestar.barion.domain.usecase.GetAllowedLayoutsUseCase
import pos.finestar.barion.domain.usecase.GetFloorTablesUseCase
import pos.finestar.barion.domain.usecase.GetOpenCheckForTableUseCase
import pos.finestar.barion.domain.usecase.GetRuntimeModeUseCase
import retrofit2.HttpException

@HiltViewModel
class FloorPlanViewModel @Inject constructor(
    private val getFloorTables: GetFloorTablesUseCase,
    private val getAllowedLayouts: GetAllowedLayoutsUseCase,
    private val createCheckForTable: CreateCheckForTableUseCase,
    private val getOpenCheckForTable: GetOpenCheckForTableUseCase,
    private val getRuntimeMode: GetRuntimeModeUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val isMutating: Boolean = false,
        val isRuntimeModeRefreshing: Boolean = false,
        val tables: List<FloorTable> = emptyList(),
        val allowedLayouts: List<AllowedLayout> = emptyList(),
        val selectedLayoutId: Long? = null,
        val selectedLayoutName: String? = null,
        val userDisplayName: String? = null,
        val runtimeMode: RuntimeMode = RuntimeMode.UNKNOWN,
        val error: String? = null
    )

    sealed interface Event {
        data class OpenCheck(val checkId: Long, val tableName: String) : Event
        data object NavigateToPin : Event
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    init {
        loadUserProfile()
        loadInitial(forceLoading = true)
    }

    fun onResume() {
        loadInitial(forceLoading = false)
    }

    fun onLayoutSelected(layoutId: Long) {
        if (_uiState.value.selectedLayoutId == layoutId) return
        _uiState.update { state ->
            state.copy(
                selectedLayoutId = layoutId,
                selectedLayoutName = state.allowedLayouts.firstOrNull { it.id == layoutId }?.name ?: state.selectedLayoutName
            )
        }
        loadTables(forceLoading = true, layoutId = layoutId)
    }

    fun onTableClick(tableId: Long) {
        viewModelScope.launch {
            val table = _uiState.value.tables.firstOrNull { it.id == tableId }
            _uiState.update { it.copy(isMutating = true, error = null) }
            runCatching {
                if (table?.status == TableStatus.OPEN) {
                    getOpenCheckForTable(tableId)
                } else {
                    createCheckForTable(tableId)
                }
            }.onSuccess { check ->
                _uiState.update { it.copy(isMutating = false) }
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
                _uiState.update { it.copy(isMutating = false) }
            }
        }
    }

    fun onLogout() {
        viewModelScope.launch {
            if (_uiState.value.isMutating) return@launch
            _uiState.update { it.copy(isMutating = true, error = null) }
            runCatching { authRepository.logout() }
            _uiState.update { it.copy(isMutating = false) }
            _events.emit(Event.NavigateToPin)
        }
    }

    private fun loadInitial(forceLoading: Boolean) {
        loadRuntimeMode(forceRefresh = true)
        loadTables(forceLoading = forceLoading, layoutId = _uiState.value.selectedLayoutId)
    }

    private fun loadTables(forceLoading: Boolean, layoutId: Long?) {
        viewModelScope.launch {
            if (forceLoading) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            } else {
                _uiState.update { it.copy(error = null) }
            }

            runCatching { getFloorTables(layoutId = layoutId, forceRefresh = false) }
                .onSuccess { floorPlan ->
                    applyFloorPlan(floorPlan = floorPlan, forceRefresh = false)
                    viewModelScope.launch {
                        runCatching { getFloorTables(layoutId = layoutId, forceRefresh = true) }
                            .onSuccess { fresh -> applyFloorPlan(floorPlan = fresh, forceRefresh = true) }
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

    private suspend fun applyFloorPlan(
        floorPlan: pos.finestar.barion.domain.model.FloorPlanData,
        forceRefresh: Boolean
    ) {
        val fallbackAllowed = runCatching {
            getAllowedLayouts(forceRefresh = forceRefresh)
        }.getOrDefault(emptyList())
        val resolvedLayouts = when {
            floorPlan.allowedLayouts.isNotEmpty() && fallbackAllowed.isNotEmpty() -> {
                (floorPlan.allowedLayouts + fallbackAllowed)
                    .distinctBy { it.id }
                    .sortedBy { it.name.lowercase() }
            }
            floorPlan.allowedLayouts.isNotEmpty() -> floorPlan.allowedLayouts
            fallbackAllowed.isNotEmpty() -> fallbackAllowed.sortedBy { it.name.lowercase() }
            else -> _uiState.value.allowedLayouts
        }
        val selected = when {
            resolvedLayouts.any { it.id == floorPlan.layoutId } -> floorPlan.layoutId
            _uiState.value.selectedLayoutId != null &&
                resolvedLayouts.any { it.id == _uiState.value.selectedLayoutId } -> _uiState.value.selectedLayoutId
            else -> resolvedLayouts.firstOrNull { it.isDefault }?.id ?: resolvedLayouts.firstOrNull()?.id ?: floorPlan.layoutId
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                tables = floorPlan.tables,
                allowedLayouts = resolvedLayouts,
                selectedLayoutId = selected,
                selectedLayoutName = floorPlan.layoutName,
                error = null
            )
        }
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            val displayName = authRepository.currentUserDisplayName()
            _uiState.update { it.copy(userDisplayName = displayName) }
        }
    }

    private fun loadRuntimeMode(forceRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRuntimeModeRefreshing = true) }
            runCatching { getRuntimeMode(forceRefresh = forceRefresh) }
                .onSuccess { runtimeMode ->
                    _uiState.update { it.copy(runtimeMode = runtimeMode, isRuntimeModeRefreshing = false) }
                }
                .onFailure {
                    _uiState.update { it.copy(isRuntimeModeRefreshing = false) }
                }
        }
    }
}
