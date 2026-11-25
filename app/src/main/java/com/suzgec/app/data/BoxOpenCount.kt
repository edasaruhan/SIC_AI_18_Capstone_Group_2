package com.suzgec.app.data

/**
 * Kutuya giriş sayımı (25 Kasım): uygulama her ekrana gelişinde (onResume) giriş sayılıyordu;
 * birkaç saniye arayla girişler sayıyı şişiriyordu. Önceki girişten 5 dakika geçmeden yapılan giriş
 * yeni giriş sayılmaz. Tek istisna: teslimden sonraki ilk giriş (teslime yanıt olarak bakış) her zaman sayılır.
 */
object BoxOpenCount {
    const val MIN_GAP_MS = 5 * 60 * 1000L

    fun shouldCount(last: BoxOpen?, now: Long, afterDelivery: Boolean): Boolean =
        last == null || now - last.time >= MIN_GAP_MS || (afterDelivery && !last.afterDelivery)
}
