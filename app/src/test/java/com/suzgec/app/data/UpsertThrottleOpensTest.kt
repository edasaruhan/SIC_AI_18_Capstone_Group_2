package com.suzgec.app.data

import com.suzgec.app.sound.AlertThrottle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 25 Kasım düzeltmeleri: taşınmış kaydı koruma (A-2), aynı sohbet sessizliği (A-3), kutuya giriş sayımı (A-4). */
class UpsertThrottleOpensTest {

    private fun rec(
        category: String, reason: String, postTime: Long, text: String = "metin", restored: Boolean = false,
        rule: String = "model",
    ) = NotificationRecord(
        key = "0|com.linkedin.android|1|null|1", packageName = "com.linkedin.android", appLabel = "LinkedIn",
        title = "Yeni bildirimler", text = text, bigText = null, subText = null, category = "social",
        channelId = null, postTime = postTime, flags = 0, isOngoing = false, isGroupSummary = false,
        isGroupConversation = null, hasContentIntent = true, firstSeen = postTime, lastUpdated = postTime,
        suzgecCategory = category, score = 1.2, contentType = 9, reason = reason, rule = rule, waitedMs = 0,
        cancelledBySuzgec = true, senderHash = "", restoredByUser = restored,
    )

    @Test
    fun tasinmisKayitGuncellemedeKorunur() {
        // 24 Kasım 13:47: kullanıcı Silinenler → Normal taşıdı; 16:07 güncellemesi Silinenler'e döndürüyordu.
        val existing = rec("NORMAL", "Silinenler'den geri alındı → Normal (önceki: Model: sosyal)", 1000, restored = true).copy(id = 7)
        val incoming = rec("SILINENLER", "Model: sosyal etkileşim, %80 → sosyal medya filtresi", 2000, text = "yeni metin")
        val m = Upsert.merge(existing, incoming)
        assertEquals("NORMAL", m.suzgecCategory)
        assertEquals(existing.reason, m.reason)
        assertEquals("yeni metin", m.text)
        assertEquals(7L, m.id)
        assertEquals(1, m.updateCount)
        assertTrue(m.restoredByUser)

        val moved = rec("ONEMLI", "Sen taşıdın → Önemli (önceki: Model)", 1000)
        assertEquals("ONEMLI", Upsert.merge(moved, incoming).suzgecCategory)
    }

    @Test
    fun kategoriDegisirseArsivlenir() {
        // 24 Kasım 21:29: WhatsApp sohbetinde önce kumar (Silinenler), sonra normal mesaj (Önemli).
        val kumar = rec("SILINENLER", "Kumar filtresi: \"kazanbet\"", 1000, rule = "kumar")
        val normal = rec("ONEMLI", "Birebir sohbet mesajı", 2000, rule = "birebir_sohbet")
        assertTrue(Upsert.shouldArchive(kumar, normal))
        assertEquals(kumar.key + "#arsiv-1000", Upsert.archiveKey(kumar))
        assertEquals(kumar.key, Upsert.baseKey(Upsert.archiveKey(kumar)))
        // Aynı kategori: tek satır güncellenir.
        assertFalse(Upsert.shouldArchive(normal, rec("ONEMLI", "Birebir sohbet mesajı", 3000)))
        // Yeniden bağlanmada aynı bildirim (postTime aynı) bölünmez.
        assertFalse(Upsert.shouldArchive(kumar, normal.copy(postTime = 1000)))
        // Elle taşınmış kayıt ve dokunulmaz kayıt bölünmez.
        assertFalse(Upsert.shouldArchive(rec("NORMAL", "Sen taşıdın → Normal", 1000), normal))
        assertFalse(Upsert.shouldArchive(rec("DOKUNMA", "Dokunulmaz: grup özeti", 1000), normal))
    }

    @Test
    fun tasinmamisKayitYenidenSiniflanir() {
        val existing = rec("NORMAL", "Model: işlem bilgisi", 1000)
        val incoming = rec("ONEMSIZ", "Model: kampanya", 2000)
        assertFalse(Upsert.isUserMoved(existing))
        assertEquals("ONEMSIZ", Upsert.merge(existing, incoming).suzgecCategory)
    }

    @Test
    fun ayniSohbetIkiDakikaSessiz() {
        val t = AlertThrottle()
        val wa = AlertThrottle.conversationKey("com.whatsapp", "905551112233@s.whatsapp.net", "k1")
        assertTrue(t.allow(wa, 0))
        assertFalse(t.allow(wa, 60_000)) // 1 dk sonra yeni mesaj: sessiz
        assertFalse(t.allow(wa, 119_000))
        assertTrue(t.allow(wa, 120_000)) // ilk uyarıdan 2 dk sonra yine uyarır
        // Başka sohbet etkilenmez; Kritik her zaman çalar.
        assertTrue(t.allow(AlertThrottle.conversationKey("com.whatsapp", "baska", "k2"), 60_000))
        assertTrue(t.allow(wa, 130_000, force = true))
        // Kısayol yoksa bildirim key'i kullanılır.
        assertEquals("k:0|pkg|1", AlertThrottle.conversationKey("pkg", null, "0|pkg|1"))
    }

    @Test
    fun kutuyaGirisBesDakika() {
        val min = 60_000L
        assertTrue(BoxOpenCount.shouldCount(null, 0, false))
        val last = BoxOpen(time = 0, afterDelivery = false)
        assertFalse(BoxOpenCount.shouldCount(last, 4 * min, false))
        assertTrue(BoxOpenCount.shouldCount(last, 5 * min, false))
        // Teslimden sonraki ilk giriş her zaman sayılır; ondan sonra 5 dk kuralı.
        assertTrue(BoxOpenCount.shouldCount(last, 1 * min, true))
        assertFalse(BoxOpenCount.shouldCount(BoxOpen(time = 0, afterDelivery = true), 1 * min, true))
        assertNull(null)
    }
}
