package com.suzgec.app.stats

import android.content.Context
import com.suzgec.app.data.BoxOpen
import com.suzgec.app.data.NotificationDao
import com.suzgec.app.data.NotificationRecord
import com.suzgec.app.rules.Category
import com.suzgec.app.rules.ContentType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/** Son 7 günün özeti. */
data class WeeklyStats(
    /** Süzgeç'in panelden kaldırdığı bildirimler. */
    val removedBySuzgec: Int,
    /** Sesi kesilen bildirimler: Kritik/Önemli dışında sınıflananlar (Normal, Önemsiz, Silinenler). */
    val silenced: Int,
    /** Sınıflanan tüm bildirimler (Dokunulmaz ve "Model bekliyor" hariç). */
    val classified: Int,
    val deletedGambling: Int,
    val deletedCampaign: Int,
    /** Kutunun teslim saatleri dışında (teslimden sonraki 30 dk dışında) açılma sayısı. */
    val opensOutsideDelivery: Int,
    /** Kategori → sayı (dağılım grafiği). */
    val byCategory: Map<Category, Int>,
) {
    /** Sesli uyarıdaki azalma (başarı ölçütü ≥ %50): sesi kesilen / sınıflanan. */
    val alertReduction: Double get() = if (classified == 0) 0.0 else silenced.toDouble() / classified
}

object Weekly {
    const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    suspend fun compute(dao: NotificationDao, now: Long = System.currentTimeMillis()): WeeklyStats {
        val since = now - WEEK_MS
        val by = dao.countByCategory(since).mapNotNull { c ->
            runCatching { Category.valueOf(c.category) }.getOrNull()?.let { it to c.n }
        }.toMap()
        val quiet = listOf(Category.NORMAL, Category.ONEMSIZ, Category.SILINENLER).sumOf { by[it] ?: 0 }
        val loud = listOf(Category.KRITIK, Category.ONEMLI).sumOf { by[it] ?: 0 }
        return WeeklyStats(
            removedBySuzgec = dao.countCancelledBySuzgec(since),
            silenced = quiet,
            classified = quiet + loud,
            deletedGambling = dao.countDeletedOfType(since, ContentType.KUMAR),
            deletedCampaign = dao.countDeletedOfType(since, ContentType.KAMPANYA),
            opensOutsideDelivery = dao.countOpensOutsideDelivery(since),
            byCategory = by,
        )
    }

    /**
     * Demo için örnek bir hafta (yalnız debug menüsü). Anahtarları "ornek|" ile başlar; yeniden
     * yüklenince eskisi silinir. Görüldü sayılır, teslim özetini etkilemez.
     */
    suspend fun loadSample(dao: NotificationDao, now: Long = System.currentTimeMillis()) {
        dao.deleteSampleRecords()
        dao.deleteSampleOpens()
        val rnd = Random(27)
        // (uygulama, kategori, içerik türü, haftalık adet) — tipik bir öğrenci telefonu
        val mix = listOf(
            Triple("WhatsApp", Category.ONEMLI, 2) to 38,
            Triple("WhatsApp", Category.NORMAL, 3) to 96,
            Triple("Ziraat Mobil", Category.KRITIK, 1) to 6,
            Triple("Mesajlar", Category.KRITIK, 1) to 9,
            Triple("OBS", Category.ONEMLI, 4) to 5,
            Triple("Takvim", Category.ONEMLI, 5) to 8,
            Triple("Trendyol", Category.NORMAL, 6) to 14,
            Triple("Hepsiburada", Category.ONEMSIZ, 7) to 41,
            Triple("Mesajlar", Category.SILINENLER, 8) to 17,
            Triple("Mesajlar", Category.SILINENLER, 7) to 12,
            Triple("Instagram", Category.ONEMSIZ, 9) to 63,
            Triple("Oyun", Category.ONEMSIZ, 10) to 22,
            Triple("Sistem", Category.ONEMSIZ, 11) to 9,
        )
        val records = ArrayList<NotificationRecord>()
        var i = 0
        for ((t, n) in mix) {
            val (app, cat, type) = t
            repeat(n) {
                val time = now - rnd.nextLong(WEEK_MS - 60_000)
                val removed = cat == Category.NORMAL || cat == Category.ONEMSIZ || cat == Category.SILINENLER
                records += NotificationRecord(
                    key = "ornek|${i++}", packageName = "ornek.$type", appLabel = app,
                    title = null, text = null, bigText = null, subText = null, category = null, channelId = null,
                    postTime = time, flags = 0, isOngoing = false, isGroupSummary = false, isGroupConversation = null,
                    hasContentIntent = false, firstSeen = time, lastUpdated = time,
                    removed = removed, removedAt = if (removed) time else null,
                    suzgecCategory = cat.name, contentType = type, reason = "Örnek veri", rule = "ornek",
                    cancelledBySuzgec = removed, seenAt = time,
                )
            }
        }
        dao.insertAll(records)
        // Kutu açılışları: 21 teslimle gelen, 9 teslim dışı (negatif id = örnek)
        dao.insertBoxOpens(List(30) { k ->
            BoxOpen(id = -(k + 1L), time = now - rnd.nextLong(WEEK_MS - 60_000), afterDelivery = k < 21)
        })
    }

    /**
     * Ödev için kayıtları CSV'ye yazar: uygulama adı, paket, kategori, içerik türü, karar kuralı,
     * karar nedeni, zaman. **Bildirim başlığı ve metni yazılmaz**; nedendeki tırnaklı metin
     * parçası (ör. kumar sözlüğünün eşleştiği kelime) da "…" ile gizlenir. Örnek veri dışarıda kalır.
     * Dosya: Android/data/com.suzgec.app/files/ (adb pull ile alınır).
     */
    suspend fun exportCsv(context: Context, dao: NotificationDao): File {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "suzgec_kayit_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.ROOT).format(Date())}.csv")
        val rows = dao.allForExport().filterNot { it.key.startsWith("ornek|") }
        file.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("﻿") // Excel Türkçe karakterleri doğru açsın
            w.write(CSV_HEADER.joinToString(",") + "\n")
            for (r in rows) w.write(csvRow(r, fmt).joinToString(",") { csv(it) } + "\n")
        }
        exportOrderPairs(dir, dao)
        return file
    }

    /** Kayıt CSV'sinin sütunları; [csvRow] ile aynı sayıda ve sırada olmalı (CsvHeaderTest). */
    val CSV_HEADER = listOf(
        "zaman", "uygulama", "paket", "kategori", "icerik_turu", "kural", "neden", "bekleme_ms",
        "panelden_silindi", "kullanici_tasidi", "kutudan_cikis", "kutudan_cikis_yolu",
    )

    /** Bir kaydın CSV satırı (bildirim başlığı ve metni yok). */
    fun csvRow(r: NotificationRecord, fmt: SimpleDateFormat): List<String> = listOf(
        fmt.format(Date(r.postTime)), r.appLabel, r.packageName, r.suzgecCategory.orEmpty(),
        r.contentType?.toString().orEmpty(), r.rule.orEmpty(), cleanReason(r.rule, r.reason, r.suzgecCategory),
        r.waitedMs.toString(), if (r.cancelledBySuzgec) "1" else "0", if (r.restoredByUser) "1" else "0",
        r.dismissedAt?.let { fmt.format(Date(it)) }.orEmpty(), r.dismissMethod.orEmpty(),
    )

    /**
     * Sürükleyerek sıralama çiftleri ayrı dosyada ("A, B'nin üstünde"). Bildirim metni yok; gönderen karmasının
     * yalnız ilk 12 karakteri (aynı göndericiyi gruplamaya yeter).
     */
    private suspend fun exportOrderPairs(dir: File, dao: NotificationDao): File {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
        val file = File(dir, "suzgec_siralama_ciftleri_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.ROOT).format(Date())}.csv")
        file.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("﻿")
            w.write("zaman,kategori,ustteki_kayit,ustteki_uygulama,ustteki_gonderen,alttaki_kayit,alttaki_uygulama,alttaki_gonderen\n")
            for (p in dao.allOrderPairs()) {
                w.write(
                    listOf(
                        fmt.format(Date(p.time)), p.category, p.aboveId.toString(), p.aboveApp, p.aboveSender.take(12),
                        p.belowId.toString(), p.belowApp, p.belowSender.take(12),
                    ).joinToString(",") { csv(it) } + "\n"
                )
            }
        }
        return file
    }

    private val QUOTED = Regex("\"[^\"]*\"")

    fun sanitizeReason(reason: String?): String = reason.orEmpty().replace(QUOTED, "\"…\"")

    private val CAT_TR = mapOf("KRITIK" to "Kritik", "ONEMLI" to "Önemli", "NORMAL" to "Normal", "ONEMSIZ" to "Önemsiz", "SILINENLER" to "Silinenler")
    private val RULE_TEXT = mapOf(
        "guvenlik" to "Güvenlik kuralı", "gizli_icerik" to "Gizli tek kullanımlık kod", "kumar" to "Kumar filtresi",
        "birebir_sohbet" to "Birebir sohbet mesajı", "grup_sohbeti" to "Grup sohbeti mesajı",
        "grup_anahtar" to "Grup sohbeti, anahtar kelime", "oncelikli_kampanya" to "Öncelikli uygulama, kampanya → Önemsiz",
        "oncelikli_kumar" to "Öncelikli uygulama, model kumar → Silinenler", "panelde_birak" to "Panelde bırak",
        "haber" to "Haber uygulaması", "eposta_linkedin" to "E-posta, iş ağı/iş ilanı gönderen → Önemsiz",
        "eposta_mulakat" to "E-posta, mülakat/görüşme → Önemli", "toplu_kutlama" to "Toplu kutlama mesajı",
        "rezervasyon" to "Rezervasyon → Önemli", "ozet_silindi" to "Dokunulmaz: grup özeti, özet silindi",
    )
    private val UNTOUCHED = Regex("Dokunulmaz: (kalıcı bildirim|ön plan servisi|grup özeti|kategori: [a-z_]+|medya oturumu|korunan uygulama|acil durum uyarısı)")
    private val MODEL_KEEP = listOf(
        Regex("Model: [a-zçğıöşü/ ]+"), Regex("%\\d+"), Regex("senin düzeltmen [+-]\\d+(,\\d+)?( → \\d+(,\\d+)?)?"),
        Regex("emin değil.*"), Regex("(→ )?(kumar|alışveriş|sosyal medya) filtresi"), Regex("(kampanya|sosyal etkileşim), Önemsiz"),
        Regex("kumar sayılmadı"),
    )

    /**
     * CSV'nin "neden" sütunu (CLAUDE.md §0 gizlilik, 25 Kasım): beyaz liste. Yalnız kural adı, içerik türü ve
     * olasılık kalır; gönderen/kanal adı, tırnaklı ifade, başlık ya da metin parçası yazılmaz.
     */
    fun cleanReason(rule: String?, reason: String?, category: String?): String {
        val r = reason.orEmpty()
        val cat = CAT_TR[category] ?: category.orEmpty()
        if (r.startsWith("Sen taşıdın")) return "Sen taşıdın → $cat"
        if (r.startsWith("Silinenler'den geri alındı")) return "Silinenler'den geri alındı → $cat"
        return when (val k = rule.orEmpty()) {
            "dokunma" -> UNTOUCHED.matchEntire(r)?.let { "Dokunulmaz: " + it.groupValues[1] } ?: "Dokunulmaz"
            "oncelikli_uygulama" -> "Öncelikli uygulama → $cat"
            in RULE_TEXT -> RULE_TEXT.getValue(k)
            "model" -> {
                val stripped = r.replace(QUOTED, "").replace(Regex("\\s*\\([^)]*\\)"), "")
                val keep = stripped.split(';', ',').map { it.trim() }.filter { p -> p.isNotEmpty() && MODEL_KEEP.any { it.matches(p) } }
                if (keep.isEmpty()) "Model" else keep.joinToString(", ")
            }
            else -> "Kural: $k"
        }
    }

    private fun csv(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
