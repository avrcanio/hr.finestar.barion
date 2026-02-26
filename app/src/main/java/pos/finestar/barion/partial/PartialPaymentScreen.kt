package pos.finestar.barion.partial

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartialPaymentScreen(
    state: PartialPaymentViewModel.UiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleRound: (Int?) -> Unit,
    onIncrease: (Long) -> Unit,
    onDecrease: (Long) -> Unit,
    onPay: () -> Unit,
    onDismissMethodDialog: () -> Unit,
    onPayCash: () -> Unit,
    onPayCard: () -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val msg = state.message
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            onMessageShown()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Partial payment")
                        Text(
                            "Check #${state.checkId} · ${state.tableName}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Natrag")
                    }
                },
                actions = {
                    TextButton(onClick = onRefresh, enabled = !state.isMutating) {
                        Text("Osvježi")
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPay,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isMutating && state.hasSelection
                ) {
                    Text(if (state.isMutating) "..." else "Naplati")
                }
            }
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.error)
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 12.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Status: ${state.checkStatus} / ${state.paymentStatus ?: "-"}")
                            Text("Preostalo: ${"%.2f".format(state.remainingTotal ?: 0.0)} EUR")
                            Text("Odabrano: ${"%.2f".format(state.selectedTotal)} EUR")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val grouped = state.items.groupBy { it.roundNumber }.toSortedMap(compareBy(nullsLast()) { it })
                        grouped.forEach { (roundNumber, roundItems) ->
                            item("round-header-${roundNumber ?: -1}") {
                                val allRoundSelected = roundItems.all { it.selectedQty == it.remainingQty }
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !state.isMutating) { onToggleRound(roundNumber) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (allRoundSelected) Color(0xFFE8F7E8) else MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "Runda R${roundNumber ?: 0}",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "Total R${roundNumber ?: 0}: ${"%.2f".format(roundItems.sumOf { it.remainingAmount })} EUR",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                            items(roundItems, key = { it.id }) { item ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = item.imageUrl,
                                                contentDescription = item.name,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                val mainDecoration = if (item.strikeMain) TextDecoration.LineThrough else null
                                                Text(
                                                    item.name,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textDecoration = mainDecoration
                                                )
                                                Text(
                                                    text = "Qty: ${item.sourceQty}",
                                                    textDecoration = mainDecoration
                                                )
                                                Text("Remaining qty: ${item.remainingQty}")
                                                Text("Remaining: ${"%.2f".format(item.remainingAmount)} EUR")
                                                Text("Odabrano: ${item.selectedQty}")
                                            }
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                TextButton(onClick = { onDecrease(item.id) }, enabled = item.selectedQty > 0 && !state.isMutating) {
                                                    Text("-")
                                                }
                                                Text("${item.selectedQty}")
                                                TextButton(
                                                    onClick = { onIncrease(item.id) },
                                                    enabled = item.selectedQty < item.remainingQty && !state.isMutating
                                                ) {
                                                    Text("+")
                                                }
                                            }
                                        }
                                        item.paidLine?.let { paid ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = paidLineColor(paid.uiColor))
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text("${paid.lineType}: ${paid.quantity} x ${"%.4f".format(paid.unitPrice)}")
                                                    Text("Naplaćeno: ${"%.2f".format(paid.totalAmount)} EUR")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.showMethodDialog) {
        AlertDialog(
            onDismissRequest = onDismissMethodDialog,
            title = { Text("Način plaćanja") },
            text = { Text("Iznos: ${"%.2f".format(state.selectedTotal)} EUR") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPayCash, enabled = !state.isMutating) { Text("Gotovina") }
                    Button(onClick = onPayCard, enabled = !state.isMutating) { Text("Kartica") }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissMethodDialog, enabled = !state.isMutating) {
                    Text("Odustani")
                }
            }
        )
    }

    if (state.isLoading || state.isMutating) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {}, onLongPress = {})
                }
        ) {
            if (state.isMutating) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun paidLineColor(uiColor: String?): Color {
    return when (uiColor?.lowercase()) {
        "light_blue" -> Color(0xFFE3F2FD)
        else -> Color(0xFFE3F2FD)
    }
}
