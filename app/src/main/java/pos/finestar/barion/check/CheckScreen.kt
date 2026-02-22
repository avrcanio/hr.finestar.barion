package pos.finestar.barion.check

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pos.finestar.barion.domain.model.CheckItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckScreen(
    state: CheckViewModel.UiState,
    onBack: () -> Unit,
    onAddItem: (productId: Long, qty: Int) -> Unit,
    onIncreaseQty: (CheckItem) -> Unit,
    onDecreaseQty: (CheckItem) -> Unit,
    onRemoveItem: (CheckItem) -> Unit,
    onPay: () -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val message = state.message
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            onMessageShown()
        }
    }

    Scaffold(
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isLoading && !state.isMutating
                ) {
                    Text("Dodaj stavku")
                }
                Button(
                    onClick = onPay,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isLoading && !state.isMutating && state.items.isNotEmpty()
                ) {
                    Text(if (state.isMutating) "..." else "Naplata")
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
                        isMutating = state.isMutating,
                        onIncreaseQty = onIncreaseQty,
                        onDecreaseQty = onDecreaseQty,
                        onRemoveItem = onRemoveItem
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TotalsBlock(
                        subtotal = state.subtotal,
                        tax = state.tax,
                        total = state.total
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddItemDialog(
            products = state.productOptions,
            isSubmitting = state.isMutating,
            onDismiss = { showAddDialog = false },
            onConfirm = { productId, qty ->
                onAddItem(productId, qty)
                showAddDialog = false
            }
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

@Composable
private fun ItemsList(
    items: List<CheckItem>,
    isMutating: Boolean,
    onIncreaseQty: (CheckItem) -> Unit,
    onDecreaseQty: (CheckItem) -> Unit,
    onRemoveItem: (CheckItem) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items) { item ->
            Card(modifier = Modifier.fillMaxWidth()) {
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
                        Text(text = item.name)
                        Text(text = "${"%.2f".format(item.qty * item.price)} EUR")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onDecreaseQty(item) }, enabled = !isMutating && item.itemId != null) {
                                Text("-")
                            }
                            Text(text = "${item.qty}x")
                            TextButton(onClick = { onIncreaseQty(item) }, enabled = !isMutating && item.itemId != null) {
                                Text("+")
                            }
                        }

                        IconButton(onClick = { onRemoveItem(item) }, enabled = !isMutating && item.itemId != null) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddItemDialog(
    products: List<CheckViewModel.ProductOption>,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (productId: Long, qty: Int) -> Unit
) {
    var selectedProductId by rememberSaveable {
        mutableLongStateOf(products.firstOrNull()?.id ?: 0L)
    }
    var qty by rememberSaveable { mutableIntStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dodaj stavku") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                products.forEach { product ->
                    val isSelected = selectedProductId == product.id
                    Button(
                        onClick = { selectedProductId = product.id },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSubmitting
                    ) {
                        Text(if (isSelected) "✓ ${product.label}" else product.label)
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Qty:")
                    TextButton(onClick = { qty = (qty - 1).coerceAtLeast(1) }, enabled = !isSubmitting) {
                        Text("-")
                    }
                    Text("$qty")
                    TextButton(onClick = { qty += 1 }, enabled = !isSubmitting) {
                        Text("+")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedProductId, qty) },
                enabled = !isSubmitting && selectedProductId > 0L
            ) {
                Text("Dodaj")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Odustani")
            }
        }
    )
}
