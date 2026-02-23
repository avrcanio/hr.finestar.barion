package pos.finestar.barion.floorplan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import pos.finestar.barion.domain.model.FloorTable
import pos.finestar.barion.domain.model.TableStatus

private const val CANVAS_WIDTH = 1000f
private const val CANVAS_HEIGHT = 1000f

@Composable
fun FloorPlanScreen(
    state: FloorPlanViewModel.UiState,
    onTableClick: (Long) -> Unit,
    onRefresh: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = state.error)
                }
            }

            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "FloorPlan",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    state.userDisplayName?.takeIf { it.isNotBlank() }?.let { displayName ->
                        Text(
                            text = "Prijavljen: $displayName",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    FloorCanvas(
                        tables = state.tables,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(16.dp),
                        onTableClick = onTableClick
                    )
                }
            }
        }
    }
}

@Composable
private fun FloorCanvas(
    tables: List<FloorTable>,
    onTableClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val scale = minOf(maxWidthPx / CANVAS_WIDTH, maxHeightPx / CANVAS_HEIGHT)

        tables.forEach { table ->
            val widthDp = with(density) { (table.width * scale).toDp() }
            val heightDp = with(density) { (table.height * scale).toDp() }
            val offsetXDp = with(density) { (table.x * scale).toDp() }
            val offsetYDp = with(density) { (table.y * scale).toDp() }

            Box(
                modifier = Modifier
                    .padding(start = offsetXDp, top = offsetYDp)
                    .size(width = widthDp, height = heightDp)
                    .background(table.status.toColor())
                    .clickable { onTableClick(table.id) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = table.name,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    if ((table.itemCount ?: 0) > 0) {
                        Text(
                            text = "${table.itemCount} item(s)",
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

private fun TableStatus.toColor(): Color {
    return when (this) {
        TableStatus.FREE -> Color(0xFF2E7D32)
        TableStatus.OPEN -> Color(0xFFC62828)
    }
}
