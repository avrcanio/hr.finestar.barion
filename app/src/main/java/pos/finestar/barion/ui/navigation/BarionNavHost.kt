package pos.finestar.barion.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pos.finestar.barion.auth.AuthGateScreen
import pos.finestar.barion.auth.AuthViewModel
import pos.finestar.barion.auth.PinLoginScreen
import pos.finestar.barion.check.CheckScreen
import pos.finestar.barion.check.CheckViewModel
import pos.finestar.barion.floorplan.FloorPlanScreen
import pos.finestar.barion.floorplan.FloorPlanViewModel

@Composable
fun BarionNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = NavRoutes.AUTH_GATE
    ) {
        composable(route = NavRoutes.AUTH_GATE) {
            val vm: AuthViewModel = hiltViewModel()
            val uiState = vm.uiState.collectAsStateWithLifecycle()

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
                    }
                }
            }

            FloorPlanScreen(
                state = uiState.value,
                onTableClick = vm::onTableClick,
                onRefresh = vm::onResume
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
            CheckScreen(
                state = uiState.value,
                onBack = { navController.popBackStack() },
                onAddItem = vm::onAddItem,
                onIncreaseQty = vm::onIncreaseQty,
                onDecreaseQty = vm::onDecreaseQty,
                onRemoveItem = vm::onRemoveItem,
                onPay = vm::onPay,
                onMessageShown = vm::onMessageShown
            )
        }
    }
}
