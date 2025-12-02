package t.saito.exoplayercastdemo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import t.saito.exoplayercastdemo.service.PlaybackServiceConnection
import t.saito.exoplayercastdemo.ui.screen.FullPlayerScreen
import t.saito.exoplayercastdemo.ui.screen.MediaListScreen
import t.saito.exoplayercastdemo.viewmodel.MediaViewModel
import t.saito.exoplayercastdemo.viewmodel.PlayerViewModel

sealed class Screen(val route: String) {
    object MediaList : Screen("media_list")
    object FullPlayer : Screen("full_player")
}

@Composable
fun AppNavigation(
    mediaViewModel: MediaViewModel,
    playerViewModel: PlayerViewModel,
    serviceConnection: PlaybackServiceConnection,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.MediaList.route,
        modifier = modifier
    ) {
        composable(Screen.MediaList.route) {
            MediaListScreen(
                mediaViewModel = mediaViewModel,
                playerViewModel = playerViewModel,
                onExpandPlayer = {
                    navController.navigate(Screen.FullPlayer.route)
                }
            )
        }

        composable(Screen.FullPlayer.route) {
            FullPlayerScreen(
                playerViewModel = playerViewModel,
                serviceConnection = serviceConnection,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
