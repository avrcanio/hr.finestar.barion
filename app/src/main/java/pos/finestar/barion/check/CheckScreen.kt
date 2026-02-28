package pos.finestar.barion.check

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import java.util.Locale
import pos.finestar.barion.BuildConfig
import pos.finestar.barion.ReceiptPdfActivity
import pos.finestar.barion.domain.model.CheckItem
import pos.finestar.barion.domain.model.SettlementReceipt
import pos.finestar.barion.domain.model.SettlementPartStatus

private enum class TipChoice {
    ZERO,
    FIVE,
    TEN,
    CUSTOM
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CheckScreen(
    state: CheckViewModel.UiState,
    onBack: () -> Unit,
    onOpenAddItems: () -> Unit,
    onItemLongPress: (CheckItem) -> Unit,
    onItemActionReasonChanged: (String) -> Unit,
    onItemActionQtyChanged: (String) -> Unit,
    onDismissItemActionDialog: () -> Unit,
    onConfirmStorno: () -> Unit,
    onConfirmGratis: () -> Unit,
    onConfirmOtpis: () -> Unit,
    onFree: () -> Unit,
    onPay: () -> Unit,
    onDismissPaymentChoice: () -> Unit,
    onStartFullPayment: () -> Unit,
    onStartSplitPayment: () -> Unit,
    onDismissSplitDialog: () -> Unit,
    onSplitQtyIncrease: (Long) -> Unit,
    onSplitQtyDecrease: (Long) -> Unit,
    onSplitNext: () -> Unit,
    onSplitPayNow: () -> Unit,
    onSplitShowSummary: () -> Unit,
    onSplitPayPart: (Long) -> Unit,
    onSplitCloseCheck: () -> Unit,
    onDismissMethodDialog: () -> Unit,
    onChooseCash: () -> Unit,
    onChooseCard: (Double) -> Unit,
    onStartFiscalizeReceipt: (Long) -> Unit,
    onDismissFiscalizeDialog: () -> Unit,
    onFiscalizePinChanged: (String) -> Unit,
    onConfirmFiscalizeReceipt: () -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        val message = state.message
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            onMessageShown()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(),
                title = {
                    Column {
                        Text(text = state.tableName)
                        Text(
                            text = "Status: ${state.status}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            val isZeroOpenTotal = when (val openTotal = state.settlementOpenTotal) {
                null -> kotlin.math.abs(state.total) < 0.005
                else -> kotlin.math.abs(openTotal) < 0.005
            }
            val canCloseAsFree = state.status.equals("OPEN", ignoreCase = true) && isZeroOpenTotal
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenAddItems,
                        modifier = Modifier.weight(1f),
                        enabled = !state.isLoading && !state.isMutating
                    ) {
                        Text("Dodaj stavku")
                    }
                    Button(
                        onClick = if (canCloseAsFree) onFree else onPay,
                        modifier = Modifier.weight(1f),
                        enabled = !state.isLoading &&
                            !state.isMutating &&
                            state.items.isNotEmpty() &&
                            (canCloseAsFree || !state.settlementCompleted),
                        colors = if (canCloseAsFree) {
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF7BC67B),
                                contentColor = Color(0xFF1D1D1D)
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        }
                    ) {
                        Text(if (state.isMutating) "..." else if (canCloseAsFree) "Free" else "Naplata")
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = state.error, style = MaterialTheme.typography.bodyMedium)
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
                        Text(text = "Check #${state.checkId}", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        ItemsList(
                            items = state.items,
                            remainingByItemId = state.settlementRemainingByItemId,
                            roundStateByItemId = state.roundStateByItemId,
                            openSubtotal = state.settlementOpenSubtotal,
                            openTax = state.settlementOpenTax,
                            openTotal = state.settlementOpenTotal,
                            receipts = state.settlementReceipts,
                            onOpenReceiptPdf = { pdfUrl ->
                                val normalizedPdfUrl = pdfUrl.normalizeReceiptUrl() ?: return@ItemsList
                                runCatching {
                                    context.startActivity(
                                        ReceiptPdfActivity.createIntent(context, normalizedPdfUrl).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }
                            },
                            onStartFiscalizeReceipt = onStartFiscalizeReceipt,
                            onItemLongPress = onItemLongPress,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (state.isLoading || (state.isMutating && !state.paymentFlow.showMethodDialog)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {}, onLongPress = {})
                        }
                )
            }
        }
    }

    if (state.paymentFlow.showChoiceDialog) {
        PaymentChoiceDialog(
            canSplitPayment = state.paymentFlow.canSplitPayment,
            onDismiss = onDismissPaymentChoice,
            onFullPayment = onStartFullPayment,
            onSplitPayment = onStartSplitPayment
        )
    }

    if (state.paymentFlow.showSplitDialog) {
        SplitFlowDialog(
            state = state,
            onDismiss = onDismissSplitDialog,
            onSplitQtyIncrease = onSplitQtyIncrease,
            onSplitQtyDecrease = onSplitQtyDecrease,
            onSplitNext = onSplitNext,
            onSplitPayNow = onSplitPayNow,
            onSplitShowSummary = onSplitShowSummary,
            onSplitPayPart = onSplitPayPart,
            onSplitCloseCheck = onSplitCloseCheck
        )
    }

    if (state.paymentFlow.showMethodDialog) {
        PaymentMethodDialog(
            targetLabel = state.paymentFlow.methodTargetLabel,
            amount = state.paymentFlow.methodTargetAmount,
            onDismiss = onDismissMethodDialog,
            onCash = onChooseCash,
            onCard = onChooseCard,
            isSubmitting = state.isMutating
        )
    }

    if (state.showItemActionDialog && state.selectedActionItem != null) {
        ItemActionDialog(
            item = state.selectedActionItem,
            reason = state.actionReason,
            qty = state.actionQty,
            isSubmitting = state.isMutating,
            onReasonChanged = onItemActionReasonChanged,
            onQtyChanged = onItemActionQtyChanged,
            onDismiss = onDismissItemActionDialog,
            onConfirmStorno = onConfirmStorno,
            onConfirmGratis = onConfirmGratis,
            onConfirmOtpis = onConfirmOtpis,
            maxQty = state.actionMaxQty
        )
    }

    if (state.showFiscalizeDialog && state.fiscalizeReceiptId != null) {
        AlertDialog(
            onDismissRequest = onDismissFiscalizeDialog,
            title = { Text("Fiskaliziraj račun #${state.fiscalizeReceiptId}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Unesite PIN za potvrdu fiskalizacije.")
                    OutlinedTextField(
                        value = state.fiscalizePin,
                        onValueChange = onFiscalizePinChanged,
                        label = { Text("PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        enabled = !state.isMutating
                    )
                }
            },
            confirmButton = {
                Button(onClick = onConfirmFiscalizeReceipt, enabled = !state.isMutating) {
                    Text("Fiskaliziraj račun")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissFiscalizeDialog, enabled = !state.isMutating) {
                    Text("Odustani")
                }
            }
        )
    }
}

@Composable
private fun PaymentChoiceDialog(
    canSplitPayment: Boolean,
    onDismiss: () -> Unit,
    onFullPayment: () -> Unit,
    onSplitPayment: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = { Text("Odabir naplate") },
        text = { Text("Odaberite način naplate računa.") },
        confirmButton = {
            Button(onClick = onFullPayment) {
                Text("Kompletna naplata")
            }
        },
        dismissButton = {
            if (canSplitPayment) {
                TextButton(onClick = onSplitPayment) {
                    Text("Naplati dio (Split)")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Odustani")
                }
            }
        }
    )
}

private fun String?.normalizeReceiptUrl(): String? {
    val raw = this?.trim().orEmpty()
    if (raw.isBlank()) return null
    return when {
        raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true) -> raw
        raw.startsWith("/") -> BuildConfig.BARION_API_BASE_URL.trimEnd('/') + raw
        else -> BuildConfig.BARION_API_BASE_URL.trimEnd('/') + "/" + raw
    }
}

private fun SettlementReceipt.toPaymentText(): String {
    val rawMethod = paymentMethod?.trim().orEmpty()
    val normalizedMethod = rawMethod.uppercase(Locale.US)
    return when {
        rawMethod.equals("Gotovina", ignoreCase = true) -> "Gotovina"
        normalizedMethod.contains("CASH") -> "Gotovina"
        normalizedMethod.contains("CARD") || cardMaskedPan != null || cardBrand != null -> {
            val brand = cardBrand?.trim().orEmpty()
            val pan = cardMaskedPan?.trim().orEmpty()
            listOf(brand, pan).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Kartica" }
        }
        rawMethod.isNotBlank() -> rawMethod
        else -> ""
    }
}

@Composable
private fun PaymentMethodDialog(
    targetLabel: String,
    amount: Double,
    onDismiss: () -> Unit,
    onCash: () -> Unit,
    onCard: (Double) -> Unit,
    isSubmitting: Boolean
) {
    var showCardTipStep by remember { mutableStateOf(false) }
    var tipChoice by remember { mutableStateOf(TipChoice.ZERO) }
    var customTipInput by remember { mutableStateOf("") }
    val parsedCustomTip = customTipInput.replace(',', '.').toDoubleOrNull()
    val tipAmount = when (tipChoice) {
        TipChoice.ZERO -> 0.0
        TipChoice.FIVE -> (amount * 0.05)
        TipChoice.TEN -> (amount * 0.10)
        TipChoice.CUSTOM -> (parsedCustomTip ?: 0.0).coerceAtLeast(0.0)
    }
    val normalizedTipAmount = kotlin.math.round(tipAmount * 100.0) / 100.0
    val totalWithTip = amount + normalizedTipAmount
    val isCustomTipValid = tipChoice != TipChoice.CUSTOM || (parsedCustomTip != null && parsedCustomTip >= 0.0)
    val isTipWithinAmount = normalizedTipAmount <= amount

    AlertDialog(
        onDismissRequest = {
            showCardTipStep = false
            onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = { Text("$targetLabel · način plaćanja") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Iznos: ${"%.2f".format(amount)} EUR")
                if (showCardTipStep) {
                    if (isSubmitting) {
                        Text("Čekanje odgovora terminala...", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { tipChoice = TipChoice.ZERO }, enabled = !isSubmitting) {
                            Text(if (tipChoice == TipChoice.ZERO) "0% ✓" else "0%")
                        }
                        TextButton(onClick = { tipChoice = TipChoice.FIVE }, enabled = !isSubmitting) {
                            Text(if (tipChoice == TipChoice.FIVE) "5% ✓" else "5%")
                        }
                        TextButton(onClick = { tipChoice = TipChoice.TEN }, enabled = !isSubmitting) {
                            Text(if (tipChoice == TipChoice.TEN) "10% ✓" else "10%")
                        }
                        TextButton(onClick = { tipChoice = TipChoice.CUSTOM }, enabled = !isSubmitting) {
                            Text(if (tipChoice == TipChoice.CUSTOM) "Custom ✓" else "Custom")
                        }
                    }
                    if (tipChoice == TipChoice.CUSTOM) {
                        OutlinedTextField(
                            value = customTipInput,
                            onValueChange = { customTipInput = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                            label = { Text("Napojnica (EUR)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            enabled = !isSubmitting
                        )
                    }
                    Text("Napojnica: ${"%.2f".format(normalizedTipAmount)} EUR")
                    Text("Kartica ukupno: ${"%.2f".format(totalWithTip)} EUR")
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
                        enabled = !isSubmitting
                    ) {
                        Text("Natrag")
                    }
                    Button(
                        onClick = { onCard(normalizedTipAmount) },
                        enabled = !isSubmitting && isCustomTipValid && isTipWithinAmount
                    ) {
                        Text("Potvrdi karticu")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onCash, enabled = !isSubmitting) {
                        Text("Gotovina")
                    }
                    Button(
                        onClick = { showCardTipStep = true },
                        enabled = !isSubmitting
                    ) {
                        Text("Kartica + tip")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Odustani")
            }
        }
    )
}

@Composable
private fun SplitFlowDialog(
    state: CheckViewModel.UiState,
    onDismiss: () -> Unit,
    onSplitQtyIncrease: (Long) -> Unit,
    onSplitQtyDecrease: (Long) -> Unit,
    onSplitNext: () -> Unit,
    onSplitPayNow: () -> Unit,
    onSplitShowSummary: () -> Unit,
    onSplitPayPart: (Long) -> Unit,
    onSplitCloseCheck: () -> Unit
) {
    val flow = state.paymentFlow
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = {
            Text(if (flow.isSplitSummary) "Split summary" else "Split #${flow.splitStepIndex}")
        },
        text = {
            if (flow.isSplitSummary) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (flow.splitParts.isEmpty()) {
                        Text("Nema kreiranih partova.")
                    } else {
                        flow.splitParts.forEach { part ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("${part.label}: ${"%.2f".format(part.amount)} EUR")
                                        Text("Status: ${part.status}")
                                    }
                                    if (part.status != SettlementPartStatus.PAID) {
                                        TextButton(onClick = { onSplitPayPart(part.partId) }) {
                                            Text("Plati")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text("Preostali qty: ${flow.totalRemainingQty}")
                }
            } else {
                val lines = flow.payableItems.filter { it.remainingQty > 0 }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (lines.isEmpty()) {
                        Text("Nema više preostalih stavki. Otvorite summary.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.height(280.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(lines) { item ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(item.name, style = MaterialTheme.typography.titleSmall)
                                        Text("Cijena: ${"%.2f".format(item.price)} EUR · Remaining: ${item.remainingQty}")
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            TextButton(onClick = { onSplitQtyDecrease(item.checkItemId) }, enabled = item.selectedQty > 0) {
                                                Text("-")
                                            }
                                            Text("Za naplatu: ${item.selectedQty}")
                                            TextButton(
                                                onClick = { onSplitQtyIncrease(item.checkItemId) },
                                                enabled = item.selectedQty < item.remainingQty
                                            ) {
                                                Text("+")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onSplitPayNow, enabled = flow.hasSelection && !state.isMutating) {
                            Text("Plati sada")
                        }
                        TextButton(onClick = onSplitShowSummary, enabled = flow.splitParts.isNotEmpty()) {
                            Text("Summary")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (flow.isSplitSummary) {
                Button(onClick = onSplitCloseCheck, enabled = flow.canCloseCheck && !state.isMutating) {
                    Text("Close check")
                }
            } else {
                Button(onClick = onSplitNext, enabled = flow.hasSelection && !state.isMutating) {
                    Text("Next")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isMutating) {
                Text("Zatvori")
            }
        }
    )
}

@Composable
private fun TotalsBlock(
    subtotal: Double,
    tax: Double,
    total: Double
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TotalsRow(label = "Subtotal", value = subtotal)
            TotalsRow(label = "Tax", value = tax)
            TotalsRow(label = "Total", value = total, emphasize = true)
        }
    }
}

@Composable
private fun TotalsRow(label: String, value: Double, emphasize: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "${"%.2f".format(value)} EUR",
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemsList(
    items: List<CheckItem>,
    remainingByItemId: Map<Long, Int>,
    roundStateByItemId: Map<Long, CheckViewModel.RoundStateUi>,
    openSubtotal: Double?,
    openTax: Double?,
    openTotal: Double?,
    receipts: List<SettlementReceipt>,
    onOpenReceiptPdf: (String) -> Unit,
    onStartFiscalizeReceipt: (Long) -> Unit,
    onItemLongPress: (CheckItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val roundSections = items
        .groupBy { it.roundNumber }
        .toList()
        .sortedBy { it.first ?: Int.MAX_VALUE }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        roundSections.forEach { (roundNumber, roundItems) ->
            val displayCards = buildRoundDisplayCards(
                roundItems = roundItems,
                remainingByItemId = remainingByItemId,
                roundStateByItemId = roundStateByItemId
            )
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (roundNumber == null) "NEW (nije poslano)" else "Runda R$roundNumber",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            items(displayCards) { card ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { card.sourceItem?.let(onItemLongPress) }
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = when (card.lineType.uppercase()) {
                            "STORNO" -> Color(0xFFFFE5E5)
                            "GRATIS" -> Color(0xFFE8F7E8)
                            "OTPIS" -> Color(0xFFFFF1E0)
                            "PAID" -> paidLineColor(card.uiColor)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = card.name,
                                style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (card.strikeMain) TextDecoration.LineThrough else null
                            )
                            if (card.lineType != "NORMAL") {
                                Text(
                                    text = card.lineType,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            } else if (card.isFullyPaid || card.isPartialPaid) {
                                PaymentStatusIndicator(
                                    isFullyPaid = card.isFullyPaid,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Količina: ${formatQty(card.qty)}", style = MaterialTheme.typography.bodySmall)
                            Text("Cijena: ${"%.2f".format(card.price)} EUR", style = MaterialTheme.typography.bodySmall)
                            Text("Ukupno: ${"%.2f".format(card.total)} EUR", style = MaterialTheme.typography.bodySmall)
                        }

                        if (card.displayLines.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                card.displayLines.forEach { line ->
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

        }

        if (openSubtotal != null && openTax != null && openTotal != null) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Otvoreno za platiti", style = MaterialTheme.typography.titleSmall)
            }
            item {
                TotalsBlock(
                    subtotal = openSubtotal,
                    tax = openTax,
                    total = openTotal
                )
            }
        }

        if (receipts.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Plaćeni računi", style = MaterialTheme.typography.titleSmall)
            }
            items(receipts.sortedByDescending { it.id }) { receipt ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val number = receipt.receiptNumber ?: receipt.id.toInt()
                        val amountText = receipt.totalAmount?.let { " · ${"%.2f".format(it)} EUR" }.orEmpty()
                        val statusText = receipt.status?.replace('_', ' ')?.uppercase(Locale.US).orEmpty()
                        val paymentText = receipt.toPaymentText()
                        val normalizedPdfUrl = receipt.pdfUrl.normalizeReceiptUrl()
                        Text("Račun #$number$amountText")
                        if (paymentText.isNotBlank()) {
                            Text(
                                text = paymentText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (statusText.isNotBlank()) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (receipt.status.equals("fiscalized", ignoreCase = true)) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!receipt.status.equals("fiscalized", ignoreCase = true)) {
                                TextButton(onClick = { onStartFiscalizeReceipt(receipt.id) }) {
                                    Text("Fiskaliziraj račun", textAlign = TextAlign.End)
                                }
                            }
                            if (!normalizedPdfUrl.isNullOrBlank()) {
                                TextButton(onClick = { onOpenReceiptPdf(normalizedPdfUrl) }) {
                                    Text("PDF")
                                }
                            } else if (receipt.status.equals("fiscalized", ignoreCase = true)) {
                                Text(
                                    text = "Nema PDF",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (normalizedPdfUrl.isNullOrBlank() && !receipt.status.equals("fiscalized", ignoreCase = true)) {
                            Text(
                                text = "Nema PDF",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
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

private data class DisplayCard(
    val sourceItem: CheckItem? = null,
    val lineType: String,
    val name: String,
    val qty: Double,
    val price: Double,
    val total: Double,
    val displayLines: List<String> = emptyList(),
    val strikeMain: Boolean = false,
    val isFullyPaid: Boolean = false,
    val isPartialPaid: Boolean = false,
    val uiColor: String? = null
)

private data class ActionGroupKey(
    val lineType: String,
    val name: String,
    val price: Double
)

private fun buildRoundDisplayCards(
    roundItems: List<CheckItem>,
    remainingByItemId: Map<Long, Int>,
    roundStateByItemId: Map<Long, CheckViewModel.RoundStateUi>
): List<DisplayCard> {
    val normalCards = roundItems
        .filter { it.lineType.equals("NORMAL", ignoreCase = true) }
        .flatMap { item ->
            val roundState = item.itemId?.let { roundStateByItemId[it] }
            val sourceQty = roundState?.sourceQuantity ?: item.qty
            val remainingQty = roundState?.remainingQuantity ?: item.itemId?.let { remainingByItemId[it] }
            val isFullyPaid = remainingQty == 0
            val isPartialPaid = remainingQty != null && remainingQty in 1 until sourceQty
            val mainCard = DisplayCard(
                sourceItem = item,
                lineType = "NORMAL",
                name = item.name,
                qty = sourceQty.toDouble(),
                price = item.price,
                total = sourceQty * item.price,
                displayLines = item.displayLines,
                strikeMain = roundState?.strikeMain == true || isFullyPaid,
                isFullyPaid = isFullyPaid,
                isPartialPaid = isPartialPaid
            )
            val paidCard = roundState?.paidLine?.let { paid ->
                DisplayCard(
                    lineType = paid.lineType,
                    name = item.name,
                    qty = paid.quantity,
                    price = paid.unitPrice,
                    total = paid.totalAmount,
                    uiColor = paid.uiColor
                )
            }
            listOfNotNull(mainCard, paidCard)
        }

    val actionCards = roundItems
        .filter {
            it.lineType.equals("STORNO", ignoreCase = true) ||
                it.lineType.equals("GRATIS", ignoreCase = true) ||
                it.lineType.equals("OTPIS", ignoreCase = true)
        }
        .groupBy { action ->
            ActionGroupKey(
                lineType = action.lineType.uppercase(Locale.US),
                name = action.name,
                price = action.price
            )
        }
        .map { (key, grouped) ->
            DisplayCard(
                lineType = key.lineType,
                name = key.name,
                qty = grouped.sumOf { it.qty }.toDouble(),
                price = key.price,
                total = grouped.sumOf { it.qty * it.price },
                displayLines = grouped.flatMap { it.displayLines }.distinct()
            )
        }
        .sortedWith(
            compareBy<DisplayCard>(
                {
                    when (it.lineType) {
                        "STORNO" -> 0
                        "GRATIS" -> 1
                        "OTPIS" -> 2
                        else -> 3
                    }
                },
                { it.name }
            )
        )

    return normalCards + actionCards
}

private fun formatQty(qty: Double): String {
    val isWhole = kotlin.math.abs(qty % 1.0) < 0.0001
    return if (isWhole) {
        "${qty.toInt()}x"
    } else {
        "${"%.1f".format(qty)}x"
    }
}

@Composable
private fun PaymentStatusIndicator(
    isFullyPaid: Boolean,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isFullyPaid) Color(0xFF2E7D32) else Color(0xFF9CCC65)
    val contentColor = Color.White
    Box(
        modifier = modifier
            .background(
                color = containerColor,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = if (isFullyPaid) "Plaćeno" else "Djelomično plaćeno",
            tint = contentColor,
            modifier = Modifier.height(10.dp)
        )
    }
}

@Composable
private fun ItemActionDialog(
    item: CheckItem,
    reason: String,
    qty: String,
    isSubmitting: Boolean,
    onReasonChanged: (String) -> Unit,
    onQtyChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirmStorno: () -> Unit,
    onConfirmGratis: () -> Unit,
    onConfirmOtpis: () -> Unit,
    maxQty: Int
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Akcija stavke") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Stavka: ${item.name}")
                OutlinedTextField(
                    value = reason,
                    onValueChange = onReasonChanged,
                    label = { Text("Razlog (opcionalno)") },
                    enabled = !isSubmitting,
                    singleLine = false
                )
                OutlinedTextField(
                    value = qty,
                    onValueChange = onQtyChanged,
                    label = { Text("Količina (max $maxQty)") },
                    enabled = !isSubmitting,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onConfirmStorno,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFE5E5),
                            contentColor = Color(0xFF2E2E2E)
                        )
                    ) {
                        Text("Storno")
                    }
                    Button(
                        onClick = onConfirmGratis,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE8F7E8),
                            contentColor = Color(0xFF2E2E2E)
                        )
                    ) {
                        Text("Gratis")
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onConfirmOtpis,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFF1E0),
                            contentColor = Color(0xFF2E2E2E)
                        )
                    ) {
                        Text("Otpis")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Odustani")
            }
        }
    )
}
