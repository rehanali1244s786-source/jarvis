package app.shilex.android.namaz

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telecom.TelecomManager
import app.shilex.android.Diag
import java.util.Locale

/**
 * Picks up a ringing call (if that switch is on) and texts the caller (if that one is on), plays the namaz message twice on the loudspeaker (the caller hears
 * it through the phone's microphone), then hangs up. Android gives apps no way to put sound
 * straight into a call, so the loudspeaker is the route.
 */
object NamazResponder {
    private const val ANSWER_DELAY_MS = 1_500L // let it ring once, like a person picking up
    private const val CONNECT_DELAY_MS = 1_200L // the call needs a moment to connect
    private const val GIVE_UP_MS = 30_000L
    private const val TEXT_ONLY_DELAY_MS = 3_000L

    private val main = Handler(Looper.getMainLooper())

    @Volatile private var busy = false
    private var tts: TextToSpeech? = null
    private var wasOnSpeaker = false
    @Volatile private var answered = false
    @Volatile private var caller: String? = null

    fun onRinging(context: Context, number: String?) {
        if (!number.isNullOrBlank()) caller = number // arrives in the second broadcast
        if (busy) return // the system can announce one ring twice
        if (!NamazMode.hasPermissions(context)) {
            Diag.w("namaz: call rang but permissions are missing")
            return
        }
        busy = true
        answered = false
        if (NamazMode.answerCalls(context)) {
            main.postDelayed({ answer(context) }, ANSWER_DELAY_MS)
        } else {
            main.postDelayed({ finish(context) }, TEXT_ONLY_DELAY_MS) // wait for the number, then text
        }
    }

    /** The call is over (they hung up first, or we did). */
    fun onIdle(context: Context) {
        main.post {
            if (busy) {
                sendSms(context) // they hung up before we finished: still text them
                cleanup(context)
            }
            caller = null
        }
    }

    private fun answer(context: Context) {
        val telecom = context.getSystemService(TelecomManager::class.java)
        val ok = runCatching { telecom.acceptRingingCall() }
            .onFailure { Diag.w("namaz: couldn't answer (${it.javaClass.simpleName})") }
            .isSuccess
        if (!ok) {
            finish(context) // couldn't pick up: the text still goes
            return
        }
        answered = true
        Diag.i("namaz: call answered")
        main.postDelayed({ speak(context) }, CONNECT_DELAY_MS)
        main.postDelayed({ finish(context) }, GIVE_UP_MS) // never leave a call hanging
    }

    @Suppress("DEPRECATION")
    private fun speak(context: Context) {
        if (!busy) return // they hung up already
        val audio = context.getSystemService(AudioManager::class.java)
        wasOnSpeaker = audio.isSpeakerphoneOn
        runCatching {
            audio.isSpeakerphoneOn = true
            audio.setStreamVolume(AudioManager.STREAM_VOICE_CALL, audio.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL), 0)
        }
        val text = NamazMode.message(context)
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            val e = engine
            if (status != TextToSpeech.SUCCESS || e == null || !busy) {
                finish(context)
                return@TextToSpeech
            }
            val wanted = if (NamazMode.isUrduScript(text)) Locale("ur", "PK") else Locale("en", "IN")
            if (e.setLanguage(wanted) < TextToSpeech.LANG_AVAILABLE) e.setLanguage(Locale.ENGLISH)
            e.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == LAST) main.postDelayed({ finish(context) }, 600)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    main.post { finish(context) }
                }
            })
            e.speak(text, TextToSpeech.QUEUE_FLUSH, null, FIRST)
            e.playSilentUtterance(800, TextToSpeech.QUEUE_ADD, "namaz_gap")
            e.speak(text, TextToSpeech.QUEUE_ADD, null, LAST)
        }
        tts = engine
    }

    private fun finish(context: Context) {
        if (!busy) return
        // endCall needs Android 9; on older phones the caller hangs up after the message.
        if (answered && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { context.getSystemService(TelecomManager::class.java).endCall() }
                .onFailure { Diag.w("namaz: couldn't end the call (${it.javaClass.simpleName})") }
        }
        Diag.i("namaz: call handled")
        sendSms(context)
        cleanup(context)
    }

    /** Texts the caller once (if that switch is on and their number is known). */
    @Suppress("DEPRECATION")
    private fun sendSms(context: Context) {
        val to = caller?.takeIf { it.isNotBlank() }
        caller = null
        if (to == null || !NamazMode.sendSms(context)) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return
        if (!NamazMode.mayText(context, to)) return
        runCatching {
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
            sms.sendMultipartTextMessage(to, null, sms.divideMessage(NamazMode.smsMessage(context)), null, null)
            Diag.i("namaz: text sent")
        }.onFailure { Diag.w("namaz: text failed (${it.javaClass.simpleName})") }
    }

    @Suppress("DEPRECATION")
    private fun cleanup(context: Context?) {
        main.removeCallbacksAndMessages(null)
        tts?.run {
            stop()
            shutdown()
        }
        tts = null
        if (context != null) runCatching { context.getSystemService(AudioManager::class.java).isSpeakerphoneOn = wasOnSpeaker }
        busy = false
        answered = false
    }

    private const val FIRST = "namaz_1"
    private const val LAST = "namaz_2"
}
