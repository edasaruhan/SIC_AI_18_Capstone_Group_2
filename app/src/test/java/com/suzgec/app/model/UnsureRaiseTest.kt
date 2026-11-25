package com.suzgec.app.model

import com.suzgec.app.rules.Category
import com.suzgec.app.rules.NotificationFacts
import com.suzgec.app.rules.RuleEngine
import com.suzgec.app.rules.RuleSettings
import com.suzgec.app.rules.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** §7: belirsizlik yükseltmesi en fazla Normal; tür 1/2 ilk ikide ve ≥ 0,30 ise Önemli. */
class UnsureRaiseTest {

    private val classes = (1..11).toList().toIntArray()

    /** Verilen türlere olasılık, kalanı tür 11'e. */
    private fun pred(vararg tp: Pair<Int, Double>): Prediction {
        val p = DoubleArray(11)
        tp.forEach { (t, v) -> p[t - 1] = v }
        p[10] += 1.0 - p.sum()
        return Prediction(classes, p)
    }

    private fun decide(p: Prediction) = CategoryMapper.decide(p, RuleSettings())

    @Test
    fun kampanyaEminDegilseNormalUstuneCikmaz() {
        // 21:21 Claude bildirimine benzer: kampanya %47, gerisi dağınık → eskiden Önemli + ses
        val d = decide(pred(7 to 0.47, 6 to 0.25, 4 to 0.15))
        assertEquals(Category.NORMAL, d.category)
        assertTrue(d.reason, d.reason.contains("emin değil"))
    }

    @Test
    fun onemsizEminDegilseNormaleCikar() {
        assertEquals(Category.NORMAL, decide(pred(10 to 0.45, 9 to 0.30)).category)
    }

    @Test
    fun kisiselMesajIstisnasiKalkti() {
        // 25 Kasım: haber/hava/kütüphane uygulamaları "kişisel mesaj %30–43" ile Önemli oluyordu → artık en fazla Normal.
        assertEquals(Category.NORMAL, decide(pred(7 to 0.45, 2 to 0.32)).category)
        assertEquals(Category.NORMAL, decide(pred(2 to 0.43, 11 to 0.30)).category)
    }

    @Test
    fun guvenlikIlkIkideVeEnAz30IseOnemli() {
        assertEquals(Category.ONEMLI, decide(pred(6 to 0.40, 1 to 0.31)).category)
    }

    @Test
    fun guvenlik30AltindaysaIstisnaYok() {
        assertEquals(Category.NORMAL, decide(pred(7 to 0.45, 1 to 0.29, 6 to 0.20)).category)
    }

    @Test
    fun guvenlikUcuncuSiradaysaIstisnaYok() {
        // 1 olasılığı ≥ 0,30 ama üçüncü sırada olamaz (ilk ikisi daha yüksek) → bu durumda 0,30'un altında kalır
        assertEquals(Category.NORMAL, decide(pred(7 to 0.36, 6 to 0.33, 1 to 0.31)).category)
    }

    @Test
    fun puanZatenOnemliIseDusurulmez() {
        // okul/iş %48 + zaman hassas %40: puan Önemli → yükseltme yok, düşürme de yok
        assertEquals(Category.ONEMLI, decide(pred(4 to 0.48, 5 to 0.40)).category)
    }

    @Test
    fun eminseDegismez() {
        assertEquals(Category.ONEMSIZ, decide(pred(7 to 0.90)).category)
        assertEquals(Category.KRITIK, decide(pred(1 to 0.95)).category)
    }

    @Test
    fun claude2121BildirimiArtikOnemliDegil() {
        val engine = RuleEngine(
            Rules.parse(File("src/main/assets/rules.json").readText()),
            TextModel.parse(File("src/main/assets/model_tfidf_lr.json").readText()),
        )
        val text = "Rapor taslakları tamamlandı ve paylaşılan klasöre eklendi. " +
            "Biçim aynı kaldı; yalnız tablo genişlikleri değişti…"
        val f = NotificationFacts(
            "com.anthropic.claude", "Claude", "Proje planı", text, text, null,
            false, false, false, false, 0, null, null,
        )
        val d = engine.classify(f)
        println(d.reason)
        assertTrue("${d.category}: ${d.reason}", d.category == Category.NORMAL || d.category == Category.ONEMSIZ)
    }
}
