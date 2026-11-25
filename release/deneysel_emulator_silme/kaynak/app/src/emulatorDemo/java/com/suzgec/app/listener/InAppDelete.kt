package com.suzgec.app.listener

import com.suzgec.app.emulatordemo.EmulatorInAppDelete

/** emulatorDemo varyantı: "uygulamadan da sil" demosu (yalnız emülatörde, yalnız Demo Gönderici). */
object InAppDelete {
    val hook: InAppDeleteHook = EmulatorInAppDelete
}
