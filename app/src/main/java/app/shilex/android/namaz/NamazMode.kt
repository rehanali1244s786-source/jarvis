package app.shilex.android.namaz

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Namaz mode: for a set time, calls are answered by the phone and the caller hears that the
 * user is praying. State lives in its own preferences so the call receiver can read it even
 * when the app was not running. Everything here is editable and saved until changed.
 */
object NamazMode {
    const val DEFAULT_MINUTES = 20
    const val DEFAULT_MESSAGE = "Abhi sir namaz par hain. Barae meharbani thori der baad call karain."

    const val DEFAULT_SMS = "Assalam o Alaikum, abhi sir namaz par hain. Barae meharbani thori der baad call karain."

    private const val SMS_GAP_MS = 10 * 60_000L // one text per number per 10 minutes

    /** Always: see a call ring. Answering needs the second; texting needs the call number and SMS. */
    private val RING = arrayOf(Manifest.permission.READ_PHONE_STATE)
    private val ANSWER = arrayOf(Manifest.permission.ANSWER_PHONE_CALLS)
    private val TEXT = arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.SEND_SMS)

    private fun prefs(context: Context) = context.getSharedPreferences("namaz", Context.MODE_PRIVATE)

    /** Permissions still missing for the switches that are on. */
    fun missing(context: Context): Array<String> {
        val needed = RING + (if (answerCalls(context)) ANSWER else emptyArray()) + (if (sendSms(context)) TEXT else emptyArray())
        return needed.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()
    }

    fun hasPermissions(context: Context): Boolean = missing(context).isEmpty()

    /** Pick up calls and play the message. On by default. */
    fun answerCalls(context: Context): Boolean = prefs(context).getBoolean("answer_calls", true)

    fun setAnswerCalls(context: Context, on: Boolean) = prefs(context).edit().putBoolean("answer_calls", on).apply()

    /** Also text the caller. Off until the user turns it on. */
    fun sendSms(context: Context): Boolean = prefs(context).getBoolean("send_sms", false)

    fun setSendSms(context: Context, on: Boolean) = prefs(context).edit().putBoolean("send_sms", on).apply()

    fun smsMessage(context: Context): String =
        prefs(context).getString("sms_message", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_SMS

    fun setSmsMessage(context: Context, text: String) = prefs(context).edit().putString("sms_message", text).apply()

    /** True (and remembered) when this number wasn't texted in the last 10 minutes. */
    fun mayText(context: Context, number: String): Boolean {
        val key = "sms_${number.hashCode()}"
        val now = System.currentTimeMillis()
        if (now - prefs(context).getLong(key, 0L) < SMS_GAP_MS) return false
        prefs(context).edit().putLong(key, now).apply()
        return true
    }

    /** Turns the mode on for [minutes] (the saved length when null). */
    fun start(context: Context, minutes: Int? = null) {
        val length = (minutes ?: minutes(context)).coerceIn(1, 180)
        prefs(context).edit().putLong("until", System.currentTimeMillis() + length * 60_000L).apply()
    }

    fun stop(context: Context) = prefs(context).edit().putLong("until", 0L).apply()

    fun isActive(context: Context): Boolean = prefs(context).getLong("until", 0L) > System.currentTimeMillis()

    fun minutesLeft(context: Context): Int =
        ((prefs(context).getLong("until", 0L) - System.currentTimeMillis()) / 60_000L + 1).toInt().coerceAtLeast(0)

    /** How long "namaz time" lasts, in minutes. */
    fun minutes(context: Context): Int = prefs(context).getInt("minutes", DEFAULT_MINUTES)

    fun setMinutes(context: Context, minutes: Int) =
        prefs(context).edit().putInt("minutes", minutes.coerceIn(1, 180)).apply()

    /** What the caller hears. Roman Urdu is read by an English voice; Urdu script by an Urdu voice if installed. */
    fun message(context: Context): String =
        prefs(context).getString("message", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_MESSAGE

    fun setMessage(context: Context, text: String) = prefs(context).edit().putString("message", text).apply()

    /** True when the text has Arabic/Urdu letters, so an Urdu voice should read it. */
    fun isUrduScript(text: String): Boolean = text.any { it in '\u0600'..'\u06FF' }
}
