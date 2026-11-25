package com.suzgec.app.listener

/**
 * Beklemeden (erken) karar verilen bildirimler (25 Kasım): aynı key 5 sn içinde yeniden erken karar alırsa
 * (bildirim güncellendi) kayıt güncellenir ama ikinci kez uyarılmaz. Bugün 5 sn bekleme bu güncellemeleri
 * tek karara topluyordu; erken kararda çift titreşim olmasın diye. Android'den bağımsız, birim testli.
 */
class EarlyGate {
    private val last = HashMap<String, Long>()

    /** Bu key için [windowMs] içinde ilk erken karar mı? Zamanı kaydeder. */
    @Synchronized
    fun firstWithin(key: String, now: Long, windowMs: Long): Boolean {
        val t = last.put(key, now)
        if (last.size > 500) last.entries.removeAll { now - it.value >= windowMs }
        return t == null || now - t >= windowMs
    }
}
