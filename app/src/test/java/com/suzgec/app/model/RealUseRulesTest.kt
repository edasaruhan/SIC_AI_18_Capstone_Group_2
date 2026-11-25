package com.suzgec.app.model

import com.suzgec.app.rules.Category
import com.suzgec.app.rules.NotificationFacts
import com.suzgec.app.rules.RuleEngine
import com.suzgec.app.rules.RuleSettings
import com.suzgec.app.rules.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 24 Kasım M51 gerçek kullanımından çıkan kurallar (CLAUDE.md §5, §7). Metinler gerçek bildirimlerden
 * kısaltıldı; kişisel bilgiler çıkarıldı.
 */
class RealUseRulesTest {

    companion object {
        lateinit var engine: RuleEngine
        lateinit var rules: Rules

        @BeforeClass @JvmStatic
        fun load() {
            rules = Rules.parse(File("src/main/assets/rules.json").readText())
            engine = RuleEngine(rules, TextModel.parse(File("src/main/assets/model_tfidf_lr.json").readText()))
        }

        const val MESAJLAR = "com.samsung.android.messaging"
        const val OUTLOOK = "com.microsoft.office.outlook"
        const val TELEGRAM = "org.telegram.messenger"
        const val WHATSAPP = "com.whatsapp"
    }

    /** M51'deki ayarlara benzer: Mesajlar ve İşCep öncelikli (Önemli), alışveriş ve sosyal filtre açık. */
    private val m51 = RuleSettings(
        shoppingFilter = true, socialFilter = true,
        priorityApps = mapOf(MESAJLAR to Category.ONEMLI, "com.pozitron.iscep" to Category.ONEMLI),
        userName = "Süleyman",
    )

    private fun n(
        pkg: String, app: String, title: String?, text: String, category: String? = null,
        group: Boolean? = null, sender: String? = null,
        channelId: String? = null, shortcutId: String? = null, reply: Boolean? = null, appCategory: Int = -1,
    ) = NotificationFacts(
        pkg, app, title, text, null, category, false, false, false, false, 0, group, sender,
        channelId = channelId, shortcutId = shortcutId, hasReplyAction = reply, appCategory = appCategory,
    )

    private fun sms(sender: String, text: String) =
        n(MESAJLAR, "Mesajlar", "⁨$sender⁩", text, "msg", group = false, sender = "⁨$sender⁩")

    private fun cat(f: NotificationFacts, s: RuleSettings = m51) = engine.classify(f, s).also { println("${it.category.label.padEnd(10)} [${it.rule}] ${it.reason}") }

    // --- B-1, B-3: güvenlik ve kumar öncelikli uygulamadan önce ---

    @Test
    fun babamGazinoSmsiSilinir() {
        val d = cat(sms("Babam", "Kazanmak için hemen gazino.com adresine gir"))
        assertEquals(Category.SILINENLER, d.category)
        assertEquals("kumar", d.rule)
    }

    @Test
    fun muzikliGazinoKumarDegil() {
        assertNull(rules.gamblingMatch("mesajlar | ayse | bu aksam muzikli gazinoya gidiyoruz, sen de gel"))
        assertEquals(null, rules.gamblingMatch("haber | | eski istanbul gazinolari ve sanatcilari"))
    }

    @Test
    fun internetSifresiKritik() {
        val d = cat(sms("ISS", "482913 numarali modem sifrenizdir, baskasina vermeyiniz."))
        assertEquals(Category.KRITIK, d.category)
        assertEquals("guvenlik", d.rule)
    }

    // --- B-2: öncelikli uygulama kampanyayı geçirmez ---

    @Test
    fun oncelikliUygulamadaKampanyaOnemsiz() {
        val iscep = n("com.pozitron.iscep", "İşCep", null, "Otomatik ödeme talimatı ver, puan kazan")
        assertEquals(Category.ONEMSIZ, cat(iscep).category)
        val money = sms("MARKET", "Bu haftaki firsatlar seni bekliyor, hemen alisverise gel.")
        assertEquals(Category.ONEMSIZ, cat(money).category)
        val yk = sms("BANKA", "Kartinla yaptigin alisveriste indirim kazandin!")
        assertEquals(Category.ONEMSIZ, cat(yk).category)
    }

    @Test
    fun oncelikliUygulamaDegismeyenler() {
        // Y.T.U rezervasyon SMS'i (kurum, kampanya değil) Önemli kalır.
        val ytu = sms("Y.T.U", "Sn. AD SOYAD Yarın 10:00 - 12:00, B BLOK çalışma salonu, 3. sandalyede rezervasyonunuz gerçekleşmiştir. QR kodunuz: https://ktprezervasyon.yildiz.edu.tr/url/?u=TEST")
        assertEquals(Category.ONEMLI, cat(ytu).category)
        // Kişiden gelen SMS'te "kazan" geçse de kampanya sayılmaz.
        val babam = sms("Babam", "Maçı kazandık mı, haberin var mı?")
        assertEquals(Category.ONEMLI, cat(babam).category)
    }

    // --- F: YTÜ rezervasyonu ---

    @Test
    fun ytuRezervasyonuOnemli() {
        // Rezervasyon SMS'i biçiminde sahte metin. Öncelikli uygulama olmasa da, Panelde bırak seçilse de Önemli.
        val ytu = sms(
            "Y.T.U",
            "Sn. AD SOYAD\n\nYarın 10:00 - 12:00, B BLOK çalışma salonu, 3. sandalyede  " +
                "rezervasyonunuz gerçekleşmiştir.\n\nQR kodunuz:\nhttps://ktprezervasyon.yildiz.edu.tr/url/?u=TEST",
        )
        for (s in listOf(m51, RuleSettings(), m51.copy(priorityApps = emptyMap(), panelApps = setOf(MESAJLAR)))) {
            val d = cat(ytu, s)
            assertEquals(Category.ONEMLI, d.category)
            assertEquals("rezervasyon", d.rule)
        }
        // Gönderen YTÜ + "rezervasyon"; yalnız alan adı; başka uygulamada rezervasyon + iptal/hatırlatma/giriş/QR.
        assertEquals("rezervasyon", cat(sms("YTU", "Yarınki rezervasyonunuz onaylandı.")).rule)
        assertEquals("rezervasyon", cat(n(OUTLOOK, "Outlook", "Kütüphane", "Ayrıntılar: https://kutuphane.yildiz.edu.tr/a", "email")).rule)
        assertEquals("rezervasyon", cat(n("com.ornek.salon", "Salon", "Hatırlatma", "Rezervasyonunuz 1 saat sonra başlıyor, girişte QR kodunu okutun.")).rule)
        assertEquals("rezervasyon", cat(n("com.ornek.salon", "Salon", null, "Rezervasyonunuz iptal edildi.")).rule)
        // Yalnız "rezervasyon" (başka işaret yok) bu kurala girmez; kumar ve güvenlik önde.
        assertNotEquals("rezervasyon", cat(n("com.ornek.otel", "Otel", "Fırsat", "Yaz rezervasyonlarında %20 indirim")).rule)
        assertEquals("kumar", cat(n("com.ornek.salon", "Salon", null, "Rezervasyon iptal oldu, canlı bahiste yüksek oran seni bekliyor")).rule)
        assertEquals("guvenlik", cat(sms("Y.T.U", "Rezervasyon giriş kodunuz: 482913")).rule)
    }

    // --- B-4: toplu kutlama ---

    @Test
    fun topluKutlamaOnemsiz() {
        val d = cat(sms("BELEDIYE", "Eğitim emekçilerimizin özel gününü kutluyorum. Belediye Başkanlığı"))
        assertEquals(Category.ONEMSIZ, d.category)
        assertEquals("toplu_kutlama", d.rule)
        // Kişiden gelen kutlama birebir sohbet kalır.
        assertEquals(Category.ONEMLI, cat(sms("Annem", "Öğretmenler Günün kutlu olsun kuzum")).category)
    }

    // --- A-1: haber, hava, kütüphane artık Önemli değil ---

    @Test
    fun haberHavaKutuphaneOnemliDegil() {
        val bundle = n("com.dwarfplanet.bundle", "Bundle", "Son Dakika", "Şehirde yeni metro hattı açıldı")
        assertEquals("haber", cat(bundle).rule)
        assertEquals(Category.ONEMSIZ, cat(bundle).category)
        val news = n("com.ornek.haber", "Haber", "Gündem", "Yeni gelişme", appCategory = Rules.CATEGORY_NEWS)
        assertEquals("haber", cat(news).rule)
        val hava = n("com.sec.android.daemonapp", "Hava", "12° Şehir merkezi", "En yüksek 12°, en düşük 4°")
        assertNotEquals(Category.ONEMLI, cat(hava).category)
        val kutuphane = n("com.yordam.cep.kutuphanem", "Cep Kütüphanem", "Eğitimcilerimizin günü 🌸",
            "Değerli eğitimcilerimizin gününü kutlarız")
        assertNotEquals(Category.ONEMLI, cat(kutuphane).category)
    }

    // --- C-4: LinkedIn e-postaları ---

    @Test
    fun linkedinEpostalariOnemsiz() {
        for (from in listOf("LinkedIn İş İlanı Uyarıları", "LinkedIn", "Kariyer via LinkedIn")) {
            val d = cat(n(OUTLOOK, "Outlook", from, "Bir teknoloji şirketi ağ güvenliği uzmanı arıyor", "email"))
            assertEquals(from, Category.ONEMSIZ, d.category)
            assertEquals("eposta_linkedin", d.rule)
        }
        val obs = n(OUTLOOK, "Outlook", "Öğrenci Bilgi Sistemi",
            "Ders duyurusu: deney raporu teslim tarihi güncellendi.", "email")
        assertEquals(Category.ONEMLI, cat(obs).category)
    }

    @Test
    fun mulakatEpostasiOnemli() {
        // Gerçek kullanımda bir şirketin mülakat maili Normal kalmıştı (model kampanya %44).
        val mail = n(OUTLOOK, "Outlook", "İnsan Kaynakları",
            "Genç yetenek programı\nSevgili aday, video mülakat aşamasına davet edildin.", "email")
        val d = cat(mail)
        assertEquals(Category.ONEMLI, d.category)
        assertEquals("eposta_mulakat", d.rule)
        for (t in listOf("Görüşme davetiniz: 28 Kasım 14:00", "Interview invitation", "Başvurunuz değerlendirme sürecindedir")) {
            assertEquals(t, "eposta_mulakat", cat(n(OUTLOOK, "Outlook", "İK", t, "email")).rule)
        }
        // Yalnız e-posta uygulamalarında; SMS ya da başka uygulama bu kurala girmez.
        assertNotEquals("eposta_mulakat", cat(n("com.ornek.app", "Uygulama", "İK", "Video mülakat daveti")).rule)
    }

    // --- C-2: filtre kapsamı ---

    @Test
    fun egitimDuyurusuSilinmez() {
        val biz = n("com.gsb.gsbbiz", "Biz", "Yeni kurs kayıtları açıldı",
            "Gençlik merkezlerinde kurs kayıtları başladı")
        assertNotEquals(Category.SILINENLER, cat(biz).category)
    }

    // --- C-6: Telegram kanalı ---

    @Test
    fun telegramKanaliBirebirSayilmaz() {
        val text = "KASIM İNDİRİMİNDE SON GÜN! Bütün eğitimlerde %50 indirim. Fırsatı kaçırma!"
        for (sign in listOf(
            n(TELEGRAM, "Telegram", "Kurs Etkinlik Kanalı", text, "msg", group = false, sender = "Kurs Etkinlik Kanalı", reply = false),
            n(TELEGRAM, "Telegram", "Kurs Etkinlik Kanalı", text, "msg", group = false, sender = "Kurs Etkinlik Kanalı", shortcutId = "ndid_-1001234", reply = true),
            n(TELEGRAM, "Telegram", "Kurs Etkinlik Kanalı", text, "msg", group = false, sender = "Kurs Etkinlik Kanalı", channelId = "0channel_channels_b9fad_59", reply = true),
        )) {
            val d = cat(sign)
            assertEquals("model", d.rule)
            assertEquals(Category.ONEMSIZ, d.category)
        }
        // Birebir Telegram sohbeti: yanıt eylemi var, kısayol pozitif diyalog.
        val dm = n(TELEGRAM, "Telegram", "Ayşe", "Yarın derse geliyor musun?", "msg", group = false, sender = "Ayşe",
            channelId = "Other3", shortcutId = "ndid_123456", reply = true)
        assertEquals("birebir_sohbet", cat(dm).rule)
    }

    // --- C-5: grup anahtar kelimeleri ---

    @Test
    fun grupAnahtarKelimeleri() {
        fun grup(text: String, s: RuleSettings = m51) =
            cat(n(WHATSAPP, "WhatsApp", "Aile: Babam", text, "msg", group = true, sender = "Babam"), s)
        assertEquals(Category.ONEMLI, grup("Yarın 10'da toplantı var").category)
        assertEquals(Category.ONEMLI, grup("Yarın sınav ertelendi").category)
        assertEquals(Category.ONEMLI, grup("ACİL bakar mısınız").category)
        assertEquals(Category.ONEMLI, grup("Ödevi kim teslim etti?").category)
        assertEquals(Category.ONEMLI, grup("@Süleyman sen gelebilir misin").category)
        assertEquals(Category.ONEMLI, grup("Süleyman'ın notları bende").category)
        // "toplam" yanlış alarm vermez; ad boşsa aranmaz.
        assertEquals(Category.NORMAL, grup("Yarın akşam toplam kaç kişi geliyor").category)
        assertEquals(Category.NORMAL, grup("Süleyman gelecek mi", m51.copy(userName = "")).category)
        assertEquals(Category.NORMAL, grup("Açılış saat kaçta").category)
    }

    // --- C-1: Panelde bırak ---

    @Test
    fun paneldeBirak() {
        val s = m51.copy(panelApps = setOf("com.sec.android.app.shealth"))
        val health = n("com.sec.android.app.shealth", "Samsung Health", "Günlük etkinlik hedefine ulaşıldı", "Tebrik ederiz!")
        val d = cat(health, s)
        assertEquals(Category.DOKUNMA, d.category)
        assertEquals("panelde_birak", d.rule)
        assertEquals(false, d.category.removeFromPanel)
        // Güvenlik ve kumar kuralları önde.
        assertEquals(Category.KRITIK, cat(n("com.sec.android.app.shealth", "Samsung Health", null, "Doğrulama kodunuz: 482913"), s).category)
        assertEquals(Category.SILINENLER, cat(n("com.sec.android.app.shealth", "Samsung Health", null, "Canlı bahiste yüksek oran!"), s).category)
    }

    // --- Değişmemesi gerekenler ---

    @Test
    fun birebirAileMesajlariDegismez() {
        for ((who, text) in listOf("Annem" to "Yeni telefonun hayırlı olsun", "Babam" to "Orada hava nasıl, yağmur devam ediyor mu")) {
            val d = cat(n(WHATSAPP, "WhatsApp", who, text, "msg", group = false, sender = who))
            assertEquals(Category.ONEMLI, d.category)
            assertEquals("birebir_sohbet", d.rule)
        }
    }
}
