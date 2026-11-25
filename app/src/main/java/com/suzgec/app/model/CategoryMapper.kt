package com.suzgec.app.model

import com.suzgec.app.rules.Category
import com.suzgec.app.rules.ContentType
import com.suzgec.app.rules.Decision
import com.suzgec.app.rules.RuleSettings
import java.util.Locale

/**
 * Model olasılıklarından kategori (CLAUDE.md §7, v1):
 * - Silinenler puanla değil, içerik türü + filtreyle: en olası tür (olasılığı ≥ 0,5) kumar (8) ve
 *   kumar filtresi açık, kampanya (7) ve alışveriş filtresi açık ve uygulama alışveriş uygulaması, ya da
 *   sosyal etkileşim (9) ve sosyal medya filtresi açık ve uygulama sosyal medya uygulaması ise (rules.json).
 *   Filtre dışında kalan kampanya/sosyal içerik (≥ 0,5) Önemsiz'e düşer (kişisel düzeltme yine eklenir).
 * - Diğerleri: beklenen önem puanı = Σ p(tür) × ağırlık(varsayılan kategori);
 *   Kritik=4, Önemli=3, Normal=2, Önemsiz=1. Puan en yakın kategoriye yuvarlanır.
 * - Emin değilse (en yüksek olasılık < 0,5) bir üst kategoriye alınır, ama bu yükseltme en fazla Normal'e
 *   çıkarır (ses/titreşim vermesin). Tek istisna: ilk iki tahminden biri güvenlik/para (1) ve olasılığı ≥ 0,30 ise
 *   Önemli (25 Kasım: kişisel mesaj istisnası kaldırıldı; haber/hava bildirimlerini Önemli yapıyordu). Kural katmanı ve öncelikli uygulama kararları bundan etkilenmez.
 * - Kişisel düzeltme (Personalization.delta) puana eklenir.
 */
object CategoryMapper {

    const val UNSURE_BELOW = 0.5
    /** Belirsizlikte Önemli'ye çıkarabilecek türün (1) ilk iki tahminde en az olasılığı. */
    const val UNSURE_IMPORTANT_MIN = 0.30
    /** Bir filtrenin (kumar, alışveriş, sosyal) silmesi için modelin en az bu kadar emin olması gerekir. */
    const val FILTER_MIN = 0.5

    /** İçerik türünün varsayılan kategorisi (§6 tablosu); kumar puanda Önemsiz sayılır. */
    fun defaultCategory(type: Int): Category = when (type) {
        1 -> Category.KRITIK
        2, 4, 5 -> Category.ONEMLI
        3, 6 -> Category.NORMAL
        else -> Category.ONEMSIZ // 7, 8, 9, 10, 11
    }

    fun weight(c: Category): Int = when (c) {
        Category.KRITIK -> 4
        Category.ONEMLI -> 3
        Category.NORMAL -> 2
        else -> 1
    }

    fun fromScore(score: Double): Category = when {
        score >= 3.5 -> Category.KRITIK
        score >= 2.5 -> Category.ONEMLI
        score >= 1.5 -> Category.NORMAL
        else -> Category.ONEMSIZ
    }

    /**
     * Belirsizlik yükseltmesi (§7): ilk iki tahminde tür 1 olasılığı ≥ 0,30 ise en az Önemli;
     * değilse bir üst kategori ama en fazla Normal (puan zaten Önemli/Kritik diyorsa o kalır).
     *  (kategori, "Neden" açıklaması)
     */
    fun unsureRaise(byScore: Category, p: Prediction): Pair<Category, String> {
        val top2 = p.proba.indices.sortedByDescending { p.proba[it] }.take(2)
        val urgent = top2.firstOrNull { p.classes[it] in URGENT_TYPES && p.proba[it] >= UNSURE_IMPORTANT_MIN }
        if (urgent != null) {
            val cat = if (weight(byScore) >= weight(Category.ONEMLI)) byScore else Category.ONEMLI
            return cat to "emin değil, ${typeLabel(p.classes[urgent])} ${pct(p.proba[urgent])} → ${cat.label}"
        }
        val cat = if (weight(byScore) >= weight(Category.NORMAL)) byScore else Category.NORMAL
        return cat to (if (cat == byScore) "emin değil (yükseltme en fazla Normal)" else "emin değil → ${cat.label}")
    }

    /** Belirsizlikte Önemli'ye çıkabilen tür: yalnız güvenlik/para (kişisel mesajı birebir sohbet kuralı yakalar). */
    private val URGENT_TYPES = setOf(1)

    /** Önemsiz'in puan aralığının üstü: filtre dışı kampanya/sosyal içerik bu puanı geçemez. */
    private const val ONEMSIZ_MAX = 1.49

    /** Modelin tür adları, "Neden:" satırı için okunur biçimde. */
    fun typeLabel(type: Int): String = when (type) {
        1 -> "güvenlik/para"
        2 -> "kişisel mesaj"
        3 -> "grup mesajı"
        4 -> "okul/iş/resmi"
        5 -> "zaman hassas"
        6 -> "işlem bilgisi"
        7 -> "kampanya"
        8 -> "kumar"
        9 -> "sosyal etkileşim"
        10 -> "geri çağırma/oyun"
        11 -> "sistem/diğer"
        else -> "tür $type"
    }

    private fun pct(p: Double) = "%" + String.format(Locale.ROOT, "%.0f", p * 100)
    private fun num(x: Double) = String.format(Locale.forLanguageTag("tr"), "%.2f", x)

    /** "Neden burada?" satırındaki kişisel düzeltme parçası, ör. "senin düzeltmen +0,50". */
    fun personalText(delta: Double): String = "senin düzeltmen " + (if (delta > 0) "+" else "") + num(delta)

    /**
     * @param gamblingException rules.json `kumar.model_istisna` eşleşmesi: model "kumar" dese bile
     *   silme, puanla kategorile (ör. "bonus kart" kampanyası).
     * @param personalDelta kişiselleştirme v1 düzeltmesi (kategori basamağı), puana eklenir.
     * @param shoppingApp uygulama alışveriş listesinde mi (alışveriş filtresi yalnız bunlara uygulanır).
     * @param socialApp uygulama sosyal medya listesinde mi (sosyal medya filtresi yalnız bunlara).
     */
    fun decide(
        p: Prediction,
        settings: RuleSettings,
        note: String? = null,
        gamblingException: String? = null,
        personalDelta: Double = 0.0,
        shoppingApp: Boolean = true,
        socialApp: Boolean = true,
    ): Decision {
        val best = p.bestType
        val bestP = p.bestProba
        var head = "Model: ${typeLabel(best)}, ${pct(bestP)}" + (note?.let { " ($it)" } ?: "")

        // Silinenler: puanla değil, içerik türü + kullanıcı filtresiyle (§7).
        if (bestP >= FILTER_MIN) {
            when {
                best == ContentType.KUMAR && settings.gamblingFilter -> {
                    if (gamblingException == null) {
                        return Decision(Category.SILINENLER, best, "$head → kumar filtresi", "model")
                    }
                    head += "; \"$gamblingException\" kumar sayılmadı"
                }
                best == ContentType.KAMPANYA && settings.shoppingFilter && shoppingApp ->
                    return Decision(Category.SILINENLER, best, "$head → alışveriş filtresi", "model")
                best == ContentType.SOSYAL && settings.socialFilter && socialApp ->
                    return Decision(Category.SILINENLER, best, "$head → sosyal medya filtresi", "model")
            }
        }

        var score = 0.0
        for (i in p.classes.indices) score += p.proba[i] * weight(defaultCategory(p.classes[i]))
        // Filtre dışı (ya da filtre kapalı) emin kampanya/sosyal içerik: Önemsiz'in üstüne çıkmaz.
        val capped = bestP >= FILTER_MIN && (best == ContentType.KAMPANYA || best == ContentType.SOSYAL) && score > ONEMSIZ_MAX
        val base = if (capped) ONEMSIZ_MAX else score
        val adjusted = (base + personalDelta).coerceIn(1.0, 4.0)
        val byScore = fromScore(adjusted)
        val unsure = bestP < UNSURE_BELOW
        val raised = if (unsure) unsureRaise(byScore, p) else null
        val cat = raised?.first ?: byScore
        var tail = "puan ${num(score)}" + (if (capped) " → ${typeLabel(best)}, Önemsiz" else "")
        if (personalDelta != 0.0) tail += ", ${personalText(personalDelta)} → ${num(adjusted)}"
        if (raised != null) tail += ", ${raised.second}"
        return Decision(cat, best, "$head; $tail", "model", adjusted)
    }
}
