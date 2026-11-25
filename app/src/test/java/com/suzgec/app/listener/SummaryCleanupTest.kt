package com.suzgec.app.listener

import com.suzgec.app.listener.SummaryCleanup.PanelItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Altı boşalan grup özetini silme (24 Kasım 21:29 WhatsApp kumar mesajları özette kalıyordu). */
class SummaryCleanupTest {

    private val wa = "0|com.whatsapp|g:group_key_messages"
    private val summary = PanelItem("0|com.whatsapp|1|null|10285", wa, isSummary = true)
    private val now = 1_000_000L

    @Test
    fun altiBosOzetSilinir() {
        // Süzgeç kumar sohbet bildirimini 2 sn önce sildi; panelde yalnız özet kaldı.
        assertEquals(listOf(summary.key), SummaryCleanup.summariesToCancel(listOf(summary), wa, now - 2_000, now))
    }

    @Test
    fun alttaOnemliYaDaKritikSohbetVarsaDokunulmaz() {
        val onemli = PanelItem("0|com.whatsapp|1|sExp…=|10285", wa, isSummary = false)
        assertTrue(SummaryCleanup.summariesToCancel(listOf(summary, onemli), wa, now - 2_000, now).isEmpty())
    }

    @Test
    fun alttaDokunulmazYaDaBekleyenBildirimVarsaDokunulmaz() {
        // Dokunulmaz (ör. WhatsApp'ın bağlantı bildirimi) ya da 5 sn kuralında bekleyen bildirim de paneldedir.
        val dokunulmaz = PanelItem("0|com.whatsapp|11|null|10285", wa, isSummary = false)
        assertTrue(SummaryCleanup.summariesToCancel(listOf(dokunulmaz, summary), wa, now - 2_000, now).isEmpty())
        // Süzgeç bu gruptan hiç silmediyse (ya da 5 dakikadan önce sildiyse) uygulamanın kendi özetine dokunulmaz.
        assertTrue(SummaryCleanup.summariesToCancel(listOf(summary), wa, null, now).isEmpty())
        assertTrue(SummaryCleanup.summariesToCancel(listOf(summary), wa, now - SummaryCleanup.WINDOW_MS - 1, now).isEmpty())
    }

    @Test
    fun baskaGrupEtkilemez() {
        // Telegram'ın sohbet bildirimi panelde dursa da WhatsApp özeti (altı boş) silinir; Telegram özetine dokunulmaz.
        val tg = "0|org.telegram.messenger|g:messages"
        val tgSummary = PanelItem("0|org.telegram.messenger|1|null|10617", tg, isSummary = true)
        val tgChild = PanelItem("0|org.telegram.messenger|-149|null|10617", tg, isSummary = false)
        val active = listOf(summary, tgSummary, tgChild)
        assertEquals(listOf(summary.key), SummaryCleanup.summariesToCancel(active, wa, now - 2_000, now))
        assertTrue(SummaryCleanup.summariesToCancel(active, tg, now - 2_000, now).isEmpty())
    }
}
