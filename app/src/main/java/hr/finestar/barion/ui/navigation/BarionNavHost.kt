package hr.finestar.barion.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import hr.finestar.barion.additem.AddItemScreen
import hr.finestar.barion.additem.AddItemViewModel
import hr.finestar.barion.auth.AuthGateScreen
import hr.finestar.barion.auth.AuthViewModel
import hr.finestar.barion.auth.PinLoginScreen
import hr.finestar.barion.check.CheckScreen
import hr.finestar.barion.check.CheckViewModel
import hr.finestar.barion.floorplan.FloorPlanScreen
import hr.finestar.barion.floorplan.FloorPlanViewModel
import hr.finestar.barion.partial.PartialPaymentScreen
import hr.finestar.barion.partial.PartialPaymentViewModel

@Composable
fun BarionNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = NavRoutes.AUTH_GATE
    ) {
        composable(route = NavRoutes.AUTH_GATE) {
            val vm: AuthViewModel = hiltViewModel()

            LaunchedEffect(Unit) {
                vm.events.collect { event ->
                    when (event) {
                        AuthViewModel.Event.NavigateToFloor -> {
                            navController.navigate(NavRoutes.FLOOR_PLAN) {
                                popUpTo(NavRoutes.AUTH_GATE) { inclusive = true }
                            }
                        }
                        AuthViewModel.Event.NavigateToPin -> {
                            navController.navigate(NavRoutes.PIN_LOGIN) {
                                popUpTo(NavRoutes.AUTH_GATE) { inclusive = true }
                            }
                        }
                    }
                }
            }

            AuthGateScreen(onBootstrap = vm::bootstrapSession)
        }

        composable(route = NavRoutes.PIN_LOGIN) {
            val vm: AuthViewModel = hiltViewModel()
            val uiState = vm.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                vm.events.collect { event ->
                    when (event) {
                        AuthViewModel.Event.NavigateToFloor -> {
                            navController.navigate(NavRoutes.FLOOR_PLAN) {
                                popUpTo(NavRoutes.PIN_LOGIN) { inclusive = true }
                            }
                        }
                        AuthViewModel.Event.NavigateToPin -> Unit
                    }
                }
            }

            PinLoginScreen(
                state = uiState.value,
                onPinChanged = vm::onPinChanged,
                onLoginClick = vm::loginWithPin
            )
        }

        composable(route = NavRoutes.FLOOR_PLAN) {
            val vm: FloorPlanViewModel = hiltViewModel()
            val uiState = vm.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                vm.events.collect { event ->
                    when (event) {
                        is FloorPlanViewModel.Event.OpenCheck -> {
                            navController.navigate(
                                NavRoutes.checkRoute(
                                    checkId = event.checkId,
                                    tableName = event.tableName
                                )
                            )
                        }
                        FloorPlanViewModel.Event.NavigateToPin -> {
                            navController.navigate(NavRoutes.PIN_LOGIN) {
                                popUpTo(NavRoutes.FLOOR_PLAN) { inclusive = true }
                            }
                        }
                    }
                }
            }

            FloorPlanScreen(
                state = uiState.value,
                onTableClick = vm::onTableClick,
                onRefresh = vm::onResume,
                onLayoutSelected = vm::onLayoutSelected,
                onLogout = vm::onLogout
            )
        }

        composable(
            route = NavRoutes.checkRoutePattern,
            arguments = listOf(
                navArgument(NavRoutes.ARG_CHECK_ID) { type = NavType.LongType },
                navArgument(NavRoutes.ARG_TABLE_NAME) { type = NavType.StringType }
            )
        ) {
            val vm: CheckViewModel = hiltViewModel()
            val uiState = vm.uiState.collectAsStateWithLifecycle()
            val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        vm.refresh()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            CheckScreen(
                state = uiState.value,
                onBack = { navController.popBackStack() },
                onOpenAddItems = vm::onOpenAddItems,
                onItemLongPress = vm::onItemLongPress,
                onItemActionReasonChanged = vm::onItemActionReasonChanged,
                onItemActionQtyChanged = vm::onItemActionQtyChanged,
                onDismissItemActionDialog = vm::onDismissItemActionDialog,
                onConfirmStorno = vm::onConfirmStorno,
                onConfirmGratis = vm::onConfirmGratis,
                onConfirmOtpis = vm::onConfirmOtpis,
                onFree = vm::onFree,
                onPay = vm::onPay,
                onDismissPaymentChoice = vm::onDismissPaymentChoice,
                onStartFullPayment = vm::onStartFullPayment,
                onStartSplitPayment = {
                    vm.onDismissPaymentChoice()
                    navController.navigate(
                        NavRoutes.partialPaymentRoute(
                            checkId = uiState.value.checkId,
                            tableName = uiState.value.tableName
                        )
                    )
                },
                onDismissSplitDialog = vm::onDismissSplitDialog,
                onSplitQtyIncrease = vm::onSplitQtyIncrease,
                onSplitQtyDecrease = vm::onSplitQtyDecrease,
                onSplitNext = vm::onSplitNext,
                onSplitPayNow = vm::onSplitPayNow,
                onSplitShowSummary = vm::onSplitShowSummary,
                onSplitPayPart = vm::onSplitPayPart,
                onSplitCloseCheck = vm::onSplitCloseCheck,
                onDismissMethodDialog = vm::onDismissMethodDialog,
                onChooseCash = vm::onChooseCash,
                onChooseCard = vm::onChooseCard,
                onStartFiscalizeReceipt = vm::onStartFiscalizeReceipt,
                onDismissFiscalizeDialog = vm::onDismissFiscalizeDialog,
                onFiscalizePinChanged = vm::onFiscalizePinChanged,
                onConfirmFiscalizeReceipt = vm::onConfirmFiscalizeReceipt,
                onMessageShown = vm::onMessageShown
            )

            LaunchedEffect(Unit) {
                vm.events.collect { event ->
                    when (event) {
                        is CheckViewModel.Event.OpenAddItems -> {
                            navController.navigate(
                                NavRoutes.addItemsRoute(
                                    checkId = event.checkId,
                                    tableName = event.tableName
                                )
                            )
                        }
                    }
                }
            }
        }

        composable(
            route = NavRoutes.addItemsRoutePattern,
            arguments = listOf(
                navArgument(NavRoutes.ARG_CHECK_ID) { type = NavType.LongType },
                navArgument(NavRoutes.ARG_TABLE_NAME) { type = NavType.StringType }
            )
        ) {
            val vm: AddItemViewModel = hiltViewModel()
            val uiState = vm.uiState.collectAsStateWithLifecycle()
            val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        vm.onForeground()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            LaunchedEffect(uiState.value.navigateToCheckRequestId) {
                if (uiState.value.navigateToCheckRequestId > 0L) {
                    val popped = navController.popBackStack()
                    if (!popped) {
                        navController.navigate(
                            NavRoutes.checkRoute(
                                checkId = uiState.value.checkId,
                                tableName = uiState.value.tableName
                            )
                        ) {
                            launchSingleTop = true
                        }
                    }
                }
            }

            AddItemScreen(
                state = uiState.value,
                onBack = { navController.popBackStack() },
                onQueryChanged = vm::onQueryChanged,
                onCategorySelected = vm::onCategorySelected,
                onSwipeLeft = vm::onCategorySwipeLeft,
                onSwipeRight = vm::onCategorySwipeRight,
                onProductTapped = vm::onProductTapped,
                onProductLongPressed = vm::onProductLongPressed,
                onModifierDialogDismiss = vm::onModifierDialogDismiss,
                onModifierNoteChanged = vm::onModifierNoteChanged,
                onModifierSimpleToggle = vm::onModifierSimpleToggle,
                onModifierBundleQtyChange = vm::onModifierBundleQtyChange,
                onModifierDialogConfirm = vm::onModifierDialogConfirm,
                onCartOpen = vm::onCartOpen,
                onCartDismiss = vm::onCartDismiss,
                onCartIncrease = vm::onCartIncrease,
                onCartDecrease = vm::onCartDecrease,
                onCartRemove = vm::onCartRemove,
                onSendRound = vm::onSendRound,
                onMessageShown = vm::onMessageShown
            )
        }

        composable(
            route = NavRoutes.partialPaymentRoutePattern,
            arguments = listOf(
                navArgument(NavRoutes.ARG_CHECK_ID) { type = NavType.LongType },
                navArgument(NavRoutes.ARG_TABLE_NAME) { type = NavType.StringType }
            )
        ) {
            val vm: PartialPaymentViewModel = hiltViewModel()
            val uiState = vm.uiState.collectAsStateWithLifecycle()

            PartialPaymentScreen(
                state = uiState.value,
                onBack = { navController.popBackStack() },
                onRefresh = vm::refresh,
                onToggleRound = vm::onToggleRound,
                onIncrease = vm::onIncrease,
                onDecrease = vm::onDecrease,
                onPay = vm::onPay,
                onDismissMethodDialog = vm::onDismissMethodDialog,
                onPayCash = vm::onPayCash,
                onPayCard = vm::onPayCard,
                onMessageShown = vm::onMessageShown
            )
        }
    }
}
