package app.shilex.android.lists

import app.shilex.android.memory.MemoryRules

/** One entry on a list ("milk"). */
data class ListItem(val text: String, val atMillis: Long)

/** A named list ("Shopping", "To-do"). */
data class NamedList(val name: String, val items: List<ListItem>)

/**
 * How lists are named, filled, read aloud and shared. Pure, so it is unit-tested.
 * Items are refused if they look like a password, PIN, OTP or card number: lists are read
 * to Gemini and can be shared out of the phone.
 */
object ListRules {
    const val DEFAULT = "Shopping"
    const val MAX_LISTS = 20
    const val MAX_ITEMS = 100
    const val MAX_ITEM_LENGTH = 80
    const val MAX_NAME = 30

    private val ALIASES = mapOf(
        "shopping" to DEFAULT, "shopping list" to DEFAULT, "grocery" to DEFAULT, "groceries" to DEFAULT,
        "grocery list" to DEFAULT, "list" to DEFAULT, "my list" to DEFAULT, "market" to DEFAULT,
        "todo" to "To-do", "to do" to "To-do", "to do list" to "To-do", "todo list" to "To-do",
        "tasks" to "To-do", "task list" to "To-do",
    )

    /** "Groceries!" and "grocery list" are the same list; an empty name means the shopping list. */
    fun canonical(name: String): String {
        val k = MemoryRules.key(name)
        if (k.isEmpty()) return DEFAULT
        ALIASES[k]?.let { return it }
        return name.trim().replace(Regex("\\s+"), " ").take(MAX_NAME).replaceFirstChar { it.uppercase() }
    }

    fun sameList(a: String, b: String) = MemoryRules.key(canonical(a)) == MemoryRules.key(canonical(b))

    /** Cleans what the model passed: trimmed, shortened, no blanks, no repeats. */
    fun clean(items: List<String>): List<String> = items
        .map { it.trim().trim('.', ',', ';').replace(Regex("\\s+"), " ").take(MAX_ITEM_LENGTH) }
        .filter { it.isNotEmpty() }
        .distinctBy { MemoryRules.key(it) }

    fun hasSecret(items: List<String>) = items.any(MemoryRules::looksSecret)

    /** Adds [new] items to [list]; ones already on it (same words) are skipped. */
    fun add(list: NamedList, new: List<String>, now: Long): Pair<NamedList, List<String>> {
        val have = list.items.map { MemoryRules.key(it.text) }.toSet()
        val fresh = clean(new).filter { MemoryRules.key(it) !in have }.take((MAX_ITEMS - list.items.size).coerceAtLeast(0))
        return NamedList(list.name, list.items + fresh.map { ListItem(it, now) }) to fresh
    }

    /** Items matching [words]: an exact match wins, otherwise every item sharing a word. */
    fun matching(items: List<ListItem>, words: String): List<ListItem> {
        val k = MemoryRules.key(words)
        if (k.isEmpty()) return emptyList()
        items.filter { MemoryRules.key(it.text) == k }.let { if (it.isNotEmpty()) return it }
        val wanted = k.split(' ').filter { it.length >= 2 }
        return items.filter { item ->
            val have = MemoryRules.key(item.text).split(' ')
            wanted.any { w -> have.any { h -> h == w || (w.length >= 4 && (h.startsWith(w) || w.startsWith(h))) } }
        }
    }

    /** For the model to say aloud: "Shopping has 3 items: milk, eggs and bread." */
    fun spoken(list: NamedList): String {
        val n = list.items.size
        if (n == 0) return "${list.name} is empty."
        val names = list.items.map { it.text }
        val joined = if (n == 1) names.single() else names.dropLast(1).joinToString(", ") + " and " + names.last()
        return "${list.name} has $n ${if (n == 1) "item" else "items"}: $joined."
    }

    /** Plain text for WhatsApp: a title and one "- item" line each. */
    fun shareText(list: NamedList): String =
        (listOf("${list.name}:") + list.items.map { "- ${it.text}" }).joinToString("\n")
}
