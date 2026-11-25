package com.suzgec.app.sound

import android.media.AudioManager
import com.suzgec.app.rules.Category
import org.junit.Assert.assertEquals
import org.junit.Test

/** Kritik/Önemli uyarısı kullanıcının zil moduna uyar (CLAUDE.md §4.2). */
class AlertPlanTest {

    @Test
    fun normalModdaSesVeTitresim() {
        for (c in listOf(Category.KRITIK, Category.ONEMLI)) {
            assertEquals(AlertPlan(sound = true, vibrate = true), AlertPlan.of(c, AudioManager.RINGER_MODE_NORMAL))
        }
    }

    @Test
    fun titresimModundaYalnizTitresim() {
        for (c in listOf(Category.KRITIK, Category.ONEMLI)) {
            assertEquals(AlertPlan(sound = false, vibrate = true), AlertPlan.of(c, AudioManager.RINGER_MODE_VIBRATE))
        }
    }

    @Test
    fun sessizModdaHicbiri() {
        for (c in listOf(Category.KRITIK, Category.ONEMLI)) {
            assertEquals(AlertPlan(sound = false, vibrate = false), AlertPlan.of(c, AudioManager.RINGER_MODE_SILENT))
        }
    }

    @Test
    fun digerKategorilerUyarmaz() {
        for (c in listOf(Category.NORMAL, Category.ONEMSIZ, Category.SILINENLER)) {
            assertEquals(AlertPlan(sound = false, vibrate = false), AlertPlan.of(c, AudioManager.RINGER_MODE_NORMAL))
        }
    }
}
