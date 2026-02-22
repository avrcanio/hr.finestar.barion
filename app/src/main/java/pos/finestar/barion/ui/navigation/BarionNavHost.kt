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
import pos.finestar.barion.check.CheckScreen
import pos.finestar.barion.check.CheckViewModel
import pos.finestar.barion.floorplan.FloorPlanScreen
import pos.finestar.barion.floorplan.FloorPlanViewModel

@Composable
fun BarionNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = NavRoutes.FLOOR_PLAN
    ) {
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
                onTableClick = vm::onTableClick
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
                onPay = vm::onPay
            )
        }
    }
}
