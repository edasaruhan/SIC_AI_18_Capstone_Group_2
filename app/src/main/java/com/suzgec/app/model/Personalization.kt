package com.suzgec.app.model

import com.suzgec.app.rules.Category
import java.security.MessageDigest

/**
 * Kişiselleştirme v1 (CLAUDE.md §7). Kullanıcının kategori düzeltmeleri
 * (gönderen karması, uygulama, içerik türü) anahtarıyla sayılır:
 *
 *   delta = eta × (n_yukarı − n_aşağı) / (n_yukarı + n_aşağı + k),  k = 2, eta = 1
 *
 * delta bir kategori basamağı cinsindendir ve önem puanına eklenir.
 * Örnek: aynı türden bildirimi iki kez yukarı taşımak → delta = 2/4 = 0,5
 * (Normal 2,0 → 2,5 → Önemli).
 */
object Personalization {
    const val K = 2.0
    const val ETA = 1.0

    fun delta(up: Int, down: Int): Double = ETA * (up - down) / (up + down + K)

    /** Kategorilerin basamak sırası; Silinenler en altta (geri alma = yukarı). */
    fun rank(category: String): Int? = when (category) {
        Category.KRITIK.name -> 4
        Category.ONEMLI.name -> 3
        Category.NORMAL.name -> 2
        Category.ONEMSIZ.name -> 1
        Category.SILINENLER.name -> 0
        else -> null
    }

    /** Gönderen kimliği düz metin saklanmaz (§7): SHA-256, küçük harf ve boşluksuz. */
    fun senderHash(sender: String?): String {
        val s = sender?.trim()?.lowercase().orEmpty()
        if (s.isEmpty()) return ""
        val d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }
}

/** Düzeltme anahtarı. [app] uygulama etiketi (debug'da :sender'ın taklit adı da ayrışsın diye). */
data class PersonalKey(val senderHash: String, val app: String, val contentType: Int)

data class Counts(val up: Int, val down: Int) {
    val delta: Double get() = Personalization.delta(up, down)
}
