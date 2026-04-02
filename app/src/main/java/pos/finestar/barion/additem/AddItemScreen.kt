package pos.finestar.barion.additem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.util.Locale
import kotlin.math.abs
import pos.finestar.barion.domain.model.ModifierType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemScreen(
    state: AddItemViewModel.UiState,
    onBack: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onCategorySelected: (Long?) -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onProductTapped: (AddItemViewModel.ProductUi) -> Unit,
    onProductLongPressed: (AddItemViewModel.ProductUi) -> Unit,
    onModifierDialogDismiss: () -> Unit,
    onModifierNoteChanged: (String) -> Unit,
    onModifierSimpleToggle: (Long, Long) -> Unit,
    onModifierBundleQtyChange: (Long, Long, Int) -> Unit,
    onModifierDialogConfirm: () -> Unit,
    onCartOpen: () -> Unit,
    onCartDismiss: () -> Unit,
    onCartIncrease: (AddItemViewModel.CartItemUi) -> Unit,
    onCartDecrease: (AddItemViewModel.CartItemUi) -> Unit,
    onCartRemove: (AddItemViewModel.CartItemUi) -> Unit,
    onSendRound: () -> Unit,
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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(),
                    title = {
                        Column {
                            Text("Dodaj stavke")
                            Text(
                                text = state.tableName,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Natrag")
                        }
                    }
                )
            },
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(12.dp)
                ) {
                    Button(
                        onClick = onCartOpen,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.cart.isNotEmpty() && !state.isSubmitting
                    ) {
                        Text("Pregled košarice (${state.cartItemsCount})")
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

                state.error != null && state.products.isEmpty() -> {
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
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = onQueryChanged,
                            label = { Text("Pretraga artikla") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val isWide = maxWidth >= 700.dp
                            val searchActive = state.isSearchActive
                            if (isWide) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (!searchActive) {
                                        CategoriesColumn(
                                            categories = state.categories,
                                            selectedId = state.selectedCategoryId,
                                            onCategorySelected = onCategorySelected,
                                            modifier = Modifier
                                                .weight(0.34f)
                                                .fillMaxSize()
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(if (searchActive) 1f else 0.66f)
                                            .fillMaxSize()
                                    ) {
                                        if (state.isProductsLoading) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator()
                                            }
                                        } else {
                                            ProductsGrid(
                                                products = state.products,
                                                cartQtyByProductId = state.cartQtyByProductId,
                                                hasModifiersByProductId = state.hasModifiersByProductId,
                                                cartConfiguredByProductId = state.cartConfiguredByProductId,
                                                onSwipeLeft = if (searchActive) ({}) else onSwipeLeft,
                                                onSwipeRight = if (searchActive) ({}) else onSwipeRight,
                                                onProductTapped = onProductTapped,
                                                onProductLongPressed = onProductLongPressed,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (!searchActive) {
                                        CategoriesRow(
                                            categories = state.categories,
                                            selectedId = state.selectedCategoryId,
                                            onCategorySelected = onCategorySelected
                                        )
                                    }
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        if (state.isProductsLoading) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator()
                                            }
                                        } else {
                                            ProductsGrid(
                                                products = state.products,
                                                cartQtyByProductId = state.cartQtyByProductId,
                                                hasModifiersByProductId = state.hasModifiersByProductId,
                                                cartConfiguredByProductId = state.cartConfiguredByProductId,
                                                onSwipeLeft = if (searchActive) ({}) else onSwipeLeft,
                                                onSwipeRight = if (searchActive) ({}) else onSwipeRight,
                                                onProductTapped = onProductTapped,
                                                onProductLongPressed = onProductLongPressed,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.showModifierDialog && state.modifierDialogProduct != null) {
            ModifierDialog(
                state = state,
                onDismiss = onModifierDialogDismiss,
                onNoteChanged = onModifierNoteChanged,
                onSimpleToggle = onModifierSimpleToggle,
                onBundleQtyChange = onModifierBundleQtyChange,
                onConfirm = onModifierDialogConfirm
            )
        }

        if (state.showCartDialog) {
            OrderReviewScreen(
                tableName = state.tableName,
                cart = state.cart,
                subtotal = state.cartSubtotal,
                isSubmitting = state.isSubmitting,
                onBack = onCartDismiss,
                onIncrease = onCartIncrease,
                onDecrease = onCartDecrease,
                onRemove = onCartRemove,
                onSendRound = onSendRound
            )
        }

        if (state.isLoading || state.isSubmitting || state.modifierDialogLoading) {
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

@Composable
private fun CategoriesColumn(
    categories: List<AddItemViewModel.CategoryUi>,
    selectedId: Long?,
    onCategorySelected: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val selectedIndex = categories.indexOfFirst { it.id == selectedId }
    LaunchedEffect(selectedIndex, categories.size) {
        if (selectedIndex < 0) return@LaunchedEffect
        val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == selectedIndex }
        if (!isVisible) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(categories.size) { index ->
            val category = categories[index]
            CategoryItem(
                category = category,
                selected = category.id == selectedId,
                onClick = { onCategorySelected(category.id) }
            )
        }
    }
}

@Composable
private fun CategoriesRow(
    categories: List<AddItemViewModel.CategoryUi>,
    selectedId: Long?,
    onCategorySelected: (Long?) -> Unit
) {
    val listState = rememberLazyListState()
    val selectedIndex = categories.indexOfFirst { it.id == selectedId }
    LaunchedEffect(selectedIndex, categories.size) {
        if (selectedIndex < 0) return@LaunchedEffect
        // First ensure selected item is laid out, then center it if possible.
        listState.scrollToItem(selectedIndex)
        val layoutInfo = listState.layoutInfo
        val selectedItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
            ?: return@LaunchedEffect
        val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
        val selectedCenter = selectedItem.offset + selectedItem.size / 2
        val delta = (selectedCenter - viewportCenter).toFloat()
        if (abs(delta) > 1f) {
            // Scroll state naturally clamps at edges, giving left/right stick behavior.
            listState.animateScrollBy(delta)
        }
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(categories.size) { index ->
            val category = categories[index]
            CategoryItem(
                category = category,
                selected = category.id == selectedId,
                onClick = { onCategorySelected(category.id) }
            )
        }
    }
}

@Composable
private fun CategoryItem(
    category: AddItemViewModel.CategoryUi,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = category.label,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            style = if (selected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductsGrid(
    products: List<AddItemViewModel.ProductUi>,
    cartQtyByProductId: Map<Long, Int>,
    hasModifiersByProductId: Map<Long, Boolean>,
    cartConfiguredByProductId: Map<Long, Boolean>,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onProductTapped: (AddItemViewModel.ProductUi) -> Unit,
    onProductLongPressed: (AddItemViewModel.ProductUi) -> Unit,
    modifier: Modifier = Modifier
) {
    val swipeThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        modifier = modifier.pointerInput(swipeThresholdPx, onSwipeLeft, onSwipeRight) {
            var totalDx = 0f
            var fired = false
            detectHorizontalDragGestures(
                onDragStart = {
                    totalDx = 0f
                    fired = false
                },
                onHorizontalDrag = { _, dragAmount ->
                    if (fired) return@detectHorizontalDragGestures
                    totalDx += dragAmount
                    if (abs(totalDx) < swipeThresholdPx) return@detectHorizontalDragGestures
                    fired = true
                    if (totalDx < 0f) onSwipeLeft() else onSwipeRight()
                }
            )
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(products) { product ->
            val orderedQty = cartQtyByProductId[product.id] ?: 0
            val hasModifiers = hasModifiersByProductId[product.id] == true
            val isConfigured = cartConfiguredByProductId[product.id] == true
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onProductTapped(product) },
                        onLongClick = { onProductLongPressed(product) }
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (orderedQty > 0) Color(0xFFE8F7E8) else MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier.size(width = 92.dp, height = 120.dp)
                    ) {
                        AsyncImage(
                            model = product.imageUrl,
                            contentDescription = product.name,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (orderedQty > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .background(
                                        color = Color(0xFF2E7D32),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = orderedQty.toString(),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (hasModifiers) {
                                BadgeLabel(
                                    text = "+ opcije",
                                    background = Color(0xFF1E88E5),
                                    textColor = Color.White
                                )
                            }
                            if (isConfigured) {
                                BadgeLabel(
                                    text = "konfigurirano",
                                    background = Color(0xFF2E7D32),
                                    textColor = Color.White
                                )
                            }
                        }
                    }
                    Text(
                        text = product.name,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${formatAmount(product.unitPrice)} EUR",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun BadgeLabel(
    text: String,
    background: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun ModifierDialog(
    state: AddItemViewModel.UiState,
    onDismiss: () -> Unit,
    onNoteChanged: (String) -> Unit,
    onSimpleToggle: (Long, Long) -> Unit,
    onBundleQtyChange: (Long, Long, Int) -> Unit,
    onConfirm: () -> Unit
) {
    val product = state.modifierDialogProduct ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(product.name) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.modifierDialogLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    state.modifierDialogGroups.forEach { group ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val maxLabel = group.maxSelect?.toString() ?: "∞"
                                Text(
                                    text = "${group.name} (${group.type.name.lowercase()})",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Odabir: ${group.minSelect} - $maxLabel",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                group.options.forEach { option ->
                                    when (group.type) {
                                        ModifierType.SIMPLE -> {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onSimpleToggle(group.id, option.id) },
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(option.name)
                                                Checkbox(
                                                    checked = option.selected,
                                                    onCheckedChange = { onSimpleToggle(group.id, option.id) }
                                                )
                                            }
                                        }

                                        ModifierType.BUNDLE -> {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(option.name)
                                                    Text(
                                                        text = "+${formatAmount(option.priceDelta)} EUR",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    TextButton(onClick = { onBundleQtyChange(group.id, option.id, -1) }) { Text("-") }
                                                    Text(option.quantity.toString())
                                                    TextButton(onClick = { onBundleQtyChange(group.id, option.id, 1) }) { Text("+") }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (state.modifierDialogPricePreview != null) {
                        val delta = state.modifierDialogDelta ?: 0.0
                        Text(
                            text = "Finalna cijena: ${formatAmount(state.modifierDialogPricePreview)} EUR (Δ ${formatAmount(delta)} EUR)",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }

                    OutlinedTextField(
                        value = state.modifierDialogNote,
                        onValueChange = onNoteChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Napomena") },
                        minLines = 2
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !state.modifierDialogLoading) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.modifierDialogLoading) {
                Text("Odustani")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderReviewScreen(
    tableName: String,
    cart: List<AddItemViewModel.CartItemUi>,
    subtotal: Double,
    isSubmitting: Boolean,
    onBack: () -> Unit,
    onIncrease: (AddItemViewModel.CartItemUi) -> Unit,
    onDecrease: (AddItemViewModel.CartItemUi) -> Unit,
    onRemove: (AddItemViewModel.CartItemUi) -> Unit,
    onSendRound: () -> Unit
) {
    var sendClicked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isSubmitting) {
        if (!isSubmitting) sendClicked = false
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Pregled narudžbe")
                        Text(
                            text = tableName,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Natrag na Add Items")
                    }
                }
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(12.dp)
            ) {
                Button(
                    onClick = {
                        if (sendClicked) return@Button
                        sendClicked = true
                        onSendRound()
                    },
                    enabled = cart.isNotEmpty() && !isSubmitting && !sendClicked,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSubmitting) "..." else "Pošalji rundu")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(cart.size) { index ->
                    val item = cart[index]
                    Card {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AsyncImage(
                                model = item.imageUrl,
                                contentDescription = item.name,
                                modifier = Modifier.size(56.dp)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(item.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${formatAmount(item.unitPrice)} EUR / kom",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (item.displayLines.isNotEmpty()) {
                                    item.displayLines.forEach { line ->
                                        Text(
                                            line,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(onClick = { onDecrease(item) }, enabled = !isSubmitting) { Text("-") }
                                        Text("${item.qty}")
                                        TextButton(
                                            onClick = { onIncrease(item) },
                                            enabled = !isSubmitting && !item.isConfigured
                                        ) { Text("+") }
                                    }
                                    TextButton(onClick = { onRemove(item) }, enabled = !isSubmitting) {
                                        Text("Ukloni")
                                    }
                                }
                                Text(
                                    "Ukupno stavka: ${formatAmount(item.lineTotal)} EUR",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Stavki: ${cart.sumOf { it.qty }}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Ukupno: ${formatAmount(subtotal)} EUR",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    }
}

private fun formatAmount(value: Double): String = String.format(Locale.US, "%.2f", value)
