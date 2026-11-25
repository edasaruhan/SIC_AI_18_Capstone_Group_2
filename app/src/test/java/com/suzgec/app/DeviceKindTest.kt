package com.suzgec.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Örnek haftalık veri gerçek cihazda kapalı: emülatör algılaması (CLAUDE.md §0 göstergeleri). */
class DeviceKindTest {

    @Test
    fun m51GercekCihaz() {
        assertFalse(
            DeviceKind.isEmulator(
                "samsung/m51nsxx/m51:12/SP1A.210812.016/M515FXXU5DVL1:user/release-keys", "qcom", "SM-M515F", "m51nsxx", "",
            )
        )
        assertFalse(DeviceKind.isEmulator("samsung/m51nsxx/m51:12/x:user/release-keys", "qcom", "SM-M515F", "m51nsxx", null))
    }

    @Test
    fun emulatorGostergeleri() {
        assertTrue(DeviceKind.isEmulator("x", "x", "x", "x", "1"))
        assertTrue(DeviceKind.isEmulator("google/sdk_gphone64_x86_64/emu64xa:16/x:userdebug/dev-keys", "ranchu", "sdk_gphone64_x86_64", "sdk_gphone64_x86_64", null))
        assertTrue(DeviceKind.isEmulator("generic/sdk/generic:8.0.0/x:eng/test-keys", "goldfish", "Android SDK built for x86", "sdk", null))
    }
}
