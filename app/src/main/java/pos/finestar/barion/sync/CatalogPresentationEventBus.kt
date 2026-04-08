package pos.finestar.barion.sync

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class CatalogPresentationEventBus @Inject constructor() {
    sealed interface Event {
        data class RuntimeModeChanged(val activeModeRaw: String) : Event
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    fun notifyRuntimeModeChanged(activeModeRaw: String) {
        _events.tryEmit(Event.RuntimeModeChanged(activeModeRaw = activeModeRaw))
    }
}

