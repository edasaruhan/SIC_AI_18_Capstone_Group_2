package com.suzgec.app.model

import com.suzgec.app.countCorrections
import com.suzgec.app.data.Correction
import com.suzgec.app.rules.Category
import com.suzgec.app.rules.NotificationFacts
import com.suzgec.app.rules.RuleEngine
import com.suzgec.app.rules.RuleSettings
import com.suzgec.app.rules.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** Demo 24–25 (filtreler) ve kişiselleştirme v1 (CLAUDE.md §7). */
class FiltersAndPersonalizationTest {

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

    /** Filtreler yalnız rules.json'daki uygulamalara uygulanır: demo gönderici gerçek paket adlarını taklit eder. */
    private val pkgs = mapOf(
        "Hepsiburada" to "com.pozitron.hepsiburada", "Trendyol" to "trendyol.com", "Instagram" to "com.instagram.android",
    )

    private fun f(app: String, title: String?, text: String, category: String? = null, group: Boolean? = null, sender: String? = null) =
        NotificationFacts(pkgs[app] ?: "com.suzgec.sender", app, title, text, text, category, false, false, false, false, 0, group, sender)

    private val kampanya = f("Hepsiburada", "Sepette %40 indirim!", "Fırsatı kaçırma, sadece bugün.", "promo")
    private val kargo = f("Trendyol", "Siparişin yolda", "Siparişin kargoya verildi, yarın kapında.")
    private val sosyal = f("Instagram", "Instagram", "ayse_k gönderini beğendi.", "social")
    private val dm = f("Instagram", "ayse_k", "Yarın derse geliyor musun? Notları getirir misin?", "msg", group = false, sender = "ayse_k")

    @Test
    fun alisverisFiltresi_24() {
        val on = RuleSettings(shoppingFilter = true)
        assertEquals(Category.SILINENLER, engine.classify(kampanya, on).category)
        assertTrue(engine.classify(kampanya, on).reason.contains("alışveriş filtresi"))
        assertEquals(Category.NORMAL, engine.classify(kargo, on).category) // sipariş/kargo kalır
        assertEquals(Category.ONEMSIZ, engine.classify(kampanya, RuleSettings()).category) // filtre kapalı
    }

    @Test
    fun sosyalFiltre_25() {
        val on = RuleSettings(socialFilter = true)
        assertEquals(Category.SILINENLER, engine.classify(sosyal, on).category)
        assertEquals(Category.ONEMLI, engine.classify(dm, on).category) // mesajlar kalır
        assertEquals(Category.ONEMSIZ, engine.classify(sosyal, RuleSettings()).category)
    }

    @Test
    fun kumarFiltresiKapali() {
        val sms = f("Mesajlar", null, "Tebrikler! Çarkı çevir, anında 1.000 TL kazan: goo.su/k1")
        assertEquals(Category.SILINENLER, engine.classify(sms, RuleSettings()).category)
        assertTrue(engine.classify(sms, RuleSettings(gamblingFilter = false)).category != Category.SILINENLER)
    }

    @Test
    fun filtreKapsamiYalnizIlgiliUygulamalar() {
        val on = RuleSettings(shoppingFilter = true, socialFilter = true)
        // Outlook'taki kurs kampanyası ve LinkedIn'in sosyal bildirimi silinmez, Önemsiz'e düşer.
        val mail = NotificationFacts(
            "com.microsoft.office.outlook", "Outlook", "Kurs Platformu",
            "Yılın en büyük indirimlerinde son 7 gün! Bütün eğitimlerde fırsatlar seni bekliyor.",
            null, "email", false, false, false, false, 0, null,
        )
        val d = engine.classify(mail, on)
        assertEquals(d.reason, Category.ONEMSIZ, d.category)
        val linkedin = NotificationFacts(
            "com.linkedin.android", "LinkedIn", "Yeni bildirimler", "Kaçırdığın güncellemeler var",
            null, "social", false, false, false, false, 0, null,
        )
        val l = engine.classify(linkedin, on)
        assertTrue(l.reason, l.category != Category.SILINENLER)
    }

    @Test
    fun oncelikliUygulama() {
        val s = RuleSettings(priorityApps = mapOf("com.suzgec.sender" to Category.KRITIK))
        val oyun = f("Bahçe Macerası", "Canların doldu!", "Hemen geri dön, bahçen seni bekliyor.")
        assertEquals(Category.KRITIK, engine.classify(oyun, s).category)
        // 25 Kasım: öncelikli uygulama kampanyayı geçirmez (model tür 7 ya da güvenlik NEG ifadesi).
        val d = engine.classify(f("Bahçe Macerası", "Sepette %40 indirim!", "Fırsatı kaçırma, sadece bugün."), s)
        assertEquals(Category.ONEMSIZ, d.category)
        assertEquals("oncelikli_kampanya", d.rule)
    }

    @Test
    fun deltaFormulu() {
        assertEquals(0.0, Personalization.delta(0, 0), 1e-12)
        assertEquals(1.0 / 3, Personalization.delta(1, 0), 1e-12)
        assertEquals(0.5, Personalization.delta(2, 0), 1e-12)
        assertEquals(-0.5, Personalization.delta(0, 2), 1e-12)
        assertEquals(0.0, Personalization.delta(3, 3), 1e-12)
        assertEquals(8.0 / 10, Personalization.delta(8, 0), 1e-12) // sınır: eta = 1 basamağa yaklaşır
    }

    @Test
    fun duzeltmeSayaclari() {
        val h = Personalization.senderHash("ayse_k")
        val list = listOf(
            Correction(time = 1, senderHash = "", packageName = "p", appLabel = "Trendyol", contentType = 6, fromCategory = "NORMAL", toCategory = "ONEMLI"),
            Correction(time = 2, senderHash = "", packageName = "p", appLabel = "Trendyol", contentType = 6, fromCategory = "NORMAL", toCategory = "KRITIK"),
            Correction(time = 3, senderHash = h, packageName = "p", appLabel = "Instagram", contentType = 2, fromCategory = "ONEMLI", toCategory = "ONEMSIZ"),
            Correction(time = 4, senderHash = "", packageName = "p", appLabel = "Mesajlar", contentType = 8, fromCategory = "SILINENLER", toCategory = "NORMAL"),
        )
        val c = countCorrections(list)
        assertEquals(Counts(2, 0), c[PersonalKey("", "Trendyol", 6)])
        assertEquals(Counts(0, 1), c[PersonalKey(h, "Instagram", 2)])
        assertEquals(Counts(1, 0), c[PersonalKey("", "Mesajlar", 8)]) // Silinenler'den geri alma = yukarı
    }

    @Test
    fun ikiKezYukariTasinanKargoOnemliOlur() {
        val key = engine.personalKey(kargo, 6)
        val bir = RuleSettings(personal = mapOf(key to Counts(1, 0)))
        val iki = RuleSettings(personal = mapOf(key to Counts(2, 0)))
        assertEquals(Category.NORMAL, engine.classify(kargo, bir).category) // 2,01 + 0,33 = 2,34
        val d = engine.classify(kargo, iki)
        assertEquals(Category.ONEMLI, d.category) // 2,01 + 0,50 = 2,51
        assertTrue(d.reason, d.reason.contains("senin düzeltmen +0,50"))
        // Başka uygulamanın kargo bildirimi etkilenmez
        val baska = f("Hepsiburada", "Siparişin yolda", "Siparişin kargoya verildi, yarın kapında.")
        assertEquals(Category.NORMAL, engine.classify(baska, iki).category)
    }

    @Test
    fun gondereneGoreBirebirMesajDuzeltmesi() {
        val key = engine.personalKey(dm, 2)
        val d = engine.classify(dm, RuleSettings(personal = mapOf(key to Counts(0, 2)))) // 3,0 − 0,5 = 2,5
        assertEquals(Category.ONEMLI, d.category)
        val d2 = engine.classify(dm, RuleSettings(personal = mapOf(key to Counts(0, 3)))) // 3,0 − 0,6 = 2,4
        assertEquals(Category.NORMAL, d2.category)
        // Başka gönderen etkilenmez
        val anne = f("Instagram", "Annem", "Akşam geliyor musun?", "msg", group = false, sender = "Annem")
        assertEquals(Category.ONEMLI, engine.classify(anne, RuleSettings(personal = mapOf(key to Counts(0, 3)))).category)
    }

    @Test
    fun gonderenKarmasi() {
        assertEquals(64, Personalization.senderHash("Ayşe").length)
        assertEquals(Personalization.senderHash(" ayşe "), Personalization.senderHash("AYŞE".lowercase()))
        assertEquals("", Personalization.senderHash(null))
    }
}
