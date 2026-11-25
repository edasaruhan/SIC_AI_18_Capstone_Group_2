package com.suzgec.app.model

import com.suzgec.app.rules.Category
import com.suzgec.app.rules.NotificationFacts
import com.suzgec.app.rules.PreFilter
import com.suzgec.app.rules.RuleEngine
import com.suzgec.app.rules.RuleSettings
import com.suzgec.app.rules.Rules
import com.suzgec.app.rules.SenderHeuristics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** docs/demo_senaryosu.md: kural katmanı + metin modeli birlikte, uçtan uca (Android'siz). */
class DemoScenarioTest {

    companion object {
        lateinit var engine: RuleEngine
        const val MESAJLAR = "com.samsung.android.messaging"

        @BeforeClass @JvmStatic
        fun load() {
            engine = RuleEngine(
                Rules.parse(File("src/main/assets/rules.json").readText()),
                TextModel.parse(File("src/main/assets/model_tfidf_lr.json").readText()),
            )
        }
    }

    private data class Row(val id: String, val f: NotificationFacts, val expected: Category)

    private fun f(
        app: String, title: String?, text: String?, category: String? = null,
        ongoing: Boolean = false, summary: Boolean = false, media: Boolean = false,
        group: Boolean? = null, sender: String? = null, pkg: String = "com.suzgec.sender",
        shortcut: String? = null, reply: Boolean? = null,
    ) = NotificationFacts(
        pkg, app, title, text, text, category, ongoing, false, summary, media, 0, group, sender,
        shortcutId = shortcut, hasReplyAction = reply,
    )

    private val rows = listOf(
        Row("1 banka", f("Ziraat Mobil", "Hesap hareketi", "Hesabınızdan 1.250,00 TL para çekildi."), Category.KRITIK),
        Row("2 otp", f("Mesajlar", "ZIRAAT", "Doğrulama kodunuz: 482913. Kimseyle paylaşmayın."), Category.KRITIK),
        Row("3 anne", f("WhatsApp", "Annem", "Akşam yemeğe geliyor musun?", "msg", group = false, sender = "Annem"), Category.ONEMLI),
        Row("4 grup", f("WhatsApp", "Sınıf 12-B: Can", "Yarın forma giyiyor muyuz?", "msg", group = true, sender = "Can"), Category.NORMAL),
        Row("5 sinav", f("OBS", "Not açıklandı", "Veri Madenciliği vize notunuz açıklandı."), Category.ONEMLI),
        Row("6 toplanti", f("Takvim", "Proje toplantısı", "15 dakika sonra (10:00)", "event"), Category.ONEMLI),
        Row("7 kargo", f("Trendyol", "Siparişin yolda", "Siparişin kargoya verildi, yarın kapında."), Category.NORMAL),
        Row("8 kampanya", f("Hepsiburada", "Sepette %40 indirim!", "Fırsatı kaçırma, sadece bugün.", "promo"), Category.ONEMSIZ),
        Row("9 kumar_sms", f("Mesajlar", null, "Çevrimsiz %100 deneme bonusu! Hemen yatır, kazan: bit.ly/xx"), Category.SILINENLER),
        Row("10 sosyal", f("Instagram", "Instagram", "ayse_k gönderini beğendi.", "social"), Category.ONEMSIZ),
        Row("11 oyun", f("Bahçe Macerası", "Canların doldu!", "Hemen geri dön, bahçen seni bekliyor."), Category.ONEMSIZ),
        Row("12 guncelleme", f("Yazılım güncelleme", "Güncelleme hazır", "Yeni yazılım güncellemesi hazır.", "recommendation"), Category.ONEMSIZ),
        Row("13 muzik", f("Müzik", "Örnek Şarkı", "Örnek Sanatçı", media = true), Category.DOKUNMA),
        Row("14 navigasyon", f("Haritalar", "200 m sonra sağa dönün", "Atatürk Cd. · 12 dk", "navigation", ongoing = true), Category.DOKUNMA),
        Row("15 indirme", f("Dosyalar", "Ders notları indiriliyor", "%40", "progress", ongoing = true), Category.DOKUNMA),
        Row("17 bahis", f("Mesajlar", null, "Canlı bahiste yüksek oran! Maç başladı, kuponunu yap."), Category.SILINENLER),
        Row("18 bonus_kart", f("Bonus", "Bonus", "Bonus kartınla market alışverişine 50 TL bonus kazan!"), Category.ONEMSIZ),
        Row("19 giris", f("Garanti BBVA", "Güvenlik", "Hesabınıza yeni bir cihazdan giriş yapıldı."), Category.KRITIK),
        Row("20 grup_ozet", f("WhatsApp", "2 yeni mesaj", "Aile grubu", summary = true), Category.DOKUNMA),
        Row("20 alt mesaj", f("WhatsApp", "Aile: Babam", "Akşam geç kalacağım", "msg", group = true, sender = "Babam"), Category.NORMAL),
        Row("21 kurum_kampanya_sms", f("Mesajlar", "TURKCELL", "Size özel 20 GB sadece 99 TL! Katılmak için EVET yazıp 2222'ye gönderin.", "msg", group = false, sender = "TURKCELL"), Category.ONEMSIZ),
        Row("22 banka_sms_birebir", f("Mesajlar", "AKBANK", "Akbank giriş şifreniz: 482913. Kimseyle paylaşmayın.", "msg", group = false, sender = "AKBANK"), Category.KRITIK),
        Row("23 aldatici_kumar", f("Mesajlar", null, "Tebrikler! Çarkı çevir, anında 1.000 TL kazan: goo.su/k1"), Category.SILINENLER),
    )

    /** 25 Kasım: gerçek kullanımdan çıkan kurallar (docs/demo_senaryosu.md 27–32). Sender gerçek paketleri taklit eder. */
    private val rows25 = listOf(
        Row("27 grup_toplanti", f("WhatsApp", "Proje Ekibi", "Yarın 10:00'da toplantı var, herkes gelsin", "msg", group = true, sender = "Ayşe", pkg = "com.whatsapp"), Category.ONEMLI),
        Row("28 oncelikli_kumar", f("Mesajlar", "Babam", "Kazanmak için hemen gazino.com adresine gir", "msg", group = false, sender = "Babam", pkg = MESAJLAR), Category.SILINENLER),
        Row("29 telegram_kanal", f("Telegram", "Kurs Etkinlik Kanalı", "Kasım indiriminde son gün! Bütün eğitimlerde %50 indirim, fırsatı kaçırma!", "msg", group = false, sender = "Kurs Etkinlik Kanalı", pkg = "org.telegram.messenger", shortcut = "ndid_-1001234567890", reply = false), Category.ONEMSIZ),
        Row("30 haber", f("Bundle", "Son Dakika", "Merkez Bankası faiz kararını açıkladı", pkg = "com.dwarfplanet.bundle"), Category.ONEMSIZ),
        Row("31 linkedin_eposta", f("Outlook", "LinkedIn İş İlanı Uyarıları", "Bir teknoloji şirketi ağ güvenliği uzmanı arıyor", pkg = "com.microsoft.office.outlook"), Category.ONEMSIZ),
        Row("33 ytu_rezervasyon", f("Mesajlar", "Y.T.U", "Yarın 10:00 - 12:00, B BLOK çalışma salonu, 3. sandalyede rezervasyonunuz gerçekleşmiştir. QR kodunuz: https://ktprezervasyon.yildiz.edu.tr/url/?u=DEMO", "msg", group = false, sender = "Y.T.U", pkg = MESAJLAR), Category.ONEMLI),
        Row("32 toplu_kutlama", f("Mesajlar", "BELEDIYE", "Eğitim emekçilerimizin özel gününü kutluyorum. Belediye Başkanlığı", "msg", group = false, sender = "BELEDIYE", pkg = MESAJLAR), Category.ONEMSIZ),
    )

    /** M51'deki ayarlar: Mesajlar öncelikli (Önemli), alışveriş ve sosyal filtre açık. */
    private val m51 = RuleSettings(
        shoppingFilter = true, socialFilter = true, priorityApps = mapOf(MESAJLAR to Category.ONEMLI), userName = "Süleyman",
    )

    @Test
    fun demoSenaryosu() = check(rows, RuleSettings())

    @Test
    fun yeniKurallarVarsayilanAyarlar() = check(rows25, RuleSettings())

    @Test
    fun yeniKurallarM51Ayarlari() = check(rows25, m51)

    private fun check(rows: List<Row>, settings: RuleSettings) {
        val wrong = mutableListOf<String>()
        for (r in rows) {
            val pre = engine.preFilter(r.f)
            val (cat, why) = if (pre is PreFilter.Untouched) Category.DOKUNMA to pre.reason
            else engine.classify(r.f, settings).let { it.category to it.reason }
            val ok = cat == r.expected
            println("${if (ok) "OK  " else "HATA"} ${r.id.padEnd(22)} beklenen=${r.expected.label.padEnd(11)} gerçek=${cat.label.padEnd(11)} $why")
            if (!ok) wrong += "${r.id}: beklenen ${r.expected.label}, gerçek ${cat.label} ($why)"
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun kurumGondericisi() {
        for (n in listOf("AKBANK", "TURKCELL", "B001", "GARANTI BBVA", "ZIRAAT", "E-DEVLET")) {
            assertTrue(n, SenderHeuristics.isInstitution(n, isContact = false))
        }
        for (n in listOf("Annem", "Ayşe Yılmaz", "Can", "+90 532 123 45 67", "05321234567", "", "A")) {
            assertFalse(n, SenderHeuristics.isInstitution(n, isContact = false))
        }
        // Rehberde büyük harfle kayıtlı kişi kurum sayılmaz.
        assertFalse(SenderHeuristics.isInstitution("AHMET", isContact = true))
    }

    @Test
    fun kurumBirebirSmsModeleGider() {
        val d = engine.classify(f("Mesajlar", "TURKCELL", "Size özel 20 GB sadece 99 TL!", "msg", group = false, sender = "TURKCELL"))
        assertEquals("model", d.rule)
        assertTrue(d.reason, d.reason.startsWith("Model: ") && d.reason.contains("kurum göndericisi: TURKCELL"))
        val kisi = engine.classify(f("Mesajlar", "Ayşe", "Size özel bir şey aldım!", "msg", group = false, sender = "Ayşe"))
        assertEquals("birebir_sohbet", kisi.rule)
    }
}
