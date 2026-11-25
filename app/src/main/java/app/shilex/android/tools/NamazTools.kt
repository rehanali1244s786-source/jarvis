package app.shilex.android.tools

import app.shilex.android.namaz.NamazMode
import org.json.JSONObject

/** "Namaz time": while it is on, the phone answers calls and says the user is praying. */
object NamazTools {
    fun all(): List<PhoneTool> = listOf(NamazModeTool)

    private object NamazModeTool : PhoneTool {
        override val name = "namaz_mode"

        override fun declaration() = Schema.function(
            name,
            "Namaz mode. While it is on, every incoming call is answered by the phone, the caller hears that " +
                "the user is at namaz and should call back later, and the call ends. Use start when the user says " +
                "namaz time, namaz ka waqt ho gaya, or similar. Use stop when they are done (namaz ho gayi, " +
                "namaz mode band). It also turns itself off after the set time.",
            Schema.string("action", "What to do.", required = true, choices = listOf("start", "stop", "status")),
            Schema.integer("minutes", "For start: how long, only if the user said. Otherwise the saved length is used."),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val context = host.context
            return when (args.text("action")) {
                "start" -> {
                    NamazMode.missing(context).firstOrNull()?.let {
                        host.requestPermission(it)
                        return Results.needsPermission("phone calls (namaz mode answers calls)")
                    }
                    val minutes = args.optInt("minutes", 0).takeIf { it in 1..180 }
                    NamazMode.start(context, minutes)
                    Results.done("Namaz mode is on for ${NamazMode.minutesLeft(context)} minutes. Calls will be answered with the saved message" + (if (NamazMode.sendSms(context)) " and the caller gets a text." else "."))
                }
                "stop" -> {
                    NamazMode.stop(context)
                    Results.done("Namaz mode is off.")
                }
                "status" -> Results.done(
                    if (NamazMode.isActive(context)) "Namaz mode is on, ${NamazMode.minutesLeft(context)} minutes left." else "Namaz mode is off.",
                )
                else -> Results.failed("bad_action", "Ask whether to start or stop namaz mode.")
            }
        }
    }
}
