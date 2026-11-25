package com.suzgec.app.stats

import com.suzgec.app.data.NotificationRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

/** Kayıt CSV'si: başlık ve satır aynı sayıda sütun (24 Kasım'da başlıkta kutudan_cikis sütunları eksikti). */
class CsvHeaderTest {

    @Test
    fun baslikVeSatirAyniSutunSayisi() {
        val r = NotificationRecord(
            key = "k", packageName = "com.whatsapp", appLabel = "WhatsApp", title = "Annem", text = "metin", bigText = null,
            subText = null, category = "msg", channelId = null, postTime = 0, flags = 0, isOngoing = false,
            isGroupSummary = false, isGroupConversation = false, hasContentIntent = true, firstSeen = 0, lastUpdated = 0,
            suzgecCategory = "ONEMLI", score = null, contentType = 2, reason = "Birebir sohbet mesajı", rule = "birebir_sohbet",
            waitedMs = 5000, cancelledBySuzgec = false, dismissedAt = 60_000, dismissMethod = "kaydirildi",
        )
        val row = Weekly.csvRow(r, SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT))
        assertEquals(Weekly.CSV_HEADER.size, row.size)
        assertEquals(listOf("kutudan_cikis", "kutudan_cikis_yolu"), Weekly.CSV_HEADER.takeLast(2))
        assertEquals("kaydirildi", row.last())
        // Bildirim başlığı ve metni CSV'ye yazılmaz.
        assertEquals(false, row.contains("Annem") || row.contains("metin"))
    }

    @Test
    fun nedenSutunundaAdVeMetinYok() {
        // CLAUDE.md §0 gizlilik: yalnız kural adı / tür / olasılık; gönderen adı, tırnaklı ifade, kanal adı yazılmaz.
        val cases = listOf(
            Triple("toplu_kutlama", "Toplu kutlama mesajı: KURUM A, \"kutluyorum\"", "ONEMSIZ") to "Toplu kutlama mesajı",
            Triple("kumar", "Kumar filtresi: \"gazino + kazan\"", "SILINENLER") to "Kumar filtresi",
            Triple("model", "Model: kampanya, %100 (kurum göndericisi: OPERATOR); puan 1,00", "ONEMSIZ") to "Model: kampanya, %100",
            Triple("model", "Model: kampanya, %97 (Telegram kanalı: kısayol ndid_-100); puan 1,02", "ONEMSIZ") to "Model: kampanya, %97",
            Triple("model", "Model: kişisel mesaj, %43; puan 2,11, emin değil (yükseltme en fazla Normal)", "NORMAL") to "Model: kişisel mesaj, %43, emin değil",
            Triple("oncelikli_uygulama", "Öncelikli uygulama: Mesajlar → Önemli", "ONEMLI") to "Öncelikli uygulama → Önemli",
            Triple("oncelikli_kampanya", "Öncelikli uygulama Mesajlar, ama kampanya (\"indirim\") → Önemsiz", "ONEMSIZ") to "Öncelikli uygulama, kampanya → Önemsiz",
            Triple("grup_anahtar", "Grup sohbeti, \"ad soyad\" geçiyor → Önemli", "ONEMLI") to "Grup sohbeti, anahtar kelime",
            Triple("model", "Sen taşıdın → Normal (önceki: Model: kampanya, \"x\")", "NORMAL") to "Sen taşıdın → Normal",
            Triple("dokunma", "Dokunulmaz: kalıcı bildirim", "DOKUNMA") to "Dokunulmaz: kalıcı bildirim",
            Triple("eposta_linkedin", "E-posta, gönderen \"şirket\" → Önemsiz", "ONEMSIZ") to "E-posta, iş ağı/iş ilanı gönderen → Önemsiz",
        )
        for ((input, expected) in cases) {
            assertEquals(input.second, expected, Weekly.cleanReason(input.first, input.second, input.third))
        }
    }
}
