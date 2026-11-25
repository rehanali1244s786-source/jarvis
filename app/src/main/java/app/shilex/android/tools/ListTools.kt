package app.shilex.android.tools

import android.content.Intent
import app.shilex.android.Diag
import app.shilex.android.lists.ListRules
import app.shilex.android.lists.ListStore
import org.json.JSONObject

/**
 * Shopping and to-do lists kept on this phone ("add milk to my list"), read back by voice
 * and shared to WhatsApp. Sharing opens WhatsApp's chooser with the text filled in; the user
 * picks the chat and taps Send, so nothing leaves the phone on its own.
 */
object ListTools {
    fun all(): List<PhoneTool> = listOf(Add, Read, Remove, Clear, Share)

    private val listName = Schema.string("list", "Which list: shopping (the default), to-do, or the user's own name for it.")

    private fun items(args: JSONObject): List<String> {
        val array = args.optJSONArray("items")
        val many = (0 until (array?.length() ?: 0)).map { array!!.optString(it) }
        return ListRules.clean(many + args.text("item"))
    }

    private object Add : PhoneTool {
        override val name = "list_add"

        override fun declaration() = Schema.function(
            name,
            "Adds items to a shopping or to-do list. \"Add milk and eggs to my list\" is items [milk, eggs]. Never passwords, PINs, OTPs or card numbers.",
            Schema.strings("items", "Each thing to add, short, in the user's words, one entry per item."),
            Schema.string("item", "A single item, if there is only one."),
            listName,
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val wanted = items(args)
            if (wanted.isEmpty()) return Results.failed("empty", "Ask what to add.")
            if (ListRules.hasSecret(wanted)) {
                return Results.failed("secret", "That looks like a password, PIN, code or card number. Say it isn't kept, for the user's safety.")
            }
            val store = ListStore(host.context)
            val (list, added) = ListRules.add(store.get(args.text("list")), wanted, System.currentTimeMillis())
            if (added.isEmpty()) return Results.done("Already on ${list.name}: ${wanted.joinToString(", ")}.")
            store.put(list)
            Diag.i("lists: ${added.size} added")
            val skipped = wanted.size - added.size
            val tail = if (skipped > 0) " $skipped were already there." else ""
            return Results.done("Added ${added.joinToString(", ")} to ${list.name}. It has ${list.items.size} now.$tail")
        }
    }

    private object Read : PhoneTool {
        override val name = "list_read"

        override fun declaration() = Schema.function(
            name,
            "Reads a list back: \"what's on my list?\". With no list given, reads the shopping list; \"all\" names every list.",
            listName,
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val store = ListStore(host.context)
            if (args.text("list").equals("all", ignoreCase = true)) {
                val lists = store.all()
                if (lists.isEmpty()) return Results.done("There are no lists yet.")
                return Results.done(lists.joinToString(" ") { ListRules.spoken(it) })
            }
            return Results.done(ListRules.spoken(store.get(args.text("list"))))
        }
    }

    private object Remove : PhoneTool {
        override val name = "list_remove"

        override fun declaration() = Schema.function(
            name,
            "Takes an item off a list: bought, done, \"remove milk\". Names the item by its words.",
            Schema.string("item", "Words of the item to remove.", required = true),
            listName,
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val store = ListStore(host.context)
            val list = store.get(args.text("list"))
            val matches = ListRules.matching(list.items, args.text("item"))
            return when {
                matches.isEmpty() -> Results.failed("not_found", "Nothing on ${list.name} matches that.")
                matches.size > 1 -> Results.ask("Several items match. Ask which one.", matches.map { it.text })
                else -> {
                    store.put(list.copy(items = list.items - matches.single()))
                    Results.done("Took ${matches.single().text} off ${list.name}.")
                }
            }
        }
    }

    private object Clear : PhoneTool {
        override val name = "list_clear"

        override fun declaration() = Schema.function(
            name,
            "Empties a whole list. Only when the user clearly asks for the whole list to go.",
            listName,
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val store = ListStore(host.context)
            val list = store.get(args.text("list"))
            if (list.items.isEmpty()) return Results.done("${list.name} was already empty.")
            store.put(list.copy(items = emptyList()))
            return Results.done("Cleared ${list.name}, ${list.items.size} items.")
        }
    }

    private object Share : PhoneTool {
        override val name = "list_share"

        override fun declaration() = Schema.function(
            name,
            "Opens WhatsApp with a list typed in as a message; the user picks the chat and taps Send. Falls back to the phone's share sheet if WhatsApp isn't installed.",
            listName,
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val list = ListStore(host.context).get(args.text("list"))
            if (list.items.isEmpty()) return Results.failed("empty_list", "${list.name} is empty, so there is nothing to share.")
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, ListRules.shareText(list))
            val pm = host.context.packageManager
            val whatsapp = WHATSAPP.firstOrNull { pkg ->
                pm.resolveActivity(Intent(send).setPackage(pkg), 0) != null
            }
            val intent = if (whatsapp != null) Intent(send).setPackage(whatsapp) else Intent.createChooser(send, "Share ${list.name}")
            val app = if (whatsapp != null) "WhatsApp" else "the share sheet"
            return host.openApp(
                intent,
                if (whatsapp != null) "WhatsApp" else "Share",
                "$app will open with ${list.name} typed in after you speak; the user picks who to send it to and taps Send. " +
                    "It is not sent yet: never say it was sent.",
            )
        }
    }

    private val WHATSAPP = listOf("com.whatsapp", "com.whatsapp.w4b")
}
