package com.suzgec.app.sound

/**
 * Aynı sohbetten kısa sürede gelen yeni mesajlar tekrar ses/titreşim çalmasın (25 Kasım).
 * Sohbet anahtarı: paket + kısayol kimliği (mesajlaşma uygulamaları sohbet başına bir kısayol kullanır),
 * yoksa bildirim key'i. Süre, son ÇALINAN uyarıdan sayılır: sürekli mesaj gelse de en geç 2 dakikada bir uyarır.
 * Kritik her zaman çalar (aynı bankadan art arda gelen kodlar), ama sayacı başlatır.
 */
class AlertThrottle(private val windowMs: Long = WINDOW_MS) {
    private val last = HashMap<String, Long>()

    /** Uyarı çalınabilir mi? Çalınacaksa zamanı kaydeder. [force]: Kritik, her zaman çalar. */
    @Synchronized
    fun allow(conversation: String, now: Long, force: Boolean = false): Boolean {
        val t = last[conversation]
        if (!force && t != null && now - t < windowMs) return false
        last[conversation] = now
        if (last.size > 500) last.entries.removeAll { now - it.value >= windowMs }
        return true
    }

    companion object {
        const val WINDOW_MS = 2 * 60 * 1000L

        fun conversationKey(pkg: String, shortcutId: String?, key: String): String =
            if (!shortcutId.isNullOrEmpty()) "$pkg|s:$shortcutId" else "k:$key"
    }
}
