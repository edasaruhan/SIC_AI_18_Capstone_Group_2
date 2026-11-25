package com.suzgec.app.emulatordemo

import com.suzgec.app.DeviceKind
import com.suzgec.app.emulatordemo.InAppDeletePolicy.Verdict
import com.suzgec.app.rules.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** emulatorDemo "uygulamadan da sil" kilitleri (CLAUDE.md §0 teknik kilit). */
class InAppDeletePolicyTest {
    private val sender = "com.suzgec.sender"
    private val sil = listOf("Sil")

    private fun decide(
        pkg: String = sender,
        actions: List<String?> = sil,
        category: Category = Category.SILINENLER,
        type: Int? = 8,
        emulator: Boolean = true,
        enabled: Boolean = true,
        fired: Boolean = false,
    ) = InAppDeletePolicy.decide(pkg, actions, category, type, emulator, enabled, fired).verdict

    @Test
    fun izinListesiTekElemanVeYalnizDemoGonderici() {
        assertEquals(setOf("com.suzgec.sender"), InAppDeletePolicy.ALLOWED_PACKAGES)
        assertEquals("Sil", InAppDeletePolicy.ACTION_TITLE)
        for (p in listOf(
            "com.whatsapp", "com.samsung.android.messaging", "com.google.android.apps.messaging",
            "org.telegram.messenger", "com.microsoft.office.outlook", "com.suzgec.app",
            "com.suzgec.sender.sahte", "com.suzgec", "COM.SUZGEC.SENDER", "",
        )) {
            assertEquals(p, Verdict.IZINSIZ_PAKET, decide(pkg = p))
        }
        assertEquals(Verdict.SIL, decide())
    }

    @Test
    fun gercekCihazdaHicbirSeyYapilmaz() {
        assertEquals(Verdict.GERCEK_CIHAZ, decide(emulator = false))
        assertEquals(Verdict.GERCEK_CIHAZ, decide(emulator = false, enabled = false))
        // M51 benzeri parmak izi emülatör sayılmaz.
        assertFalse(DeviceKind.isEmulator("samsung/m51nsxx/m51:12/x:user/release-keys", "qcom", "SM-M515F", "m51nsxx", null))
    }

    @Test
    fun anahtarKapalikenHicbirSeyYapilmaz() {
        assertEquals(Verdict.KAPALI, decide(enabled = false))
    }

    @Test
    fun kumarDisiKarardaHicbirSeyYapilmaz() {
        // anne: birebir mesaj, Önemli
        assertEquals(Verdict.KUMAR_DEGIL, decide(category = Category.ONEMLI, type = 2))
        // bonus_kart: kampanya, Önemsiz
        assertEquals(Verdict.KUMAR_DEGIL, decide(category = Category.ONEMSIZ, type = 7))
        // alışveriş filtresiyle Silinenler'e giden kampanya: kumar değil
        assertEquals(Verdict.KUMAR_DEGIL, decide(category = Category.SILINENLER, type = 7))
        // sosyal medya filtresiyle Silinenler
        assertEquals(Verdict.KUMAR_DEGIL, decide(category = Category.SILINENLER, type = 9))
        // Kritik, Normal, Dokunulmaz ve türü bilinmeyen
        assertEquals(Verdict.KUMAR_DEGIL, decide(category = Category.KRITIK, type = 1))
        assertEquals(Verdict.KUMAR_DEGIL, decide(category = Category.NORMAL, type = 3))
        assertEquals(Verdict.KUMAR_DEGIL, decide(category = Category.DOKUNMA, type = null))
        assertEquals(Verdict.KUMAR_DEGIL, decide(category = Category.SILINENLER, type = null))
    }

    @Test
    fun yalnizSilBasligiVeBirKez() {
        assertEquals(Verdict.EYLEM_YOK, decide(actions = emptyList()))
        assertEquals(Verdict.EYLEM_YOK, decide(actions = listOf("Yanıtla", "Okundu", "sil", "Sil ", null)))
        val r = InAppDeletePolicy.decide(sender, listOf("Yanıtla", "Sil"), Category.SILINENLER, 8, true, true, false)
        assertEquals(Verdict.SIL, r.verdict)
        assertEquals(1, r.actionIndex)
        assertEquals(Verdict.ZATEN_SILINDI, decide(fired = true))
    }
}
