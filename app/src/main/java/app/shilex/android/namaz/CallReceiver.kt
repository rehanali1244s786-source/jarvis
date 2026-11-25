package app.shilex.android.namaz

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

/** Hears the phone ring. While namaz mode is on, hands the call to [NamazResponder]. */
class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING ->
                if (NamazMode.isActive(context)) {
                    // The number comes in a second broadcast, and only with the call-log permission.
                    @Suppress("DEPRECATION")
                    val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    NamazResponder.onRinging(context.applicationContext, number)
                }
            TelephonyManager.EXTRA_STATE_IDLE -> NamazResponder.onIdle(context.applicationContext)
        }
    }
}
