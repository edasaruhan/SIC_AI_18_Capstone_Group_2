package com.suzgec.app.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RuleEngineTest {

    private val rules = Rules.parse(File("src/main/assets/rules.json").readText())
    private val engine = RuleEngine(rules)

    private fun facts(
        app: String = "Uygulama",
        title: String? = null,
        text: String? = null,
        pkg: String = "com.ornek.app",
        category: String? = "msg",
        ongoing: Boolean = false,
        fgs: Boolean = false,
        summary: Boolean = false,
        media: Boolean = false,
        timeout: Long = 0,
        group: Boolean? = null,
    ) = NotificationFacts(pkg, app, title, text, null, category, ongoing, fgs, summary, media, timeout, group)

    private fun category(f: NotificationFacts): Category {
        val pre = engine.preFilter(f)
        if (pre is PreFilter.Untouched) return Category.DOKUNMA
        return engine.classify(f).category
    }

    // --- 1. adım: dokunma ---

    @Test
    fun dokunulmazlar() {
        assertTrue(engine.preFilter(facts(ongoing = true)) is PreFilter.Untouched)
        assertTrue(engine.preFilter(facts(fgs = true)) is PreFilter.Untouched)
        assertTrue(engine.preFilter(facts(summary = true)) is PreFilter.Untouched)
        assertTrue(engine.preFilter(facts(media = true)) is PreFilter.Untouched)
        for (c in listOf("call", "alarm", "navigation", "progress", "transport", "service", "sys", "stopwatch")) {
            assertTrue(c, engine.preFilter(facts(category = c)) is PreFilter.Untouched)
        }
        for (p in listOf("android", "com.android.systemui", "com.samsung.android.incallui",
            "com.samsung.android.dialer", "com.sec.android.app.clockpackage", "com.android.cellbroadcastreceiver")) {
            assertTrue(p, engine.preFilter(facts(pkg = p)) is PreFilter.Untouched)
        }
        // Kalıcı kumar reklamı bile dokunulmazdır: 1. adım her şeyden önce gelir.
        assertTrue(engine.preFilter(facts(text = "casino", ongoing = true)) is PreFilter.Untouched)
    }

    // --- 2. adım: 5 sn ---

    @Test
    fun besSaniyeKurali() {
        assertEquals(PreFilter.Wait(5000, "kısa ömürlü (timeoutAfter 3000 ms)"), engine.preFilter(facts(timeout = 3000)))
        assertTrue(engine.preFilter(facts(category = null)) is PreFilter.Wait)
        assertEquals(PreFilter.Proceed, engine.preFilter(facts(category = "msg", timeout = 60_000)))
        assertEquals(PreFilter.Proceed, engine.preFilter(facts(category = "promo")))
    }

    // --- 3. adım: öncelikli uygulama ---

    @Test
    fun oncelikliUygulama() {
        val s = RuleSettings(priorityApps = mapOf("com.ornek.app" to Category.KRITIK))
        val d = engine.classify(facts(text = "Yarın görüşelim", group = true), s)
        assertEquals(Category.KRITIK, d.category)
        assertEquals("oncelikli_uygulama", d.rule)
        // 25 Kasım: kumar sözlüğü öncelikli uygulamadan önce gelir.
        assertEquals("kumar", engine.classify(facts(text = "casino bonusu", group = true), s).rule)
    }

    // --- 4. adım: güvenlik ---

    @Test
    fun guvenlikKuraliYakalar() {
        val pozitif = listOf(
            "Hesabınızdan 1.250,00 TL para çekildi.",
            "Doğrulama kodunuz: 482913. Kimseyle paylaşmayın.",
            "Hesabınıza yeni bir cihazdan giriş yapıldı.",
            "Şifreniz değiştirildi. Siz yapmadıysanız bizi arayın.",
            "Kartınızla 349,90 TL harcama yapıldı.",
            "123456 onay kodunuzdur",
            "Kredi kartı ekstre borcu: 4.210 TL, son ödeme 5 Aralık",
        )
        for (t in pozitif) {
            val d = engine.classify(facts(app = "Banka", text = t))
            assertEquals(t, Category.KRITIK, d.category)
            assertEquals(t, ContentType.GUVENLIK, d.contentType)
        }
    }

    @Test
    fun guvenlikKuraliKampanyayiYakalamaz() {
        val negatif = listOf(
            "Kredi kartınıza özel taksit kampanyası! Hemen başvur.",
            "Doğrulama kodu ile giriş yap, 100 TL hediye kazan",
            "Akşam yemeğe geliyor musun?",
        )
        for (t in negatif) {
            assertTrue(t, engine.classify(facts(text = t)).rule != "guvenlik")
        }
    }

    @Test
    fun gizliIcerikKritik() {
        val d = engine.classify(facts(app = "Mesajlar", text = "Sensitive notification content hidden"))
        assertEquals(Category.KRITIK, d.category)
        assertEquals("gizli_icerik", d.rule)
    }

    // --- 5. adım: kumar ---

    @Test
    fun kumarSozluguYakalar() {
        val pozitif = listOf(
            "Çevrimsiz %100 deneme bonusu! Hemen yatır, kazan: bit.ly/xx",
            "Canlı bahiste yüksek oran! Maç başladı, kuponunu yap.",
            "CA\$INO masalarında anında çekim",
            "B0NUS yatırana 500 TL",
            "jojobet yeni giriş adresi burada",
            "Bets10 güncel adres",
            "250 free spin seni bekliyor",
            "Bugün 50 bedava spin, hemen kazan",
            "Hoşgeldin bonusu tinyurl.com/abc",
            "Süper oran: maç kuponu hazır",
            "Bugünün kombine kuponu 15.40 oran, kaçırma!",
            "Yeni üyelere 300 freebet hediye",
        )
        for (t in pozitif) {
            val d = engine.classify(facts(app = "Mesajlar", text = t))
            assertEquals(t, Category.SILINENLER, d.category)
            assertEquals(t, ContentType.KUMAR, d.contentType)
        }
    }

    @Test
    fun kumarSozluguYanlisAlarmVermez() {
        val negatif = listOf(
            "Bonus kartınla market alışverişine 50 TL bonus kazan!",
            "Maaşınız hesabınıza yatırıldı",
            "Faiz oranı %3,5'e düştü, hemen başvur",
            "Akşam sohbet edelim mi?",
            "Nöbet çizelgesi güncellendi",
            "Alfabet sırasına göre listelendi",
            "Toplantı slotu 14:00'e alındı",
            "Maçı izledin mi? Oran oyuncusu süperdi",
        )
        for (t in negatif) {
            assertNull(t, rules.gamblingMatch(Preprocess.forGambling("Mesajlar", "", t)))
        }
    }

    @Test
    fun kumarFiltresiKapaliysaSilmez() {
        val d = engine.classify(facts(text = "casino bonusu"), RuleSettings(gamblingFilter = false))
        assertTrue(d.category != Category.SILINENLER)
    }

    @Test
    fun guvenlikKumardanOnceGelir() {
        // Etiket çakışmasında 1 > 8.
        val d = engine.classify(facts(text = "Bahis sitesi için doğrulama kodunuz: 445566"))
        assertEquals(Category.KRITIK, d.category)
    }

    // --- 6a. MessagingStyle ---

    @Test
    fun sohbetTuru() {
        assertEquals(Category.ONEMLI, engine.classify(facts(text = "Akşam geliyor musun?", group = false)).category)
        assertEquals(Category.NORMAL, engine.classify(facts(text = "Yarın forma var mı?", group = true)).category)
        assertEquals(Category.MODEL_BEKLIYOR, engine.classify(facts(text = "Siparişin yolda")).category)
    }

    // --- Demo senaryosu (docs/demo_senaryosu.md), kuralla karar verilenler ---

    @Test
    fun demoSenaryosuKuralSatirlari() {
        val beklenen = listOf(
            facts(app = "Ziraat Mobil", title = "Hesap hareketi", text = "Hesabınızdan 1.250,00 TL para çekildi.", category = null) to Category.KRITIK,
            facts(app = "Mesajlar", title = "ZIRAAT", text = "Doğrulama kodunuz: 482913. Kimseyle paylaşmayın.", category = null) to Category.KRITIK,
            facts(app = "WhatsApp", title = "Annem", text = "Akşam yemeğe geliyor musun?", group = false) to Category.ONEMLI,
            facts(app = "WhatsApp", title = "Sınıf 12-B", text = "Yarın forma giyiyor muyuz?", group = true) to Category.NORMAL,
            facts(app = "Mesajlar", text = "Çevrimsiz %100 deneme bonusu! Hemen yatır, kazan: bit.ly/xx", category = null) to Category.SILINENLER,
            facts(app = "Müzik", title = "Örnek Şarkı", media = true, category = null) to Category.DOKUNMA,
            facts(app = "Haritalar", title = "200 m sonra sağa dönün", ongoing = true, category = "navigation") to Category.DOKUNMA,
            facts(app = "Dosyalar", ongoing = true, category = "progress") to Category.DOKUNMA,
            facts(app = "Mesajlar", text = "Canlı bahiste yüksek oran! Maç başladı, kuponunu yap.", category = null) to Category.SILINENLER,
            facts(app = "Bonus", title = "Bonus", text = "Bonus kartınla market alışverişine 50 TL bonus kazan!", category = null) to Category.MODEL_BEKLIYOR,
            facts(app = "Garanti BBVA", title = "Güvenlik", text = "Hesabınıza yeni bir cihazdan giriş yapıldı.", category = null) to Category.KRITIK,
            facts(app = "WhatsApp", title = "2 yeni mesaj", summary = true) to Category.DOKUNMA,
        )
        for ((f, c) in beklenen) assertEquals("${f.appLabel} ${f.title} ${f.text}", c, category(f))
    }

    @Test
    fun kuralKatmaniHizli() {
        val f = facts(app = "Mesajlar", text = "Kargonuz yola çıktı, takip için tıklayın https://kargo.example.com/abc")
        repeat(200) { engine.classify(f) } // ısınma
        val t0 = System.nanoTime()
        repeat(1000) { engine.classify(f) }
        val ms = (System.nanoTime() - t0) / 1e6 / 1000
        assertTrue("bildirim başına $ms ms", ms < 5.0)
    }
}
