package app.shilex.android.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsTest {
    private fun contact(name: String, vararg numbers: Pair<String, String>) =
        Contact(name, numbers.map { PhoneNumber(it.first, it.second) })

    private val book = listOf(
        contact("Ahmed Khan", "mobile" to "+92 300 1234567"),
        contact("Ahmed Raza", "mobile" to "0321 1122233", "work" to "042 3500 1234"),
        contact("Mummy", "mobile" to "0333 9999000"),
        contact("Sana Ahmedani", "mobile" to "0345 9000011"),
    )

    @Test
    fun exactNameWins() {
        assertEquals(listOf("Mummy"), ContactMatch.find("mummy", book).map { it.name })
    }

    @Test
    fun severalPeopleWithTheSameFirstNameAreAllReturned() {
        assertEquals(listOf("Ahmed Khan", "Ahmed Raza"), ContactMatch.find("Ahmed", book).map { it.name })
        assertEquals(listOf("Ahmed Raza"), ContactMatch.find("ahmed r", book).map { it.name })
    }

    @Test
    fun sameNumberWrittenTwoWaysCountsOnce() {
        val numbers = listOf(PhoneNumber("mobile", "+92 300 1234567"), PhoneNumber("mobile", "0300-1234567"))
        assertEquals(1, ContactMatch.distinctNumbers(numbers).size)
    }

    @Test
    fun oneNumberIsUsedDirectly() {
        val found = Recipients.pick(book[0], "") as Recipients.Result.Found
        assertEquals("+92 300 1234567", found.number)
    }

    @Test
    fun severalNumbersAskUnlessTheUserSaidWhich() {
        val asked = Recipients.pick(book[1], "") as Recipients.Result.Answer
        assertEquals("needs_clarification", asked.json.getString("status"))
        assertEquals(2, asked.json.getJSONArray("choices").length())
        assertTrue(asked.json.getJSONArray("choices").getString(1).endsWith("1234"))

        assertEquals("042 3500 1234", (Recipients.pick(book[1], "work") as Recipients.Result.Found).number)
        assertEquals("0321 1122233", (Recipients.pick(book[1], "2233") as Recipients.Result.Found).number)
    }
}
