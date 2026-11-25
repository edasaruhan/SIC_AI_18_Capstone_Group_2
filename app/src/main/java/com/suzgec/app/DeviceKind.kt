package com.suzgec.app

import android.os.Build

/**
 * Emülatör mü, gerçek cihaz mı (CLAUDE.md §0 teknik kilit göstergeleri). Gerçek cihazda riskli
 * geliştirici seçenekleri (ör. örnek haftalık veri) varsayılan olarak kapalıdır.
 */
object DeviceKind {
    fun isEmulator(): Boolean = isEmulator(
        Build.FINGERPRINT.orEmpty(), Build.HARDWARE.orEmpty(), Build.MODEL.orEmpty(), Build.PRODUCT.orEmpty(),
        systemProperty("ro.kernel.qemu"),
    )

    /** Saf karar (birim testli). */
    fun isEmulator(fingerprint: String, hardware: String, model: String, product: String, qemu: String?): Boolean =
        qemu == "1" ||
            fingerprint.startsWith("generic") || fingerprint.contains("emulator") || fingerprint.contains("/sdk_gphone") ||
            hardware == "goldfish" || hardware == "ranchu" ||
            model.contains("sdk_gphone") || model.contains("Emulator") || model.contains("Android SDK built for") ||
            product.startsWith("sdk_gphone") || product.startsWith("sdk_") || product.contains("emulator")

    private fun systemProperty(name: String): String? = try {
        @Suppress("PrivateApi")
        val c = Class.forName("android.os.SystemProperties")
        c.getMethod("get", String::class.java).invoke(null, name) as? String
    } catch (e: Exception) {
        null
    }
}
