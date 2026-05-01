package hr.finestar.barion.partial

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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

private enum class TipChoice {
    ZERO,
    FIVE,
    TEN,
    CUSTOM
}

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
    onPayCard: (Double) -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showCardTipStep by remember { mutableStateOf(false) }
    var tipChoice by remember { mutableStateOf(TipChoice.ZERO) }
    var customTipInput by remember { mutableStateOf("") }

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
                        Text("Djelomična naplata")
                        Text(
                            "Račun #${state.checkId} · ${state.tableName}",
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
                if (!state.hasSelection && !state.isMutating) {
                    Text(
                        text = "Odaberi stavke za naplatu.",
                        style = MaterialTheme.typography.bodySmall
                    )
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
                            Text("Status: ${mapCheckStatus(state.checkStatus)} / ${mapPaymentStatus(state.paymentStatus)}")
                            Text("Preostalo: ${formatMoneyEur(state.remainingTotal ?: 0.0)}")
                            Text("Odabrano: ${formatMoneyEur(state.selectedTotal)}")
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
                                            text = "Ukupno R${roundNumber ?: 0}: ${formatMoneyEur(roundItems.sumOf { it.remainingAmount })}",
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
                                                    text = "Količina: ${formatQty(item.sourceQty.toDouble())}",
                                                    textDecoration = mainDecoration
                                                )
                                                Text("Preostala kol.: ${formatQty(item.remainingQty.toDouble())}")
                                                Text("Preostalo: ${formatMoneyEur(item.remainingAmount)}")
                                            }
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                TextButton(
                                                    onClick = { onIncrease(item.id) },
                                                    enabled = item.selectedQty < item.remainingQty && !state.isMutating,
                                                    modifier = Modifier
                                                        .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                                                        .semantics { contentDescription = "Povećaj količinu" }
                                                ) {
                                                    Text("+")
                                                }
                                                Text("${item.selectedQty}")
                                                TextButton(
                                                    onClick = { onDecrease(item.id) },
                                                    enabled = item.selectedQty > 0 && !state.isMutating,
                                                    modifier = Modifier
                                                        .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                                                        .semantics { contentDescription = "Smanji količinu" }
                                                ) {
                                                    Text("-")
                                                }
                                            }
                                        }
                                        item.paidLine?.let { paid ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = paidLineColor(paid.uiColor))
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text("${mapLineType(paid.lineType)}: ${formatQty(paid.quantity)} x ${formatMoney(paid.unitPrice)}")
                                                    Text("Naplaćeno: ${formatMoneyEur(paid.totalAmount)}")
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
        val parsedCustomTip = customTipInput.replace(',', '.').toDoubleOrNull()
        val tipAmount = when (tipChoice) {
            TipChoice.ZERO -> 0.0
            TipChoice.FIVE -> (state.selectedTotal * 0.05)
            TipChoice.TEN -> (state.selectedTotal * 0.10)
            TipChoice.CUSTOM -> (parsedCustomTip ?: 0.0).coerceAtLeast(0.0)
        }
        val normalizedTipAmount = kotlin.math.round(tipAmount * 100.0) / 100.0
        val totalWithTip = state.selectedTotal + normalizedTipAmount
        val isCustomTipValid = tipChoice != TipChoice.CUSTOM || (parsedCustomTip != null && parsedCustomTip >= 0.0)
        val isTipWithinAmount = normalizedTipAmount <= state.selectedTotal
        AlertDialog(
            onDismissRequest = {
                onDismissMethodDialog()
                showCardTipStep = false
                tipChoice = TipChoice.ZERO
                customTipInput = ""
            },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            title = { Text("Način plaćanja") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Iznos: ${formatMoneyEur(state.selectedTotal)}")
                    if (showCardTipStep) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { tipChoice = TipChoice.ZERO }, enabled = !state.isMutating) {
                                Text(if (tipChoice == TipChoice.ZERO) "0% ✓" else "0%")
                            }
                            TextButton(onClick = { tipChoice = TipChoice.FIVE }, enabled = !state.isMutating) {
                                Text(if (tipChoice == TipChoice.FIVE) "5% ✓" else "5%")
                            }
                            TextButton(onClick = { tipChoice = TipChoice.TEN }, enabled = !state.isMutating) {
                                Text(if (tipChoice == TipChoice.TEN) "10% ✓" else "10%")
                            }
                            TextButton(onClick = { tipChoice = TipChoice.CUSTOM }, enabled = !state.isMutating) {
                                Text(if (tipChoice == TipChoice.CUSTOM) "Ručno ✓" else "Ručno")
                            }
                        }
                        if (tipChoice == TipChoice.CUSTOM) {
                            androidx.compose.material3.OutlinedTextField(
                                value = customTipInput,
                                onValueChange = { customTipInput = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                                label = { Text("Napojnica (EUR)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                enabled = !state.isMutating
                            )
                        }
                        Text("Napojnica: ${formatMoneyEur(normalizedTipAmount)}")
                        Text("Kartica ukupno: ${formatMoneyEur(totalWithTip)}")
                        if (!isTipWithinAmount) {
                            Text(
                                text = "Napojnica ne može biti veća od iznosa naplate.",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (showCardTipStep) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { showCardTipStep = false },
                            enabled = !state.isMutating
                        ) { Text("Natrag") }
                        Button(
                            onClick = {
                                onPayCard(normalizedTipAmount)
                                showCardTipStep = false
                                tipChoice = TipChoice.ZERO
                                customTipInput = ""
                            },
                            enabled = !state.isMutating && isCustomTipValid && isTipWithinAmount
                        ) { Text("Potvrdi karticu") }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onPayCash()
                                showCardTipStep = false
                                tipChoice = TipChoice.ZERO
                                customTipInput = ""
                            },
                            enabled = !state.isMutating
                        ) { Text("Gotovina") }
                        Button(
                            onClick = { showCardTipStep = true },
                            enabled = !state.isMutating
                        ) { Text("Kartica + tip") }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onDismissMethodDialog()
                        showCardTipStep = false
                        tipChoice = TipChoice.ZERO
                        customTipInput = ""
                    },
                    enabled = !state.isMutating
                ) {
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

private fun mapCheckStatus(status: String): String {
    return when (status.trim().uppercase(Locale.US)) {
        "OPEN" -> "Otvoren"
        "CLOSED" -> "Zatvoren"
        "FREE" -> "Slobodan"
        else -> status.ifBlank { "-" }
    }
}

private fun mapPaymentStatus(status: String?): String {
    val normalized = status?.trim()?.uppercase(Locale.US).orEmpty()
    return when (normalized) {
        "" -> "-"
        "PARTIAL" -> "Djelomično plaćeno"
        "PAID" -> "Plaćeno"
        "UNPAID" -> "Neplaćeno"
        "FAILED" -> "Neuspjelo"
        else -> status ?: "-"
    }
}

private fun formatMoney(value: Double): String = String.format(Locale.US, "%.2f", value)

private fun formatMoneyEur(value: Double): String = "${formatMoney(value)} EUR"

private fun mapLineType(lineType: String): String {
    return when (lineType.trim().uppercase(Locale.US)) {
        "PAID" -> "Naplaćeno"
        else -> lineType
    }
}

private fun formatQty(value: Double): String {
    return BigDecimal.valueOf(value)
        .setScale(2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}
