package pos.finestar.barion.floorplan

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class FloorPlanViewModel @Inject constructor() : ViewModel() {

    data class UiState(
        val title: String = "Barion FloorPlan",
        val subtitle: String = "Compose skeleton je spreman"
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun onTableClick(tableId: Long) {
        // Placeholder for future floor/check interaction.
    }
}
