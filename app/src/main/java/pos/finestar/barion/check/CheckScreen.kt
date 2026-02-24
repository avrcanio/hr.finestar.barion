package pos.finestar.barion.check

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import pos.finestar.barion.domain.model.CheckItem

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
    onPayPinChanged: (String) -> Unit,
    onPayPinDismiss: () -> Unit,
    onConfirmPay: () -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

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
            val isZeroTotal = kotlin.math.abs(state.total) < 0.005
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
                        onClick = if (isZeroTotal) onFree else onPay,
                        modifier = Modifier.weight(1f),
                        enabled = !state.isLoading && !state.isMutating && state.items.isNotEmpty()
                        ,
                        colors = if (isZeroTotal) {
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF7BC67B),
                                contentColor = Color(0xFF1D1D1D)
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        }
                    ) {
                        Text(if (state.isMutating) "..." else if (isZeroTotal) "Free" else "Naplata")
                    }
                }
            }
        }
    ) { padding ->
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
                        subtotal = state.subtotal,
                        tax = state.tax,
                        total = state.total,
                        onItemLongPress = onItemLongPress,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (state.showPayPinDialog) {
        PayPinDialog(
            pin = state.payPin,
            error = state.payPinError,
            isSubmitting = state.isMutating,
            onPinChanged = onPayPinChanged,
            onDismiss = onPayPinDismiss,
            onConfirm = onConfirmPay
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
    subtotal: Double,
    tax: Double,
    total: Double,
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
            val orderedRoundItems = orderRoundItems(roundItems)
            item {
                Text(
                    text = if (roundNumber == null) "NEW (nije poslano)" else "Runda R$roundNumber",
                    style = MaterialTheme.typography.titleSmall
                )
            }

            items(orderedRoundItems) { item ->
                val originalIsStorned =
                    item.lineType.equals("NORMAL", ignoreCase = true) &&
                        hasStornoForItem(item, roundItems)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { onItemLongPress(item) }
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = when (item.lineType.uppercase()) {
                            "STORNO" -> Color(0xFFFFE5E5)
                            "GRATIS" -> Color(0xFFE8F7E8)
                            "OTPIS" -> Color(0xFFFFF1E0)
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
                                text = item.name,
                                style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (originalIsStorned) {
                                    TextDecoration.LineThrough
                                } else {
                                    null
                                }
                            )
                            if (item.lineType != "NORMAL") {
                                Text(
                                    text = item.lineType,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Količina: ${item.qty}x", style = MaterialTheme.typography.bodySmall)
                            Text("Cijena: ${"%.2f".format(item.price)} EUR", style = MaterialTheme.typography.bodySmall)
                            Text("Ukupno: ${"%.2f".format(item.qty * item.price)} EUR", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item {
                TotalsRow(
                    label = if (roundNumber == null) "Total NEW" else "Total R$roundNumber",
                    value = roundItems.sumOf { it.qty * it.price },
                    emphasize = true
                )
            }
        }

        item {
            TotalsBlock(
                subtotal = subtotal,
                tax = tax,
                total = total
            )
        }
    }
}

private fun orderRoundItems(items: List<CheckItem>): List<CheckItem> {
    if (items.isEmpty()) return items
    val byId = items.associateBy { it.itemId }
    val stornoByTargetId = items
        .asSequence()
        .filter { it.lineType.equals("STORNO", ignoreCase = true) }
        .mapNotNull { storno -> parseActionTargetId(storno.note)?.let { targetId -> targetId to storno } }
        .groupBy({ it.first }, { it.second })

    val ordered = mutableListOf<CheckItem>()
    val addedIds = mutableSetOf<Long>()
    val addedAnonymous = mutableSetOf<CheckItem>()

    items.forEach { item ->
        val id = item.itemId
        if (id != null && id in addedIds) return@forEach
        if (id == null && item in addedAnonymous) return@forEach

        ordered += item
        if (id != null) addedIds += id else addedAnonymous += item

        if (item.lineType.equals("NORMAL", ignoreCase = true) && id != null) {
            val linkedStornos = stornoByTargetId[id].orEmpty()
            linkedStornos.forEach { storno ->
                val stornoId = storno.itemId
                if (stornoId != null) {
                    if (stornoId !in addedIds) {
                        ordered += storno
                        addedIds += stornoId
                    }
                } else if (storno !in addedAnonymous) {
                    ordered += storno
                    addedAnonymous += storno
                }
            }
        }
    }

    return ordered
}

private fun hasStornoForItem(item: CheckItem, inItems: List<CheckItem>): Boolean {
    val itemId = item.itemId ?: return false
    return inItems.any { candidate ->
        candidate.lineType.equals("STORNO", ignoreCase = true) &&
            parseActionTargetId(candidate.note) == itemId
    }
}

private fun parseActionTargetId(note: String?): Long? {
    if (note.isNullOrBlank()) return null
    val pattern = Regex("""\[(?:storno|otpis)_of:(\d+)]""", RegexOption.IGNORE_CASE)
    return pattern.find(note)?.groupValues?.getOrNull(1)?.toLongOrNull()
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

@Composable
private fun PayPinDialog(
    pin: String,
    error: String?,
    isSubmitting: Boolean,
    onPinChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PIN potvrda") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Unesite PIN za osjetljivu akciju (naplata).")
                OutlinedTextField(
                    value = pin,
                    onValueChange = onPinChanged,
                    label = { Text("PIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !isSubmitting,
                    isError = !error.isNullOrBlank()
                )
                if (!error.isNullOrBlank()) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSubmitting
            ) {
                Text(if (isSubmitting) "..." else "Potvrdi")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) {
                Text("Odustani")
            }
        }
    )
}
