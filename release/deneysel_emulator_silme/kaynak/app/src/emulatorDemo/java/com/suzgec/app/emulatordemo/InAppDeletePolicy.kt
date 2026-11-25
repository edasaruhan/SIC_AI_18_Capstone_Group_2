package com.suzgec.app.emulatordemo

import com.suzgec.app.rules.Category

/**
 * "Uygulamadan da sil" demosunun saf karar kuralı (birim testli; yalnız emulatorDemo varyantında).
 *
 * Üç kilit:
 * 1. Hedef: paket adı TAM OLARAK [ALLOWED_PACKAGES] içindeki tek paket (Demo Gönderici) ve başlığı [ACTION_TITLE]
 *    olan bildirim eylemi. Başka hiçbir paket için kod yolu yok.
 * 2. Varyant: bu kod yalnız `src/emulatorDemo/` altında (standart APK'da bu sınıf yok).
 * 3. Çalışma zamanı: emülatör değilse ya da Ayarlar'daki anahtar kapalıysa hiçbir şey yapılmaz.
 * Ayrıca yalnız kumar kararı (Silinenler + içerik türü 8) tetikler; her bildirim için en fazla bir kez.
 */
object InAppDeletePolicy {
    /** İzin listesi: tek eleman. Testle sabitlenmiştir. */
    val ALLOWED_PACKAGES: Set<String> = setOf("com.suzgec.sender")

    /** Tetiklenebilecek tek eylem başlığı. */
    const val ACTION_TITLE = "Sil"

    /** Model / kural içerik türü: kumar (CLAUDE.md §6, tür 8). */
    const val GAMBLING_TYPE = 8

    enum class Verdict { SIL, GERCEK_CIHAZ, KAPALI, IZINSIZ_PAKET, KUMAR_DEGIL, EYLEM_YOK, ZATEN_SILINDI }

    data class Result(val verdict: Verdict, val actionIndex: Int = -1)

    fun decide(
        packageName: String,
        actionTitles: List<String?>,
        category: Category,
        contentType: Int?,
        isEmulator: Boolean,
        enabled: Boolean,
        alreadyFired: Boolean,
    ): Result {
        if (!isEmulator) return Result(Verdict.GERCEK_CIHAZ)
        if (!enabled) return Result(Verdict.KAPALI)
        if (packageName !in ALLOWED_PACKAGES) return Result(Verdict.IZINSIZ_PAKET)
        if (category != Category.SILINENLER || contentType != GAMBLING_TYPE) return Result(Verdict.KUMAR_DEGIL)
        val idx = actionTitles.indexOfFirst { it == ACTION_TITLE }
        if (idx < 0) return Result(Verdict.EYLEM_YOK)
        if (alreadyFired) return Result(Verdict.ZATEN_SILINDI)
        return Result(Verdict.SIL, idx)
    }
}
