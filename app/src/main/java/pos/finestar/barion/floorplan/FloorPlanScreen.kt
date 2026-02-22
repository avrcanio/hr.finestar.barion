package pos.finestar.barion.floorplan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FloorPlanScreen(
    state: FloorPlanViewModel.UiState,
    onTableClick: (Long) -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = state.title, style = MaterialTheme.typography.headlineSmall)
            Text(text = state.subtitle, style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = { onTableClick(1L) },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Test klik")
            }
        }
    }
}
