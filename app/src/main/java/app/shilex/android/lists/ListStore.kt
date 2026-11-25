package app.shilex.android.lists

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lists in JARVIS's private storage on this phone. Never backed up or moved to another
 * device (see data_extraction_rules.xml); a list reaches Gemini only when it is read, and
 * leaves the phone only when the user taps Send in the app they shared it to.
 */
class ListStore(context: Context) {
    private val prefs = context.getSharedPreferences("lists", Context.MODE_PRIVATE)

    fun all(): List<NamedList> {
        val a = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrDefault(JSONArray())
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val items = o.optJSONArray("items") ?: JSONArray()
            NamedList(
                o.optString("name"),
                (0 until items.length()).mapNotNull { j ->
                    items.optJSONObject(j)?.let { ListItem(it.optString("t"), it.optLong("at")) }
                },
            )
        }
    }

    /** The list called [name] (aliases understood), empty if it doesn't exist yet. */
    fun get(name: String): NamedList =
        all().firstOrNull { ListRules.sameList(it.name, name) } ?: NamedList(ListRules.canonical(name), emptyList())

    /** Saves [list]; an empty list is dropped. */
    fun put(list: NamedList) {
        val others = all().filterNot { ListRules.sameList(it.name, list.name) }
        write((if (list.items.isEmpty()) others else others + list).takeLast(ListRules.MAX_LISTS))
    }

    private fun write(lists: List<NamedList>) = prefs.edit().putString(
        KEY,
        JSONArray(
            lists.map { l ->
                JSONObject().put("name", l.name).put(
                    "items",
                    JSONArray(l.items.map { JSONObject().put("t", it.text).put("at", it.atMillis) }),
                )
            },
        ).toString(),
    ).apply()

    private companion object {
        const val KEY = "lists"
    }
}
