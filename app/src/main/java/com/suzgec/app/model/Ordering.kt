package com.suzgec.app.model

import com.suzgec.app.data.NotificationRecord
import com.suzgec.app.data.OrderPair
import com.suzgec.app.rules.Category
import java.util.Locale

/**
 * Kutuda sürükleyerek sıralama (CLAUDE.md §7, 25 Kasım). YALNIZ aynı kategori içindeki sırayı etkiler;
 * kategori kararına, ses/titreşime ve δ kişiselleştirmesine (Personalization) girmez — RuleEngine bu
 * sınıfı hiç görmez.
 *
 * Kategori içi yerleşim (büyük değer üstte, eşitlikte yeni üstte):
 * - elle yerleştirilen kayıt: [NotificationRecord.manualRank]
 * - diğerleri: önem puanı (model puanı; kural kararında kategorinin ağırlığı) + sıra puanı
 *
 * Sıra puanı (uygulama + gönderici karması düzeyinde), "A, B'nin üstünde" çiftlerinden aynı sayaç formülüyle:
 *   δ_sıra = η · (n_yukarı − n_aşağı) / (n_yukarı + n_aşağı + k),  k = 2, η = 0,5
 * Hiç çift ve elle sıra yoksa sıra, sürükleme öncesiyle birebir aynıdır.
 */
object Ordering {
    const val K = 2.0
    const val ETA = 0.5

    /** Ardışık sıra değerleri arasındaki en küçük fark. */
    private const val EPS = 1e-3

    /** Listenin en üstüne / en altına bırakılan kaydın komşusundan uzaklığı. */
    private const val END_GAP = 0.1

    val BOX_ORDER = listOf(Category.KRITIK, Category.ONEMLI, Category.NORMAL, Category.ONEMSIZ)

    data class Key(val app: String, val senderHash: String)

    fun key(r: NotificationRecord) = Key(r.appLabel, r.senderHash)

    fun delta(up: Int, down: Int): Double = ETA * (up - down) / (up + down + K)

    /** Çiftlerden her anahtarın sıra puanı. Aynı anahtarlı çiftler (aynı uygulama ve gönderici) sayılmaz. */
    fun deltas(pairs: List<OrderPair>): Map<Key, Double> {
        val up = HashMap<Key, Int>()
        val down = HashMap<Key, Int>()
        for (p in pairs) {
            val a = Key(p.aboveApp, p.aboveSender)
            val b = Key(p.belowApp, p.belowSender)
            if (a == b) continue
            up[a] = (up[a] ?: 0) + 1
            down[b] = (down[b] ?: 0) + 1
        }
        return (up.keys + down.keys).associateWith { delta(up[it] ?: 0, down[it] ?: 0) }.filterValues { it != 0.0 }
    }

    private fun cat(r: NotificationRecord) = runCatching { Category.valueOf(r.suzgecCategory ?: "") }.getOrNull()

    /** Önem puanı: model puanı; kural kararının model puanı yok, kategorinin tam ağırlığı (kural = emin karar). */
    fun importance(r: NotificationRecord): Double = r.score ?: cat(r)?.let { CategoryMapper.weight(it).toDouble() } ?: 0.0

    /** Kategori içi sıra değeri (büyük üstte). */
    fun value(r: NotificationRecord, deltas: Map<Key, Double>): Double =
        r.manualRank ?: (importance(r) + (deltas[key(r)] ?: 0.0))

    /** Kutu sırası: kategori, sonra sıra değeri, sonra zaman (yeni üstte). Kategori grupları değişmez. */
    fun sortBox(list: List<NotificationRecord>, deltas: Map<Key, Double> = emptyMap()): List<NotificationRecord> =
        list.sortedWith(
            compareBy<NotificationRecord> { BOX_ORDER.indexOf(cat(it)).let { i -> if (i < 0) 99 else i } }
                .thenByDescending { value(it, deltas) }
                .thenByDescending { it.postTime }
        )

    /** "Neden burada?" satırına eklenen sıra notu; sıra puanı yoksa null. */
    fun note(r: NotificationRecord, deltas: Map<Key, Double>): String? {
        val d = deltas[key(r)] ?: return null
        return "sıra: senin sıralaman " + (if (d > 0) "+" else "") + String.format(Locale.forLanguageTag("tr"), "%.2f", d)
    }

    data class Result(val ranks: Map<Long, Double>, val pairs: List<OrderPair>)

    /**
     * Bir kategori listesinde [from] konumundaki kayıt [to] konumuna sürüklendi.
     * - Çiftler: kaydın üstünden geçtiği her kayıtla "üstteki, alttaki" (yukarı taşındıysa kayıt üstte).
     * - Sıra değerleri: yeni sıra puanlarıyla hesaplanır, taşınan kayıt komşularının ortasına konur ve
     *   liste görünen sırayla kesin azalan olacak biçimde düzeltilir. Listedeki bütün kayıtlar elle
     *   yerleştirilmiş sayılır (göreli sıraları korunur); sonra gelen bildirimler sıra puanına göre yerleşir.
     * @param list ekranda görünen sıra (sürüklemeden önce), tek kategori.
     * @param pairsBefore şimdiye kadarki bütün çiftler.
     */
    fun reorder(list: List<NotificationRecord>, from: Int, to: Int, pairsBefore: List<OrderPair>, now: Long): Result? {
        if (from == to || from !in list.indices || to !in list.indices) return null
        val moved = list[from]
        val passed = if (to < from) list.subList(to, from) else list.subList(from + 1, to + 1)
        val category = moved.suzgecCategory.orEmpty()
        val pairs = passed.map { other ->
            val (above, below) = if (to < from) moved to other else other to moved
            OrderPair(
                time = now, category = category, aboveId = above.id, belowId = below.id,
                aboveApp = above.appLabel, aboveSender = above.senderHash,
                belowApp = below.appLabel, belowSender = below.senderHash,
            )
        }
        val deltasAfter = deltas(pairsBefore + pairs)
        val order = list.toMutableList().apply { add(to, removeAt(from)) }
        val w = DoubleArray(order.size) { value(order[it], deltasAfter) }
        w[to] = when {
            order.size == 1 -> w[to]
            to == 0 -> w[1] + END_GAP
            to == order.lastIndex -> w[to - 1] - END_GAP
            else -> (w[to - 1] + w[to + 1]) / 2
        }
        for (i in 1 until w.size) if (w[i] > w[i - 1] - EPS) w[i] = w[i - 1] - EPS
        return Result(order.indices.associate { order[it].id to w[it] }, pairs)
    }
}
