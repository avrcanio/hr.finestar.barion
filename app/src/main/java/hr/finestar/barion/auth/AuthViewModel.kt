package hr.finestar.barion.auth

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
import hr.finestar.barion.domain.repo.AuthRepository

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = false,
        val pin: String = "",
        val error: String? = null
    )

    sealed interface Event {
        data object NavigateToFloor : Event
        data object NavigateToPin : Event
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    fun bootstrapSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val hasSession = authRepository.bootstrapSession()
            _uiState.update { it.copy(isLoading = false) }
            _events.emit(if (hasSession) Event.NavigateToFloor else Event.NavigateToPin)
        }
    }

    fun onPinChanged(newPin: String) {
        val onlyDigits = newPin.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(pin = onlyDigits, error = null) }
    }

    fun loginWithPin() {
        val pin = _uiState.value.pin
        if (pin.isBlank()) {
            _uiState.update { it.copy(error = "PIN je obavezan.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                authRepository.loginWithPin(pin = pin)
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false) }
                _events.emit(Event.NavigateToFloor)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = throwable.message ?: "Greška pri prijavi."
                    )
                }
            }
        }
    }
}
