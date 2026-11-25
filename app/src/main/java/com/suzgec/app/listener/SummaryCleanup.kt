package com.suzgec.app.listener

/**
 * Altı boşalan grup özetini silme (25 Kasım). WhatsApp gibi uygulamalar grup özetinin içinde (InboxStyle)
 * bütün sohbetlerin son satırlarını taşır. Süzgeç sohbet bildirimlerini panelden silince özet tek başına
 * kalır ve silinen (kumar, Normal...) satırlar özeti açınca görünür. Başka uygulamanın bildirim içeriği
 * değiştirilemez; bu yüzden yalnız şu durumda özet de silinir:
 * - özetin grubunda panelde HİÇ özet olmayan bildirim kalmamış (Önemli/Kritik/dokunulmaz/5 sn bekleyen
 *   bir sohbet bildirimi varsa özete dokunulmaz; özet silinince Android alttakileri de kaldırır,
 *   REASON_GROUP_SUMMARY_CANCELED), ve
 * - o gruptan son [WINDOW_MS] içinde Süzgeç en az bir bildirimi kendisi silmiş (uygulamanın kendi tek başına
 *   özetlerine dokunulmaz).
 * Android'den bağımsız, birim testli.
 */
object SummaryCleanup {
    const val WINDOW_MS = 5 * 60 * 1000L

    /** Panelde duran bir bildirimin bu karar için gereken özeti. */
    data class PanelItem(val key: String, val groupKey: String, val isSummary: Boolean)

    /**
     * Silinecek özet key'leri.
     * @param lastSuzgecCancel bu grupta Süzgeç'in son silme zamanı (elapsedRealtime); yoksa null.
     */
    fun summariesToCancel(active: List<PanelItem>, groupKey: String, lastSuzgecCancel: Long?, now: Long): List<String> {
        if (lastSuzgecCancel == null || now - lastSuzgecCancel > WINDOW_MS) return emptyList()
        val group = active.filter { it.groupKey == groupKey }
        if (group.any { !it.isSummary }) return emptyList()
        return group.filter { it.isSummary }.map { it.key }
    }
}
