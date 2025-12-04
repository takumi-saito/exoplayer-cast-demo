package t.saito.exoplayercastdemo.ui.component

import android.view.ContextThemeWrapper
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

@Composable
fun CastButton(
    onViewCreated: (View) -> Unit = {},
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            // Create a themed context wrapper with Material Design colors
            // Use ctx from factory which is connected to the Activity
            val themedContext = ContextThemeWrapper(
                ctx,
                com.google.android.material.R.style.Theme_MaterialComponents_Light
            )

            MediaRouteButton(themedContext).apply {
                CastButtonFactory.setUpMediaRouteButton(themedContext, this)
                onViewCreated(this)
            }
        },
        modifier = modifier
    )
}
