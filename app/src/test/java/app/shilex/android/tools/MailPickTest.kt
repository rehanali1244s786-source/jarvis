package app.shilex.android.tools

import app.shilex.android.notify.Seen
import app.shilex.android.notify.Triage
import app.shilex.android.notify.WatchRules
import app.shilex.android.tools.MailPick.Pick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MailPickTest {
    private fun contact(name: String, vararg emails: String) = Contact(name, emptyList(), emails.toList())

    @Test
    fun anAddressIsUsedAsIs() {
        assertEquals(Pick.Address("ayesha@example.com", "Ayesha@Example.com"), MailPick.choose("Ayesha@Example.com", emptyList()))
        assertTrue(MailPick.isAddress("a.b@c.pk"))
        assertTrue(!MailPick.isAddress("ayesha"))
    }

    @Test
    fun oneContactWithOneAddressIsChosen() {
        assertEquals(Pick.Address("ahmed@x.com", "Ahmed"), MailPick.choose("Ahmed", listOf(contact("Ahmed", "ahmed@x.com"))))
    }

    @Test
    fun neverGuessesBetweenPeopleOrAddresses() {
        val people = MailPick.choose("Ahmed", listOf(contact("Ahmed S", "a@x.com"), contact("Ahmed K", "b@x.com")))
        assertTrue(people is Pick.Ask && people.choices == listOf("Ahmed S", "Ahmed K"))
        val addresses = MailPick.choose("Ahmed", listOf(contact("Ahmed", "work@x.com", "home@x.com")))
        assertTrue(addresses is Pick.Ask && addresses.choices == listOf("work@x.com", "home@x.com"))
        assertTrue(MailPick.choose("Nobody", emptyList()) is Pick.NotFound)
    }

    @Test
    fun emailsAreSortedAndNoReplyStaysQuiet() {
        val boss = Triage.classify(Seen("com.google.android.gm", "Gmail", "Anita Sharma", "Q3 report due Friday", 0L))
        assertEquals(Triage.Kind.Mail, boss.kind)
        assertEquals("Anita Sharma: Q3 report due Friday", boss.line)
        val bot = Triage.classify(Seen("com.google.android.gm", "Gmail", "no-reply@bank.com", "Your statement", 0L))
        assertEquals(Triage.Kind.Quiet, bot.kind)
        val promo = Triage.classify(Seen("com.google.android.gm", "Gmail", "Daraz", "Flat 50% off today", 0L))
        assertEquals(Triage.Kind.Quiet, promo.kind)
        assertTrue(Triage.summary(listOf(boss)).contains("Emails (sender: subject):\n- Anita Sharma"))
    }

    @Test
    fun theWatchDoesNotReadEmailsAloud() {
        val mail = Seen("com.google.android.gm", "Gmail", "Anita Sharma", "Q3 report", 0L)
        assertNull(WatchRules().decide(mail, 0L, phoneSilent = false, inConversation = false))
    }
}
