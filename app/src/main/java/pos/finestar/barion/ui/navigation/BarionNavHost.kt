package pos.finestar.barion.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
            FloorPlanScreen(
                state = uiState.value,
                onTableClick = vm::onTableClick
            )
        }
    }
}
