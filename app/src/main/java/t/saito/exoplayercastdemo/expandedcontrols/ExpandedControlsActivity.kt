package t.saito.exoplayercastdemo.expandedcontrols

import android.view.Menu
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.media.widget.ExpandedControllerActivity
import t.saito.exoplayercastdemo.R

/**
 * Cast中の全画面コントロールUI
 * Cast Frameworkの標準ExpandedControllerActivityを継承
 */
class ExpandedControlsActivity : ExpandedControllerActivity() {

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.expanded_controller, menu)
        CastButtonFactory.setUpMediaRouteButton(this, menu, R.id.media_route_menu_item)
        return true
    }
}
