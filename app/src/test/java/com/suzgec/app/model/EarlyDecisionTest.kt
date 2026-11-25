package com.suzgec.app.model

import com.suzgec.app.listener.EarlyGate
import com.suzgec.app.rules.Category
import com.suzgec.app.rules.NotificationFacts
import com.suzgec.app.rules.RuleEngine
import com.suzgec.app.rules.RuleSettings
import com.suzgec.app.rules.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** 25 Kasım: güvenlik ve kumar 5 sn beklemeden önce (earlyDecision); geri kalan davranış değişmez. */
class EarlyDecisionTest {

    companion object {
        lateinit var engine: RuleEngine

        @BeforeClass @JvmStatic
        fun load() {
            engine = RuleEngine(
                Rules.parse(File("src/main/assets/rules.json").readText()),
                TextModel.parse(File("src/main/assets/model_tfidf_lr.json").readText()),
            )
        }
    }

    private fun n(app: String, title: String?, text: String, pkg: String = "com.ornek", group: Boolean? = null) =
        NotificationFacts(pkg, app, title, text, null, null, false, false, false, false, 0, group, title)

    private val m51 = RuleSettings(
        shoppingFilter = true, socialFilter = true,
        priorityApps = mapOf("com.samsung.android.messaging" to Category.ONEMLI, "com.whatsapp" to Category.KRITIK),
        panelApps = setOf("com.sec.android.app.shealth"),
    )

    @Test
    fun guvenlikVeKumarErkenKarar() {
        val kumar = engine.earlyDecision(n("WhatsApp", "Arkadaş", "Yeni üyelere deneme bonusu: kazanbet giriş adresi burada", "com.whatsapp", false), m51)
        assertEquals(Category.SILINENLER, kumar!!.category)
        assertEquals("kumar", kumar.rule)
        val kod = engine.earlyDecision(n("Mesajlar", "ZIRAAT", "Doğrulama kodunuz: 482913. Kimseyle paylaşmayın."), m51)
        assertEquals(Category.KRITIK, kod!!.category)
        assertEquals("guvenlik", kod.rule)
        val gizli = engine.earlyDecision(n("Mesajlar", "Banka", "Hassas bildirim içeriği gizlendi"), m51)
        assertEquals("gizli_icerik", gizli!!.rule)
        // Kumar filtresi kapalıysa kumar erken karar almaz.
        assertNull(engine.earlyDecision(n("Mesajlar", null, "Canlı bahiste yüksek oran!"), m51.copy(gamblingFilter = false)))
    }

    @Test
    fun digerleriErkenKararAlmaz() {
        // Rezervasyon, öncelikli uygulama, Panelde bırak, birebir mesaj: erken karar yok, 5 sn bekleme ve sonraki adımlar aynı.
        for (f in listOf(
            n("Mesajlar", "Y.T.U", "1. sandalyede rezervasyonunuz gerçekleşmiştir. QR kodunuz: https://ktprezervasyon.yildiz.edu.tr/u", "com.samsung.android.messaging", false),
            n("Samsung Health", "Hedef", "Tebrik ederiz!", "com.sec.android.app.shealth"),
            n("WhatsApp", "Annem", "Akşam yemeğe geliyor musun?", "com.whatsapp", false),
            n("Bundle", "Son Dakika", "Merkez Bankası faiz kararını açıkladı", "com.dwarfplanet.bundle"),
        )) {
            assertNull(f.title, engine.earlyDecision(f, m51))
        }
    }

    @Test
    fun erkenKararClassifyIleAyni() {
        // classify earlyDecision ile başlar: aynı bildirim için kararlar birebir aynı (Panelde bırak/öncelikliden önce).
        for (f in listOf(
            n("WhatsApp", "Arkadaş", "100 free spin çevrimsiz olarak hesabında", "com.whatsapp", false),
            n("Samsung Health", null, "Doğrulama kodunuz: 482913", "com.sec.android.app.shealth"),
            n("Mesajlar", "ISS", "482913 numarali modem sifrenizdir, baskasina vermeyiniz.", "com.samsung.android.messaging", false),
        )) {
            assertEquals(engine.classify(f, m51), engine.earlyDecision(f, m51))
        }
    }

    @Test
    fun ayniBildirimBesSaniyedeIkinciKezUyarmaz() {
        val g = EarlyGate()
        assertTrue(g.firstWithin("k", 0, 5_000))
        assertFalse(g.firstWithin("k", 3_000, 5_000)) // güncelleme: kayıt güncellenir, uyarı yok
        assertTrue(g.firstWithin("k", 8_100, 5_000))
        assertTrue(g.firstWithin("baska", 3_000, 5_000))
    }
}
