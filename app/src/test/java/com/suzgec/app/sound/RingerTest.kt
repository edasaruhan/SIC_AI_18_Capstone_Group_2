package com.suzgec.app.sound

import android.media.AudioManager.RINGER_MODE_NORMAL
import android.media.AudioManager.RINGER_MODE_SILENT
import android.media.AudioManager.RINGER_MODE_VIBRATE
import com.suzgec.app.delivery.Delivery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Zil modunun kaynağı (§4.2): sistem yayını → DND kapalıysa AudioManager → son kayıt. */
class RingerTest {

    @Test
    fun sistemYayiniVarsaOKullanilir() {
        val r = Ringer.choose(internal = RINGER_MODE_VIBRATE, dndOff = false, external = RINGER_MODE_SILENT, saved = RINGER_MODE_NORMAL)
        assertEquals(RINGER_MODE_VIBRATE, r.mode)
        assertEquals("sistem", r.source)
    }

    @Test
    fun yenidenBaslatmaSonrasiYayinYoksaSonKayit() {
        // DND açık: AudioManager hep SESSİZ der; son kaydedilen titreşim kullanılmalı.
        val r = Ringer.choose(internal = -1, dndOff = false, external = RINGER_MODE_SILENT, saved = RINGER_MODE_VIBRATE)
        assertEquals(RINGER_MODE_VIBRATE, r.mode)
        assertFalse(r.reliable) // tahmin: son kayıt olarak üzerine yazılmaz
    }

    @Test
    fun dndKapaliysaAudioManagerGuvenilir() {
        val r = Ringer.choose(internal = -1, dndOff = true, external = RINGER_MODE_NORMAL, saved = RINGER_MODE_VIBRATE)
        assertEquals(RINGER_MODE_NORMAL, r.mode)
    }

    @Test
    fun teslimMetni() {
        assertEquals("Bildirim Kutunuz Temiz", Delivery.summaryText(0, 0))
        assertEquals("Bildirimleriniz var! 3 Normal, 12 Önemsiz", Delivery.summaryText(3, 12))
        assertEquals("Bildirimleriniz var! 2 Önemsiz", Delivery.summaryText(0, 2))
    }
}
