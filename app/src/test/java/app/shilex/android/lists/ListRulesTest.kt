package app.shilex.android.lists

import app.shilex.android.task.Capabilities
import app.shilex.android.task.Risk
import app.shilex.android.task.TurnTrust
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListRulesTest {
    private fun list(vararg items: String) = NamedList("Shopping", items.map { ListItem(it, 1L) })

    @Test
    fun namesAndAliasesMeetInOnePlace() {
        assertEquals("Shopping", ListRules.canonical(""))
        assertEquals("Shopping", ListRules.canonical("my list"))
        assertEquals("Shopping", ListRules.canonical("Groceries!"))
        assertEquals("To-do", ListRules.canonical("to do list"))
        assertEquals("Trip", ListRules.canonical("trip"))
        assertTrue(ListRules.sameList("grocery list", "shopping"))
        assertFalse(ListRules.sameList("trip", "shopping"))
    }

    @Test
    fun addingSkipsRepeatsAndBlanks() {
        val (after, added) = ListRules.add(list("Milk"), listOf("milk", " eggs. ", "", "Eggs", "bread"), 5L)
        assertEquals(listOf("eggs", "bread"), added)
        assertEquals(listOf("Milk", "eggs", "bread"), after.items.map { it.text })
    }

    @Test
    fun listsStopAtTheLimit() {
        val full = NamedList("Shopping", (1..ListRules.MAX_ITEMS).map { ListItem("item $it", 1L) })
        assertTrue(ListRules.add(full, listOf("one more"), 2L).second.isEmpty())
    }

    @Test
    fun secretsAreRefused() {
        assertTrue(ListRules.hasSecret(listOf("milk", "my ATM PIN 4821")))
        assertFalse(ListRules.hasSecret(listOf("milk", "bread", "pinned notes")))
    }

    @Test
    fun itemsAreFoundByTheirWords() {
        val items = list("Whole milk", "Eggs", "Milk chocolate", "Bread").items
        assertEquals(listOf("Eggs"), ListRules.matching(items, "eggs").map { it.text })
        assertEquals(2, ListRules.matching(items, "milk").size)
        assertEquals(listOf("Bread"), ListRules.matching(items, "the bread").map { it.text })
        assertTrue(ListRules.matching(items, "rice").isEmpty())
        assertTrue(ListRules.matching(items, "").isEmpty())
    }

    @Test
    fun readAloudAndShareText() {
        assertEquals("Shopping is empty.", ListRules.spoken(list()))
        assertEquals("Shopping has 1 item: milk.", ListRules.spoken(list("milk")))
        assertEquals("Shopping has 3 items: milk, eggs and bread.", ListRules.spoken(list("milk", "eggs", "bread")))
        assertEquals("Shopping:\n- milk\n- eggs", ListRules.shareText(list("milk", "eggs")))
    }

    @Test
    fun theToolsAreRegisteredWithSafeRisk() {
        listOf("list_add", "list_read", "list_remove", "list_clear", "list_share").forEach {
            assertTrue("$it is not registered", Capabilities.of(it) != null)
        }
        // Sharing leaves the phone, so it is held like any other send after screen text.
        assertTrue(Capabilities.of("list_share")!!.risk >= Risk.High)
        // Planted screen text can't edit the user's lists.
        listOf("list_add", "list_remove", "list_clear").forEach { assertTrue(it, it in TurnTrust.SELF_WRITES) }
    }
}
