package app.shilex.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.shilex.android.namaz.NamazMode

/** Settings card for namaz mode: main switch, answer-calls switch, text-caller switch, length, messages. */
@Composable
fun NamazSection() {
    val context = LocalContext.current
    var active by remember { mutableStateOf(NamazMode.isActive(context)) }
    var answer by remember { mutableStateOf(NamazMode.answerCalls(context)) }
    var sms by remember { mutableStateOf(NamazMode.sendSms(context)) }
    var minutes by remember { mutableStateOf(NamazMode.minutes(context)) }
    var message by remember { mutableStateOf(NamazMode.message(context)) }
    var smsMessage by remember { mutableStateOf(NamazMode.smsMessage(context)) }

    // What to do once the permission prompt is answered.
    var afterAsk by remember { mutableStateOf<() -> Unit>({}) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        afterAsk()
    }

    Section("Namaz mode") {
        SwitchRow(
            "Namaz mode",
            if (active) "On, about ${NamazMode.minutesLeft(context)} min left." else
                "While on, calls are handled for you. Or say \"namaz time\".",
            active,
        ) { on ->
            if (!on) {
                NamazMode.stop(context)
                active = false
            } else if (NamazMode.hasPermissions(context)) {
                NamazMode.start(context)
                active = true
            } else {
                afterAsk = {
                    if (NamazMode.hasPermissions(context)) {
                        NamazMode.start(context)
                        active = true
                    }
                }
                ask.launch(NamazMode.missing(context))
            }
        }
        SwitchRow("Pick up calls", "Answers the call and plays your message twice, then hangs up.", answer) { on ->
            NamazMode.setAnswerCalls(context, on)
            answer = on
            if (on && !NamazMode.hasPermissions(context)) {
                afterAsk = { if (!NamazMode.hasPermissions(context)) { NamazMode.setAnswerCalls(context, false); answer = false } }
                ask.launch(NamazMode.missing(context))
            }
        }
        SwitchRow("Text the caller", "Sends your text message to the caller's number (once per number every 10 minutes). Uses your SMS plan.", sms) { on ->
            NamazMode.setSendSms(context, on)
            sms = on
            if (on && !NamazMode.hasPermissions(context)) {
                afterAsk = { if (!NamazMode.hasPermissions(context)) { NamazMode.setSendSms(context, false); sms = false } }
                ask.launch(NamazMode.missing(context))
            }
        }
        InfoRow("Length", "$minutes minutes, then it turns off by itself.") {
            Row {
                TextButton(onClick = { minutes = (minutes - 5).coerceAtLeast(5).also { NamazMode.setMinutes(context, it) } }) { Text("\u22125") }
                TextButton(onClick = { minutes = (minutes + 5).coerceAtMost(180).also { NamazMode.setMinutes(context, it) } }) { Text("+5") }
            }
        }
        OutlinedTextField(
            value = message,
            onValueChange = {
                message = it
                NamazMode.setMessage(context, it)
            },
            label = { Text("What the caller hears") },
            supportingText = { Text("Roman Urdu or Urdu script. Played twice, then the call ends.") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        OutlinedTextField(
            value = smsMessage,
            onValueChange = {
                smsMessage = it
                NamazMode.setSmsMessage(context, it)
            },
            label = { Text("Text message to the caller") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
    }
}
