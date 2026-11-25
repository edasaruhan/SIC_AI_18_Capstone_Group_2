package com.suzgec.app.model

import com.suzgec.app.data.NotificationRecord
import com.suzgec.app.data.OrderPair
import com.suzgec.app.data.Upsert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Sürükleyerek sıralama (CLAUDE.md §7, 25 Kasım): yalnız kategori içi sıra; kategori kararına girmez. */
class OrderingTest {

    private var nextId = 1L

    private fun rec(
        cat: String, app: String, score: Double? = null, postTime: Long = 1_000, sender: String = "",
        manualRank: Double? = null,
    ) = NotificationRecord(
        id = nextId++, key = "k$nextId", packageName = "p.$app", appLabel = app, title = app, text = "t", bigText = null,
        subText = null, category = null, channelId = null, postTime = postTime, flags = 0, isOngoing = false,
        isGroupSummary = false, isGroupConversation = null, hasContentIntent = true, firstSeen = postTime,
        lastUpdated = postTime, suzgecCategory = cat, score = score, contentType = null, reason = "r", rule = "model",
        waitedMs = 0, cancelledBySuzgec = false, senderHash = sender, manualRank = manualRank,
    )

    /** Sürükleme öncesi sıralama (eski sortBox), karşılaştırma için. */
    private fun oldSort(list: List<NotificationRecord>) = list.sortedWith(
        compareBy<NotificationRecord> { Ordering.BOX_ORDER.indexOfFirst { c -> c.name == it.suzgecCategory }.let { i -> if (i < 0) 99 else i } }
            .thenByDescending { it.score ?: Ordering.importance(it) }
            .thenByDescending { it.postTime }
    )

    @Test
    fun formul() {
        assertEquals(0.0, Ordering.delta(0, 0), 1e-12)
        assertEquals(0.5 / 3, Ordering.delta(1, 0), 1e-12)
        assertEquals(0.25, Ordering.delta(2, 0), 1e-12) // "sıra: senin sıralaman +0,25"
        assertEquals(-0.25, Ordering.delta(0, 2), 1e-12)
        val pairs = listOf(pair("WhatsApp", "Outlook"), pair("WhatsApp", "LinkedIn"), pair("Bundle", "Bundle"))
        val d = Ordering.deltas(pairs)
        assertEquals(0.25, d[Ordering.Key("WhatsApp", "")]!!, 1e-12)
        assertNull(d[Ordering.Key("Bundle", "")]) // aynı anahtar sayılmaz
        assertEquals("sıra: senin sıralaman +0,25", Ordering.note(rec("NORMAL", "WhatsApp"), d))
        assertNull(Ordering.note(rec("NORMAL", "Hava"), d))
    }

    private fun pair(above: String, below: String) = OrderPair(
        time = 0, category = "NORMAL", aboveId = 0, belowId = 0, aboveApp = above, aboveSender = "", belowApp = below, belowSender = "",
    )

    @Test
    fun ciftVeElleSiraYoksaSiraAynen() {
        val list = listOf(
            rec("NORMAL", "A", 2.1, 10), rec("ONEMLI", "B", null, 20), rec("NORMAL", "C", 2.1, 30),
            rec("ONEMSIZ", "D", 1.2, 40), rec("KRITIK", "E", null, 5), rec("NORMAL", "F", null, 50),
        )
        assertEquals(oldSort(list).map { it.id }, Ordering.sortBox(list).map { it.id })
    }

    @Test
    fun surukleyinceSiraKorunurVeCiftlerYazilir() {
        // Hepsi Normal ve eşit puanlı (grup mesajları gibi): en alttakini en üste taşı.
        val list = Ordering.sortBox(List(4) { rec("NORMAL", "App$it", 2.0, postTime = 100L - it) })
        val r = Ordering.reorder(list, from = 3, to = 0, pairsBefore = emptyList(), now = 1)!!
        val expected = listOf(list[3], list[0], list[1], list[2]).map { it.id }
        val after = list.map { it.copy(manualRank = r.ranks[it.id]) }
        assertEquals(expected, Ordering.sortBox(after, Ordering.deltas(r.pairs)).map { it.id })
        // Üstünden geçtiği 3 kaydın her biriyle "taşınan üstte" çifti.
        assertEquals(3, r.pairs.size)
        assertTrue(r.pairs.all { it.aboveId == list[3].id })
        // Aşağı taşıma: geçilen kayıtlar üstte.
        val down = Ordering.reorder(list, from = 0, to = 2, pairsBefore = emptyList(), now = 1)!!
        assertEquals(listOf(list[1].id, list[2].id), down.pairs.map { it.aboveId })
        assertTrue(down.pairs.all { it.belowId == list[0].id })
        // Yukarı/Aşağı taşı (bir adım) da aynı yoldan.
        val one = Ordering.reorder(list, from = 2, to = 1, pairsBefore = emptyList(), now = 1)!!
        val afterOne = list.map { it.copy(manualRank = one.ranks[it.id]) }
        assertEquals(listOf(list[0], list[2], list[1], list[3]).map { it.id }, Ordering.sortBox(afterOne, Ordering.deltas(one.pairs)).map { it.id })
    }

    @Test
    fun yeniBildirimSiraPuaniylaYerlesir() {
        // Kullanıcı WhatsApp'ı iki kez Outlook'un üstüne taşıdı → WhatsApp +0,25, Outlook −0,25.
        val pairs = listOf(pair("WhatsApp", "Outlook"), pair("WhatsApp", "Outlook"))
        val d = Ordering.deltas(pairs)
        val outlook = rec("NORMAL", "Outlook", 2.1, postTime = 200) // daha yeni ve puanı biraz yüksek
        val wa = rec("NORMAL", "WhatsApp", 2.0, postTime = 100)
        assertEquals(listOf(outlook.id, wa.id), Ordering.sortBox(listOf(outlook, wa)).map { it.id })
        assertEquals(listOf(wa.id, outlook.id), Ordering.sortBox(listOf(outlook, wa), d).map { it.id })
    }

    @Test
    fun siraKategoriyiAsmaz() {
        // Elle sıra ve sıra puanı ne kadar büyük olursa olsun kategori grupları değişmez.
        val onemsiz = rec("ONEMSIZ", "WhatsApp", 1.0, manualRank = 1e9)
        val kritik = rec("KRITIK", "Banka", null, manualRank = -1e9)
        val normal = rec("NORMAL", "Outlook", 2.0)
        val d = mapOf(Ordering.Key("WhatsApp", "") to 0.5, Ordering.Key("Outlook", "") to -0.5)
        assertEquals(listOf(kritik.id, normal.id, onemsiz.id), Ordering.sortBox(listOf(onemsiz, normal, kritik), d).map { it.id })
    }

    @Test
    fun guncellemeElleSirayiKorur() {
        val existing = rec("NORMAL", "WhatsApp", 2.0, postTime = 1, manualRank = 2.37)
        val sameCat = rec("NORMAL", "WhatsApp", 2.0, postTime = 2)
        assertEquals(2.37, Upsert.merge(existing, sameCat).manualRank!!, 1e-12)
        // Kategori değişince (arşivlenmeyen durumda da) elle sıra o kategoriye ait olduğu için kalmaz.
        val otherCat = rec("ONEMLI", "WhatsApp", 3.0, postTime = 1)
        assertNull(Upsert.merge(existing, otherCat).manualRank)
    }

    @Test
    fun siralamaKategoriKararinaSesVeKisisellestirmeyeGirmez() {
        // Kategori kararı, ses ve δ kişiselleştirmesi dosyaları sıralamayı hiç görmez.
        val src = "src/main/java/com/suzgec/app/"
        for (f in listOf(
            "rules/RuleEngine.kt", "model/CategoryMapper.kt", "model/Personalization.kt", "listener/SuzgecListener.kt",
            "sound/AlertPlayer.kt", "SuzgecApp.kt", "delivery/Delivery.kt",
        )) {
            val text = File(src + f).readText()
            for (w in listOf("Ordering", "OrderPair", "manualRank", "order_pairs")) assertFalse("$f: $w", text.contains(w))
        }
    }
}
