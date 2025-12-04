package t.saito.exoplayercastdemo.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.NotificationOptions
import com.google.android.gms.cast.LaunchOptions
import t.saito.exoplayercastdemo.expandedcontrols.ExpandedControlsActivity

class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        val notificationOptions = NotificationOptions.Builder()
            .setTargetActivityClassName(ExpandedControlsActivity::class.java.name)
            .build()

        val mediaOptions = CastMediaOptions.Builder()
            .setNotificationOptions(notificationOptions)
            .setExpandedControllerActivityClassName(ExpandedControlsActivity::class.java.name)
            .build()

        // Cast Connect対応（Android TV受信機）
        val launchOptions = LaunchOptions.Builder()
            .setAndroidReceiverCompatible(true)
            .build()

        return CastOptions.Builder()
            .setReceiverApplicationId("CC1AD845") // Default Media Receiver
            .setLaunchOptions(launchOptions)
            .setCastMediaOptions(mediaOptions)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? {
        return null
    }
}
